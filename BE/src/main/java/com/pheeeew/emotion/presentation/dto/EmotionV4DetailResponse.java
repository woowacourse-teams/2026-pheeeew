package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(name = "EmotionV4DetailResponse")
public record EmotionV4DetailResponse(
        @Schema(example = "Feature") String type,
        @Schema(description = "감정 ID", example = "42") Long id,
        PointGeometry geometry,
        Properties properties
) {

    public static EmotionV4DetailResponse from(EmotionDetailView view) {
        return new EmotionV4DetailResponse(
                "Feature", view.id(), PointGeometry.of(view.longitude(), view.latitude()), Properties.from(view)
        );
    }

    @Schema(name = "EmotionV4DetailProperties")
    public record Properties(
            @Schema(description = "감정 작성 시각", example = "2026-09-24T12:00:00Z") Instant createdAt,
            @Schema(description = "감정 상태", example = "FRUSTRATED") EmotionState state,
            @Schema(description = "스탬프 회전 각도. 0도 이상 360도 미만", minimum = "0", example = "35.5")
            double rotationDegrees,
            EmotionContentType contentType,
            @Schema(description = "감정 메모", nullable = true, example = "답답한 하루") String memo,
            @Schema(description = "녹음 목록·상세 조회에 포함되는 1시간 유효한 재생 URL입니다. 만료되면 다시 조회합니다.", nullable = true)
            PlaybackUrl audio,
            @Schema(description = "익명 감정은 '익명', 기명 감정은 작성 기기의 현재 닉네임입니다. 닉네임 수정은 기존 기명 감정에도 반영됩니다. 기기 닉네임이 없으면 '익명'으로 표시합니다.",
                    example = "익명") String nickname,
            List<Emoji> emojis,
            @Schema(description = "인증된 기기가 작성한 감정이면 true입니다. 작성 기기가 없으면 false이며, 수정·삭제 권한은 서버가 별도로 검사합니다.", example = "true")
            boolean isMine
    ) {

        public static Properties from(EmotionDetailView view) {
            return new Properties(
                    view.createdAt(), view.state(), view.rotationDegrees(),
                    view.hasAudio() ? EmotionContentType.AUDIO
                            : view.memo() == null ? EmotionContentType.NONE : EmotionContentType.MEMO,
                    view.memo(), view.audio(), view.nickname(), view.emojis().stream().map(Emoji::from).toList(),
                    view.isMine()
            );
        }
    }

    @Schema(name = "EmotionV4Emoji")
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
