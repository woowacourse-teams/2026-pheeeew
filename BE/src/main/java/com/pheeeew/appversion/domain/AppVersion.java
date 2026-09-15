package com.pheeeew.appversion.domain;

import com.pheeeew.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "app_version")
@Entity
public class AppVersion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppPlatform platform;

    @Column(name = "min_supported_version", nullable = false, length = 32)
    private String minSupportedVersion;

    @Column(name = "latest_version", nullable = false, length = 32)
    private String latestVersion;

    @Column(name = "store_url", nullable = false, length = 500)
    private String storeUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Builder
    private AppVersion(
            AppPlatform platform,
            String minSupportedVersion,
            String latestVersion,
            String storeUrl,
            boolean active
    ) {
        this.platform = Objects.requireNonNull(platform);
        this.minSupportedVersion = Objects.requireNonNull(minSupportedVersion);
        this.latestVersion = Objects.requireNonNull(latestVersion);
        this.storeUrl = Objects.requireNonNull(storeUrl);
        this.active = active;
    }
}
