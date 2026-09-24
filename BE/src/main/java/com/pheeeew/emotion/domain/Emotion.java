package com.pheeeew.emotion.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
    private static final int MAX_MEMO_LENGTH = 200;
    private static final int MAX_NICKNAME_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false, updatable = false)
    private UUID requestId;

    @Getter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false, columnDefinition = "geometry(Point,4326)")
    private Point location;

    @Column(length = MAX_MEMO_LENGTH, updatable = false)
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, updatable = false)
    private EmotionState state;

    @Column(name = "rotation_degrees", nullable = false, updatable = false)
    private double rotationDegrees;

    @Column(nullable = false, length = MAX_NICKNAME_LENGTH, updatable = false)
    private String nickname;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "device_id", updatable = false)
    private Long deviceId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(nullable = false)
    @Version
    private Long version;

    @Builder
    private Emotion(
            UUID requestId, Point location, String memo, EmotionState state,
            double rotationDegrees, String nickname, Long deviceId
    ) {
        this.requestId = Objects.requireNonNull(requestId);
        this.location = requireWgs84Point(location);
        this.memo = normalizeMemo(memo);
        this.state = state;
        this.rotationDegrees = requireValidRotationDegrees(rotationDegrees);
        this.nickname = requireValidNickname(nickname);
        this.deviceId = deviceId;
    }

    public void increaseLikeCount() {
        this.likeCount++;
    }

    public void decreaseLikeCount() {
        if (likeCount == 0) {
            throw new IllegalStateException("좋아요 수는 0보다 작아질 수 없습니다.");
        }
        this.likeCount--;
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

    private String normalizeMemo(String memo) {
        if (memo == null) {
            return null;
        }

        String normalizedMemo = memo.strip();
        if (normalizedMemo.isEmpty()) {
            return null;
        }
        if (normalizedMemo.codePointCount(0, normalizedMemo.length()) > MAX_MEMO_LENGTH) {
            throw new IllegalArgumentException("메모는 200자를 초과할 수 없습니다.");
        }

        return normalizedMemo;
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
