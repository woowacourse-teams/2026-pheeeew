package com.pheeeew.appconfig.domain;

import static com.pheeeew.appconfig.exception.AppConfigErrorCode.INVALID_PLATFORM;

import com.pheeeew.appconfig.exception.AppConfigException;

public enum AppConfigPlatform {
    ANDROID,
    IOS;

    public static AppConfigPlatform from(String value) {
        for (AppConfigPlatform platform : values()) {
            if (platform.name().equalsIgnoreCase(value)) {
                return platform;
            }
        }
        throw new AppConfigException(INVALID_PLATFORM);
    }
}
