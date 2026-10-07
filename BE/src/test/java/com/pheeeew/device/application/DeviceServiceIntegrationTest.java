package com.pheeeew.device.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.device.fixture.DeviceFixture.무결성_증명_없음;
import static com.pheeeew.device.fixture.DeviceFixture.토큰_해시;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.pheeeew.auth.infra.jwt.AccessTokenClaims;
import com.pheeeew.device.application.dto.DeviceSaveResult;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.DevicePlatform;
import com.pheeeew.device.domain.repository.DeviceRefreshTokenRepository;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeviceServiceIntegrationTest {

    private static final int 동시_요청_수 = 6;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private DeviceTokenService deviceTokenService;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private DeviceRefreshTokenRepository deviceRefreshTokenRepository;

    @Autowired
    private JwtDecoder jwtDecoder;

    @Autowired
    private JdbcClient jdbcClient;

    @AfterEach
    void tearDown() {
        deviceRefreshTokenRepository.deleteAllInBatch();
        deviceRepository.deleteAllInBatch();
    }

    @Test
    void 기기_공개_식별자로_현재_닉네임을_조회한다() {
        // given
        deviceRepository.saveAndFlush(기본_기기_빌더().nickname("다른 기기").build());
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("Star  K").build());

        // when
        String nickname = deviceService.findNickname(device.getPublicId());

        // then
        assertThat(nickname).isEqualTo("Star  K");
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 닉네임_미설정_기기는_null을_반환한다() {
        // given
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().build());

        // when
        String nickname = deviceService.findNickname(device.getPublicId());

        // then
        assertThat(nickname).isNull();
        assertThat(deviceRepository.findByPublicId(device.getPublicId()).orElseThrow().getNickname()).isNull();
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 존재하지_않는_기기의_닉네임을_조회하면_인증_정보_오류를_반환한다() {
        // given
        UUID devicePublicId = UUID.randomUUID();

        // when
        Throwable throwable = catchThrowable(() -> deviceService.findNickname(devicePublicId));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
        assertThat(throwable.getMessage()).doesNotContain(devicePublicId.toString());
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 미설정_기기의_닉네임을_최초_설정한다() {
        // given
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().build());

        // when
        deviceService.updateNickname(device.getPublicId(), "  Star  K  ");

        // then
        assertThat(deviceService.findNickname(device.getPublicId())).isEqualTo("Star  K");
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 닉네임을_수정해도_기기_식별자와_기존_토큰은_유지한다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult tokens = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));
        Device before = deviceRepository.findByRequestId(requestId).orElseThrow();

        // when
        deviceService.updateNickname(before.getPublicId(), "새 닉네임");

        // then
        Device after = deviceRepository.findByPublicId(before.getPublicId()).orElseThrow();
        assertThat(after.getNickname()).isEqualTo("새 닉네임");
        assertThat(after.getId()).isEqualTo(before.getId());
        assertThat(after.getPublicId()).isEqualTo(before.getPublicId());
        assertThat(after.getRequestId()).isEqualTo(requestId);
        assertThat(after.getPlatform()).isEqualTo(before.getPlatform());
        assertThat(deviceService.findNicknameAvailability("스타크")).isTrue();
        assertThat(기기_공개_식별자를_뽑는다(tokens.accessToken())).isEqualTo(before.getPublicId().toString());
        assertThat(기기_공개_식별자를_뽑는다(deviceTokenService.reissueAccessToken(tokens.refreshToken()).accessToken()))
                .isEqualTo(before.getPublicId().toString());
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Star K", " star k "})
    void 자기_닉네임의_유지와_대소문자_변경을_허용한다(String nickname) {
        // given
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("Star K").build());

        // when
        deviceService.updateNickname(device.getPublicId(), nickname);

        // then
        assertThat(deviceService.findNickname(device.getPublicId())).isEqualTo(nickname.strip());
        assertThat(deviceRepository.count()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Star K", " star k "})
    void 다른_기기의_닉네임으로_수정하면_충돌을_반환하고_기존_값을_유지한다(String nickname) {
        // given
        Device other = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("Star K").build());
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("스타크").build());

        // when
        Throwable throwable = catchThrowable(() -> deviceService.updateNickname(device.getPublicId(), nickname));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_DUPLICATED);
        assertThat(deviceService.findNickname(device.getPublicId())).isEqualTo("스타크");
        assertThat(deviceService.findNickname(other.getPublicId())).isEqualTo("Star K");
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" 익명 ", "Star1", "abcdefghijk"})
    void 잘못된_닉네임_수정은_거부하고_저장된_값을_유지한다(String nickname) {
        // given
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("스타크").build());

        // when
        Throwable throwable = catchThrowable(() -> deviceService.updateNickname(device.getPublicId(), nickname));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_INVALID);
        assertThat(deviceService.findNickname(device.getPublicId())).isEqualTo("스타크");
    }

    @Test
    void 존재하지_않는_기기의_닉네임을_수정할_수_없다() {
        // given
        Device device = deviceRepository.saveAndFlush(기본_기기_빌더().nickname("스타크").build());

        // when
        Throwable throwable = catchThrowable(() -> deviceService.updateNickname(UUID.randomUUID(), "새 닉네임"));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND);
        assertThat(deviceService.findNickname(device.getPublicId())).isEqualTo("스타크");
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 서로_다른_기기가_같은_닉네임으로_동시에_수정하면_한_기기만_성공한다() throws Exception {
        // given
        List<Device> devices = java.util.stream.IntStream.range(0, 동시_요청_수)
                .mapToObj(index -> deviceRepository.saveAndFlush(기본_기기_빌더()
                        .nickname(String.valueOf((char) ('가' + index))).build()))
                .toList();

        // when
        List<Throwable> results = 동시에_실행한다(index -> catchThrowable(() -> deviceService.updateNickname(
                devices.get(index).getPublicId(), index % 2 == 0 ? "Star K" : "star k")));

        // then
        assertThat(results).filteredOn(result -> result == null).hasSize(1);
        assertThat(results).filteredOn(result -> result != null).hasSize(동시_요청_수 - 1)
                .allSatisfy(result -> {
                    assertThat(result).isInstanceOf(DeviceException.class);
                    assertThat(((DeviceException) result).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_DUPLICATED);
                });
        for (int index = 0; index < 동시_요청_수; index++) {
            String nickname = deviceService.findNickname(devices.get(index).getPublicId());
            assertThat(nickname).isEqualTo(results.get(index) == null
                    ? (index % 2 == 0 ? "Star K" : "star k") : devices.get(index).getNickname());
        }
        assertThat(deviceRepository.findAll()).filteredOn(device -> device.getNickname().equalsIgnoreCase("Star K"))
                .hasSize(1);
        assertThat(deviceRepository.count()).isEqualTo(동시_요청_수);
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @Test
    void 사용_가능한_닉네임을_조회해도_기기나_토큰을_저장하지_않는다() {
        // given / when
        boolean available = deviceService.findNicknameAvailability("  Star K  ");

        // then
        assertThat(available).isTrue();
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Star K", "star k", " STAR K "})
    void 이미_사용_중인_닉네임은_대소문자와_앞뒤_공백에_관계없이_사용할_수_없다(String nickname) {
        // given
        deviceRepository.saveAndFlush(기본_기기_빌더().nickname("Star K").build());

        // when
        boolean available = deviceService.findNicknameAvailability(nickname);

        // then
        assertThat(available).isFalse();
    }

    @Test
    void 중간_공백_개수가_다른_닉네임은_구분한다() {
        // given
        deviceRepository.saveAndFlush(기본_기기_빌더().nickname("Star K").build());

        // when
        boolean available = deviceService.findNicknameAvailability("Star  K");

        // then
        assertThat(available).isTrue();
    }

    @Test
    void 사용_가능_여부_조회에도_공통_닉네임_검증을_적용한다() {
        // given / when
        Throwable throwable = catchThrowable(() -> deviceService.findNicknameAvailability(" 익명 "));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_INVALID);
    }

    @Test
    void 백필한_닉네임을_JPA로_조회한다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceRepository.saveAndFlush(기본_기기_빌더().requestId(requestId).build());
        jdbcClient.sql("UPDATE devices SET nickname = '스타크' WHERE request_id = :requestId")
                .param("requestId", requestId).update();

        // when
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();

        // then
        assertThat(device.getNickname()).isEqualTo("스타크");
    }

    @Test
    void 닉네임을_JPA로_저장하고_다시_조회한다() {
        // given
        Device device = 기본_기기_빌더().nickname("  Star  K  ").build();
        deviceRepository.saveAndFlush(device);

        // when
        Device saved = deviceRepository.findByPublicId(device.getPublicId()).orElseThrow();

        // then
        assertThat(saved.getNickname()).isEqualTo("Star  K");
    }

    @Test
    void 최초_등록은_기기와_리프레시_토큰을_하나씩_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, "  Star K  ", 무결성_증명_없음(DevicePlatform.ANDROID));

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresIn()).isEqualTo(1800L);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(device.getNickname()).isEqualTo("Star K");
        assertThat(기기_공개_식별자를_뽑는다(result.accessToken())).isEqualTo(device.getPublicId().toString());
    }

    @Test
    void 닉네임_없는_등록은_미설정_기기와_토큰을_저장한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, 무결성_증명_없음(DevicePlatform.ANDROID));

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(result.created()).isTrue();
        assertThat(device.getNickname()).isNull();
        assertThat(device.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(기기_공개_식별자를_뽑는다(result.accessToken())).isEqualTo(device.getPublicId().toString());
        assertThat(기기_공개_식별자를_뽑는다(deviceTokenService.reissueAccessToken(result.refreshToken()).accessToken()))
                .isEqualTo(device.getPublicId().toString());
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 서로_다른_기기는_닉네임_없이_등록할_수_있다() {
        // given / when
        DeviceSaveResult first = deviceService.save(UUID.randomUUID(), 무결성_증명_없음(DevicePlatform.ANDROID));
        DeviceSaveResult second = deviceService.save(UUID.randomUUID(), 무결성_증명_없음(DevicePlatform.IOS));

        // then
        assertThat(first.created()).isTrue();
        assertThat(second.created()).isTrue();
        assertThat(deviceRepository.findAll()).allSatisfy(device -> assertThat(device.getNickname()).isNull());
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 닉네임_없는_기기를_닉네임과_함께_재시도해도_최초_상태를_유지한다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult first = deviceService.save(requestId, 무결성_증명_없음(DevicePlatform.ANDROID));

        // when
        DeviceSaveResult retried = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.IOS));

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(retried.created()).isFalse();
        assertThat(device.getNickname()).isNull();
        assertThat(device.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(기기_공개_식별자를_뽑는다(retried.accessToken()))
                .isEqualTo(기기_공개_식별자를_뽑는다(first.accessToken()));
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 닉네임이_있는_기기를_닉네임_없이_재시도해도_최초_상태를_유지한다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult first = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));

        // when
        DeviceSaveResult retried = deviceService.save(requestId, 무결성_증명_없음(DevicePlatform.IOS));

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(retried.created()).isFalse();
        assertThat(device.getNickname()).isEqualTo("스타크");
        assertThat(device.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(기기_공개_식별자를_뽑는다(retried.accessToken()))
                .isEqualTo(기기_공개_식별자를_뽑는다(first.accessToken()));
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 두_등록_방식이_같은_요청으로_경합해도_최초_기기_정보와_토큰_대상을_유지한다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        List<DeviceSaveResult> results = 동시에_실행한다(index -> {
            if (index % 2 == 0) {
                return deviceService.save(requestId, 무결성_증명_없음(DevicePlatform.ANDROID));
            }
            return deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.IOS));
        });

        // then
        assertThat(results).filteredOn(DeviceSaveResult::created).hasSize(1);
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        for (int index = 0; index < results.size(); index++) {
            DeviceSaveResult result = results.get(index);
            assertThat(result.refreshToken()).isNotBlank();
            assertThat(기기_공개_식별자를_뽑는다(result.accessToken())).isEqualTo(device.getPublicId().toString());
            if (result.created()) {
                assertThat(device.getNickname()).isEqualTo(index % 2 == 0 ? null : "스타크");
                assertThat(device.getPlatform()).isEqualTo(index % 2 == 0 ? DevicePlatform.ANDROID : DevicePlatform.IOS);
            }
        }
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(동시_요청_수);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", " 익명 ", "Star1", "abcdefghijk", "Star\tK"})
    void 잘못된_닉네임으로_등록하면_기기와_토큰을_저장하지_않는다(String nickname) {
        // given / when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                UUID.randomUUID(), nickname, 무결성_증명_없음(DevicePlatform.ANDROID)));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_INVALID);
        assertThat(deviceRepository.count()).isZero();
        assertThat(deviceRefreshTokenRepository.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Star K", "star k", " STAR K "})
    void 다른_요청이_중복_닉네임으로_등록하면_기기와_토큰을_추가하지_않는다(String nickname) {
        // given
        deviceService.save(UUID.randomUUID(), "Star K", 무결성_증명_없음(DevicePlatform.ANDROID));
        UUID failedRequestId = UUID.randomUUID();

        // when
        Throwable throwable = catchThrowable(() -> deviceService.save(
                failedRequestId, nickname, 무결성_증명_없음(DevicePlatform.ANDROID)));

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NICKNAME_DUPLICATED);
        assertThat(deviceRepository.findByRequestId(failedRequestId)).isEmpty();
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 등록_재시도에서_다른_사용중인_닉네임을_보내도_최초_닉네임을_유지한다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, "Star K", 무결성_증명_없음(DevicePlatform.ANDROID));
        deviceRepository.saveAndFlush(기본_기기_빌더().nickname("다른 닉네임").build());

        // when
        DeviceSaveResult result = deviceService.save(requestId, "다른 닉네임", 무결성_증명_없음(DevicePlatform.IOS));

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        assertThat(result.created()).isFalse();
        assertThat(device.getNickname()).isEqualTo("Star K");
        assertThat(device.getPlatform()).isEqualTo(DevicePlatform.ANDROID);
        assertThat(deviceRepository.count()).isEqualTo(2);
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 다른_요청이_같은_닉네임으로_동시에_등록하면_한_기기에만_토큰을_발급한다() throws Exception {
        // given
        List<UUID> requestIds = java.util.stream.IntStream.range(0, 동시_요청_수)
                .mapToObj(index -> UUID.randomUUID()).toList();

        // when
        List<Object> results = 동시에_실행한다(index -> {
            try {
                String nickname = index % 2 == 0 ? "Star K" : "star k";
                return deviceService.save(requestIds.get(index), nickname, 무결성_증명_없음(DevicePlatform.ANDROID));
            } catch (DeviceException exception) {
                return exception;
            }
        });

        // then
        assertThat(results).filteredOn(DeviceSaveResult.class::isInstance).hasSize(1);
        List<DeviceException> failures = results.stream().filter(DeviceException.class::isInstance)
                .map(DeviceException.class::cast).toList();
        assertThat(failures).hasSize(동시_요청_수 - 1)
                .extracting(DeviceException::getErrorCode).containsOnly(DeviceErrorCode.DEVICE_NICKNAME_DUPLICATED);
        Device winner = deviceRepository.findAll().getFirst();
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
        for (UUID requestId : requestIds) {
            if (!requestId.equals(winner.getRequestId())) {
                assertThat(deviceRepository.findByRequestId(requestId)).isEmpty();
            }
        }
    }

    @Test
    void 토큰_저장_오류를_기기_중복으로_오인해_재발급하지_않는다() {
        // given
        Long sequenceBefore = jdbcClient.sql("""
                SELECT nextval(pg_get_serial_sequence('device_refresh_tokens', 'id')::regclass)
                """).query(Long.class).single();
        jdbcClient.sql("""
                ALTER TABLE device_refresh_tokens
                ADD CONSTRAINT ck_test_reject_token CHECK (false) NOT VALID
                """).update();
        try {
            // when
            Throwable throwable = catchThrowable(() -> deviceService.save(
                    UUID.randomUUID(), "Star K", 무결성_증명_없음(DevicePlatform.ANDROID)));

            // then
            assertThat(throwable).isInstanceOf(DataIntegrityViolationException.class);
            Long sequenceAfter = jdbcClient.sql("""
                    SELECT pg_sequence_last_value(pg_get_serial_sequence('device_refresh_tokens', 'id')::regclass)
                    """).query(Long.class).single();
            assertThat(sequenceAfter).isEqualTo(sequenceBefore + 1);
            assertThat(deviceRepository.count()).isOne();
            assertThat(deviceRefreshTokenRepository.count()).isZero();
        } finally {
            jdbcClient.sql("ALTER TABLE device_refresh_tokens DROP CONSTRAINT ck_test_reject_token").update();
        }
    }

    @Test
    void 같은_요청_식별자로_다시_등록해도_기기는_하나만_생긴다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult first = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));

        // when
        DeviceSaveResult retried = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.IOS));

        // then
        assertThat(retried.created()).isFalse();
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRepository.findByRequestId(requestId).orElseThrow().getPlatform())
                .isEqualTo(DevicePlatform.ANDROID);
        assertThat(retried.refreshToken()).isNotEqualTo(first.refreshToken());
    }

    @Test
    void 재시도로_받은_두_리프레시_토큰_모두_재발급에_쓸_수_있다() {
        // given
        UUID requestId = UUID.randomUUID();
        DeviceSaveResult first = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));
        DeviceSaveResult retried = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));

        // when
        String firstSubject = 기기_공개_식별자를_뽑는다(deviceTokenService.reissueAccessToken(first.refreshToken())
                .accessToken());
        String retriedSubject = 기기_공개_식별자를_뽑는다(deviceTokenService.reissueAccessToken(retried.refreshToken())
                .accessToken());

        // then
        assertThat(firstSubject).isEqualTo(retriedSubject);
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 같은_요청_식별자로_동시에_등록해도_기기는_하나만_생기고_모두_토큰을_받는다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        List<DeviceSaveResult> results = 동시에_등록한다(requestId);

        // then
        assertThat(results).hasSize(동시_요청_수);
        assertThat(results).allMatch(result -> !result.refreshToken().isBlank());
        assertThat(results).filteredOn(DeviceSaveResult::created).hasSize(1);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(동시_요청_수);
    }

    @Test
    void 동시_등록에서_경합에_진_요청이_받은_토큰도_재발급에_쓸_수_있다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        List<DeviceSaveResult> results = 동시에_등록한다(requestId);

        // when
        List<String> subjects = results.stream()
                .map(result -> deviceTokenService.reissueAccessToken(result.refreshToken()).accessToken())
                .map(this::기기_공개_식별자를_뽑는다)
                .distinct()
                .toList();

        // then
        assertThat(results).hasSize(동시_요청_수);
        assertThat(subjects).hasSize(1);
    }

    @Test
    void 동시_등록의_제약_위반은_요청_식별자를_로그에_남기지_않는다() throws Exception {
        // given
        UUID requestId = UUID.randomUUID();
        ListAppender<ILoggingEvent> appender = 로그_수집을_시작한다();

        try {
            // when
            List<DeviceSaveResult> results = 동시에_등록한다(requestId);

            // then
            Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
            String 남은_로그 = 수집한_로그(appender);
            assertThat(남은_로그).doesNotContain(requestId.toString());
            assertThat(남은_로그).doesNotContain(device.getPublicId().toString());
            for (DeviceSaveResult result : results) {
                assertThat(남은_로그).doesNotContain(result.refreshToken());
                assertThat(남은_로그).doesNotContain(result.accessToken());
            }
        } finally {
            로그_수집을_끝낸다(appender);
        }
    }

    @Test
    void 등록_후_5분이_지나기_전_재요청에는_토큰을_다시_발급한다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));
        등록_시각을_민다(requestId, "4 minutes 59 seconds");

        // when
        DeviceSaveResult retried = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));

        // then
        assertThat(retried.created()).isFalse();
        assertThat(retried.refreshToken()).isNotBlank();
        assertThat(deviceRefreshTokenRepository.count()).isEqualTo(2);
    }

    @Test
    void 등록_후_5분이_지난_재요청에는_토큰을_발급하지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, "Star K", 무결성_증명_없음(DevicePlatform.ANDROID));
        등록_시각을_민다(requestId, "5 minutes 1 second");

        // when
        Throwable throwable = catchThrowable(
                () -> deviceService.save(requestId, "다른 닉네임", 무결성_증명_없음(DevicePlatform.ANDROID))
        );

        // then
        assertThat(throwable).isInstanceOf(DeviceException.class);
        assertThat(((DeviceException) throwable).getErrorCode())
                .isEqualTo(DeviceErrorCode.DEVICE_REGISTRATION_WINDOW_EXPIRED);
        assertThat(deviceRepository.count()).isOne();
        assertThat(deviceRefreshTokenRepository.count()).isOne();
    }

    @Test
    void 창_밖_재요청의_예외_메시지에_요청_식별자가_들어가지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();
        deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));
        등록_시각을_민다(requestId, "5 minutes 1 second");

        // when
        Throwable throwable = catchThrowable(
                () -> deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID))
        );

        // then
        assertThat(throwable).hasMessage("기기 등록 재시도 시간이 지났습니다. 새 요청으로 등록해 주세요.");
        assertThat(throwable.getMessage()).doesNotContain(requestId.toString());
    }

    @Test
    void 리프레시_토큰은_해시로만_저장되고_액세스_토큰은_저장되지_않는다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));

        // then
        Map<String, Object> row = jdbcClient.sql("SELECT * FROM device_refresh_tokens")
                .query()
                .singleRow();
        String 저장된_행 = row.toString();
        assertThat(row.get("token_hash")).isEqualTo(토큰_해시(result.refreshToken()));
        assertThat(저장된_행)
                .doesNotContain(result.refreshToken())
                .doesNotContain(result.accessToken());
        assertThat(row.get("revoked_at")).isNull();
        assertThat(row.get("expires_at")).isNull();
    }

    @Test
    void 발급한_액세스_토큰의_대상은_그_기기의_공개_식별자다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        DeviceSaveResult result = deviceService.save(requestId, "스타크", 무결성_증명_없음(DevicePlatform.ANDROID));

        // then
        Device device = deviceRepository.findByRequestId(requestId).orElseThrow();
        AccessTokenClaims claims = AccessTokenClaims.from(jwtDecoder.decode(result.accessToken()));
        assertThat(claims.devicePublicId()).isEqualTo(device.getPublicId());
        assertThat(device.getId()).isNotNull();
        assertThat(catchThrowable(() -> Long.parseLong(claims.devicePublicId().toString())))
                .isInstanceOf(NumberFormatException.class);
    }

    private List<DeviceSaveResult> 동시에_등록한다(UUID requestId) throws Exception {
        return 동시에_실행한다(index -> deviceService.save(
                requestId, "Star K", 무결성_증명_없음(DevicePlatform.ANDROID)));
    }

    private <T> List<T> 동시에_실행한다(IntFunction<T> operation) throws Exception {
        CountDownLatch ready = new CountDownLatch(동시_요청_수);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executorService = Executors.newFixedThreadPool(동시_요청_수)) {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < 동시_요청_수; index++) {
                int requestIndex = index;
                futures.add(executorService.submit(() -> {
                    ready.countDown();
                    start.await();
                    return operation.apply(requestIndex);
                }));
            }

            boolean allRequestsReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allRequestsReady).isTrue();

            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            return results;
        }
    }

    private void 등록_시각을_민다(UUID requestId, String interval) {
        jdbcClient.sql("""
                        UPDATE devices
                        SET created_at = NOW() - CAST(:interval AS INTERVAL)
                        WHERE request_id = :requestId
                        """)
                .param("interval", interval)
                .param("requestId", requestId)
                .update();
    }

    private String 기기_공개_식별자를_뽑는다(String accessToken) {
        return AccessTokenClaims.from(jwtDecoder.decode(accessToken)).devicePublicId().toString();
    }

    private ListAppender<ILoggingEvent> 로그_수집을_시작한다() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        루트_로거().addAppender(appender);
        개발_프로파일_로거().forEach(logger -> logger.setLevel(Level.DEBUG));
        return appender;
    }

    private void 로그_수집을_끝낸다(ListAppender<ILoggingEvent> appender) {
        개발_프로파일_로거().forEach(logger -> logger.setLevel(null));
        루트_로거().detachAppender(appender);
        appender.stop();
    }

    private List<Logger> 개발_프로파일_로거() {
        return List.of(
                (Logger) LoggerFactory.getLogger("com.pheeeew"),
                (Logger) LoggerFactory.getLogger("org.hibernate.SQL")
        );
    }

    private String 수집한_로그(ListAppender<ILoggingEvent> appender) {
        StringBuilder collected = new StringBuilder();
        for (ILoggingEvent event : List.copyOf(appender.list)) {
            collected.append(event.getLoggerName()).append(' ').append(event.getFormattedMessage()).append('\n');
            IThrowableProxy throwableProxy = event.getThrowableProxy();
            while (throwableProxy != null) {
                collected.append(throwableProxy.getMessage()).append('\n');
                throwableProxy = throwableProxy.getCause();
            }
        }
        return collected.toString();
    }

    private Logger 루트_로거() {
        return (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    }
}
