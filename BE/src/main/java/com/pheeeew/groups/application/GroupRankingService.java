package com.pheeeew.groups.application;

import com.pheeeew.groups.application.dto.GroupRankingItem;
import com.pheeeew.groups.application.dto.GroupRankingResult;
import com.pheeeew.groups.domain.repository.GroupRankingRepository;
import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class GroupRankingService {

    private final GroupRankingRepository groupRankingRepository;
    private final Clock clock;

    public GroupRankingResult findGroupRanking(int weeksAgo) {
        RankingWeek week = RankingWeek.of(clock.instant(), weeksAgo);
        List<GroupScoreProjection> scores =
                groupRankingRepository.findGroupScores(week.startAt(), week.endAt());
        List<GroupRankingItem> items = rank(scores);

        return GroupRankingResult.of(
                weeksAgo,
                week.startAt(),
                week.endAt(),
                groupRankingRepository.existsBefore(week.startAt()),
                items
        );
    }

    private List<GroupRankingItem> rank(List<GroupScoreProjection> scores) {
        List<GroupRankingItem> items = new ArrayList<>(scores.size());
        int rank = 0;
        long previousScore = Long.MIN_VALUE;
        for (int index = 0; index < scores.size(); index++) {
            GroupScoreProjection score = scores.get(index);
            if (score.getScore() != previousScore) {
                rank = index + 1;
                previousScore = score.getScore();
            }
            items.add(GroupRankingItem.of(rank, score));
        }

        return items;
    }
}
