package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.emotion.presentation.dto.EmotionListRequest;
import com.pheeeew.emotion.presentation.dto.EmotionMapResponse;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.presentation.dto.EmotionUpdateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionContentType;
import com.pheeeew.emotion.application.dto.EmotionPageView;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.presentation.dto.EmotionDetailResponse;
import com.pheeeew.emotion.presentation.dto.EmotionCreateRequest;
import com.pheeeew.emotion.presentation.dto.EmotionCreateResponse;
import java.net.URI;
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
@RequestMapping("/api/v1/emotions")
@RestController
public class EmotionController implements EmotionControllerApi {

    private static final MediaType GEO_JSON = MediaType.parseMediaType("application/geo+json");

    private final EmotionCommandService emotionCommandService;
    private final EmotionQueryService emotionQueryService;

    @Override
    @GetMapping
    public ResponseEntity<CursorResponse<EmotionDetailResponse>> findAll(
            @ModelAttribute EmotionListRequest request, @CurrentDevice UUID devicePublicId
    ) {
        EmotionPageView page = request.isNextPageRequest()
                ? emotionQueryService.findNextListPage(request.cursor(), devicePublicId)
                : emotionQueryService.findFirstListPage(request.toBounds(), devicePublicId, request.groupId());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionDetailResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @GetMapping("/map")
    public ResponseEntity<CursorResponse<EmotionMapResponse>> findMap(
            @ModelAttribute EmotionListRequest request, @CurrentDevice UUID devicePublicId
    ) {
        EmotionMapPageView page = request.isNextPageRequest()
                ? emotionQueryService.findNextMapPage(request.cursor(), devicePublicId)
                : emotionQueryService.findFirstMapPage(request.toBounds(), devicePublicId, request.groupId());
        return ResponseEntity.ok().cacheControl(CacheControl.noCache().cachePrivate())
                .body(CursorResponse.of(page.items().stream().map(EmotionMapResponse::from).toList(),
                        page.hasNext(), page.nextCursor()));
    }

    @Override
    @PostMapping
    public ResponseEntity<EmotionCreateResponse> save(
            @RequestBody EmotionCreateRequest request, @CurrentDevice UUID devicePublicId
    ) {
        EmotionCreateResponse result = EmotionCreateResponse.from(emotionCommandService.save(
                request.requestId(), request.state(), request.longitude(), request.latitude(), request.rotationDegrees(),
                request.memo(), request.audioUploadId(), request.groupId(), devicePublicId));
        return ResponseEntity.ok().location(URI.create("/api/v1/emotions/" + result.id()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @Override
    @GetMapping("/{emotionId}")
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
    @PutMapping("/{emotionId}")
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
    @DeleteMapping("/{emotionId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long emotionId,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.delete(emotionId, devicePublicId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PutMapping("/{emotionId}/emojis/{emojiType}")
    public ResponseEntity<Void> select(
            @PathVariable Long emotionId,
            @PathVariable EmojiType emojiType,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.updateEmoji(emotionId, devicePublicId, emojiType, true);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{emotionId}/emojis/{emojiType}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long emotionId,
            @PathVariable EmojiType emojiType,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionCommandService.updateEmoji(emotionId, devicePublicId, emojiType, false);
        return ResponseEntity.noContent().build();
    }
}
