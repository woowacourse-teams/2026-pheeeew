package com.pheeeew.climb.stairs;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** 별도 Testcontainers DB만 사용한다. 로컬 앱이나 climb-local-db에는 접속하지 않는다. */
class StairsRepositoryTest {
    private static PostgreSQLContainer database;
    private static JdbcClient jdbc;
    private static StairsRepository repository;
    private static final LocalDate DAY = LocalDate.of(2026, 11, 13);

    @BeforeAll static void startDatabase() {
        database = new PostgreSQLContainer("postgres:17-alpine"); database.start();
        var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V20261112_90__create_climb_tables.sql"),
                new ClassPathResource("db/migration/V20261113_92__create_climb_stairs.sql")).execute(source);
        jdbc = JdbcClient.create(source); repository = new StairsRepository(jdbc);
    }
    @AfterAll static void stopDatabase() { if (database != null) database.stop(); }
    @BeforeEach void clean() { jdbc.sql("truncate climb_guest cascade").update(); }

    @Test void updatesBestOnlyForGreaterHeightOrShorterTimeAndStoresEveryRun() {
        UUID guest = guest();
        repository.save(guest, DAY, "angry", new StairsRules.Score(20, 4000));
        repository.save(guest, DAY, "tired", new StairsRules.Score(19, 1000));
        assertThat(repository.ranking(DAY, guest).me().character()).isEqualTo("angry");
        repository.save(guest, DAY, "tired", new StairsRules.Score(20, 3999));
        assertThat(repository.ranking(DAY, guest).me().timeMs()).isEqualTo(3999);
        repository.save(guest, DAY, "annoyed", new StairsRules.Score(20, 3999));
        assertThat(repository.ranking(DAY, guest).me().character()).isEqualTo("tired");
        repository.save(guest, DAY, "defeated", new StairsRules.Score(21, 9000));
        assertThat(repository.ranking(DAY, guest).me().height()).isEqualTo(21);
        assertThat(jdbc.sql("select count(*) from climb_stairs_run").query(Integer.class).single()).isEqualTo(5);
    }
    @Test void ranksHeightDescendingTimeAscendingAndReturnsMeOutsideTop50() {
        UUID mine = guest(); repository.save(mine, DAY, "angry", new StairsRules.Score(1, 0));
        for (int i = 0; i < 51; i++) repository.save(guest(), DAY, "angry", new StairsRules.Score(100, 5000 - i));
        var board = repository.ranking(DAY, mine);
        assertThat(board.top()).hasSize(50);
        assertThat(board.top().getFirst().timeMs()).isEqualTo(4950);
        assertThat(board.top().getLast().timeMs()).isEqualTo(4999);
        assertThat(board.me().rank()).isEqualTo(52);
        assertThat(repository.ranking(DAY, null).me()).isNull();
        assertThat(repository.ranking(DAY.minusDays(1), mine).top()).isEmpty();
    }
    @Test void serviceReplaysBeforeSavingAndReturnsBestRank() {
        UUID guest = guest();
        var service = new StairsService(repository, Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC));
        var score = service.submit(guest, DAY, "angry", StairsRulesTest.inputs(StairsRules.seed(DAY), 30, 100));
        assertThat(score.height()).isEqualTo(30); assertThat(score.timeMs()).isEqualTo(2900);
        assertThat(score.best()).isEqualTo(new StairsRules.Score(30, 2900)); assertThat(score.rank()).isEqualTo(1);
        assertThatThrownBy(() -> service.submit(guest, DAY, "angry", StairsRulesTest.inputs(StairsRules.seed(DAY), 4, 1)))
                .hasMessage("TOO_FAST");
        assertThat(jdbc.sql("select count(*) from climb_stairs_run").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void concurrentSubmissionsKeepTheBest() throws Exception {
        UUID guest = guest();
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 12).mapToObj(i -> executor.submit(() ->
                    repository.save(guest, DAY, "angry", new StairsRules.Score(20, 2000 - i)))).toList();
            for (var task : tasks) task.get();
        }
        assertThat(repository.ranking(DAY, guest).me().timeMs()).isEqualTo(1989);
    }
    private static UUID guest() {
        UUID id = UUID.randomUUID();
        jdbc.sql("insert into climb_guest values (:id, 'abcd', '손님', :hash, now(), now())")
                .param("id", id).param("hash", id.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)).update();
        return id;
    }
}
