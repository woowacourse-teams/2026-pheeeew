package com.pheeeew.pressrace.v2.presentation.dto;

import com.pheeeew.pressrace.v2.domain.PressRanker;
import io.swagger.v3.oas.annotations.media.Schema;

public record PressSnapshotMemberResponse(
        @Schema(
                description = "오늘 날짜에만 쓰는 공개 ID 입니다. 인증 기기 ID 와 다릅니다. 미참여한 me 이면 null 입니다.",
                example = "0f4c7a52-8f0e-4a8c-9a37-6d2f3b1d2e11"
        )
        String publicId,

        @Schema(description = "닉네임. 없으면 익명 입니다. 미참여한 me 이면 null 입니다.", example = "익명")
        String nickname,

        @Schema(description = "오늘 반영된 감정 프레스 합계. 미참여면 0 입니다.", example = "42")
        long score,

        @Schema(
                description = "점수만으로 정한 공동 순위(1, 2, 2, 4). 미참여한 me 이면 null 입니다.",
                example = "2"
        )
        Long rank,

        @Schema(description = "오늘 점수가 양수이면 true 입니다.")
        boolean participating
) {

    public static PressSnapshotMemberResponse from(PressRanker ranker) {
        return new PressSnapshotMemberResponse(ranker.publicId(), ranker.nickname(), ranker.score(), ranker.rank(),
                ranker.participating());
    }
}
