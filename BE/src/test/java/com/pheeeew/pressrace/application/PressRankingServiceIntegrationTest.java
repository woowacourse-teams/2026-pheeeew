package com.pheeeew.pressrace.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.pressrace.domain.PressRanking;
import com.pheeeew.pressrace.domain.PressView;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import(PressRankingReader.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PressRankingServiceIntegrationTest {

    @Autowired
    private PressRankingReader reader;
    @Autowired
    private PressRaceService service;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private javax.sql.DataSource dataSource;
    private final List<Device> created = new ArrayList<>();

    @BeforeEach
    void activate() {
        jdbc.sql("""
                UPDATE press_race_control SET active_server_generation = :generation, enabled = TRUE,
                  activation_date = (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date
                """).param("generation", service.serverGeneration()).update();
    }

    @AfterEach
    void cleanup() {
        created.forEach(device -> devices.deleteById(device.getId()));
        jdbc.sql("UPDATE press_race_control SET active_server_generation = NULL, enabled = FALSE, activation_date = NULL")
                .update();
    }

    @Test
    void 실제_PostgreSQL_UUID_순서와_메모리_순위가_같고_닉네임을_전체_재조회한다() {
        // given
        Device first = participant("7fffffff-ffff-ffff-ffff-ffffffffffff", 5);
        Device second = participant("80000000-0000-0000-0000-000000000000", 5);
        List<String> databaseOrder = jdbc.sql("SELECT public_id FROM press_daily_scores ORDER BY total DESC, public_id ASC")
                .query(UUID.class).list().stream().map(UUID::toString).toList();

        // when
        PressRanking initial = reader.read();
        jdbc.sql("UPDATE devices SET nickname = '바뀐이름' WHERE id = :id").param("id", second.getId()).update();
        PressRanking updated = reader.read();

        // then
        assertThat(initial.forDevice(first.getPublicId(), PressView.ALL).top100())
                .extracting(PressRanking.Member::publicId).containsExactlyElementsOf(databaseOrder);
        assertThat(initial.forDevice(second.getPublicId(), PressView.ALL).me().nickname()).isEqualTo("익명");
        assertThat(updated.forDevice(second.getPublicId(), PressView.ALL).me().nickname()).isEqualTo("바뀐이름");
        assertThat(updated.forDevice(first.getPublicId(), PressView.ALL).me().rank()).isEqualTo(1);
        assertThat(updated.forDevice(second.getPublicId(), PressView.ALL).me().rank()).isEqualTo(1);
    }

    @Test
    void 제어_관찰_뒤_동시_커밋도_점수와_닉네임을_같은_REPEATABLE_READ로_읽는다() throws Exception {
        // given
        Device device = participant("00000000-0000-0000-0000-000000000003", 3);
        CountDownLatch readingScores = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        var observedDataSource = new org.springframework.jdbc.datasource.DelegatingDataSource(dataSource) {
            @Override
            public java.sql.Connection getConnection() throws java.sql.SQLException {
                java.sql.Connection connection = super.getConnection();
                return (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(
                        java.sql.Connection.class.getClassLoader(), new Class<?>[]{java.sql.Connection.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("prepareStatement") && args[0] instanceof String sql
                                    && sql.contains("FROM press_daily_scores")) {
                                assertThat(connection.getTransactionIsolation()).isEqualTo(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
                                assertThat(connection.isReadOnly()).isTrue();
                                readingScores.countDown();
                                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                            }
                            try {
                                return method.invoke(connection, args);
                            } catch (java.lang.reflect.InvocationTargetException exception) {
                                throw exception.getCause();
                            }
                        });
            }
        };
        PressRankingReader observedReader = new PressRankingReader(JdbcClient.create(observedDataSource), service,
                new org.springframework.jdbc.datasource.DataSourceTransactionManager(observedDataSource));

        // when
        PressRanking old;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var query = executor.submit(observedReader::read);
            try {
                assertThat(readingScores.await(10, TimeUnit.SECONDS)).isTrue();
                var write = new org.springframework.transaction.support.TransactionTemplate(
                        new org.springframework.jdbc.datasource.DataSourceTransactionManager(dataSource));
                write.executeWithoutResult(status -> {
                    jdbc.sql("UPDATE press_daily_scores SET total = 9 WHERE device_id = :id").param("id", device.getId()).update();
                    jdbc.sql("UPDATE devices SET nickname = '같이변경' WHERE id = :id").param("id", device.getId()).update();
                });
                release.countDown();
                old = query.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }

        // then
        assertThat(old.forDevice(device.getPublicId(), PressView.ALL).me().score()).isEqualTo(3);
        assertThat(old.forDevice(device.getPublicId(), PressView.ALL).me().nickname()).isEqualTo("익명");
        var next = reader.read().forDevice(device.getPublicId(), PressView.ALL);
        assertThat(next.me().score()).isEqualTo(9);
        assertThat(next.me().nickname()).isEqualTo("같이변경");
    }

    @Test
    void 오늘_조회는_DB의_KST_날짜를_사용하고_전날_투영과_활동을_제외한다() {
        // given
        Device device = participant("00000000-0000-0000-0000-000000000004", 3);
        jdbc.sql("UPDATE press_daily_scores SET press_date = press_date - 1 WHERE device_id = :id")
                .param("id", device.getId()).update();

        // when
        var state = reader.read().forDevice(device.getPublicId(), PressView.ALL);
        var databaseDate = jdbc.sql("SELECT (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date")
                .query(java.time.LocalDate.class).single();

        // then
        assertThat(state.date()).isEqualTo(databaseDate);
        assertThat(state.me().participating()).isFalse();
        assertThat(state.top100()).isEmpty();
        assertThat(state.activeCount()).isZero();
    }

    @Test
    void 콜백_없이_커밋된_변경과_메모리_재생성을_전체_조회로_복구한다() {
        // given
        Device device = participant("00000000-0000-0000-0000-000000000001", 3);
        List<PressRankingPublisher.Snapshot> received = new ArrayList<>();
        PressRankingPublisher publisher = new PressRankingPublisher(reader);
        publisher.subscribe("observer", device.getPublicId(), PressView.PRESS, received::add, code -> {});
        publisher.refresh();

        // when: afterCommit 신호를 보내지 않는 DB 변경도 다음 전체 조회에 포함한다.
        jdbc.sql("UPDATE press_daily_scores SET total = 9, last_applied_at = clock_timestamp() WHERE device_id = :id")
                .param("id", device.getId()).update();
        publisher.refresh();
        List<PressRankingPublisher.Snapshot> rebuilt = new ArrayList<>();
        PressRankingPublisher restarted = new PressRankingPublisher(reader);
        restarted.subscribe("new-connection", device.getPublicId(), PressView.PRESS, rebuilt::add, code -> {});
        restarted.refresh();

        // then
        assertThat(received).extracting(snapshot -> snapshot.screen().me().score()).containsExactly(3L, 9L);
        assertThat(rebuilt).singleElement().satisfies(snapshot -> {
            assertThat(snapshot.screen().me().score()).isEqualTo(9);
            assertThat(snapshot.baseline()).isTrue();
        });
    }

    @Test
    void 느린_과거_조회가_게시되기_전에는_다음_조회와_화면_기준이_앞서가지_못한다() throws Exception {
        // given
        Device device = participant("00000000-0000-0000-0000-000000000002", 2);
        PressRankingReader delayed = spy(reader);
        CountDownLatch oldRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            PressRanking result = (PressRanking) invocation.callRealMethod();
            if (first.getAndSet(false)) {
                oldRead.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            }
            return result;
        }).when(delayed).read();
        List<PressRankingPublisher.Snapshot> received = new CopyOnWriteArrayList<>();
        PressRankingPublisher publisher = new PressRankingPublisher(delayed);
        publisher.subscribe("observer", device.getPublicId(), PressView.PRESS, received::add, code -> {});

        // when
        try (var executor = Executors.newFixedThreadPool(3)) {
            var old = executor.submit(publisher::refresh);
            try {
                assertThat(oldRead.await(10, TimeUnit.SECONDS)).isTrue();
                jdbc.sql("UPDATE press_daily_scores SET total = 8 WHERE device_id = :id").param("id", device.getId()).update();
                var newer = executor.submit(publisher::refresh);
                assertThatThrownBy(() -> newer.get(150, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                assertThat(received).isEmpty();
                release.countDown();
                old.get(10, TimeUnit.SECONDS);
                newer.get(10, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }

        // then
        assertThat(received).extracting(snapshot -> snapshot.screen().me().score()).containsExactly(2L, 8L);
        assertThat(received).extracting(PressRankingPublisher.Snapshot::snapshotVersion).containsExactly("1", "2");
        publisher.changeView("observer", PressView.ALL, () -> {});
        publisher.refresh();
        assertThat(received.getLast().snapshotVersion()).isEqualTo("3");
        assertThat(received.getLast().baseline()).isTrue();
        assertThat(received.getLast().screen().view()).isEqualTo(PressView.ALL);
    }

    @Test
    void 입력_없는_관찰도_서버_교체와_기능_중단을_감지한다() {
        // given
        PressRankingPublisher publisher = new PressRankingPublisher(reader);
        List<String> stopped = new ArrayList<>();
        publisher.subscribe("observer", UUID.randomUUID(), PressView.PRESS, state -> {}, stopped::add);

        // when
        jdbc.sql("UPDATE press_race_control SET enabled = FALSE").update();
        publisher.refresh();
        activate();
        publisher.subscribe("observer", UUID.randomUUID(), PressView.PRESS, state -> {}, stopped::add);
        jdbc.sql("UPDATE press_race_control SET active_server_generation = :generation")
                .param("generation", UUID.randomUUID()).update();
        publisher.refresh();

        // then
        assertThat(stopped).containsExactly("RACE_DISABLED", "SERVER_REPLACED");
        assertThatThrownBy(reader::read).isInstanceOf(PressRaceException.class).satisfies(exception -> assertThat(((PressRaceException) exception).code()).isEqualTo("SERVER_REPLACED"));
    }

    private Device participant(String publicId, long total) {
        Device device = devices.save(기본_기기_빌더().build());
        created.add(device);
        jdbc.sql("""
                INSERT INTO press_daily_scores(device_id, press_date, total, last_applied_at, public_id)
                VALUES (:id, (clock_timestamp() AT TIME ZONE 'Asia/Seoul')::date, :total, clock_timestamp(), :publicId)
                """).param("id", device.getId()).param("total", total).param("publicId", UUID.fromString(publicId)).update();
        return device;
    }
}
