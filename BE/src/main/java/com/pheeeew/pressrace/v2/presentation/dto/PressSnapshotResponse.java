package com.pheeeew.pressrace.v2.presentation.dto;

import com.pheeeew.pressrace.v2.domain.PressRanker;
import com.pheeeew.pressrace.v2.domain.PressScreen;
import com.pheeeew.pressrace.v2.domain.PressView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PressSnapshotResponse(
        @Schema(description = "KST 기준 경쟁 날짜", example = "2026-11-04")
        LocalDate date,

        @Schema(description = "서버가 DB 를 관찰한 시각(UTC ISO-8601)", example = "2026-11-04T11:00:00.123Z")
        Instant generatedAt,

        @Schema(
                description = "최근 5초 안에 프레스가 반영된 서로 다른 기기 수입니다. 사람 수가 아니라 기기 수이고, "
                        + "한 기기가 여러 감정을 눌러도 1로 셉니다. 새 프레스가 없으면 시간이 지나며 줄어듭니다.",
                example = "7"
        )
        int activeCount,

        @Schema(description = "요청한 화면")
        PressView view,

        @Schema(description = "내 참여 상태와 실제 점수, 순위. 상위 100명 밖이어도 실제 순위를 담습니다.")
        PressSnapshotMemberResponse me,

        @Schema(description = "나를 포함한 주변 최대 3명. 미참여면 상위 최대 2명입니다.")
        List<PressSnapshotMemberResponse> nearby,

        @Schema(description = "표시 순서의 첫 100명. view 가 PRESS 이면 빈 목록입니다.")
        List<PressSnapshotMemberResponse> top100
) {

    public static PressSnapshotResponse from(PressScreen screen) {
        return new PressSnapshotResponse(screen.date(), screen.generatedAt(), screen.activeCount(), screen.view(),
                PressSnapshotMemberResponse.from(screen.me()), toResponses(screen.nearby()),
                toResponses(screen.top100()));
    }

    private static List<PressSnapshotMemberResponse> toResponses(List<PressRanker> rankers) {
        return rankers.stream()
                .map(PressSnapshotMemberResponse::from)
                .toList();
    }
}
