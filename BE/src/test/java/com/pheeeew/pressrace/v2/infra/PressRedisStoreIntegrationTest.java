package com.pheeeew.pressrace.v2.infra;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.pressrace.v2.domain.PressDayRead;
import com.pheeeew.pressrace.v2.domain.PressReadStatus;
import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.domain.PressTodayParticipant;
import com.pheeeew.pressrace.v2.domain.PressTodayRead;
import com.pheeeew.pressrace.v2.domain.PressWriteStatus;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

class PressRedisStoreIntegrationTest {

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
            .withExposedPorts(6379);
    private static final UUID DEVICE = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static LettuceConnectionFactory connections;
    private static StringRedisTemplate redis;

    private PressRedisStore store;
    private LocalDate today;

    @BeforeAll
    static void startRedis() {
        REDIS.start();
        connections = new LettuceConnectionFactory(new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connections.afterPropertiesSet();
        connections.start();
        redis = new StringRedisTemplate(connections);
    }

    @AfterAll
    static void stopRedis() {
        connections.destroy();
        REDIS.stop();
    }

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        store = new PressRedisStore(redis);
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        store.markSeeded(today);
    }

    @Test
    void 그날_시딩_표시가_없으면_아무것도_쓰지_않고_시딩이_필요하다고_돌려준다() {
        // given
        redis.delete("{press}:seeded:" + today);

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // then
        assertThat(result.status()).isEqualTo(PressWriteStatus.NOT_SEEDED);
        assertThat(redis.keys("{press}:*")).isEmpty();
    }

