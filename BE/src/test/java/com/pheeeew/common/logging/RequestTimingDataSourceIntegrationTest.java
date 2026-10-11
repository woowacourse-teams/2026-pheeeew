package com.pheeeew.common.logging;

import static com.pheeeew.common.logging.RequestTiming.Stage.DB_CONNECTION_ACQUIRE_FAILED;
import static com.pheeeew.common.logging.RequestTiming.Stage.DB_CONNECTION_ACQUIRE_SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.support.SharedTestContainers;
import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityManagerFactory;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.health.DataSourceHealthContributorAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.metrics.DataSourcePoolMetricsAutoConfiguration;
import org.springframework.boot.jdbc.health.DataSourceHealthIndicator;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RequestTimingDataSourceIntegrationTest {

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @ParameterizedTest
    @ValueSource(strings = {"immediate", "lazy"})
    void 자동_등록이_마이그레이션과_JPA_트랜잭션_및_풀_메트릭과_종료를_보존한다(String fetch) {
        var pool = new AtomicReference<HikariDataSource>();
        runner().withPropertyValues("spring.datasource.connection-fetch=" + fetch).run(context -> {
            // given: 실제 Boot 자동 설정과 Testcontainers DB에서 마이그레이션을 수행한다.
            assertThat(context).hasNotFailed();
            var source = context.getBean(DataSource.class);
            var hikari = source.unwrap(HikariDataSource.class);
            pool.set(hikari);
            var timing = fetch.equals("lazy") ? ((LazyConnectionDataSourceProxy) source).getTargetDataSource() : source;
            assertThat(timing).isInstanceOf(RequestTimingDataSource.class);
            assertThat(((DelegatingDataSource) timing).getTargetDataSource()).isSameAs(hikari);
            assertThat(context.getBean(Flyway.class).getConfiguration().getDataSource()).isSameAs(source);
            var jdbc = new JdbcTemplate(source);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM timing_probe", Long.class)).isZero();
            var registry = context.getBean(MeterRegistry.class);
            double before = registry.get("hikaricp.connections.acquire").timer().count();
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            var entityManager = SharedEntityManagerCreator.createSharedEntityManager(context.getBean(EntityManagerFactory.class));
            var request = bindRequest();

            // when: 트랜잭션 안에서 JDBC도 같은 연결을 재사용하며, 롤백은 데이터에 남지 않는다.
            transaction.executeWithoutResult(status -> {
                entityManager.createNativeQuery("INSERT INTO timing_probe VALUES (1)").executeUpdate();
                assertThat(jdbc.queryForObject("SELECT count(*) FROM timing_probe", Long.class)).isEqualTo(1);
            });
            var failure = new IllegalStateException("rollback probe");
            assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
                entityManager.createNativeQuery("INSERT INTO timing_probe VALUES (2)").executeUpdate();
                throw failure;
            })).isSameAs(failure);

            // then: 트랜잭션당 획득 한 번, 기존 메트릭 한 계열과 풀 설정을 유지한다.
            assertThat(RequestTiming.snapshot(request).get(DB_CONNECTION_ACQUIRE_SUCCESS).count()).isEqualTo(2);
            assertThat(RequestTiming.snapshot(request)).doesNotContainKey(DB_CONNECTION_ACQUIRE_FAILED);
            assertThat(registry.get("hikaricp.connections.acquire").timer().count() - before).isEqualTo(2);
            assertThat(registry.find("hikaricp.connections.acquire").timers()).hasSize(1);
            assertThat(registry.get("hikaricp.connections.usage").timer().count()).isPositive();
            assertThat(registry.get("jdbc.connections.max").gauge().value()).isEqualTo(2);
            assertThat(hikari.getMaximumPoolSize()).isEqualTo(2);
            assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
            RequestContextHolder.resetRequestAttributes();
            assertThat(jdbc.queryForList("SELECT id FROM timing_probe", Long.class)).containsExactly(1L);
        });
        assertThat(pool.get().isClosed()).isTrue();
    }

    @Test
    void health의_획득_timeout을_기록하고_반환_후_정상_조회로_회복한다() {
        runner().run(context -> {
            // given: 풀의 두 연결을 점유한 격리 DB에서 짧은 획득 제한만 검증한다.
            var source = context.getBean(DataSource.class);
            var health = context.getBean("dbHealthContributor", DataSourceHealthIndicator.class);
            var registry = context.getBean(MeterRegistry.class);
            var hikari = source.unwrap(HikariDataSource.class);
            try (var firstHeld = source.getConnection(); var secondHeld = source.getConnection()) {
                var request = bindRequest();
                // when
                var result = health.health();
                // then
                assertThat(result.getStatus().getCode()).isEqualTo("DOWN");
                assertThat(result.getDetails().get("error").toString()).contains(CannotGetJdbcConnectionException.class.getName());
                var failed = RequestTiming.snapshot(request).get(DB_CONNECTION_ACQUIRE_FAILED);
                assertThat(failed.count()).isEqualTo(1);
                assertThat(failed.totalNanos()).isGreaterThanOrEqualTo(200_000_000L);
                assertThat(RequestTiming.snapshot(request)).doesNotContainKey(DB_CONNECTION_ACQUIRE_SUCCESS);
                assertThat(registry.get("hikaricp.connections.timeout").counter().count()).isEqualTo(1);
                RequestContextHolder.resetRequestAttributes();
            }
            var recovered = bindRequest();
            assertThat(health.health().getStatus().getCode()).isEqualTo("UP");
            assertThat(RequestTiming.snapshot(recovered).get(DB_CONNECTION_ACQUIRE_SUCCESS).count()).isPositive();
            assertThat(hikari.getHikariPoolMXBean().getActiveConnections()).isZero();
        });
    }

    @Test
    void 필터_요청의_JDBC_획득이_기존_느린_요청_JSON에_남는다() {
        runner().run(context -> {
            // given: 실제 지연 대신 필터의 시계만 진행시켜 로그 출력 경계를 검증한다.
            var source = context.getBean(DataSource.class);
            var clock = new java.util.concurrent.atomic.AtomicLong();
            var filter = new RequestLoggingFilter(() -> clock.getAndAdd(2_000_000_000L));
            var request = new MockHttpServletRequest("GET", "/probe");
            var capture = StructuredLogCapture.attach(RequestLogWriter.class);
            try {
                // when
                filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
                    RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
                    try {
                        assertThat(new JdbcTemplate(source).queryForObject("SELECT 1", Integer.class)).isEqualTo(1);
                    } finally {
                        RequestContextHolder.resetRequestAttributes();
                    }
                });
                // then: MVC 문맥이 해제돼도 필터에서 요청 속성의 시간을 읽는다.
                var event = capture.onlyEvent();
                assertThat(event).containsEntry("event", "http_request_slow");
                var timings = (Map<?, ?>) event.get("timings");
                var success = (Map<?, ?>) timings.get("db_connection_acquire_success");
                assertThat(((Number) success.get("count")).longValue()).isEqualTo(1);
                assertThat(timings.containsKey("db_connection_acquire_failed")).isFalse();
            } finally {
                capture.detach();
            }
        });
    }

    private ApplicationContextRunner runner() {
        var database = SharedTestContainers.postgis();
        String schema = "timing_" + UUID.randomUUID().toString().replace("-", "");
        return new ApplicationContextRunner()
                .withUserConfiguration(RequestTimingDataSourceConfiguration.class, JpaConfiguration.class)
                .withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class, MetricsAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
                        DataSourcePoolMetricsAutoConfiguration.class, DataSourceHealthContributorAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=" + database.getJdbcUrl(),
                        "spring.datasource.username=" + database.getUsername(), "spring.datasource.password=" + database.getPassword(),
                        "spring.datasource.hikari.maximum-pool-size=2", "spring.datasource.hikari.minimum-idle=0",
                        "spring.datasource.hikari.connection-timeout=250", "spring.datasource.hikari.schema=" + schema,
                        "spring.flyway.default-schema=" + schema, "spring.flyway.locations=classpath:db/request-timing",
                        "spring.jpa.hibernate.ddl-auto=none");
    }

    private MockHttpServletRequest bindRequest() {
        var request = new MockHttpServletRequest();
        RequestTiming.begin(request);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        return request;
    }

    @Configuration(proxyBeanMethods = false)
    @AutoConfigurationPackage
    static class JpaConfiguration {
    }
}
