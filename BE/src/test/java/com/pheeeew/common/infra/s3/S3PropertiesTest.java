package com.pheeeew.common.infra.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class S3PropertiesTest {

    @ParameterizedTest
    @CsvSource({"dev,pheeeew/development/", "prod,pheeeew/production/"})
    void 환경별_저장소_설정이_설정_파일을_통해_연결된다(String profile, String keyPrefix) {
        // given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(S3Config.class)
                .withPropertyValues("spring.profiles.active=" + profile,
                        "S3_BUCKET=techcourse-project-2026", "S3_KEY_PREFIX=" + keyPrefix,
                        "S3_REGION=ap-northeast-2");

        // when / then
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            S3Properties properties = context.getBean(S3Properties.class);
            assertThat(properties.bucket()).isEqualTo("techcourse-project-2026");
            assertThat(properties.keyPrefix()).isEqualTo(keyPrefix);
            assertThat(properties.region()).isEqualTo("ap-northeast-2");
        });
    }

    @ParameterizedTest
    @ValueSource(strings = {"S3_BUCKET", "S3_KEY_PREFIX", "S3_REGION"})
    void 필수_환경_설정이_비어_있으면_바인딩에_실패한다(String missingSetting) {
        // given
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withUserConfiguration(S3Config.class)
                .withPropertyValues("spring.profiles.active=local",
                        "S3_BUCKET=techcourse-project-2026", "S3_KEY_PREFIX=pheeeew/development/",
                        "S3_REGION=ap-northeast-2")
                .withPropertyValues(missingSetting + "=");

        // when / then
        contextRunner.run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseInstanceOf(IllegalArgumentException.class)
                .hasStackTraceContaining(missingSetting));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "NULL,pheeeew/development/,ap-northeast-2",
            "bucket,NULL,ap-northeast-2",
            "bucket,pheeeew/development/,NULL",
            "' ',pheeeew/development/,ap-northeast-2",
            "bucket,' ',ap-northeast-2",
            "bucket,pheeeew/development/,' '",
            "' bucket',pheeeew/development/,ap-northeast-2",
            "bucket,'pheeeew/development/ ',ap-northeast-2",
            "bucket,pheeeew/development/,'ap-northeast-2 '"
    }, nullValues = "NULL")
    void 누락되거나_앞뒤_공백이_있는_설정은_거부한다(String bucket, String keyPrefix, String region) {
        // given / when / then
        assertThatThrownBy(() -> new S3Properties(bucket, keyPrefix, region))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"s3://bucket", "https://bucket.s3.ap-northeast-2.amazonaws.com", "bucket/pheeeew/"})
    void 버킷에_URL이나_객체_경로를_지정할_수_없다(String bucket) {
        // given / when / then
        assertThatThrownBy(() -> new S3Properties(bucket, "pheeeew/development/", "ap-northeast-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("S3_BUCKET");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/", "/pheeeew/development/", "pheeeew/development", "pheeeew//development/",
            "./", "../", "pheeeew/./", "pheeeew/../production/", "pheeeew\\development/", "s3://bucket/"
    })
    void 객체_prefix는_명확한_상대_디렉터리_경로여야_한다(String keyPrefix) {
        // given / when / then
        assertThatThrownBy(() -> new S3Properties("bucket", keyPrefix, "ap-northeast-2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("S3_KEY_PREFIX");
    }
}
