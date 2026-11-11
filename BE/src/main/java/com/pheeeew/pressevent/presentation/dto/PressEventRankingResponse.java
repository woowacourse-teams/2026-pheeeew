package com.pheeeew.pressevent.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pheeeew.common.presentation.dto.CursorResponse;
import com.pheeeew.pressevent.application.PressEventRankingResult;
import com.pheeeew.pressevent.application.PressEventRankingResult.MyRank;
import com.pheeeew.pressevent.application.PressEventRankingResult.Standing;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PressEventRankingResponse(
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "동점 정렬 기준의 첫 참가자. 참가자가 없으면 null입니다.", nullable = true)
        Leader leader,
        CursorResponse<RankingItem> rankings,
        @JsonInclude(JsonInclude.Include.ALWAYS)
        @Schema(description = "현재 페이지와 별개로 제공하는 내 이벤트 기록. 미참여면 null입니다.", nullable = true)
        MyStanding me,
        Instant rankedAt
) {

    public static PressEventRankingResponse from(PressEventRankingResult result) {
        List<RankingItem> items = result.items().stream().map(RankingItem::from).toList();
        return new PressEventRankingResponse(
                result.leader() == null ? null : Leader.from(result.leader()),
                CursorResponse.of(items, result.nextCursor() != null, result.nextCursor()),
                result.me() == null ? null : MyStanding.from(result.me()),
                result.rankedAt());
    }

    public record Leader(UUID publicId, String nickname, int bestScore) {

        public static Leader from(Standing leader) {
            return new Leader(leader.publicId(), leader.nickname(), leader.bestScore());
        }
    }

    public record RankingItem(long rank, UUID publicId, String nickname, int bestScore, boolean mine) {

        public static RankingItem from(Standing standing) {
            return new RankingItem(standing.rank(), standing.publicId(), standing.nickname(), standing.bestScore(),
                    standing.mine());
        }
    }

    public record MyStanding(int bestScore, long rank) {

        public static MyStanding from(MyRank me) {
            return new MyStanding(me.bestScore(), me.rank());
        }
    }
}
