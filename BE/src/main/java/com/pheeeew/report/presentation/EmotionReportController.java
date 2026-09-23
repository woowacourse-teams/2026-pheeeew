package com.pheeeew.report.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.report.application.EmotionReportService;
import com.pheeeew.report.application.dto.EmotionReportResult;
import com.pheeeew.report.presentation.dto.EmotionReportCreateRequest;
import com.pheeeew.report.presentation.dto.EmotionReportResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/reports")
@RestController
public class EmotionReportController implements EmotionReportControllerApi {

    private final EmotionReportService emotionReportService;

    @Override
    @PostMapping
    public ResponseEntity<EmotionReportResponse> save(
            @CurrentDevice UUID devicePublicId,
            @Valid @RequestBody EmotionReportCreateRequest request
    ) {
        EmotionReportResult result = emotionReportService.save(request.emotionId(), devicePublicId, request.reason());

        HttpStatus status = HttpStatus.OK;
        if (result.created()) {
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .body(EmotionReportResponse.from(result));
    }
}
