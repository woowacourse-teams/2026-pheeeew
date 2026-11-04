package com.pheeeew.appconfig.fixture;

import com.pheeeew.appconfig.domain.AppConfig;
import com.pheeeew.appconfig.domain.AppConfigPlatform;

public final class AppConfigFixture {

    private AppConfigFixture() {
    }

    public static AppConfig.AppConfigBuilder 기본_앱_설정_빌더() {
        return AppConfig.builder()
                .platform(AppConfigPlatform.ANDROID)
                .dailyPressRankingEnabled(true)
                .noticeMessage("")
                .revision(1);
    }
}
