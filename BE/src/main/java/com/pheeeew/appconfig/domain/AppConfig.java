package com.pheeeew.appconfig.domain;

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
@Table(name = "app_config")
@Entity
public class AppConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppConfigPlatform platform;

    @Column(name = "daily_press_ranking_enabled", nullable = false)
    private boolean dailyPressRankingEnabled;

    @Column(name = "notice_message", nullable = false, columnDefinition = "text")
    private String noticeMessage;

    @Column(nullable = false)
    private long revision;

    @Builder
    private AppConfig(AppConfigPlatform platform, boolean dailyPressRankingEnabled, String noticeMessage, long revision) {
        this.platform = Objects.requireNonNull(platform);
        this.noticeMessage = Objects.requireNonNull(noticeMessage);
        if (noticeMessage.codePointCount(0, noticeMessage.length()) > 200) {
            throw new IllegalArgumentException("공지는 최대 200 코드 포인트입니다.");
        }
        if (revision < 1) {
            throw new IllegalArgumentException("revision은 양의 정수여야 합니다.");
        }
        this.dailyPressRankingEnabled = dailyPressRankingEnabled;
        this.revision = revision;
    }
}
