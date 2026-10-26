package com.pheeeew.notification.domain;

public record NotificationSettings(boolean collectionClaimableEnabled, boolean rankChangedEnabled) {

    public static NotificationSettings defaults() {
        return new NotificationSettings(true, true);
    }

    public static NotificationSettings of(boolean collectionClaimableEnabled, boolean rankChangedEnabled) {
        return new NotificationSettings(collectionClaimableEnabled, rankChangedEnabled);
    }
}
