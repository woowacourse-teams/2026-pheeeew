package com.pheeeew.appversion.fixture;

import com.pheeeew.appversion.domain.AppPlatform;
import com.pheeeew.appversion.domain.AppVersion;

public final class AppVersionFixture {

    private AppVersionFixture() {
    }

    public static AppVersion.AppVersionBuilder 기본_앱_버전_정책_빌더() {
        return AppVersion.builder()
                .platform(AppPlatform.ANDROID)
                .minSupportedVersion("1.0.0")
                .latestVersion("1.1.0")
                .storeUrl("https://example.com/app")
                .active(true);
    }
}
