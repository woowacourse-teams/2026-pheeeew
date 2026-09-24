package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.emoji.EmotionEmojiService;
import com.pheeeew.emotion.domain.EmojiType;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v1/emotions")
@RestController
public class EmotionController implements EmotionControllerApi {

    private final EmotionEmojiService emotionEmojiService;

    @Override
    @PutMapping("/{emotionId}/emojis/{emojiType}")
    public ResponseEntity<Void> select(
            @PathVariable Long emotionId,
            @PathVariable EmojiType emojiType,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionEmojiService.update(emotionId, devicePublicId, emojiType, true);
        return ResponseEntity.noContent().build();
    }

    @Override
    @DeleteMapping("/{emotionId}/emojis/{emojiType}")
    public ResponseEntity<Void> cancel(
            @PathVariable Long emotionId,
            @PathVariable EmojiType emojiType,
            @CurrentDevice UUID devicePublicId
    ) {
        emotionEmojiService.update(emotionId, devicePublicId, emojiType, false);
        return ResponseEntity.noContent().build();
    }
}
