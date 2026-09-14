package com.pheeeew.device.infra.attestation.appattest;

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class AppAttestObjectDecoder {

    private static final int MAX_ATTESTATION_BASE64_LENGTH = 32_768;
    private static final int MAX_NESTING_DEPTH = 8;
    private static final int MAX_STRING_LENGTH = 65_536;
    private static final String FORMAT_KEY = "fmt";
    private static final String STATEMENT_KEY = "attStmt";
    private static final String CERTIFICATE_CHAIN_KEY = "x5c";
    private static final String AUTHENTICATOR_DATA_KEY = "authData";
    private static final String CERTIFICATE_TYPE = "X.509";

    private final CBORMapper cborMapper;

    public AppAttestObjectDecoder() {
        this.cborMapper = new CBORMapper(CBORFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .maxStringLength(MAX_STRING_LENGTH)
                        .build())
                .build());
    }

    public byte[] decodeToken(String token) {
        if (token.length() > MAX_ATTESTATION_BASE64_LENGTH) {
            throw new IllegalArgumentException("무결성 증명 객체가 너무 깁니다.");
        }
        return Base64.getDecoder().decode(token);
    }

    public AppAttestObject decode(byte[] attestationObject) {
        JsonNode root = readTree(attestationObject);

        return AppAttestObject.of(
                formatOf(root),
                certificatesOf(root),
                binaryOf(root.get(AUTHENTICATOR_DATA_KEY))
        );
    }

    private JsonNode readTree(byte[] attestationObject) {
        try {
            return cborMapper.readTree(attestationObject);
        } catch (IOException exception) {
            throw new IllegalArgumentException("무결성 증명 객체를 읽지 못했습니다.");
        }
    }

    private String formatOf(JsonNode root) {
        JsonNode format = root.get(FORMAT_KEY);
        if (format == null || format.textValue() == null) {
            throw new IllegalArgumentException("무결성 증명 객체에 형식이 없습니다.");
        }
        return format.textValue();
    }

    private List<X509Certificate> certificatesOf(JsonNode root) {
        JsonNode statement = root.get(STATEMENT_KEY);
        if (statement == null) {
            throw new IllegalArgumentException("무결성 증명 객체에 증명 구문이 없습니다.");
        }

        JsonNode chain = statement.get(CERTIFICATE_CHAIN_KEY);
        if (chain == null || !chain.isArray() || chain.isEmpty()) {
            throw new IllegalArgumentException("무결성 증명 객체에 인증서 체인이 없습니다.");
        }

        CertificateFactory certificateFactory = certificateFactory();
        List<X509Certificate> certificates = new ArrayList<>();
        for (JsonNode certificate : chain) {
            certificates.add(parseCertificate(certificateFactory, binaryOf(certificate)));
        }

        return List.copyOf(certificates);
    }

    private CertificateFactory certificateFactory() {
        try {
            return CertificateFactory.getInstance(CERTIFICATE_TYPE);
        } catch (CertificateException exception) {
            throw new IllegalStateException("X.509 인증서 팩토리를 만들지 못했습니다.", exception);
        }
    }

    private X509Certificate parseCertificate(CertificateFactory certificateFactory, byte[] encoded) {
        try {
            return (X509Certificate) certificateFactory.generateCertificate(new ByteArrayInputStream(encoded));
        } catch (CertificateException exception) {
            throw new IllegalArgumentException("무결성 증명 인증서를 읽지 못했습니다.");
        }
    }

    private byte[] binaryOf(JsonNode node) {
        if (node == null || !node.isBinary()) {
            throw new IllegalArgumentException("무결성 증명 객체의 이진 값이 올바르지 않습니다.");
        }
        try {
            return node.binaryValue();
        } catch (IOException exception) {
            throw new IllegalArgumentException("무결성 증명 객체의 이진 값을 읽지 못했습니다.");
        }
    }
}
