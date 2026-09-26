package com.pheeeew.emotion.presentation.dto;

import com.pheeeew.emotion.application.AudioPlaybackUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.application.dto.EmotionDetailView;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import com.pheeeew.groups.presentation.dto.GroupStampResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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
            PlaybackUrl audio,
            @Schema(description = "연결된 그룹 스탬프의 현재 모양. 선택하지 않았으면 null입니다.", nullable = true)
            GroupStampResponse groupStamp,
            @Schema(description = "선택한 그룹의 공개 ID. 수정 시 기존 스탬프를 유지하려면 이 값을 보냅니다. 그룹 스탬프가 없으면 null입니다.", nullable = true)
            UUID groupId,
            @Schema(description = "인증된 기기가 작성한 감정이면 true입니다. 작성 기기가 없으면 false이며, 수정·삭제 권한은 서버가 별도로 검사합니다.", example = "true")
            boolean isMine
    ) {

        public static Properties from(EmotionDetailView view) {
            return new Properties(
                    view.createdAt(), view.state(), view.rotationDegrees(), view.memo(), view.nickname(),
                    view.emojis().stream().map(Emoji::from).toList(),
                    view.hasAudio() ? EmotionContentType.AUDIO
                            : view.memo() == null ? EmotionContentType.NONE : EmotionContentType.MEMO,
                    view.audio(), view.groupStamp() == null ? null : GroupStampResponse.from(view.groupStamp()),
                    view.groupId(), view.isMine()
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
