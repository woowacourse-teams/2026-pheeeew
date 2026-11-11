package com.pheeeew.pressrace.v2.application;

import com.pheeeew.pressrace.application.PressRaceService;
import com.pheeeew.pressrace.exception.PressRaceException;
import com.pheeeew.pressrace.infra.PressRaceRepository;
import com.pheeeew.pressrace.v2.domain.PressDaySnapshot;
import com.pheeeew.pressrace.v2.infra.PressBufferRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class PressBufferWriter {

    private final PressRaceRepository pressRaceRepository;
    private final PressBufferRepository pressBufferRepository;
    private final PressRaceService pressRaceService;

    @Transactional
    public void write(List<PressDaySnapshot> snapshots) {
        Map<String, Object> control = pressRaceRepository.lockServerFence();
        if (!pressRaceService.serverGeneration().equals(control.get("active_server_generation"))) {
            throw new PressRaceException("SERVER_REPLACED");
        }
        snapshots.forEach(pressBufferRepository::save);
    }
}
