package com.pheeeew.emotion.domain;

import com.pheeeew.common.domain.BaseEntity;
import com.pheeeew.groups.domain.GroupStamp;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.locationtech.jts.geom.Point;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "emotions")
@Entity
public class Emotion extends BaseEntity {

    private static final int WGS84_SRID = 4326;
    private static final int MAX_NICKNAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Getter(AccessLevel.NONE)
    @Embedded
    private EmotionContent content;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private EmotionState state;

    @Column(name = "rotation_degrees", nullable = false, updatable = false)
    private double rotationDegrees;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_stamp_id")
    private GroupStamp groupStamp;

    @Column(nullable = false, length = MAX_NICKNAME_LENGTH, updatable = false)
    private String nickname;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "device_id", updatable = false)
    private Long deviceId;

    @Column(nullable = false)
    @Version
    private Long version;

    @Builder
    private Emotion(
            UUID requestId, Point location, String memo, Audio audio, EmotionState state,
            double rotationDegrees, String nickname, Long deviceId, GroupStamp groupStamp
    ) {
        this.requestId = Objects.requireNonNull(requestId);
        this.location = requireWgs84Point(location);
        this.content = EmotionContent.builder().memo(memo).audio(audio).build();
        this.state = state;
        this.rotationDegrees = requireValidRotationDegrees(rotationDegrees);
        this.groupStamp = groupStamp;
        this.nickname = requireValidNickname(nickname);
        this.deviceId = deviceId;
    }

    public void update(EmotionState state, EmotionContent content, GroupStamp groupStamp) {
        if (state == null || content == null) {
            throw new IllegalArgumentException("감정 상태와 내용은 필수입니다.");
        }
        this.state = state;
        this.content = content;
        this.groupStamp = groupStamp;
    }

    public void delete() {
        if (deletedAt != null) {
            return;
        }
        this.deletedAt = Instant.now();
    }

    public double getLongitude() {
        return location.getX();
    }

    public double getLatitude() {
        return location.getY();
    }

    public EmotionContent getContent() {
        return content == null ? EmotionContent.builder().build() : content;
    }

    public String getMemo() {
        return getContent().getMemo();
    }

    private Point requireWgs84Point(Point location) {
        Objects.requireNonNull(location);
        if (location.isEmpty() || location.getSRID() != WGS84_SRID) {
            throw new IllegalArgumentException("위치는 비어 있지 않은 WGS84(SRID 4326) 점 좌표여야 합니다.");
        }
        return location;
    }

    private double requireValidRotationDegrees(double rotationDegrees) {
        if (!Double.isFinite(rotationDegrees) || rotationDegrees < 0 || rotationDegrees >= 360) {
            throw new IllegalArgumentException("스탬프 각도는 0도 이상 360도 미만이어야 합니다.");
        }
        return rotationDegrees;
    }

    private String requireValidNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("닉네임은 비어 있을 수 없습니다.");
        }
        if (nickname.length() > MAX_NICKNAME_LENGTH) {
            throw new IllegalArgumentException("닉네임은 50자를 초과할 수 없습니다.");
        }

        return nickname;
    }
}
