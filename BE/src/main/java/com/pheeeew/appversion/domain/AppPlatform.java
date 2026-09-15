package com.pheeeew.appversion.domain;

import static com.pheeeew.appversion.exception.AppVersionErrorCode.INVALID_PLATFORM;

import com.pheeeew.appversion.exception.AppVersionException;

public enum AppPlatform {
    ANDROID,
    IOS;

    public static AppPlatform from(String value) {
        for (AppPlatform platform : values()) {
            if (platform.name().equalsIgnoreCase(value)) {
                return platform;
            }
        }
        throw new AppVersionException(INVALID_PLATFORM);
    }
}
