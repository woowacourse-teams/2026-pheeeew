package com.pheeeew.groups.application;

import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.groups.application.dto.GroupPressRankingItem;
import com.pheeeew.groups.application.dto.GroupPressRankingResult;
import com.pheeeew.groups.application.dto.GroupRankingItem;
import com.pheeeew.groups.application.dto.GroupRankingResult;
import com.pheeeew.groups.application.dto.GroupStatePressRankingResult;
import com.pheeeew.groups.application.dto.GroupWeeklyRankResult;
import com.pheeeew.groups.domain.repository.GroupDailyPressRepository;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupPressRankingRepository;
import com.pheeeew.groups.domain.repository.GroupRankingRepository;
import com.pheeeew.groups.domain.repository.projection.GroupScoreProjection;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class GroupRankingService {

    private final GroupRankingRepository groupRankingRepository;
    private final GroupDailyPressRepository groupDailyPressRepository;
    private final GroupPressRankingRepository groupPressRankingRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final Clock clock;

    public GroupRankingResult findGroupRanking(int weeksAgo) {
        RankingWeek week = RankingWeek.of(clock.instant(), weeksAgo);
        List<GroupScoreProjection> scores =
                groupRankingRepository.findGroupScores(week.startAt(), week.endAt());
        List<GroupRankingItem> items = rank(scores, GroupRankingItem::of);

        return GroupRankingResult.of(
                weeksAgo,
                week.startAt(),
                week.endAt(),
                groupRankingRepository.existsBefore(week.startAt()),
                items
        );
    }

    public GroupPressRankingResult findPressRanking(UUID devicePublicId, int weeksAgo) {
        RankingWeek week = RankingWeek.of(clock.instant(), weeksAgo);
        List<GroupScoreProjection> scores =
                groupDailyPressRepository.findPressScores(week.startDate(), week.endDate());

        return GroupPressRankingResult.of(
                weeksAgo,
                week.startAt(),
                week.endAt(),
                groupDailyPressRepository.existsPressBefore(week.startDate()),
                rankPresses(scores, devicePublicId)
        );
    }

    public GroupStatePressRankingResult findPressRankingByState(
            UUID devicePublicId,
            EmotionState state,
            int weeksAgo
    ) {
        RankingWeek week = RankingWeek.of(clock.instant(), weeksAgo);
        List<GroupScoreProjection> scores =
                groupDailyPressRepository.findPressScoresByState(state, week.startDate(), week.endDate());

        return GroupStatePressRankingResult.of(
                state,
                weeksAgo,
                week.startAt(),
                week.endAt(),
                groupDailyPressRepository.existsPressBefore(week.startDate()),
                rankPresses(scores, devicePublicId)
        );
    }

    public GroupWeeklyRankResult findWeeklyRanks(UUID groupPublicId) {
        RankingWeek week = RankingWeek.of(clock.instant(), 0);
        List<GroupScoreProjection> stampScores =
                groupRankingRepository.findGroupScores(week.startAt(), week.endAt());
        List<GroupScoreProjection> emotionPressScores =
                groupPressRankingRepository.findPressScores(week.startDate(), week.endDate());

        return GroupWeeklyRankResult.of(
                findRankedGroup(stampScores, groupPublicId),
                findRankedGroup(emotionPressScores, groupPublicId)
        );
    }

    private List<GroupPressRankingItem> rankPresses(List<GroupScoreProjection> scores, UUID devicePublicId) {
        Set<UUID> myGroupPublicIds = Set.copyOf(groupMemberRepository.findMyGroupPublicIds(devicePublicId));

        return rank(scores, (rank, score) ->
                GroupPressRankingItem.of(rank, score, myGroupPublicIds.contains(score.getGroupPublicId())));
    }

    private GroupRankingItem findRankedGroup(List<GroupScoreProjection> scores, UUID groupPublicId) {
        return rank(scores, GroupRankingItem::of).stream()
                .filter(item -> item.groupPublicId().equals(groupPublicId))
                .findFirst()
                .orElse(null);
    }

    private <T> List<T> rank(
            List<GroupScoreProjection> scores,
            BiFunction<Integer, GroupScoreProjection, T> toItem
    ) {
        List<T> items = new ArrayList<>(scores.size());
        int rank = 0;
        long previousScore = Long.MIN_VALUE;
        for (int index = 0; index < scores.size(); index++) {
            GroupScoreProjection score = scores.get(index);
            if (score.getScore() != previousScore) {
                rank = index + 1;
                previousScore = score.getScore();
            }
            items.add(toItem.apply(rank, score));
        }

        return items;
    }
}
