package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.v2.domain.PressStoredDay;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PressRedisFlushWriter {

    private final PressBufferRepository pressBufferRepository;

    @Transactional
    public int write(List<PressStoredDay> days) {
        Map<UUID, Long> deviceIds = pressBufferRepository.findDeviceIds(
                days.stream().map(PressStoredDay::devicePublicId).distinct().toList());
        List<PressStoredDay> known = days.stream()
                .filter(day -> deviceIds.containsKey(day.devicePublicId()))
                .sorted(Comparator.comparing((PressStoredDay day) -> deviceIds.get(day.devicePublicId()))
                        .thenComparing(PressStoredDay::date))
                .toList();
        int accepted = 0;
        for (PressStoredDay day : known) {
            if (pressBufferRepository.saveVersioned(deviceIds.get(day.devicePublicId()), day)) {
                accepted++;
            }
        }
        return accepted;
    }
}
