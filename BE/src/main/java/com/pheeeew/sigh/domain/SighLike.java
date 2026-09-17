package com.pheeeew.sigh.domain;

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
@Table(name = "sigh_likes")
@Entity
public class SighLike extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sigh_id", nullable = false, updatable = false)
    private Long sighId;

    @Column(name = "device_id", nullable = false, updatable = false)
    private Long deviceId;

    @Builder
    private SighLike(Long sighId, Long deviceId) {
        this.sighId = Objects.requireNonNull(sighId);
        this.deviceId = Objects.requireNonNull(deviceId);
    }
}
