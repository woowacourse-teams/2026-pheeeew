package com.pheeeew.climb.stairs;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
                new ClassPathResource("db/migration/V20261113_93__create_climb_stairs.sql")).execute(source);
        jdbc = JdbcClient.create(source); repository = new StairsRepository(jdbc);
    }
    @AfterAll static void stopDatabase() { if (database != null) database.stop(); }
    @BeforeEach void clean() { jdbc.sql("truncate climb_stairs_run, climb_stairs_best").update(); }

    @Test void updatesBestOnlyForGreaterHeightOrShorterTimeAndStoresEveryRun() {
        String nickname = "손님";
        repository.save(nickname, DAY, "angry", new StairsRules.Score(20, 4000));
        repository.save(nickname, DAY, "tired", new StairsRules.Score(19, 1000));
        assertThat(repository.best(DAY, nickname).character()).isEqualTo("angry");
        repository.save(nickname, DAY, "tired", new StairsRules.Score(20, 3999));
        assertThat(repository.best(DAY, nickname).timeMs()).isEqualTo(3999);
        repository.save(nickname, DAY, "annoyed", new StairsRules.Score(20, 3999));
        assertThat(repository.best(DAY, nickname).character()).isEqualTo("tired");
        repository.save(nickname, DAY, "defeated", new StairsRules.Score(21, 9000));
        assertThat(repository.best(DAY, nickname).height()).isEqualTo(21);
        assertThat(jdbc.sql("select count(*) from climb_stairs_run").query(Integer.class).single()).isEqualTo(5);
    }
    @Test void ranksHeightDescendingTimeAscendingAndFindsNicknameOutsideTop200() {
        String mine = "내기록"; repository.save(mine, DAY, "angry", new StairsRules.Score(1, 0));
        for (int i = 0; i < 201; i++) repository.save("손님" + i, DAY, "angry", new StairsRules.Score(100, 5000 - i));
        var board = repository.ranking(DAY);
        assertThat(board.top()).hasSize(200);
        assertThat(board.total()).isEqualTo(202);
        assertThat(board.top().getFirst().timeMs()).isEqualTo(4800);
        assertThat(board.top().getLast().timeMs()).isEqualTo(4999);
        assertThat(board.top().getLast().rank()).isEqualTo(200);
        assertThat(repository.best(DAY, mine).rank()).isEqualTo(202);
        assertThat(repository.ranking(DAY.minusDays(1)).top()).isEmpty();
        assertThat(repository.ranking(DAY.minusDays(1)).total()).isZero();
    }
    @Test void serviceReplaysBeforeSavingAndReturnsBestRank() {
        String nickname = "손님";
        var service = new StairsService(repository, Clock.fixed(Instant.parse("2026-11-13T00:00:00Z"), ZoneOffset.UTC));
        var score = service.submit(nickname, DAY, "angry", StairsRulesTest.inputs(StairsRules.seed(DAY), 30, 100));
        assertThat(score.height()).isEqualTo(30); assertThat(score.timeMs()).isEqualTo(2900);
        assertThat(score.best()).isEqualTo(new StairsRules.Score(30, 2900)); assertThat(score.rank()).isEqualTo(1);
        assertThatThrownBy(() -> service.submit(nickname, DAY, "angry", StairsRulesTest.inputs(StairsRules.seed(DAY), 4, 1)))
                .hasMessage("TOO_FAST");
        assertThat(jdbc.sql("select count(*) from climb_stairs_run").query(Integer.class).single()).isEqualTo(1);
    }
    @Test void concurrentSubmissionsKeepTheBest() throws Exception {
        String nickname = "손님";
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var tasks = java.util.stream.IntStream.range(0, 12).mapToObj(i -> executor.submit(() ->
                    repository.save(nickname, DAY, "angry", new StairsRules.Score(20, 2000 - i)))).toList();
            for (var task : tasks) task.get();
        }
        assertThat(repository.best(DAY, nickname).timeMs()).isEqualTo(1989);
    }
    @Test void equalHeightAndTimeRanksEarlierRegistrationFirst() {
        repository.save("먼저", DAY, "angry", new StairsRules.Score(20, 4000));
        repository.save("나중", DAY, "tired", new StairsRules.Score(20, 4000));
        repository.save("먼저", DAY, "defeated", new StairsRules.Score(20, 4000));
        assertThat(repository.ranking(DAY).top()).extracting(StairsRepository.Item::nickname)
                .containsExactly("먼저", "나중");
        assertThat(repository.best(DAY, "먼저").character()).isEqualTo("angry");
    }
}
