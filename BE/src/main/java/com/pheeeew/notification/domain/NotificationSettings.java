package com.pheeeew.notification.domain;

public record NotificationSettings(boolean collectionIssuedEnabled, boolean rankDroppedEnabled) {

    public static NotificationSettings defaults() {
        return new NotificationSettings(true, true);
    }

    public static NotificationSettings of(boolean collectionIssuedEnabled, boolean rankDroppedEnabled) {
        return new NotificationSettings(collectionIssuedEnabled, rankDroppedEnabled);
    }
}
