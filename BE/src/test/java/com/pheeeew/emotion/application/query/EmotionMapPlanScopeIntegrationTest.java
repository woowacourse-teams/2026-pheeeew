package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionContentFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.region.domain.RegionLevel;
import com.pheeeew.support.PostgisDataJpaTest;
import com.pheeeew.support.SharedTestContainers;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, EmotionQueryService.class,
        EmotionMapPlanScopeIntegrationTest.ObservationConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@TestPropertySource(properties = {
        "spring.datasource.hikari.maximum-pool-size=1",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.datasource.hikari.data-source-properties.prepareThreshold=1"
})
class EmotionMapPlanScopeIntegrationTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

    @Autowired private EmotionMapQueryService service;
    @Autowired private EmotionQueryService queryService;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcClient jdbc;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private PlanObserver observer;

    private Device viewer;
    private Emotion emotion;
    private Group group;
    private Group otherGroup;
    private GroupStamp stamp;
    private Map<String, Object> previousReadiness;
    private int backendPid;

    @BeforeEach
    void setUp() {
        observer.stop();
        previousReadiness = jdbc.sql("""
                SELECT boundaries_verified_at, backfill_verified_at
                FROM region_datasets WHERE dataset_key = 'SGIS_2025_2Q'
                """).query().singleRow();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            검증용_지역_계층을_저장한다(jdbc);
            jdbc.sql("""
                    UPDATE region_datasets SET boundaries_verified_at = CURRENT_TIMESTAMP,
                        backfill_verified_at = CURRENT_TIMESTAMP
                    WHERE dataset_key = 'SGIS_2025_2Q'
                    """).update();
            viewer = 기본_기기_빌더().build();
            entityManager.persist(viewer);
            group = 기본_그룹_빌더().build();
            entityManager.persist(group);
            otherGroup = 기본_그룹_빌더().name("다른모임").build();
            entityManager.persist(otherGroup);
            stamp = 기본_스탬프_빌더(group).build();
            entityManager.persist(stamp);
            emotion = 기본_한숨_빌더().deviceId(viewer.getId()).memo("지도 계획 범위 검증").build();
            entityManager.persist(emotion);
        });
        backendPid = jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single();
        assertThat(currentMode()).isEqualTo("auto");
        observer.start();
    }

    @AfterEach
    void tearDown() {
        observer.stop();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.sql("DELETE FROM emotions WHERE id = :id").param("id", emotion.getId()).update();
            jdbc.sql("DELETE FROM group_stamps WHERE id = :id").param("id", stamp.getId()).update();
            jdbc.sql("DELETE FROM groups WHERE id = :id").param("id", group.getId()).update();
            jdbc.sql("DELETE FROM groups WHERE id = :id").param("id", otherGroup.getId()).update();
            jdbc.sql("DELETE FROM devices WHERE id = :id").param("id", viewer.getId()).update();
            jdbc.sql("DELETE FROM regions WHERE code IN ('11010530', '11010', '11')").update();
            jdbc.sql("""
                    UPDATE region_datasets SET boundaries_verified_at = :boundaries,
                        backfill_verified_at = :backfill
                    WHERE dataset_key = 'SGIS_2025_2Q'
                    """).param("boundaries", previousReadiness.get("boundaries_verified_at"))
                    .param("backfill", previousReadiness.get("backfill_verified_at")).update();
        });
    }

    @Test
    void v2_필터_지도는_custom으로_조회하고_정상_종료_후_동일_연결의_지역_조회에_전파하지_않는다() {
        // when
        var page = findMap(viewer.getPublicId());

        // then
        assertThat(page.items()).singleElement().satisfies(item -> assertThat(item.id()).isEqualTo(emotion.getId()));
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertPinPlanScope();
        assertRestoredAndRegionsUnaffected();
    }

    @Test
    void 기기_조회가_실패하면_custom_설정을_적용하지_않는다() {
        // when / then: 기기 검증 실패 시 핀 조회 저장소에 도달하지 않는다.
        assertThatThrownBy(() -> findMap(UUID.randomUUID())).isInstanceOf(DeviceException.class);
        assertThat(observer.queries).noneMatch(ObservedQuery::isPinQuery);
        assertObservedMode("auto");
        assertRestoredAndRegionsUnaffected();
    }

    @Test
    void 설정_후_DB_쿼리가_실패해도_롤백으로_복원하고_동일_연결을_재사용한다() {
        // given
        observer.failNextMapQuery = true;

        // when / then: 실제 PostgreSQL 오류로 트랜잭션을 중단시킨다.
        assertThatThrownBy(() -> findMap(viewer.getPublicId()))
                .isInstanceOfSatisfying(DataAccessException.class, exception ->
                        assertThat(exception.getMostSpecificCause()).isInstanceOfSatisfying(SQLException.class,
                                sqlException -> assertThat(sqlException.getSQLState()).isEqualTo("22012")));
        assertPinPlanScope();
        assertRestoredAndRegionsUnaffected();
    }

    @Test
    void v1_지도는_custom_설정을_적용하지_않는다() {
        // when
        var page = service.findMapWithinBounds(BOUNDS, viewer.getPublicId(), null, null);

        // then
        assertThat(page.items()).hasSize(1);
        assertObservedMode("auto");
        assertRestoredAndRegionsUnaffected();
    }

    @Test
    void 같은_저장소를_쓰는_v1_영역_목록은_custom_설정을_적용하지_않는다() {
        // when
        var page = queryService.findListWithinBounds(BOUNDS, viewer.getPublicId(), null, null);

        // then
        assertThat(page.items()).hasSize(1);
        assertObservedMode("auto");
        assertRestoredAndRegionsUnaffected();
    }

    @Test
    void 커서_검증에_실패하면_Hibernate_조회_SQL을_실행하지_않는다() {
        // when / then
        assertThatThrownBy(() -> service.findContentMapWithinBounds(null, viewer.getPublicId(), null,
                null, null, null, null, "invalid-cursor")).isInstanceOf(EmotionException.class);
        assertThat(observer.queries).isEmpty();
        assertRestoredAndRegionsUnaffected();
    }

    @Test
    void 외부_트랜잭션에_참여하면_설정은_외부_트랜잭션_종료까지_유지된다() {
        // given
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        outer.setReadOnly(true);

        // when
        outer.executeWithoutResult(status -> {
            findMap(viewer.getPublicId());
            assertThat(currentMode()).isEqualTo("force_custom_plan");
            observer.start();
            assertThat(service.findRegionMap(BOUNDS, RegionLevel.SIDO, viewer.getPublicId(),
                    EmotionSearchFilter.from(null))).hasSize(1);
            assertObservedMode("force_custom_plan");
        });

        // then
        assertRestoredAndRegionsUnaffected();
    }

    @ParameterizedTest
    @CsvSource({"SIDO,force_custom_plan", "SIDO,force_generic_plan",
            "SIGUNGU,force_custom_plan", "SIGUNGU,force_generic_plan"})
    void 전국_집계는_JDBC가_준비한_custom_generic_계획에서_같은_결과를_반환한다(RegionLevel level, String mode) {
        // given: SQL PREPARE를 만들지 않고 실제 서비스 호출로 준비된 statement를 관찰한다.
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                jdbc.sql("UPDATE emotions SET group_stamp_id = :stamp WHERE id = :id")
                        .param("stamp", stamp.getId()).param("id", emotion.getId()).update());
        var day = emotion.getCreatedAt().atZone(ZoneId.of("Asia/Seoul")).toLocalDate();
        var filters = List.of(
                EmotionSearchFilter.of(null, null, null, null, null),
                EmotionSearchFilter.of(day, day, null, null, null),
                EmotionSearchFilter.of(null, null, EmotionContentFilter.MEMO, EmotionSearchScope.MY, null),
                EmotionSearchFilter.of(null, null, EmotionContentFilter.MEMO, EmotionSearchScope.GROUP,
                        List.of(group.getPublicId())),
                EmotionSearchFilter.of(null, null, EmotionContentFilter.MEMO, EmotionSearchScope.GROUP,
                        List.of(group.getPublicId(), otherGroup.getPublicId())));
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setReadOnly(true);

        // when / then
        transaction.executeWithoutResult(status -> {
            jdbc.sql("SELECT set_config('plan_cache_mode', :mode, true)").param("mode", mode).query(String.class).single();
            for (EmotionSearchFilter filter : filters) {
                observer.start();
                var expected = service.findRegionMap(BOUNDS, level, viewer.getPublicId(), filter);
                assertThat(expected).singleElement().satisfies(item -> assertThat(item.summary().totalCount()).isEqualTo(1));
                var arguments = nationalQueryArguments(filter);
                String sql = observer.queries.stream().map(ObservedQuery::sql)
                        .filter(query -> query.contains("AS region_code")).findFirst().orElseThrow();
                for (int index = 0; index < arguments.size(); index++) {
                    sql = sql.replaceFirst("\\?", "\\$" + (index + 1));
                }
                Map<String, Object> before = preparedQuery(sql);
                for (int repeat = 0; repeat < 5; repeat++) {
                    assertThat(service.findRegionMap(BOUNDS, level, viewer.getPublicId(), filter)).isEqualTo(expected);
                }
                Map<String, Object> after = preparedQuery(sql);
                assertThat(after.get("name")).isEqualTo(before.get("name"));
                assertThat(after.get("from_sql")).isEqualTo(false);
                assertThat(after.get("parameter_count")).isEqualTo(arguments.size());
                assertThat((Long) after.get("custom_plans") - (Long) before.get("custom_plans"))
                        .isEqualTo(mode.equals("force_custom_plan") ? 5L : 0L);
                assertThat((Long) after.get("generic_plans") - (Long) before.get("generic_plans"))
                        .isEqualTo(mode.equals("force_generic_plan") ? 5L : 0L);
                String root = "left(e.region_code, " + (level == RegionLevel.SIDO ? 2 : 5) + ")";
                assertThat(sql).contains(root)
                        .doesNotContain("JOIN regions", root + " IN (", "e.region_code IN (");
                assertObservedMode(mode);
                String name = (String) after.get("name");
                assertThat(name).matches("S_\\d+");
                // 실행 시간은 이 작은 fixture의 관측값이며 성능 임계값으로 사용하지 않는다.
                List<String> plan = jdbc.sql("EXPLAIN (ANALYZE, BUFFERS) EXECUTE \"" + name + "\"("
                        + String.join(", ", arguments) + ")").query(String.class).list();
                System.out.printf("NATIONAL_JDBC_PLAN level=%s mode=%s content=%s scope=%s date=%s params=%d %s%n",
                        level, mode, filter.contentType(), filter.scope(), filter.startDate(), arguments.size(),
                        plan.stream().filter(line -> line.startsWith("Execution Time:")).findFirst().orElseThrow());
            }
        });
        assertThat(currentMode()).isEqualTo("auto");
        assertThat(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()).isEqualTo(backendPid);
    }

    private Map<String, Object> preparedQuery(String sql) {
        return jdbc.sql("""
                SELECT name, from_sql, cardinality(parameter_types) AS parameter_count, custom_plans, generic_plans
                FROM pg_prepared_statements WHERE statement = :sql
                """).param("sql", sql).query().singleRow();
    }

    private List<String> nationalQueryArguments(EmotionSearchFilter filter) {
        List<String> arguments = new ArrayList<>();
        arguments.add("'" + Instant.now() + "'::timestamptz");
        if (filter.startInclusive() != null) {
            arguments.add("'" + filter.startInclusive() + "'::timestamptz");
            arguments.add("'" + filter.endExclusive() + "'::timestamptz");
        }
        switch (filter.scope()) {
            case ALL -> { }
            case MY -> arguments.add(viewer.getId().toString());
            case GROUP -> filter.groupIds().forEach(groupId -> arguments.add("'" + groupId + "'::uuid"));
        }
        return arguments;
    }

    private EmotionMapPageView findMap(UUID deviceId) {
        // SQL 계획 범위 검증은 캐시 대상이 아닌 MEMO 조회에서 수행한다.
        return service.findContentMapWithinBounds(BOUNDS, deviceId, null, null, null, EmotionContentFilter.MEMO, null, null);
    }

    private String currentMode() {
        return jdbc.sql("SELECT current_setting('plan_cache_mode')").query(String.class).single();
    }

    private void assertPinPlanScope() {
        assertThat(observer.queries).filteredOn(ObservedQuery::isPinQuery).isNotEmpty().allSatisfy(query -> {
            assertThat(query.mode()).isEqualTo("force_custom_plan");
            assertThat(query.backendPid()).isEqualTo(backendPid);
        });
        assertThat(observer.queries).filteredOn(query -> !query.isPinQuery()).isNotEmpty().allSatisfy(query -> {
            assertThat(query.mode()).isEqualTo("auto");
            assertThat(query.backendPid()).isEqualTo(backendPid);
        });
    }

    private void assertObservedMode(String mode) {
        assertThat(observer.queries).isNotEmpty().allSatisfy(query -> {
            assertThat(query.mode()).isEqualTo(mode);
            assertThat(query.backendPid()).isEqualTo(backendPid);
        });
    }

    private void assertRestoredAndRegionsUnaffected() {
        assertThat(currentMode()).isEqualTo("auto");
        assertThat(jdbc.sql("SELECT pg_backend_pid()").query(Integer.class).single()).isEqualTo(backendPid);
        observer.start();
        var regions = service.findRegionMap(BOUNDS, RegionLevel.SIDO, viewer.getPublicId(),
                EmotionSearchFilter.from(null));
        assertThat(regions).singleElement().satisfies(item -> {
            assertThat(item.region().code()).isEqualTo("11");
            assertThat(item.summary().totalCount()).isEqualTo(1);
        });
        assertObservedMode("auto");
        assertThat(currentMode()).isEqualTo("auto");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ObservationConfiguration {

        @Bean
        DynamicPropertyRegistrar flywayProperties() {
            // Flyway의 보조 연결은 조회 검증용 단일 연결 풀과 분리한다.
            var postgis = SharedTestContainers.postgis();
            return registry -> {
                registry.add("spring.flyway.url", postgis::getJdbcUrl);
                registry.add("spring.flyway.user", postgis::getUsername);
                registry.add("spring.flyway.password", postgis::getPassword);
            };
        }

        @Bean
        PlanObserver planObserver(JdbcClient jdbc) {
            return new PlanObserver(jdbc);
        }

        @Bean
        HibernatePropertiesCustomizer planObserverCustomizer(PlanObserver observer) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", observer);
        }
    }

    static class PlanObserver implements StatementInspector {

        private final JdbcClient jdbc;
        private final List<ObservedQuery> queries = new ArrayList<>();
        private boolean enabled;
        private boolean failNextMapQuery;

        PlanObserver(JdbcClient jdbc) {
            this.jdbc = jdbc;
        }

        void start() {
            queries.clear();
            enabled = true;
        }

        void stop() {
            enabled = false;
            failNextMapQuery = false;
            queries.clear();
        }

        @Override
        public String inspect(String sql) {
            if (enabled) {
                // JdbcClient는 현재 JPA 트랜잭션에 연결된 같은 JDBC 연결을 사용한다.
                queries.add(jdbc.sql("SELECT current_setting('plan_cache_mode'), pg_backend_pid()")
                        .query((row, index) -> ObservedQuery.of(sql, row.getString(1), row.getInt(2))).single());
                if (failNextMapQuery && sql.contains("from emotions ") && sql.contains("st_intersects")) {
                    failNextMapQuery = false;
                    jdbc.sql("SELECT 1 / 0").query(Integer.class).single();
                }
            }
            return sql;
        }
    }

    private record ObservedQuery(String sql, String mode, int backendPid) {

        static ObservedQuery of(String sql, String mode, int backendPid) {
            return new ObservedQuery(sql, mode, backendPid);
        }

        boolean isPinQuery() {
            return sql.contains("from emotions ") && sql.contains("st_intersects");
        }
    }
}
