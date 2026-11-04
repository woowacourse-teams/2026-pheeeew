package com.pheeeew.notification.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "device_notification_settings")
@Entity
public class DeviceNotificationSetting extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Column(name = "collection_claimable_enabled", nullable = false)
    private boolean collectionClaimableEnabled;

    @Column(name = "rank_changed_enabled", nullable = false)
    private boolean rankChangedEnabled;

    @Builder
    private DeviceNotificationSetting(Long deviceId, NotificationSettings settings) {
        if (deviceId == null || deviceId <= 0) {
            throw new IllegalArgumentException("설정 기기는 필수입니다.");
        }
        this.deviceId = deviceId;
        update(settings);
    }

    public void update(NotificationSettings settings) {
        Objects.requireNonNull(settings);
        this.collectionClaimableEnabled = settings.collectionClaimableEnabled();
        this.rankChangedEnabled = settings.rankChangedEnabled();
    }

    public NotificationSettings toSettings() {
        return NotificationSettings.of(collectionClaimableEnabled, rankChangedEnabled);
    }
}
