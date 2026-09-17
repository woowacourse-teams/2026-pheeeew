#!/usr/bin/env bash
# App Attest 테스트 픽스처 생성기.
#
# nonce 가 challenge 에 암호학적으로 묶여 있어 런타임에 발급된 challenge 로는 정상 attestation 을
# 만들 수 없다. 그래서 고정 challenge 로 미리 만들어 두고 산출물을 커밋한다.
# JDK 에는 인증서를 발급하는 공개 API 가 없으므로 저장소 바깥에서 openssl 로 만든다.
#
# 실행: bash generate.sh   (openssl 과 python3 가 필요하다. CI 에서는 돌지 않는다.)
set -euo pipefail

cd "$(dirname "$0")"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

TEAM_ID="1234567890"
BUNDLE_ID="com.pheeeew.app"
APP_ID="${TEAM_ID}.${BUNDLE_ID}"
OTHER_APP_ID="${TEAM_ID}.com.attacker.app"
CHALLENGE="appattest-fixture-challenge-000000000000000"

# 합성 루트 CA 와 중간 CA. 유효기간 100년이라 픽스처가 만료로 깨지지 않는다.
openssl ecparam -name secp384r1 -genkey -noout -out "$WORK/root.key" 2>/dev/null
openssl req -x509 -new -key "$WORK/root.key" -sha384 -days 36500 \
    -subj "/ST=California/O=Pheeeew Test/CN=Pheeeew Test App Attestation Root CA" \
    -addext "basicConstraints=critical,CA:TRUE" \
    -addext "keyUsage=critical,keyCertSign,cRLSign" \
    -out synthetic-root-ca.pem 2>/dev/null

openssl ecparam -name prime256v1 -genkey -noout -out "$WORK/intermediate.key" 2>/dev/null
openssl req -new -key "$WORK/intermediate.key" \
    -subj "/ST=California/O=Pheeeew Test/CN=Pheeeew Test App Attestation CA 1" \
    -out "$WORK/intermediate.csr" 2>/dev/null
cat > "$WORK/intermediate.ext" <<EXT
basicConstraints=critical,CA:TRUE,pathlen:0
keyUsage=critical,keyCertSign,cRLSign
EXT
openssl x509 -req -in "$WORK/intermediate.csr" -CA synthetic-root-ca.pem -CAkey "$WORK/root.key" \
    -CAcreateserial -CAserial "$WORK/root.srl" -days 36500 -sha384 -extfile "$WORK/intermediate.ext" \
    -out "$WORK/intermediate.pem" 2>/dev/null
openssl x509 -in "$WORK/intermediate.pem" -outform DER -out "$WORK/intermediate.der"

# 리프 키 하나를 모든 변형이 공유한다. keyId 가 변형마다 달라지면 비교 기준이 흔들린다.
openssl ecparam -name prime256v1 -genkey -noout -out "$WORK/leaf.key" 2>/dev/null
openssl req -new -key "$WORK/leaf.key" \
    -subj "/ST=California/O=Pheeeew Test/OU=AAA Certification/CN=pheeeew-app-attest-fixture" \
    -out "$WORK/leaf.csr" 2>/dev/null

python3 - "$WORK" "$APP_ID" "$OTHER_APP_ID" "$CHALLENGE" <<'PY'
import hashlib, subprocess, sys, base64, re, os

work, app_id, other_app_id, challenge = sys.argv[1:5]

def run(args, **kw):
    return subprocess.run(args, check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.DEVNULL, **kw).stdout

# 리프 공개키의 X9.62 비압축 점. keyId = SHA256(0x04 || X || Y) 다.
pub_text = run(['openssl', 'ec', '-in', f'{work}/leaf.key', '-pubout', '-text', '-noout'], text=True)
point = bytes.fromhex(re.sub(r'[\s:]', '', re.search(r'pub:\n((?:\s+[0-9a-f:]+\n)+)', pub_text).group(1)))
assert len(point) == 65 and point[0] == 4
key_id = hashlib.sha256(point).digest()
spki = run(['openssl', 'ec', '-in', f'{work}/leaf.key', '-pubout', '-outform', 'DER'])
spki_key_id = hashlib.sha256(spki).digest()

PRODUCTION_AAGUID = b'appattest' + bytes(7)
DEVELOPMENT_AAGUID = b'appattestdevelop'
SANDBOX_AAGUID = b'appattestsandbox'


def auth_data(app_id, aaguid, counter=0, credential_id=None, flags=0x40):
    credential_id = key_id if credential_id is None else credential_id
    return (hashlib.sha256(app_id.encode('ascii')).digest()
            + bytes([flags])
            + counter.to_bytes(4, 'big')
            + aaguid
            + len(credential_id).to_bytes(2, 'big')
            + credential_id)


def issue_leaf(name, nonce_der):
    ext = f"""basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,nonRepudiation,keyEncipherment,dataEncipherment
extendedKeyUsage=1.2.840.113635.100.4.24
1.2.840.113635.100.8.2={nonce_der}
1.2.840.113635.100.8.5=DER:30:03:02:01:01
1.2.840.113635.100.8.6=DER:30:03:02:01:01
1.2.840.113635.100.8.7=DER:30:03:02:01:01
"""
    with open(f'{work}/{name}.ext', 'w') as handle:
        handle.write(ext)
    run(['openssl', 'x509', '-req', '-in', f'{work}/leaf.csr',
         '-CA', f'{work}/intermediate.pem', '-CAkey', f'{work}/intermediate.key',
         '-CAcreateserial', '-CAserial', f'{work}/intermediate.srl', '-days', '36500', '-sha256',
         '-extfile', f'{work}/{name}.ext', '-out', f'{work}/{name}.pem'])
    run(['openssl', 'x509', '-in', f'{work}/{name}.pem', '-outform', 'DER',
         '-out', f'{work}/{name}.der'])
    return open(f'{work}/{name}.der', 'rb').read()


