package com.pheeeew.notification.domain;

public record PushRecipient(PushTarget target, NotificationSettings settings) {

    public static PushRecipient of(PushTarget target, NotificationSettings settings) {
        return new PushRecipient(target, settings);
    }

    public boolean accepts(NotificationOutboxType type) {
        return type.isCollection() ? settings.collectionClaimableEnabled() : settings.rankChangedEnabled();
    }
}
