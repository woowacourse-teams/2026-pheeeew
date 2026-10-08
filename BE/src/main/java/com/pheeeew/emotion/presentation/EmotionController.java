package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionsWithinBoundsRequest;
import com.pheeeew.emotion.presentation.dto.EmotionsWithoutBoundsRequest;
import com.pheeeew.emotion.presentation.dto.EmotionMapResponse;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapRequest;
import com.pheeeew.emotion.presentation.dto.EmotionRegionMapResponse;
import com.pheeeew.emotion.application.dto.EmotionRegionMapItemView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.presentation.dto.EmotionUpdateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionContentType;
import com.pheeeew.emotion.application.dto.EmotionPageView;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.presentation.dto.EmotionDetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionCreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionV3CreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionCreateResponse;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class EmotionController implements EmotionControllerApi {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final EmotionCommandService emotionCommandService;
    private final EmotionQueryService emotionQueryService;

    @Override
    @GetMapping("/v1/emotions")
    public ResponseEntity<CursorResponse<EmotionDetailResponse>> findListWithinBounds(
            @ModelAttribute EmotionsWithinBoundsRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        EmotionPageView page = emotionQueryService.findListWithinBounds(
                request.toBounds(), devicePublicId, request.groupId(), request.cursor());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionDetailResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @GetMapping("/v3/emotions")
    public ResponseEntity<CursorResponse<EmotionDetailResponse>> findListWithoutBounds(
            @ModelAttribute EmotionsWithoutBoundsRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        EmotionPageView page = emotionQueryService.findListWithoutBounds(devicePublicId, request.groupId(), request.cursor());

        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionDetailResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @GetMapping("/v1/emotions/map")
    public ResponseEntity<CursorResponse<EmotionMapResponse>> findMap(
            @ModelAttribute EmotionsWithinBoundsRequest request, @CurrentDevice UUID devicePublicId
    ) {
        EmotionMapPageView page = emotionQueryService.findMapWithinBounds(
                request.toBounds(), devicePublicId, request.groupId(), request.cursor());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionMapResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @GetMapping(value = "/v1/emotions/map/regions", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<EmotionRegionMapResponse>> findRegionMap(
            @ModelAttribute EmotionRegionMapRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        List<EmotionRegionMapItemView> items = emotionQueryService.findRegionMap(request.toBounds(), request.level(), request.groupId());
        List<EmotionRegionMapResponse> responses = items.stream()
                .map(EmotionRegionMapResponse::from)
                .toList();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(responses);
    }

    @Override
    @PostMapping("/v1/emotions")
    public ResponseEntity<EmotionCreateResponse> save(
            @RequestBody EmotionCreateRequest request, @CurrentDevice UUID devicePublicId
    ) {
        EmotionCreateResponse result = EmotionCreateResponse.from(emotionCommandService.save(
                request.requestId(), request.state(), request.longitude(), request.latitude(), request.rotationDegrees(),
                request.memo(), request.audioUploadId(), request.groupId(), devicePublicId, null));
        return ResponseEntity.ok().location(URI.create("/api/v1/emotions/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @Override
    @PostMapping("/v3/emotions")
    public ResponseEntity<EmotionCreateResponse> saveV3(
            @RequestBody EmotionV3CreateRequest request, @CurrentDevice UUID devicePublicId
    ) {
        EmotionCreateResponse result = EmotionCreateResponse.from(emotionCommandService.save(
                request.requestId(), request.state(), request.longitude(), request.latitude(), request.rotationDegrees(),
                request.memo(), request.audioUploadId(), request.groupId(), devicePublicId, request.anonymous()));
        return ResponseEntity.ok().location(URI.create("/api/v1/emotions/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @Override
    @GetMapping("/v1/emotions/{emotionId}")
    public ResponseEntity<EmotionDetailResponse> findById(
            @PathVariable Long emotionId,
            @CurrentDevice UUID devicePublicId
    ) {
        // TODO: Define per-device cache keys and immediate block invalidation before short-lived HTTP caching.
        return ResponseEntity.ok()
                .contentType(GEO_JSON)
                .cacheControl(CacheControl.noCache().cachePrivate())
                .body(EmotionDetailResponse.from(emotionQueryService.findById(emotionId, devicePublicId)));
    }

    @Override
    @PutMapping("/v1/emotions/{emotionId}")
    public ResponseEntity<Void> update(
            @PathVariable Long emotionId,
            @RequestBody EmotionUpdateRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.update(emotionId, devicePublicId, request.state(), request.memo(), request.audioUploadId(),
                request.contentType() == EmotionContentType.AUDIO && request.audioUploadId() == null, request.groupId());
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/v1/emotions/{emotionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long emotionId,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.delete(emotionId, devicePublicId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/v1/emotions/{emotionId}/emojis/{emojiType}")
    public ResponseEntity<Void> select(
            @PathVariable Long emotionId,
            @PathVariable EmojiType emojiType,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.updateEmoji(emotionId, devicePublicId, emojiType, true);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/v1/emotions/{emotionId}/emojis/{emojiType}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long emotionId,
            @PathVariable EmojiType emojiType,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.updateEmoji(emotionId, devicePublicId, emojiType, false);
        return ResponseEntity.noContent().build();
    }
}