    @Test
    void 입력을_반영하면_감정별_개수와_합계_순위_총합_변경_표시가_함께_오른다() {
        // given
        UUID publicId = UUID.randomUUID();

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3, EmotionState.IRRITATED, 2), publicId);

        // then
        assertThat(result.status()).isEqualTo(PressWriteStatus.APPLIED);
        assertThat(result.counts()).containsEntry(EmotionState.ANGRY, 3L).containsEntry(EmotionState.IRRITATED, 2L)
                .containsEntry(EmotionState.EXHAUSTED, 0L);
        assertThat(redis.opsForHash().get(deviceKey(), "total")).isEqualTo("5");
        assertThat(redis.opsForZSet().rangeWithScores(rankKey(), 0, -1))
                .extracting(TypedTuple::getValue, TypedTuple::getScore)
                .containsExactly(tuple(publicId + ":" + DEVICE, -5.0));
        assertThat(redis.opsForZSet().score(activeKey(), DEVICE.toString())).isEqualTo(
                Double.parseDouble(redis.opsForHash().get(deviceKey(), "last_applied_at").toString()));
        assertThat(redis.opsForValue().get("{press}:total:" + today)).isEqualTo("5");
        assertThat(redis.opsForZSet().score("{press}:dirty", today + ":" + DEVICE)).isEqualTo(1.0);
    }

    @Test
    void 같은_기기의_입력은_누적되고_버전이_오르며_순위표_멤버는_처음_공개_식별자_하나다() {
        // given
        UUID first = UUID.randomUUID();
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), first);

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 4), UUID.randomUUID());

        // then
        assertThat(result.counts()).containsEntry(EmotionState.ANGRY, 7L);
        assertThat(redis.opsForHash().get(deviceKey(), "version")).isEqualTo("2");
        assertThat(redis.opsForZSet().score("{press}:dirty", today + ":" + DEVICE)).isEqualTo(2.0);
        assertThat(redis.opsForZSet().rangeWithScores(rankKey(), 0, -1))
                .extracting(TypedTuple::getValue, TypedTuple::getScore)
                .containsExactly(tuple(first + ":" + DEVICE, -7.0));
        assertThat(redis.opsForZSet().zCard(activeKey())).isEqualTo(1L);
    }

    @Test
    void 공개_식별자는_처음_정한_값을_유지한다() {
        // given
        var first = UUID.randomUUID();
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), first);

        // when
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // then
        assertThat(redis.opsForHash().get(deviceKey(), "public_id")).isEqualTo(first.toString());
    }

    @Test
    void 토큰이_모자라면_거절하고_집계를_바꾸지_않는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 100), UUID.randomUUID());

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 50), UUID.randomUUID());

        // then
        assertThat(result.status()).isEqualTo(PressWriteStatus.RATE_LIMITED);
        assertThat(redis.opsForHash().get(deviceKey(), "total")).isEqualTo("100");
        assertThat(redis.opsForValue().get("{press}:total:" + today)).isEqualTo("100");
    }

    @Test
    void 날짜가_Redis_기준과_다르면_아무것도_쓰지_않고_Redis_날짜를_돌려준다() {
        // when
        var result = store.write(DEVICE, today.minusDays(1), Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // then
        assertThat(result.status()).isEqualTo(PressWriteStatus.DATE_MISMATCH);
        assertThat(result.storeDate()).isEqualTo(today);
        assertThat(redis.keys("{press}:*")).containsExactly("{press}:seeded:" + today);
    }

    @Test
    void 같은_요청을_다시_보내면_한_번만_반영한다() {
        // given
        var requestId = UUID.randomUUID();
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID(), requestId, "hash-a");

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID(), requestId, "hash-a");

        // then
        assertThat(result.status()).isEqualTo(PressWriteStatus.DUPLICATE);
        assertThat(result.counts()).containsEntry(EmotionState.ANGRY, 3L);
        assertThat(redis.opsForHash().get(deviceKey(), "total")).isEqualTo("3");
    }

    @Test
    void 같은_요청_식별자에_내용이_다르면_충돌로_거절하고_쓰지_않는다() {
        // given
        var requestId = UUID.randomUUID();
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID(), requestId, "hash-a");

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 9), UUID.randomUUID(), requestId, "hash-b");

        // then
        assertThat(result.status()).isEqualTo(PressWriteStatus.INPUT_CONFLICT);
        assertThat(redis.opsForHash().get(deviceKey(), "total")).isEqualTo("3");
    }

    @Test
    void 변경_표시를_읽으면_기기_해시의_누적값과_버전을_함께_가져온다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());
        store.write(DEVICE, today, Map.of(EmotionState.EXHAUSTED, 2), UUID.randomUUID());

        // when
        var dirty = store.findDirty();
        var days = store.findDays(dirty);

        // then
        assertThat(dirty).singleElement().satisfies(day -> assertThat(day.version()).isEqualTo(2L));
        assertThat(days).singleElement().satisfies(day -> {
            assertThat(day.devicePublicId()).isEqualTo(DEVICE);
            assertThat(day.total()).isEqualTo(5L);
            assertThat(day.version()).isEqualTo(2L);
            assertThat(day.counts()).containsExactlyInAnyOrderEntriesOf(Map.of(EmotionState.ANGRY, 3L, EmotionState.EXHAUSTED, 2L));
        });
    }

    @Test
    void 반영한_버전보다_새_입력이_있으면_변경_표시를_지우지_않는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        var flushed = store.findDays(store.findDirty()).getFirst();
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // when
        store.clearDirty(Map.of(flushed.dirtyMember(), flushed.version()));

        // then
        assertThat(store.findDirty()).singleElement().satisfies(day -> assertThat(day.version()).isEqualTo(2L));
    }

    @Test
    void 반영한_버전까지_입력이_없으면_변경_표시를_지운다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        var flushed = store.findDays(store.findDirty()).getFirst();

        // when
        store.clearDirty(Map.of(flushed.dirtyMember(), flushed.version()));

        // then
        assertThat(store.findDirty()).isEmpty();
    }

    @Test
    void 시딩하면_DB_값과_버전으로_해시와_순위와_활동_시각과_총합을_채우고_변경_표시는_만들지_않는다() {
        // given
        PressStoredDay day = 저장된_하루(DEVICE, 10, 7);

        // when
        boolean seeded = store.seedDay(day);

        // then
        assertThat(seeded).isTrue();
        assertThat(redis.opsForHash().get(deviceKey(), "total")).isEqualTo("10");
        assertThat(redis.opsForHash().get(deviceKey(), "version")).isEqualTo("7");
        assertThat(redis.opsForZSet().rangeWithScores(rankKey(), 0, -1))
                .extracting(TypedTuple::getValue, TypedTuple::getScore)
                .containsExactly(tuple(day.publicId() + ":" + DEVICE, -10.0));
        assertThat(redis.opsForZSet().score(activeKey(), DEVICE.toString()))
                .isEqualTo((double) day.lastAppliedAt().toEpochMilli());
        assertThat(redis.opsForValue().get("{press}:total:" + today)).isEqualTo("10");
        assertThat(store.findDirty()).isEmpty();
    }

    @Test
    void 이미_Redis에_있는_기기는_시딩하지_않는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());

        // when
        boolean seeded = store.seedDay(저장된_하루(DEVICE, 10, 7));

        // then
        assertThat(seeded).isFalse();
        assertThat(redis.opsForHash().get(deviceKey(), "total")).isEqualTo("3");
        assertThat(redis.opsForValue().get("{press}:total:" + today)).isEqualTo("3");
    }

    @Test
    void 시딩한_버전_다음부터_입력_버전이_이어지고_순위표는_시딩_때의_공개_식별자_멤버에_누적된다() {
        // given
        PressStoredDay seeded = 저장된_하루(DEVICE, 10, 7);
        store.seedDay(seeded);

        // when
        var result = store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // then
        assertThat(result.counts()).containsEntry(EmotionState.ANGRY, 11L);
        assertThat(store.findDirty()).singleElement().satisfies(day -> assertThat(day.version()).isEqualTo(8L));
        assertThat(redis.opsForZSet().rangeWithScores(rankKey(), 0, -1))
                .extracting(TypedTuple::getValue, TypedTuple::getScore)
                .containsExactly(tuple(seeded.publicId() + ":" + DEVICE, -11.0));
        assertThat(redis.opsForZSet().score(activeKey(), DEVICE.toString())).isEqualTo(
                Double.parseDouble(redis.opsForHash().get(deviceKey(), "last_applied_at").toString()));
    }

    @Test
    void 반영_리스는_한_번에_한_보유자만_잡고_보유자만_풀_수_있다() {
        // given
        store.acquireFlushLease("first", Duration.ofSeconds(30));

        // when
        boolean second = store.acquireFlushLease("second", Duration.ofSeconds(30));
        store.releaseFlushLease("second");
        boolean afterWrongRelease = store.acquireFlushLease("third", Duration.ofSeconds(30));
        store.releaseFlushLease("first");
        boolean afterRelease = store.acquireFlushLease("fourth", Duration.ofSeconds(30));

        // then
        assertThat(List.of(second, afterWrongRelease, afterRelease)).containsExactly(false, false, true);
    }

    @Test
    void 오늘_읽기에서_예상_날짜가_Redis_날짜와_다르면_참가자_없이_Redis_날짜를_돌려준다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());

        // when
        PressTodayRead read = store.readToday(today.minusDays(1), "", -1, -1);

        // then
        assertThat(read.status()).isEqualTo(PressReadStatus.DATE_MISMATCH);
        assertThat(read.date()).isEqualTo(today);
        assertThat(read.participants()).isEmpty();
    }

    @Test
    void 오늘_읽기에서_시딩_표시가_없으면_참가자를_읽지_않는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        redis.delete("{press}:seeded:" + today);

        // when
        PressTodayRead read = store.readToday(today, "", -1, -1);

        // then
        assertThat(read.status()).isEqualTo(PressReadStatus.NOT_SEEDED);
        assertThat(read.participants()).isEmpty();
    }

    @Test
    void 표시_버전_닉네임_버전이_모두_같으면_변경_없음이고_참가자를_싣지_않는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        PressTodayRead first = store.readToday(today, "", -1, -1);

        // when
        PressTodayRead second = store.readToday(today, first.token(), first.version(), first.nicknameVersion());

        // then
        assertThat(first.status()).isEqualTo(PressReadStatus.READ);
        assertThat(second.status()).isEqualTo(PressReadStatus.UNCHANGED);
        assertThat(second.participants()).isEmpty();
        assertThat(second.observedAt()).isAfterOrEqualTo(first.observedAt());
    }

    @Test
    void 다시_시딩해_표시_토큰이_바뀌면_버전이_같아도_전체를_다시_읽는다() {
        // given
        store.seedDay(저장된_하루(DEVICE, 10, 7));
        PressTodayRead first = store.readToday(today, "", -1, -1);
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        store.seedDay(저장된_하루(DEVICE, 12, 7));
        store.markSeeded(today);

        // when
        PressTodayRead second = store.readToday(today, first.token(), first.version(), first.nicknameVersion());

        // then
        assertThat(second.version()).isEqualTo(first.version());
        assertThat(second.token()).isNotEqualTo(first.token());
        assertThat(second.status()).isEqualTo(PressReadStatus.READ);
        assertThat(second.participants()).singleElement().satisfies(participant ->
                assertThat(participant.total()).isEqualTo(12L));
    }

    @Test
    void 닉네임_사본이_없으면_채울_대상으로_돌려주고_빈_문자열_사본은_닉네임_없음이다() {
        // given
        UUID unnamed = UUID.fromString("00000000-0000-0000-0000-000000000002");
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        store.write(unnamed, today, Map.of(EmotionState.ANGRY, 2), UUID.randomUUID());
        store.fillNicknames(Collections.singletonMap(unnamed, null));

        // when
        PressTodayRead read = store.readToday(today, "", -1, -1);

        // then
        assertThat(read.missingNicknames()).containsExactly(DEVICE);
        assertThat(read.participants()).extracting(PressTodayParticipant::devicePublicId, PressTodayParticipant::nickname)
                .containsExactlyInAnyOrder(tuple(DEVICE, null), tuple(unnamed, null));
        assertThat(redis.opsForValue().get("{press}:nickname:" + unnamed)).isEmpty();
    }

    @Test
    void 참가자는_기기_해시의_공개_식별자_합계_반영_시각_감정별_개수를_싣는다() {
        // given
        UUID publicId = UUID.randomUUID();
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3, EmotionState.IRRITATED, 2), publicId);
        store.fillNicknames(Map.of(DEVICE, "별"));

        // when
        PressTodayRead read = store.readToday(today, "", -1, -1);

        // then
        assertThat(read.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.publicId()).isEqualTo(publicId);
            assertThat(participant.nickname()).isEqualTo("별");
            assertThat(participant.total()).isEqualTo(5L);
            assertThat(participant.lastAppliedAt().toEpochMilli())
                    .isEqualTo(Long.parseLong(redis.opsForHash().get(deviceKey(), "last_applied_at").toString()));
            assertThat(participant.counts()).containsEntry(EmotionState.ANGRY, 3L)
                    .containsEntry(EmotionState.IRRITATED, 2L).containsEntry(EmotionState.EXHAUSTED, 0L);
        });
    }

    @Test
    void 닉네임을_바꾸면_사본을_덮어쓰고_닉네임_버전을_올려_다음_읽기가_전체를_다시_읽는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 1), UUID.randomUUID());
        store.fillNicknames(Map.of(DEVICE, "별"));
        PressTodayRead first = store.readToday(today, "", -1, -1);

        // when
        store.changeNickname(DEVICE, "달");
        PressTodayRead second = store.readToday(today, first.token(), first.version(), first.nicknameVersion());

        // then
        assertThat(second.status()).isEqualTo(PressReadStatus.READ);
        assertThat(second.nicknameVersion()).isEqualTo(first.nicknameVersion() + 1);
        assertThat(second.participants()).singleElement()
                .satisfies(participant -> assertThat(participant.nickname()).isEqualTo("달"));
        assertThat(redis.getExpire("{press}:nickname:" + DEVICE, TimeUnit.MILLISECONDS)).isBetween(1L, 600_000L);
    }

    @Test
    void 닉네임_채우기는_이미_있는_사본을_덮어쓰지_않는다() {
        // given
        store.changeNickname(DEVICE, "달");

        // when
        store.fillNicknames(Map.of(DEVICE, "별"));

        // then
        assertThat(redis.opsForValue().get("{press}:nickname:" + DEVICE)).isEqualTo("달");
    }

    @Test
    void 하루_읽기는_내_감정별_개수와_오늘_총합을_같은_스크립트에서_돌려준다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());
        store.write(UUID.randomUUID(), today, Map.of(EmotionState.EXHAUSTED, 4), UUID.randomUUID());

        // when
        PressDayRead mine = store.readDay(today, DEVICE);
        PressDayRead total = store.readTotal(today);
        PressDayRead absent = store.readDay(today, UUID.randomUUID());

        // then
        assertThat(mine.status()).isEqualTo(PressReadStatus.READ);
        assertThat(mine.counts()).containsEntry(EmotionState.ANGRY, 3L).containsEntry(EmotionState.EXHAUSTED, 0L);
        assertThat(mine.total()).isEqualTo(7L);
        assertThat(total.total()).isEqualTo(7L);
        assertThat(absent.counts()).hasSize(EmotionState.values().length).allSatisfy((state, count) ->
                assertThat(count).isZero());
    }

    @Test
    void 하루_읽기는_날짜가_다르거나_시딩_표시가_없으면_값을_읽지_않는다() {
        // given
        store.write(DEVICE, today, Map.of(EmotionState.ANGRY, 3), UUID.randomUUID());

        // when
        PressDayRead mismatch = store.readDay(today.plusDays(1), DEVICE);
        redis.delete("{press}:seeded:" + today);
        PressDayRead notSeeded = store.readTotal(today);

        // then
        assertThat(mismatch.status()).isEqualTo(PressReadStatus.DATE_MISMATCH);
        assertThat(mismatch.date()).isEqualTo(today);
        assertThat(notSeeded.status()).isEqualTo(PressReadStatus.NOT_SEEDED);
        assertThat(notSeeded.total()).isZero();
    }

    private PressStoredDay 저장된_하루(UUID device, long total, long version) {
        return PressStoredDay.of(device, today, Map.of(EmotionState.ANGRY, total), total,
                Instant.parse("2026-11-09T03:00:00Z"), UUID.randomUUID(), version);
    }

    private String deviceKey() {
        return "{press}:device:" + today + ":" + DEVICE;
    }

    private String rankKey() {
        return "{press}:rank:" + today;
    }

    private String activeKey() {
        return "{press}:active:" + today;
    }
}
