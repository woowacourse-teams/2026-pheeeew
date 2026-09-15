package com.pheeeew.report.application;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.domain.repository.SighBlockRepository;
import com.pheeeew.sigh.application.SighService;
import com.pheeeew.sigh.application.dto.SighMapItem;
import com.pheeeew.sigh.domain.repository.SighRepository;
import com.pheeeew.sigh.domain.repository.query.SighSearchBounds;
import com.pheeeew.support.PostgisDataJpaTest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@PostgisDataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class BlockFilterIntegrationTest {

    private static final Instant CURRENT_TIME = Instant.parse("2026-09-01T12:00:00Z");
    private static final SighSearchBounds SEOUL_BOUNDS =
            SighSearchBounds.of(126.9000, 37.5000, 127.1000, 37.6000);
    private static final double SEOUL_CITY_HALL_LONGITUDE = 126.9780;
    private static final double SEOUL_CITY_HALL_LATITUDE = 37.5664;

    private int 등록_순번;

    @Autowired
    private SighService sighService;

    @Autowired
    private SighBlockService sighBlockService;

    @Autowired
    private DeviceBlockService deviceBlockService;

    @Autowired
    private SighRepository sighRepository;

    @Autowired
    private SighBlockRepository sighBlockRepository;

    @Autowired
    private DeviceBlockRepository deviceBlockRepository;

    @Autowired
    private DeviceRepository deviceRepository;

    @Autowired
    private JdbcClient jdbcClient;

    @MockitoBean(enforceOverride = true)
    private Clock clock;

    @BeforeEach
    void setUp() {
        given(clock.instant()).willReturn(CURRENT_TIME);
        given(clock.getZone()).willReturn(ZoneId.of("Asia/Seoul"));
    }

    @AfterEach
    void tearDown() {
        sighBlockRepository.deleteAll();
        deviceBlockRepository.deleteAll();
        sighRepository.deleteAll();
        deviceRepository.deleteAll();
        등록_순번 = 0;
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 작성자를_모르는_한숨은_두_차단이_모두_있어도_결과에_남는다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 사용자_차단_대상 = 기기를_저장한다();
        Device 한숨_차단_대상 = 기기를_저장한다();
        Long 작성자를_모르는_한숨 = 한숨을_저장한다(null);
        Long 사용자_차단_대상의_한숨 = 한숨을_저장한다(사용자_차단_대상.getId());
        Long 한숨_차단_대상의_한숨 = 한숨을_저장한다(한숨_차단_대상.getId());
        sighBlockService.save(한숨_차단_대상의_한숨, 차단자.getPublicId());
        deviceBlockService.save(사용자_차단_대상의_한숨, 차단자.getPublicId());

        // when
        List<Long> 조회된_한숨들 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(sighBlockRepository.count()).isOne();
        assertThat(deviceBlockRepository.count()).isOne();
        assertThat(조회된_한숨들).containsExactly(작성자를_모르는_한숨);
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 작성자를_모르는_한숨은_한숨_차단만_있어도_결과에_남는다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 작성자 = 기기를_저장한다();
        Long 작성자를_모르는_한숨 = 한숨을_저장한다(null);
        Long 차단할_한숨 = 한숨을_저장한다(작성자.getId());
        Long 차단하지_않은_한숨 = 한숨을_저장한다(작성자.getId());
        sighBlockService.save(차단할_한숨, 차단자.getPublicId());

        // when
        List<Long> 조회된_한숨들 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(sighBlockRepository.count()).isOne();
        assertThat(deviceBlockRepository.count()).isZero();
        assertThat(조회된_한숨들).containsExactly(차단하지_않은_한숨, 작성자를_모르는_한숨);
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 작성자를_모르는_한숨은_사용자_차단만_있어도_결과에_남는다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 작성자를_모르는_한숨 = 한숨을_저장한다(null);
        Long 차단_대상의_한숨 = 한숨을_저장한다(차단_대상.getId());
        deviceBlockService.save(차단_대상의_한숨, 차단자.getPublicId());

        // when
        List<Long> 조회된_한숨들 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(sighBlockRepository.count()).isZero();
        assertThat(deviceBlockRepository.count()).isOne();
        assertThat(조회된_한숨들).containsExactly(작성자를_모르는_한숨);
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 차단한_한숨만_결과에서_사라지고_해제하면_다시_보인다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 작성자 = 기기를_저장한다();
        Long 차단할_한숨 = 한숨을_저장한다(작성자.getId());
        Long 같은_작성자의_다른_한숨 = 한숨을_저장한다(작성자.getId());
        sighBlockService.save(차단할_한숨, 차단자.getPublicId());

        // when
        List<Long> 차단_후 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());
        sighBlockService.delete(차단할_한숨, 차단자.getPublicId());
        List<Long> 해제_후 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(차단_후).containsExactly(같은_작성자의_다른_한숨);
        assertThat(해제_후).containsExactly(같은_작성자의_다른_한숨, 차단할_한숨);
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 사용자를_차단하면_그_기기의_한숨이_차단_이후에_올린_것까지_전부_사라진다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Device 다른_작성자 = 기기를_저장한다();
        Long 차단_대상의_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 차단_대상의_다른_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 다른_작성자의_한숨 = 한숨을_저장한다(다른_작성자.getId());
        deviceBlockService.save(차단_대상의_한숨, 차단자.getPublicId());

        // when
        Long 차단_이후에_올린_한숨 = 한숨을_저장한다(차단_대상.getId());
        List<Long> 조회된_한숨들 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(deviceBlockRepository.count()).isOne();
        assertThat(조회된_한숨들)
                .containsExactly(다른_작성자의_한숨)
                .doesNotContain(차단_대상의_한숨, 차단_대상의_다른_한숨, 차단_이후에_올린_한숨);
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 사용자_차단을_해제하면_그_기기의_한숨이_다시_보인다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 차단_대상의_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 차단_대상의_다른_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 차단_식별자 = deviceBlockService.save(차단_대상의_한숨, 차단자.getPublicId()).block().blockId();

        // when
        deviceBlockService.delete(차단_식별자, 차단자.getPublicId());
        List<Long> 조회된_한숨들 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(deviceBlockRepository.count()).isZero();
        assertThat(조회된_한숨들).containsExactly(차단_대상의_다른_한숨, 차단_대상의_한숨);
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 사용자_차단을_해제해도_한숨_차단이_남아_있으면_그_한숨은_계속_가려진다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 두_번_차단한_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 차단_대상의_다른_한숨 = 한숨을_저장한다(차단_대상.getId());
        sighBlockService.save(두_번_차단한_한숨, 차단자.getPublicId());
        Long 차단_식별자 = deviceBlockService.save(두_번_차단한_한숨, 차단자.getPublicId()).block().blockId();

        // when
        deviceBlockService.delete(차단_식별자, 차단자.getPublicId());
        List<Long> 조회된_한숨들 = 조회.조회한다(sighService, SEOUL_BOUNDS, 차단자.getPublicId());

        // then
        assertThat(조회된_한숨들).containsExactly(차단_대상의_다른_한숨);
    }

    @Test
    void 인증하지_않은_지도_조회에는_차단이_적용되지_않는다() {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 작성자를_모르는_한숨 = 한숨을_저장한다(null);
        Long 개별로_차단한_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 사용자_차단으로_가려진_한숨 = 한숨을_저장한다(차단_대상.getId());
        sighBlockService.save(개별로_차단한_한숨, 차단자.getPublicId());
        deviceBlockService.save(사용자_차단으로_가려진_한숨, 차단자.getPublicId());

        // when
        List<Long> 조회된_한숨들 = 인증하지_않고_지도를_조회한다();

        // then
        assertThat(조회된_한숨들).containsExactly(
                사용자_차단으로_가려진_한숨,
                개별로_차단한_한숨,
                작성자를_모르는_한숨
        );
    }

    @ParameterizedTest
    @EnumSource(조회_방식.class)
    void 다른_기기가_만든_차단은_내_조회에_영향을_주지_않는다(조회_방식 조회) {
        // given
        Device 차단자 = 기기를_저장한다();
        Device 차단하지_않은_기기 = 기기를_저장한다();
        Device 차단_대상 = 기기를_저장한다();
        Long 개별로_차단한_한숨 = 한숨을_저장한다(차단_대상.getId());
        Long 사용자_차단으로_가려진_한숨 = 한숨을_저장한다(차단_대상.getId());
        sighBlockService.save(개별로_차단한_한숨, 차단자.getPublicId());
        deviceBlockService.save(사용자_차단으로_가려진_한숨, 차단자.getPublicId());

        // when
        List<Long> 조회된_한숨들 =
                조회.조회한다(sighService, SEOUL_BOUNDS, 차단하지_않은_기기.getPublicId());

        // then
        assertThat(조회된_한숨들).containsExactly(사용자_차단으로_가려진_한숨, 개별로_차단한_한숨);
    }

    @Test
    void 인증된_조회가_한숨을_하나도_가리지_않으면_비인증_조회와_결과가_같다() {
        // given
        Device 차단하지_않은_기기 = 기기를_저장한다();
        Device 작성자 = 기기를_저장한다();
        Long 작성자를_모르는_한숨 = 한숨을_저장한다(null);
        Long 작성자가_있는_한숨 = 한숨을_저장한다(작성자.getId());

        // when
        List<Long> 인증_조회 = 조회_방식.지도.조회한다(sighService, SEOUL_BOUNDS, 차단하지_않은_기기.getPublicId());
        List<Long> 비인증_조회 = 인증하지_않고_지도를_조회한다();

        // then
        assertThat(인증_조회).containsExactly(작성자가_있는_한숨, 작성자를_모르는_한숨);
        assertThat(인증_조회).isEqualTo(비인증_조회);
    }

    private List<Long> 인증하지_않고_지도를_조회한다() {
        return sighService.findAllWithinBounds(SEOUL_BOUNDS, Optional.empty()).sighs().stream()
                .map(SighMapItem::id)
                .toList();
    }

    private Device 기기를_저장한다() {
        return deviceRepository.saveAndFlush(기본_기기_빌더().build());
    }

    private Long 한숨을_저장한다(Long deviceId) {
        등록_순번++;
        return jdbcClient.sql("""
                        INSERT INTO sighs (request_id, location, nickname, device_id, created_at, updated_at)
                        VALUES (
                            :requestId,
                            ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326),
                            '외로운 회사원',
                            :deviceId,
                            TIMESTAMPTZ '2026-09-01T10:00:00Z' + :sequence * INTERVAL '1 minute',
                            TIMESTAMPTZ '2026-09-01T10:00:00Z' + :sequence * INTERVAL '1 minute'
                        )
                        RETURNING id
                        """)
                .param("requestId", UUID.randomUUID())
                .param("longitude", SEOUL_CITY_HALL_LONGITUDE)
                .param("latitude", SEOUL_CITY_HALL_LATITUDE)
                .param("deviceId", deviceId)
                .param("sequence", 등록_순번)
                .query(Long.class)
                .single();
    }

    private enum 조회_방식 {
        지도 {
            @Override
            List<Long> 조회한다(SighService sighService, SighSearchBounds bounds, UUID viewerPublicId) {
                return sighService.findAllWithinBounds(bounds, Optional.of(viewerPublicId)).sighs().stream()
                        .map(SighMapItem::id)
                        .toList();
            }
        },
        목록 {
            @Override
            List<Long> 조회한다(SighService sighService, SighSearchBounds bounds, UUID viewerPublicId) {
                return sighService.findFirstListPage(bounds, viewerPublicId).items().stream()
                        .map(item -> item.sigh().id())
                        .toList();
            }
        };

        abstract List<Long> 조회한다(SighService sighService, SighSearchBounds bounds, UUID viewerPublicId);
    }
}
