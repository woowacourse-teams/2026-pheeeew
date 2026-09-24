package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.emoji.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.query.dto.EmotionDetailView;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "EmotionDetailResponse")
public record EmotionDetailResponse(
        @Schema(example = "Feature") String type,
        @Schema(description = "감정 ID", example = "42") Long id,
        PointGeometry geometry,
        Properties properties
) {

    public static EmotionDetailResponse from(EmotionDetailView view) {
        return new EmotionDetailResponse(
                "Feature", view.id(), PointGeometry.of(view.longitude(), view.latitude()), Properties.from(view)
        );
    }

    public record Properties(
            @Schema(description = "감정 작성 시각", example = "2026-09-24T12:00:00Z") Instant createdAt,
            @Schema(description = "감정 상태", example = "FRUSTRATED") EmotionState state,
            @Schema(description = "스탬프 회전 각도. 0도 이상 360도 미만", minimum = "0", example = "35.5")
            double rotationDegrees,
            @Schema(description = "감정 메모", nullable = true, example = "답답한 하루") String memo,
            @Schema(description = "익명 닉네임", example = "먼지구름") String nickname,
            List<Emoji> emojis,
            EmotionContentType contentType,
            @Schema(description = "녹음 상세 조회에만 포함됩니다. 만료되면 상세를 다시 조회합니다.", nullable = true)
            PlaybackUrl audio
    ) {

        public static Properties from(EmotionDetailView view) {
            return new Properties(
                    view.createdAt(), view.state(), view.rotationDegrees(), view.memo(), view.nickname(),
                    view.emojis().stream().map(Emoji::from).toList(),
                    view.hasAudio() ? EmotionContentType.AUDIO
                            : view.memo() == null ? EmotionContentType.NONE : EmotionContentType.MEMO,
                    view.audio()
            );
        }
    }

    public record Emoji(
            @Schema(description = "이모지 코드", example = "HEART") EmojiType type,
            @Schema(description = "전체 선택 수", minimum = "0", example = "2") long count,
            @Schema(description = "인증된 기기의 선택 여부", example = "true") boolean selected
    ) {

        public static Emoji from(EmotionEmojiResult result) {
            return new Emoji(result.type(), result.count(), result.selected());
        }
    }
}
