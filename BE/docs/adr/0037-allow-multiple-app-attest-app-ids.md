# ADR-0037: App Attest App ID 를 허용 목록으로 받는다

## Status

Accepted (2026-10-14)

## Context

- Apple 개발자 계정을 이전했어요. 새 계정의 App ID prefix 는 `WKN466N42M` 이고 Bundle ID 는 `com.itda.pheeeew` 로 그대로예요.
- App Store 에 나간 앱은 이전 계정(`Z6V4SXKRL6`)으로 빌드돼 있어요. 업데이트를 내도 구버전을 쓰는 기기가 남아요.
- 검증기는 App ID 하나의 SHA-256 해시만 `authenticatorData.rpIdHash` 와 비교했어요. 설정을 새 prefix 하나로 교체하면 **구버전의 신규 기기 등록이 전부 거절돼요.**
- 기존 세션의 토큰 갱신은 App Attest 를 다시 요구하지 않아요. 그래서 깨지는 것은 신규 등록뿐이에요.
- `team-id` 는 `application.yml` 리터럴이고 환경변수로 주입하지 않아요. 프로덕션 참조도 검증기 한 곳이에요.

## Decision

설정을 `team-ids` 목록으로 바꾸고, **그중 하나의 App ID 해시와 맞으면 통과**시켜요. 인증서 체인, nonce, keyId, counter, 환경 검증은 그대로 두고 비교도 `MessageDigest.isEqual` 을 유지해요.

## Alternatives

- 새 prefix 하나로 교체 — 구버전의 신규 등록이 즉시 거절돼요. 업데이트가 전부 퍼질 때까지 쓸 수 없어요.
- `team-id` 를 두고 `legacy-team-ids` 를 따로 추가 — 지원을 끝낼 때 속성 하나를 통째로 없애야 하고, 검증기가 둘 중 어느 쪽이 옛것인지 알 필요가 없어요.
- 번들 ID 까지 포함한 App ID 전체를 목록으로 받기 — 지금 바뀌는 것은 prefix 뿐이라 같은 번들 ID 를 두 번 적게 돼요.

## Consequences

- (+) 두 계정의 빌드가 함께 쓰이는 동안 어느 쪽이든 신규 등록이 돼요. 서버를 먼저 배포하면 TestFlight 와 심사를 막지 않아요.
- (+) 지원을 끝낼 때 목록에서 한 줄만 지워요. 코드는 안 바꿔요.
- (-) **통과하는 App ID 가 둘로 늘어요.** 허용 목록에 적은 만큼 검증이 넓어지니, 쓰지 않는 prefix 를 남겨 두면 그만큼 느슨해져요.
- (-) 비교 횟수가 항목 수만큼 늘어요. 등록 요청마다 해시를 두 번 계산해요.
- (=) 같은 prefix 를 두 번 적거나 형식이 어긋나면 부팅을 실패시켜요. 목록이 되면서 중복이 조용히 통과할 수 있어 막았어요.
- 재검토: **이전 계정 빌드의 지원을 끝내면** `Z6V4SXKRL6` 을 지워요. 앱 심사가 끝나고 구버전 사용자가 사라진 시점을 iOS 쪽과 함께 판단해요.

## Compliance

- 허용 목록의 첫 항목이 아니어도 통과하는지는 `AppAttestRegistrationIntegrationTest` 가 고정해요. 맞지 않는 prefix 를 앞에 두고 등록이 성공하는 것을 봐요.
- 목록 어디에도 없는 App ID 가 `APP_ID_MISMATCH` 로 거절되는지도 같은 곳에서 고정해요.
- 설정 파일이 두 prefix 를 담고 있는지는 `AppAttestPropertiesTest` 가 실제 `application.yml` 을 읽어 고정해요.
- 신규 계정으로 서명된 실제 증명의 통과는 자동 테스트로 확인할 수 없어요. TestFlight 빌드로 개발 서버에서 확인해요.
