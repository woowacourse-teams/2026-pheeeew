package com.pheeeew.climb.presentation;

import com.pheeeew.climb.domain.ClimbGuest;
import com.pheeeew.climb.domain.ClimbHallEntry;
import com.pheeeew.climb.domain.ClimbRankView;
import com.pheeeew.climb.domain.ClimbRunRecord;
import java.util.List;

/** HTTP 와 웹소켓이 함께 쓰는 응답 모양. */
final class ClimbResponses {

    private ClimbResponses() {
    }

    record GuestResponse(String id, String nickname, String tag) {

        static GuestResponse from(ClimbGuest guest) {
            return new GuestResponse(guest.id().toString(), guest.nickname(), guest.tag());
        }
    }

    record MeResponse(String id, String nickname, String tag, RunResponse run) {

        static MeResponse of(ClimbGuest guest, ClimbRunRecord run) {
            return new MeResponse(guest.id().toString(), guest.nickname(), guest.tag(),
                    run == null ? null : RunResponse.from(run));
        }
    }

    record RunResponse(String map, String character, int height, int best, int x, int falls, int time) {

        static RunResponse from(ClimbRunRecord run) {
            return new RunResponse(run.mapVersion(), run.character(), run.height(), run.best(), run.x(), run.falls(),
                    run.playSeconds());
        }
    }

    record RankItem(int rank, String tag, String nickname, String character, int best, boolean finished,
                    Integer time) {

        static RankItem from(ClimbRankView.Ranked ranked) {
            return new RankItem(ranked.rank(), ranked.entry().tag(), ranked.entry().nickname(),
                    ranked.entry().character(), ranked.entry().best(), ranked.entry().finished(),
                    ranked.entry().finishSeconds());
        }

        static List<RankItem> from(List<ClimbRankView.Ranked> ranked) {
            return ranked.stream().map(RankItem::from).toList();
        }
    }

    record MyRank(int rank, int best) {

        static MyRank from(ClimbRankView.Ranked ranked) {
            return ranked == null ? null : new MyRank(ranked.rank(), ranked.entry().best());
        }
    }

    record RankingResponse(List<RankItem> top, MyRank me, int online) {

        static RankingResponse of(ClimbRankView view, int online) {
            return new RankingResponse(RankItem.from(view.top()), MyRank.from(view.me()), online);
        }
    }

    record HallItem(int order, String tag, String nickname, String character, int time, int falls,
                    String finishedAt, int best, int bestFalls, String bestAt) {
    }

    record HallResponse(List<HallItem> hall) {

        static HallResponse of(List<ClimbHallEntry> entries) {
            return new HallResponse(java.util.stream.IntStream.range(0, entries.size())
                    .mapToObj(index -> {
                        ClimbHallEntry entry = entries.get(index);
                        return new HallItem(index + 1, entry.tag(), entry.nickname(), entry.character(),
                                entry.finishSeconds(), entry.falls(), entry.finishedAt().toString(),
                                entry.bestSeconds(), entry.bestFalls(), entry.bestAt().toString());
                    })
                    .toList());
        }
    }

    record ErrorResponse(String code, String message) {

        static ErrorResponse of(String code, String message) {
            return new ErrorResponse(code, message);
        }
    }
}
