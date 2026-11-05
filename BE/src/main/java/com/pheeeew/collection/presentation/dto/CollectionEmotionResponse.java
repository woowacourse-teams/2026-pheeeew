package com.pheeeew.collection.presentation.dto;

import com.pheeeew.collection.application.dto.CollectionEmotionResult;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.presentation.dto.EmotionContentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record CollectionEmotionResponse(
        @Schema(description = "해당 날짜에 본인이 등록한 감정의 식별자") Long id,
        Instant createdAt,
        EmotionState state,
        EmotionContentType contentType,
        @Schema(description = "MEMO일 때의 메모. NONE·AUDIO이면 null") String memo,
        @Schema(description = "AUDIO일 때의 1시간 유효한 재생 URL과 만료 시각. NONE·MEMO이면 null") PlaybackUrl audio
) {

    public static CollectionEmotionResponse from(CollectionEmotionResult result) {
        var type = result.audio() != null ? EmotionContentType.AUDIO
                : result.memo() != null ? EmotionContentType.MEMO : EmotionContentType.NONE;
        return new CollectionEmotionResponse(result.id(), result.createdAt(), result.state(), type,
                result.memo(), result.audio());
    }
}
