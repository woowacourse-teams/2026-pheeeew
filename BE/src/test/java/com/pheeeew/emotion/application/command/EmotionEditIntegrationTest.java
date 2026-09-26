package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.fixture.DeviceFixture.기본_기기_빌더;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_REQUIRED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.fixture.EmotionFixture.기본_한숨_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_그룹_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.기본_스탬프_빌더;
import static com.pheeeew.groups.fixture.GroupFixture.일반_멤버_빌더;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.AudioUploadLinker;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.emotion.exception.EmotionException;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.exception.GroupException;
import com.pheeeew.report.domain.EmotionReport;
import com.pheeeew.report.domain.repository.EmotionReportRepository;
import com.pheeeew.support.PostgisDataJpaTest;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@PostgisDataJpaTest
@Import({EmotionCommandService.class, EmotionContentResolver.class, EmotionQueryService.class})
class EmotionEditIntegrationTest {

    @Autowired
    private EmotionCommandService command;
    @Autowired
    private EmotionQueryService query;
    @Autowired
    private EmotionRepository emotions;
    @Autowired
    private DeviceRepository devices;
    @Autowired
    private EmotionReportRepository reports;
    @Autowired
    private EntityManager em;
    @Autowired
    private JdbcClient jdbc;
    @MockitoBean
    private AudioUploadLinker linker;

    private Device owner;
    private Device other;

    @BeforeEach
    void setUp() {
        owner = devices.save(기본_기기_빌더().build());
        other = devices.save(기본_기기_빌더().build());
    }

    @ParameterizedTest
    @MethodSource("contentTransitions")
    void 오래된_본인_감정도_모든_내용_유형으로_변경하고_위치와_각도는_유지한다(String before, String after) {
        // given
        Emotion original = save(before);
        Instant createdAt = Instant.parse("2020-01-01T00:00:00Z");
        jdbc.sql("UPDATE emotions SET created_at = :at WHERE id = :id")
                .param("at", java.sql.Timestamp.from(createdAt)).param("id", original.getId()).update();
        em.clear();
        when(linker.claim("new-upload", owner.getId(), original.getRequestId())).thenReturn("recordings/new.m4a");

        // when
        command.update(original.getId(), owner.getPublicId(), EmotionState.ANGRY,
                after.equals("MEMO") ? " 새 메모 " : null, after.equals("AUDIO") ? "new-upload" : null, false, null);
        em.flush();
        em.clear();

        // then
        Emotion loaded = emotions.findById(original.getId()).orElseThrow();
        assertThat(loaded.getState()).isEqualTo(EmotionState.ANGRY);
        assertThat(loaded.getMemo()).isEqualTo(after.equals("MEMO") ? "새 메모" : null);
        assertThat(loaded.getContent().getAudio()).isEqualTo(after.equals("AUDIO")
                ? Audio.builder().objectKey("recordings/new.m4a").build() : null);
        assertThat(loaded.getLongitude()).isEqualTo(original.getLongitude());
        assertThat(loaded.getLatitude()).isEqualTo(original.getLatitude());
        assertThat(loaded.getRotationDegrees()).isEqualTo(35.5);
        assertThat(loaded.getNickname()).isEqualTo(original.getNickname());
        assertThat(loaded.getRequestId()).isEqualTo(original.getRequestId());
        assertThat(loaded.getCreatedAt()).isEqualTo(createdAt);
    }

    @Test
    void 기존_녹음을_유지하면_업로드를_다시_연결하지_않는다() {
        // given
        Emotion emotion = save("AUDIO");

        // when
        command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY, null, null, true, null);
        em.flush();
        em.clear();

