package com.pheeeew.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class SharedPostgisTestConfiguration {

    // 컨테이너가 JVM 당 하나이므로 스키마도 JVM 당 한 번만 만들면 된다.
    private static boolean migrated;

    /**
     * 같은 JVM 의 두 번째 컨텍스트부터 Flyway 를 건너뛴다.
     *
     * <p>컨텍스트가 뜰 때마다 Flyway 가 이미 적용된 스키마를 다시 확인한다. 전체 실행에서
     * 컨텍스트가 105번 뜨고 그 확인이 1회당 약 109ms 다. 컨테이너는 JVM 당 하나이고
     * 마이그레이션 파일도 같으므로 두 번째부터는 결과가 바뀔 수 없다.
     *
     * <p>마이그레이션 자체의 검증은 첫 컨텍스트에서 그대로 수행된다.
     */
    @Bean
    FlywayMigrationStrategy migrateOncePerJvm() {
        return flyway -> {
            // 실패하면 표시를 남기지 않아 다음 컨텍스트가 다시 마이그레이션한다.
            synchronized (SharedPostgisTestConfiguration.class) {
                if (!migrated) {
                    flyway.migrate();
                    migrated = true;
                }
            }
        };
    }

    @Bean
    DynamicPropertyRegistrar postgisProperties() {
        PostgreSQLContainer postgis = SharedTestContainers.postgis();
        return registry -> {
            registry.add("spring.datasource.url", postgis::getJdbcUrl);
            registry.add("spring.datasource.username", postgis::getUsername);
            registry.add("spring.datasource.password", postgis::getPassword);
        };
    }
}
