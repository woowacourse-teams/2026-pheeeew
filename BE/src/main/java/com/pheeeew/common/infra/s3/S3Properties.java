package com.pheeeew.common.infra.s3;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pheeeew.s3")
public record S3Properties(String bucket, String keyPrefix, String region) {

    public S3Properties {
        requireValue(bucket, "S3_BUCKET");
        requireValue(keyPrefix, "S3_KEY_PREFIX");
        requireValue(region, "S3_REGION");

        if (bucket.contains("/") || bucket.contains(":")) {
            throw new IllegalArgumentException("S3_BUCKET에는 URL이나 경로가 아닌 버킷 이름만 지정해야 합니다.");
        }
        if (keyPrefix.startsWith("/") || !keyPrefix.endsWith("/")
                || keyPrefix.contains("//") || keyPrefix.contains("\\") || keyPrefix.contains(":")) {
            throw new IllegalArgumentException("S3_KEY_PREFIX는 상대 경로이며 /로 끝나야 합니다.");
        }
        for (String segment : keyPrefix.split("/")) {
            if (segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("S3_KEY_PREFIX에는 . 또는 .. 경로를 사용할 수 없습니다.");
            }
        }
    }

    private static void requireValue(String value, String name) {
        if (value == null || value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(name + "는 앞뒤 공백 없이 지정해야 합니다.");
        }
    }
}