        // then
        assertThat(emotions.findById(emotion.getId()).orElseThrow().getContent().getAudio().getObjectKey())
                .isEqualTo("recordings/old.m4a");
        verifyNoInteractions(linker);
    }

    @Test
    void 녹음이_없는_감정에_녹음_유지를_요청하면_거부한다() {
        Emotion emotion = save("MEMO");
        assertThatThrownBy(() -> command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY,
                null, null, true, null)).isInstanceOfSatisfying(EmotionException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_AUDIO_REQUIRED));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 다른_기기는_수정하거나_삭제할_수_없다(boolean update) {
        // given
        Emotion emotion = save("MEMO");

        // when / then
        assertThatThrownBy(() -> {
            if (update) {
                command.update(emotion.getId(), other.getPublicId(), EmotionState.ANGRY, null, "upload", false, null);
            } else {
                command.delete(emotion.getId(), other.getPublicId());
            }
        }).isInstanceOfSatisfying(EmotionException.class,
                error -> assertThat(error.getErrorCode()).isEqualTo(EMOTION_NOT_VISIBLE));
        assertThat(emotion.getMemo()).isEqualTo("기존 메모");
        assertThat(emotion.getDeletedAt()).isNull();
        verifyNoInteractions(linker);
    }

    @Test
    void 없는_기기와_없는_감정은_변경하지_않는다() {
        Emotion emotion = save("NONE");
        assertThatThrownBy(() -> command.delete(emotion.getId(), UUID.randomUUID())).isInstanceOf(DeviceException.class);
        assertThatThrownBy(() -> command.delete(Long.MAX_VALUE, owner.getPublicId())).isInstanceOf(EmotionException.class);
        assertThatThrownBy(() -> command.update(Long.MAX_VALUE, owner.getPublicId(), EmotionState.ANGRY,
                null, "upload", false, null)).isInstanceOf(EmotionException.class);
        verifyNoInteractions(linker);
    }

    @Test
    void 소프트_삭제는_재시도할_수_있고_신고와_녹음은_보존하되_조회와_수정에서는_제외한다() {
        // given
        Emotion emotion = save("AUDIO");
        EmotionReport report = reports.save(EmotionReport.builder().emotionId(emotion.getId())
                .reporterDeviceId(other.getId()).reason("신고 사유").build());

        // when
        command.delete(emotion.getId(), owner.getPublicId());
        em.flush();
        em.clear();
        Instant deletedAt = emotions.findById(emotion.getId()).orElseThrow().getDeletedAt();
        command.delete(emotion.getId(), owner.getPublicId());
        em.flush();
        em.clear();

        // then
        Emotion deleted = emotions.findByRequestId(emotion.getRequestId()).orElseThrow();
        assertThat(deleted.getDeletedAt()).isEqualTo(deletedAt).isNotNull();
        assertThat(deleted.getContent().getAudio().getObjectKey()).isEqualTo("recordings/old.m4a");
        assertThat(reports.existsById(report.getId())).isTrue();
        assertThat(query.findFirstListPage(EmotionSearchBounds.of(126, 37, 128, 38), owner.getPublicId()).items()).isEmpty();
        assertThatThrownBy(() -> query.findById(emotion.getId(), owner.getPublicId())).isInstanceOf(EmotionException.class);
        assertThatThrownBy(() -> command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY,
                "수정", null, false, null)).isInstanceOf(EmotionException.class);
        verifyNoInteractions(linker);
    }

    @Test
    void 그룹_스탬프를_선택하고_탈퇴_후_유지하거나_제거할_수_있다() {
        // given
        Emotion emotion = save("MEMO");
        Group group = 기본_그룹_빌더().build();
        em.persist(group);
        GroupStamp stamp = 기본_스탬프_빌더(group).build();
        em.persist(stamp);
        GroupMember member = 일반_멤버_빌더(group, owner).build();
        em.persist(member);

        // when / then
        command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY, "메모", null, false, group.getPublicId());
        em.flush();
        em.clear();
        assertThat(emotions.findById(emotion.getId()).orElseThrow().getGroupStamp().getId()).isEqualTo(stamp.getId());
        em.find(GroupMember.class, member.getId()).leave(Instant.now());
        command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY, "변경", null, false, group.getPublicId());
        command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY, "변경", null, false, null);
        em.flush();
        em.clear();
        assertThat(emotions.findById(emotion.getId()).orElseThrow().getGroupStamp()).isNull();
        assertThatThrownBy(() -> command.update(emotion.getId(), owner.getPublicId(), EmotionState.ANGRY,
                "메모", null, false, group.getPublicId())).isInstanceOf(GroupException.class);
    }

    private Emotion save(String type) {
        return emotions.saveAndFlush(기본_한숨_빌더().deviceId(owner.getId()).state(EmotionState.FRUSTRATED)
                .rotationDegrees(35.5).memo(type.equals("MEMO") ? "기존 메모" : null)
                .audio(type.equals("AUDIO") ? Audio.builder().objectKey("recordings/old.m4a").build() : null).build());
    }

    private static Stream<Arguments> contentTransitions() {
        return Stream.of("NONE", "MEMO", "AUDIO").flatMap(before ->
                Stream.of("NONE", "MEMO", "AUDIO").map(after -> Arguments.of(before, after)));
    }
}
