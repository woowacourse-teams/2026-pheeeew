package com.pheeeew.sigh.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.sigh.fixture.SighFixture.기본_한숨_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceErrorCode;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.sigh.application.dto.SighDetailResult;
import com.pheeeew.sigh.application.dto.SighResult;
import com.pheeeew.sigh.application.dto.SighSaveResult;
import com.pheeeew.sigh.application.like.SighLikeService;
import com.pheeeew.sigh.application.like.dto.SighLikeResult;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighLikeRepository;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.exception.SighErrorCode;
import com.pheeeew.sigh.exception.SighException;
import com.pheeeew.support.PostgisDataJpaTest;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Import(SighLikeService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SighServiceDetailIntegrationTest {

    @Autowired
    private SighService sighService;

    @Autowired
    private SighLikeService sighLikeService;

    @Autowired
    private SighRepository sighRepository;

    @Autowired
    private SighLikeRepository sighLikeRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    private Device device;
    private Sigh sigh;

    @BeforeEach
    void setUp() {
        device = deviceRepository.save(기본_기기_빌더().build());
        sigh = sighRepository.save(기본_한숨_빌더().memo("오늘은 힘들었다").build());
    }

    @AfterEach
    void tearDown() {
        sighLikeRepository.deleteAll();
        sighRepository.deleteAll();
        deviceRepository.deleteAll();
    }

    @Test
    void 한숨_내용과_전체_좋아요_수는_유지하고_조회한_기기의_좋아요_여부를_반환한다() {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        Device unlikedDevice = deviceRepository.save(기본_기기_빌더().build());
        Sigh anotherSigh = sighRepository.save(기본_한숨_빌더().build());
        sighLikeService.update(sigh.getId(), device.getPublicId(), true);
        sighLikeService.update(sigh.getId(), anotherDevice.getPublicId(), true);
        sighLikeService.update(anotherSigh.getId(), unlikedDevice.getPublicId(), true);
        SighResult expectedSigh = SighResult.from(sighRepository.findById(sigh.getId()).orElseThrow());

        // when
        SighDetailResult liked = sighService.findById(sigh.getId(), device.getPublicId());
        SighDetailResult unliked = sighService.findById(sigh.getId(), unlikedDevice.getPublicId());
        SighDetailResult anonymous = sighService.findById(sigh.getId(), null);

        // then
        assertThat(liked.sigh()).isEqualTo(expectedSigh);
        assertThat(unliked.sigh()).isEqualTo(expectedSigh);
        assertThat(anonymous.sigh()).isEqualTo(expectedSigh);
        assertThat(liked.like()).isEqualTo(SighLikeResult.of(true, 2));
        assertThat(unliked.like()).isEqualTo(SighLikeResult.of(false, 2));
        assertThat(anonymous.like()).isEqualTo(SighLikeResult.of(false, 2));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 좋아요가_없으면_인증_여부와_관계없이_false와_0을_반환한다(boolean authenticated) {
        // given
        UUID devicePublicId = authenticated ? device.getPublicId() : null;

        // when
        SighDetailResult result = sighService.findById(sigh.getId(), devicePublicId);

        // then
        assertThat(result.like()).isEqualTo(SighLikeResult.of(false, 0));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 없거나_삭제된_한숨은_인증_여부와_관계없이_조회할_수_없다(boolean authenticated) {
        // given
        UUID devicePublicId = authenticated ? device.getPublicId() : null;
        sighLikeService.update(sigh.getId(), device.getPublicId(), true);
        Sigh deletedSigh = sighRepository.findById(sigh.getId()).orElseThrow();
        deletedSigh.delete();
        sighRepository.saveAndFlush(deletedSigh);

        // when / then
        assertSighNotFound(Long.MAX_VALUE, devicePublicId);
        assertSighNotFound(sigh.getId(), devicePublicId);
    }

    @Test
    void 등록되지_않은_기기의_식별자가_전달되면_거부한다() {
        // given
        UUID unknownDevicePublicId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> sighService.findById(sigh.getId(), unknownDevicePublicId))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 등록_재요청은_삭제_여부와_관계없이_최초_내용과_기기별_좋아요_정보를_조회한다(boolean deleted) {
        // given
        Device anotherDevice = deviceRepository.save(기본_기기_빌더().build());
        sighLikeService.update(sigh.getId(), device.getPublicId(), true);
        Sigh savedSigh = sighRepository.findById(sigh.getId()).orElseThrow();
        if (deleted) {
            savedSigh.delete();
            sighRepository.saveAndFlush(savedSigh);
        }
        SighResult expectedSigh = SighResult.from(savedSigh);

        // when
        SighSaveResult liked = sighService.save(sigh.getRequestId(), 129.0756, 35.1796, "변경한 메모", device.getPublicId());
        SighSaveResult unliked = sighService.save(sigh.getRequestId(), 129.0756, 35.1796, "변경한 메모", anotherDevice.getPublicId());

        // then
        assertThat(liked.created()).isFalse();
        assertThat(unliked.created()).isFalse();
        assertThat(liked.sigh()).isEqualTo(expectedSigh);
        assertThat(unliked.sigh()).isEqualTo(expectedSigh);
        assertThat(liked.like()).isEqualTo(SighLikeResult.of(true, 1));
        assertThat(unliked.like()).isEqualTo(SighLikeResult.of(false, 1));
        assertThat(sighRepository.count()).isOne();
    }

    @Test
    void 등록_재요청의_좋아요_조회에서도_등록되지_않은_기기는_거부한다() {
        // given
        UUID unknownDevicePublicId = UUID.randomUUID();

        // when / then
        assertThatThrownBy(() -> sighService.save(sigh.getRequestId(), 129.0756, 35.1796, null, unknownDevicePublicId))
                .isInstanceOfSatisfying(DeviceException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(DeviceErrorCode.DEVICE_NOT_FOUND));
    }

    @Test
    void 새로운_한숨의_저장_결과는_초기_좋아요_정보를_포함한다() {
        // given
        UUID requestId = UUID.randomUUID();

        // when
        SighSaveResult result = sighService.save(requestId, 126.9780, 37.5664, null, device.getPublicId());

        // then
        assertThat(result.created()).isTrue();
        assertThat(result.like()).isEqualTo(SighLikeResult.of(false, 0));
        assertThat(sighRepository.findById(result.sigh().id()).orElseThrow().getLikeCount()).isZero();
    }

    private void assertSighNotFound(Long sighId, UUID devicePublicId) {
        assertThatThrownBy(() -> sighService.findById(sighId, devicePublicId))
                .isInstanceOfSatisfying(SighException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(SighErrorCode.SIGH_NOT_FOUND));
    }
}
