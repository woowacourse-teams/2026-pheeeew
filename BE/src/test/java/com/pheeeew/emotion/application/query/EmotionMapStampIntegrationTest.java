package com.pheeeew.emotion.application.query;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.region.fixture.RegionFixture.검증용_지역_계층을_저장한다;
import static org.assertj.core.api.Assertions.assertThat;

import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.application.dto.EmotionCursor;
import com.pheeeew.emotion.application.dto.EmotionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.application.mapcache.EmotionMapCacheCoordinator;
import com.pheeeew.emotion.application.mapcache.PinCandidateCache;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.hibernate.SessionFactory;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@PostgisDataJpaTest
@Import({EmotionMapQueryService.class, MapCacheTestTime.class, EmotionMapStampIntegrationTest.SqlConfiguration.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class EmotionMapStampIntegrationTest {

    private static final EmotionSearchBounds BOUNDS = EmotionSearchBounds.of(126, 37, 128, 39);

    @Autowired private EmotionMapQueryService queries;
    @Autowired private EmotionMapDbQueryService dbQueries;
    @Autowired private PinCandidateCache pins;
    @Autowired private EmotionMapCacheCoordinator coordinator;
    @Autowired private EntityManager entityManager;
    @Autowired private EntityManagerFactory entityManagerFactory;
    @Autowired private PlatformTransactionManager transactions;
    @Autowired private JdbcClient jdbc;
    @Autowired private SqlCapture capture;
    @Autowired private MapCacheTestTime.MutableClock clock;

    private Device viewer;
    private Group group;
    private GroupStamp stamp;
    private Emotion emotion;

    @BeforeEach
    void setUp() {
        pins.invalidate();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            검증용_지역_계층을_저장한다(jdbc);
            viewer = 기본_기기_빌더().build();
            entityManager.persist(viewer);
            group = 기본_그룹_빌더().build();
            entityManager.persist(group);
            stamp = 기본_스탬프_빌더(group).build();
            entityManager.persist(stamp);
            emotion = 기본_한숨_빌더().deviceId(viewer.getId()).memo("스탬프 연결 핀").groupStamp(stamp).build();
            entityManager.persist(emotion);
            entityManager.flush();
            entityManager.clear();
        });
        capture.queries.clear();
    }

    @AfterEach
    void tearDown() {
        pins.invalidate();
        jdbc.sql("DELETE FROM emotions WHERE id = :id").param("id", emotion.getId()).update();
        jdbc.sql("DELETE FROM group_stamps WHERE id = :id").param("id", stamp.getId()).update();
        jdbc.sql("DELETE FROM groups WHERE id = :id").param("id", group.getId()).update();
        jdbc.sql("DELETE FROM devices WHERE id = :id").param("id", viewer.getId()).update();
        jdbc.sql("DELETE FROM regions WHERE code IN ('11010530', '11010', '11')").update();
    }

    @ParameterizedTest
    @ValueSource(strings = {"load", "hit", "month", "fallback"})
    void v4는_스탬프_연결_핀을_보존하면서_표시용_스탬프와_그룹_SQL을_실행하지_않는다(String path) {
        // given: fixture 트랜잭션과 영속성 문맥이 종료되어 LAZY 초기화가 숨겨지지 않는다.
        if (path.equals("hit")) {
            findV4(null, null);
        }
        LocalDate end = LocalDate.ofInstant(clock.instant(), ZoneId.of("Asia/Seoul"));
        capture.queries.clear();

        // when: 캐시 최초 적재 잠금을 다른 스레드가 보유하면 기존 SQL fallback을 사용한다.
        EmotionMapPageView page = path.equals("fallback")
                ? coordinator.serialized(() -> CompletableFuture.supplyAsync(() -> findV4(null, null))
                        .orTimeout(10, TimeUnit.SECONDS).join())
                : findV4(path.equals("month") ? end.minusMonths(1) : null, path.equals("month") ? end : null);

        // then
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(emotion.getId());
            assertThat(item.longitude()).isEqualTo(emotion.getLongitude());
            assertThat(item.latitude()).isEqualTo(emotion.getLatitude());
            assertThat(item.createdAt()).isEqualTo(emotion.getCreatedAt());
            assertThat(item.state()).isEqualTo(emotion.getState());
            assertThat(item.rotationDegrees()).isEqualTo(emotion.getRotationDegrees());
            assertThat(item.groupStamp()).isNull();
            assertThat(item.groupId()).isNull();
        });
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
        assertThat(capture.queries).noneMatch(SqlCapture::readsGroupDisplay);
        if (path.equals("fallback")) {
            assertThat(capture.queries).anyMatch(sql -> sql.contains("st_intersects"));
        } else if (path.equals("hit") || path.equals("month")) {
            assertThat(capture.queries).noneMatch(sql -> sql.contains("st_intersects"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void v2는_캐시와_SQL에서_실제_스탬프_표시값과_groupId를_유지한다(boolean sql) {
        // given
        LocalDate date = LocalDate.ofInstant(emotion.getCreatedAt(), ZoneId.of("Asia/Seoul"));

        // when
        var page = queries.findContentMapWithinBounds(BOUNDS, viewer.getPublicId(), null,
                sql ? date : null, sql ? date : null, null, null, null);

        // then
        assertThat(page.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(emotion.getId());
            assertThat(item.groupId()).isEqualTo(group.getPublicId());
            assertThat(item.groupStamp()).isEqualTo(GroupStampResult.from(stamp));
        });
        assertThat(capture.queries).anyMatch(SqlCapture::readsGroupDisplay);
    }

    @Test
    void v4_캐시_응답_조립은_실제_프록시에서도_물리_트랜잭션과_DB_연결을_시작하지_않는다() {
        // given
        EmotionCursor cursor = EmotionCursor.initialWithinBounds(BOUNDS, Instant.now(clock), EmotionSearchFilter.from(null));
        PinCandidateCache.Page cached = pins.find(cursor, true, viewer.getId(), 1);
        assertThat(cached.pins()).singleElement().satisfies(pin -> assertThat(pin.stampId()).isEqualTo(stamp.getId()));
        var statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean enabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        long transactionsBefore = statistics.getTransactionCount();
        long connectionsBefore = statistics.getConnectCount();
        try {
            // when
            var page = dbQueries.findPinPageV4(cached, 1);

            // then
            assertThat(page.items()).extracting(EmotionMapItemView::id).containsExactly(emotion.getId());
            assertThat(statistics.getTransactionCount()).isEqualTo(transactionsBefore);
            assertThat(statistics.getConnectCount()).isEqualTo(connectionsBefore);
        } finally {
            statistics.setStatisticsEnabled(enabled);
        }
    }

    private EmotionMapPageView findV4(LocalDate start, LocalDate end) {
        return queries.findContentMapWithinBoundsV4(BOUNDS, viewer.getPublicId(), start, end, null, null, null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SqlConfiguration {

        @Bean
        SqlCapture stampSqlCapture() {
            return new SqlCapture();
        }

        @Bean
        HibernatePropertiesCustomizer stampSqlCustomizer(SqlCapture capture) {
            return properties -> properties.put("hibernate.session_factory.statement_inspector", capture);
        }
    }

    static class SqlCapture implements StatementInspector {

        private final List<String> queries = new ArrayList<>();

        @Override
        public String inspect(String sql) {
            queries.add(sql.toLowerCase(Locale.ROOT));
            return sql;
        }

        static boolean readsGroupDisplay(String sql) {
            return sql.matches("(?s).*\\b(from|join)\\s+(group_stamps|groups)\\b.*");
        }
    }
}
