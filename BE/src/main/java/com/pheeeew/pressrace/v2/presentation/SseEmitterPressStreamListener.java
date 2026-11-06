package com.pheeeew.pressrace.v2.presentation;

import com.pheeeew.pressrace.v2.application.PressStreamListener;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.presentation.dto.PressSnapshotResponse;
import java.io.IOException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterPressStreamListener implements PressStreamListener {

    static final String SNAPSHOT_EVENT = "snapshot";
    private static final String HEARTBEAT_COMMENT = "heartbeat";

    private final SseEmitter emitter;

    SseEmitterPressStreamListener(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void sendSnapshot(PressScreen screen) throws IOException {
        emitter.send(SseEmitter.event()
                .name(SNAPSHOT_EVENT)
                .data(PressSnapshotResponse.from(screen)));
    }

    @Override
    public void sendHeartbeat() throws IOException {
        emitter.send(SseEmitter.event().comment(HEARTBEAT_COMMENT));
    }

    @Override
    public void close() {
        emitter.complete();
    }
}
