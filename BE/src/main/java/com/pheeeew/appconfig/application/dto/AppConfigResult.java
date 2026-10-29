package com.pheeeew.appconfig.application.dto;

import com.pheeeew.appconfig.domain.AppConfig;
import java.time.Instant;

public record AppConfigResult(long revision, boolean dailyPressRankingEnabled, String noticeMessage, Instant updatedAt) {

    public static AppConfigResult from(AppConfig config) {
        return new AppConfigResult(config.getRevision(), config.isDailyPressRankingEnabled(),
                config.getNoticeMessage(), config.getUpdatedAt());
    }
}
