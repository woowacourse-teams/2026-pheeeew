package com.pheeeew.pressevent.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.pressevent.application.PressEventService;
import com.pheeeew.pressevent.presentation.dto.PressEventAttemptRequest;
import com.pheeeew.pressevent.presentation.dto.PressEventAttemptResponse;
import com.pheeeew.pressevent.presentation.dto.PressEventRankingResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/press-events/{eventId}")
@RestController
public class PressEventController implements PressEventControllerApi {

    private final PressEventService pressEventService;

    @Override
    @PostMapping("/attempts")
    public PressEventAttemptResponse submit(
            @PathVariable UUID eventId,
            @RequestBody PressEventAttemptRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        return PressEventAttemptResponse.from(
                pressEventService.submit(eventId, devicePublicId, request.requestId(), request.validatedCounts()));
    }

    @Override
    @GetMapping("/rankings")
    public PressEventRankingResponse findRanking(
            @PathVariable UUID eventId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentDevice UUID devicePublicId
    ) {
        return PressEventRankingResponse.from(pressEventService.findRanking(eventId, devicePublicId, cursor, limit));
    }
}
