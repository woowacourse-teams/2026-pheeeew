package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.command.EmotionCommandService;
import com.pheeeew.emotion.application.query.EmotionQueryService;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.presentation.dto.EmotionDetailResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
