package com.pheeeew.appversion.application.dto;

import com.pheeeew.appversion.domain.AppVersion;

public record AppVersionResult(String minSupportedVersion, String latestVersion, String storeUrl) {

    public static AppVersionResult from(AppVersion appVersion) {
        return new AppVersionResult(
                appVersion.getMinSupportedVersion(),
                appVersion.getLatestVersion(),
                appVersion.getStoreUrl()
        );
    }
}
