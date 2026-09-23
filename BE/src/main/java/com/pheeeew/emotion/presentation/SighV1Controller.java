package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.EmotionService;
import com.pheeeew.emotion.application.dto.EmotionMapResult;
import com.pheeeew.emotion.application.dto.EmotionResult;
import com.pheeeew.emotion.application.dto.EmotionSaveResult;
import com.pheeeew.emotion.presentation.dto.EmotionCreateV1Request;
import com.pheeeew.emotion.presentation.dto.SighFeature;
import com.pheeeew.emotion.presentation.dto.EmotionMapRequest;
import com.pheeeew.emotion.presentation.dto.SighMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV1Properties;
import jakarta.validation.Valid;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/sighs")
@RestController
public class SighV1Controller implements SighV1ControllerApi {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final EmotionService emotionService;

    @Override
    @GetMapping
    public ResponseEntity<SighMapResponse> findAllWithinBounds(
            @CurrentDevice Optional<UUID> devicePublicId,
            @Valid @ModelAttribute EmotionMapRequest request
    ) {
        EmotionMapResult result = emotionService.findAllWithinBounds(request.toBounds(), devicePublicId);

        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .body(SighMapResponse.from(result));
    }

    @Override
    @PostMapping
    public ResponseEntity<SighFeature<EmotionV1Properties>> save(
            @Valid @RequestBody EmotionCreateV1Request request
    ) {
        EmotionSaveResult result = emotionService.save(request.requestId(), request.longitude(), request.latitude());
        EmotionResult emotion = result.emotion();

        HttpStatus status = HttpStatus.OK;
        if (result.created()) {
            status = HttpStatus.CREATED;
        }

        return ResponseEntity.status(status)
                .contentType(GEO_JSON)
                .body(SighFeature.of(
                        emotion,
                        EmotionV1Properties.from(emotion.createdAt())
                ));
    }
}
