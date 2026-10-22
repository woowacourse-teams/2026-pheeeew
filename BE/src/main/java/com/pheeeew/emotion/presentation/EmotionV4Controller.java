package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.exception.CommonErrorCode;
import com.pheeeew.common.exception.PheeeewException;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionMapQueryService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.presentation.dto.EmotionContentType;
import com.pheeeew.emotion.presentation.dto.EmotionCreateResponse;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4CreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV4DetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4MapRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV4MapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionV4RegionMapRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV4UpdateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionsV4WithoutBoundsRequest;
import java.net.URI;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v4/emotions")
@RestController
public class EmotionV4Controller implements EmotionV4ControllerApi {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final EmotionCommandService emotionCommandService;
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

    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<EmotionV4DetailResponse>> findListWithoutBoundsV4(
            @ModelAttribute EmotionsV4WithoutBoundsRequest request,
            @CurrentDevice UUID devicePublicId,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        rejectGroupParameters(parameters);
        var page = emotionQueryService.findListWithoutBoundsV4(devicePublicId, request.startDate(), request.endDate(),
                request.contentType(), request.scope(), request.cursor());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionV4DetailResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @GetMapping("/map")
    public ResponseEntity<CursorResponse<EmotionV4MapResponse>> findContentMapWithinBoundsV4(
            @ModelAttribute EmotionV4MapRequest request,
            @CurrentDevice UUID devicePublicId,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        rejectGroupParameters(parameters);
        if (parameters.containsKey("limit") && request.limit() == null) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, null);
        }
        var page = emotionMapQueryService.findContentMapWithinBoundsV4(request.toBounds(), devicePublicId,
                request.startDate(), request.endDate(), request.contentType(), request.scope(), request.cursor(), request.limit());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionV4MapResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @PostMapping
    public ResponseEntity<EmotionCreateResponse> saveV4(
            @RequestBody EmotionV4CreateRequest request,
            @CurrentDevice UUID devicePublicId,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        rejectGroupParameters(parameters);
        var result = EmotionCreateResponse.from(emotionCommandService.save(request.requestId(), request.state(),
                request.longitude(), request.latitude(), request.rotationDegrees(), request.memo(), request.audioUploadId(),
                null, devicePublicId, request.anonymous()));
        return ResponseEntity.ok().location(URI.create("/api/v4/emotions/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @Override
    @PutMapping("/{emotionId}")
    public ResponseEntity<Void> updateWithoutGroupChange(
            @PathVariable Long emotionId,
            @RequestBody EmotionV4UpdateRequest request,
            @CurrentDevice UUID devicePublicId,
            @RequestParam MultiValueMap<String, String> parameters
    ) {
        rejectGroupParameters(parameters);
        emotionCommandService.updateWithoutGroupChange(emotionId, devicePublicId, request.state(), request.memo(),
                request.audioUploadId(), request.contentType() == EmotionContentType.AUDIO && request.audioUploadId() == null);
        return ResponseEntity.noContent().build();
    }

    private void rejectGroupParameters(MultiValueMap<String, String> parameters) {
        if (parameters.containsKey("groupId") || parameters.getOrDefault("scope", List.of()).contains("GROUP")) {
            throw new PheeeewException(CommonErrorCode.INVALID_REQUEST, null);
        }
    }
}
