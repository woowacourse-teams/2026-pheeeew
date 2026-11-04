package com.pheeeew.pressrace.v2.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.pressrace.v2.application.PressSnapshotService;
import com.pheeeew.pressrace.v2.domain.PressView;
import com.pheeeew.pressrace.v2.presentation.dto.PressSnapshotResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/press-race")
@RestController
public class PressSnapshotController implements PressSnapshotControllerApi {

    private final PressSnapshotService pressSnapshotService;

    @Override
    @GetMapping("/snapshot")
    public PressSnapshotResponse findSnapshot(
            @RequestParam(defaultValue = "PRESS") PressView view,
            @CurrentDevice UUID devicePublicId
    ) {
        return PressSnapshotResponse.from(pressSnapshotService.findSnapshot(devicePublicId, view));
    }
}