def nonce_der(nonce, wrap_with_context_tag=True):
    body = b'\x04\x20' + nonce
    body = b'\xa1' + bytes([len(body)]) + body if wrap_with_context_tag else body
    sequence = b'\x30' + bytes([len(body)]) + body
    return 'DER:' + ':'.join('%02X' % b for b in sequence)


# CBOR 인코딩. 우리가 만드는 값의 모양이 전부 고정이라 최소 인코더로 충분하다.
def cbor(value):
    if isinstance(value, dict):
        out = head(5, len(value))
        for key, item in value.items():
            out += cbor(key) + cbor(item)
        return out
    if isinstance(value, list):
        out = head(4, len(value))
        for item in value:
            out += cbor(item)
        return out
    if isinstance(value, bytes):
        return head(2, len(value)) + value
    if isinstance(value, str):
        encoded = value.encode('utf-8')
        return head(3, len(encoded)) + encoded
    raise TypeError(type(value))


def head(major, length):
    if length < 24:
        return bytes([(major << 5) | length])
    if length < 256:
        return bytes([(major << 5) | 24, length])
    if length < 65536:
        return bytes([(major << 5) | 25]) + length.to_bytes(2, 'big')
    return bytes([(major << 5) | 26]) + length.to_bytes(4, 'big')


intermediate = open(f'{work}/intermediate.der', 'rb').read()
RECEIPT = b'not-a-real-receipt'


def write_attestation(file_name, authenticator_data, leaf_name,
                      nonce_challenge=None, fmt='apple-appattest'):
    nonce_challenge = challenge if nonce_challenge is None else nonce_challenge
    client_data_hash = hashlib.sha256(nonce_challenge.encode('ascii')).digest()
    nonce = hashlib.sha256(authenticator_data + client_data_hash).digest()
    leaf = issue_leaf(leaf_name, nonce_der(nonce))
    encoded = cbor({
        'fmt': fmt,
        'attStmt': {'x5c': [leaf, intermediate], 'receipt': RECEIPT},
        'authData': authenticator_data,
    })
    with open(file_name, 'w') as handle:
        handle.write(base64.b64encode(encoded).decode('ascii'))


write_attestation('production-attestation.txt', auth_data(app_id, PRODUCTION_AAGUID), 'leaf-production')
write_attestation('development-attestation.txt', auth_data(app_id, DEVELOPMENT_AAGUID), 'leaf-development')
write_attestation('sandbox-attestation.txt', auth_data(app_id, SANDBOX_AAGUID), 'leaf-sandbox')
write_attestation('other-app-id-attestation.txt', auth_data(other_app_id, PRODUCTION_AAGUID), 'leaf-other-app')
write_attestation('counter-one-attestation.txt', auth_data(app_id, PRODUCTION_AAGUID, counter=1), 'leaf-counter-one')
write_attestation('credential-id-mismatch-attestation.txt',
                  auth_data(app_id, PRODUCTION_AAGUID, credential_id=bytes(32)), 'leaf-credential-id')
write_attestation('unsupported-format-attestation.txt', auth_data(app_id, PRODUCTION_AAGUID),
                  'leaf-unsupported-format', fmt='packed')

# 87바이트에 한 바이트 모자란 authData. 오프셋을 벗어나는 입력을 거절하는지 본다.
write_attestation('short-auth-data-attestation.txt',
                  auth_data(app_id, PRODUCTION_AAGUID)[:86], 'leaf-short-auth-data')

# 애플 설명이 생략한 문맥 태그 [1](0xA1) 이 없는 nonce 확장. 우리 파서가 거절해야 한다.
plain_auth_data = auth_data(app_id, PRODUCTION_AAGUID)
plain_nonce = hashlib.sha256(plain_auth_data + hashlib.sha256(challenge.encode('ascii')).digest()).digest()
issue_leaf('leaf-without-context-tag', nonce_der(plain_nonce, wrap_with_context_tag=False))
run(['cp', f'{work}/leaf-without-context-tag.pem', 'nonce-extension-without-context-tag.pem'])

with open('fixture-values.txt', 'w') as handle:
    handle.write(f'teamId={sys.argv[2].split(".")[0]}\n')
    handle.write(f'appId={app_id}\n')
    handle.write(f'otherAppId={other_app_id}\n')
    handle.write(f'challenge={challenge}\n')
    handle.write(f'keyId={base64.b64encode(key_id).decode()}\n')
    handle.write(f'spkiKeyId={base64.b64encode(spki_key_id).decode()}\n')
PY

echo "생성 완료:"
ls -1 *.txt *.pem
echo
echo "리프 키를 새로 만들었으므로 fixture-values.txt 의 keyId 와 spkiKeyId 를"
echo "AppAttestFixture 의 키_식별자, DER_공개키_정보를_해시한_키_식별자 에 옮겨 적어야 한다."
