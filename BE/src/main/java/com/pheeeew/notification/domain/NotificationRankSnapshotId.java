package com.pheeeew.notification.domain;

import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRankSnapshotId implements Serializable {

    private Instant roundAt;
    private Long deviceId;
}
