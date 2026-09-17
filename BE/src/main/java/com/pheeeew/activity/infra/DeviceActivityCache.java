package com.pheeeew.activity.infra;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

class DeviceActivityCache {

    private static final ZoneId ACTIVITY_ZONE = ZoneId.of("Asia/Seoul");

    private final Cache<Key, ActivityMarker> markers;

    DeviceActivityCache(int capacity, Caffeine<Object, Object> builder) {
        this.markers = builder.maximumSize(capacity)
                .expireAfter(Expiry.creating((Key key, ActivityMarker marker) -> marker.timeToLive))
                .build();
    }

    ActivityMarker acquire(UUID devicePublicId, Instant occurredAt, Instant now) {
        Key key = Key.of(devicePublicId, occurredAt);
        Instant midnight = now.atZone(ACTIVITY_ZONE).toLocalDate().plusDays(1)
                .atStartOfDay(ACTIVITY_ZONE).toInstant();
        // 대기 중인 작업도 중복 제출을 막는다. 성공하면 유지하고 실패하면 해제한다.
        ActivityMarker marker = ActivityMarker.of(key, Duration.between(now, midnight));
        if (markers.asMap().putIfAbsent(key, marker) != null) {
            return null;
        }
        return marker;
    }

    void release(ActivityMarker marker) {
        // 만료, 퇴출 후 같은 키로 새 작업이 들어왔다면 이전 작업의 실패가 새 표식을 지우면 안 된다.
        markers.asMap().remove(marker.key, marker);
    }

    static final class ActivityMarker {

        private final Key key;
        private final Duration timeToLive;

        private ActivityMarker(Key key, Duration timeToLive) {
            this.key = key;
            this.timeToLive = timeToLive;
        }

        private static ActivityMarker of(Key key, Duration timeToLive) {
            return new ActivityMarker(key, timeToLive);
        }
    }

    private record Key(UUID devicePublicId, LocalDate activityDate) {
        private static Key of(UUID devicePublicId, Instant occurredAt) {
            return new Key(devicePublicId, occurredAt.atZone(ACTIVITY_ZONE).toLocalDate());
        }
    }
}
