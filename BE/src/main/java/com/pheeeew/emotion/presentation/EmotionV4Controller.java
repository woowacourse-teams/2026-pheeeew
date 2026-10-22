package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.emotion.application.query.EmotionMapQueryService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4DetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4RegionMapRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v4/emotions")
@RestController
public class EmotionV4Controller implements EmotionV4ControllerApi {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final EmotionQueryService emotionQueryService;
    private final EmotionMapQueryService emotionMapQueryService;

    @Override
    @GetMapping("/{emotionId}")
    public ResponseEntity<EmotionV4DetailResponse> findById(
            @PathVariable Long emotionId,
            @CurrentDevice UUID devicePublicId,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        rejectGroupParameters(parameters);
        return ResponseEntity.ok().contentType(GEO_JSON)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(EmotionV4DetailResponse.from(emotionQueryService.findById(emotionId, devicePublicId)));
    }

    @Override
    @GetMapping(value = "/map/regions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EmotionRegionMapResponse>> findRegionMap(
            @ModelAttribute EmotionV4RegionMapRequest request,
            @CurrentDevice UUID devicePublicId,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        rejectGroupParameters(parameters);
        var items = emotionMapQueryService.findRegionMap(
                request.toBounds(), request.level(), devicePublicId, request.toFilter());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(items.stream().map(EmotionRegionMapResponse::from).toList());
    }

    private void rejectGroupParameters(MultiValueMap<String, String> parameters) {
        if (parameters.containsKey("groupId") || parameters.getOrDefault("scope", List.of()).contains("GROUP")) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, null);
        }
    }
}
