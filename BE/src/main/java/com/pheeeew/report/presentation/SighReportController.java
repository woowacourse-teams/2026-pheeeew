package com.pheeeew.report.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.report.application.SighReportResult;
import com.pheeeew.report.application.SighReportService;
import com.pheeeew.report.presentation.dto.SighReportCreateRequest;
import com.pheeeew.report.presentation.dto.SighReportResponse;
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
public class SighReportController implements SighReportControllerApi {

    private final SighReportService sighReportService;

    @Override
    @PostMapping
    public ResponseEntity<SighReportResponse> save(
            @CurrentDevice UUID devicePublicId,
            @Valid @RequestBody SighReportCreateRequest request
    ) {
        SighReportResult result = sighReportService.save(request.sighId(), devicePublicId, request.reason());

        HttpStatus status = HttpStatus.OK;
        if (result.created()) {
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .body(SighReportResponse.from(result));
    }
}
