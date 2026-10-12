package com.pheeeew.activity.infra.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pheeeew.activity.infra.metrics.DeviceActivityMetricSnapshot.Count;
import com.pheeeew.activity.infra.metrics.DeviceActivityMetricSnapshot.Platform;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class DeviceActivityMetricSnapshotTest {

    @Test
    void 입력_목록이나_반환_목록을_통해_스냅샷의_수치를_바꿀_수_없다() {
        // given
        Count count = Count.of(Platform.ANDROID, 2, 8);
        List<Count> counts = new ArrayList<>(List.of(count));
        DeviceActivityMetricSnapshot snapshot = DeviceActivityMetricSnapshot.of(
                LocalDate.of(2026, 9, 17), Instant.parse("2026-09-17T14:59:59Z"),
                Instant.parse("2026-09-01T03:00:00Z"), counts);

        // when
        counts.clear();

        // then
        assertThat(snapshot.counts()).containsExactly(count);
        assertThatThrownBy(() -> snapshot.counts().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
}
