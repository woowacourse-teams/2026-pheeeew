package com.pheeeew.emotion.application.query;

import com.pheeeew.common.application.Ranking;
import com.pheeeew.common.application.RankingDay;
import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionDailyPressRankingResult;
import com.pheeeew.emotion.application.dto.EmotionPressRankingItem;
import com.pheeeew.emotion.application.dto.EmotionPressRankingResult;
import com.pheeeew.emotion.application.dto.EmotionStatePressRankingResult;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.DeviceDailyPressRepository;
import com.pheeeew.emotion.domain.repository.projection.DeviceDailyPressSum;
import com.pheeeew.emotion.domain.repository.projection.DeviceWeeklyPressSum;
import com.pheeeew.groups.application.RankingWeek;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionPressRankingService {

    private static final int TOP_SIZE = 100;

    private final DeviceDailyPressRepository deviceDailyPressRepository;
    private final Clock clock;

    public EmotionPressRankingResult findPressRanking(UUID devicePublicId, int weeksAgo) {
        RankingWeek week = RankingWeek.of(clock.instant(), weeksAgo);
        List<DeviceWeeklyPressSum> sums = deviceDailyPressRepository.findDevicePressSums(
                week.startDate(), week.endDate(), devicePublicId);
        List<EmotionPressRankingItem> ranked =
                Ranking.rank(sums, DeviceWeeklyPressSum::pressCount, EmotionPressRankingItem::of);

        return EmotionPressRankingResult.of(weeksAgo, week.startAt(), week.endAt(), top(ranked), findMine(ranked));
    }

    public EmotionDailyPressRankingResult findDailyPressRanking(UUID devicePublicId, LocalDate date) {
        RankingDay day = RankingDay.of(date);
        List<DeviceDailyPressSum> sums = deviceDailyPressRepository.findDailyPressRanking(devicePublicId, date);
        List<EmotionDailyPressRankingItem> ranked =
                Ranking.rank(sums, DeviceDailyPressSum::pressCount, EmotionDailyPressRankingItem::of);
        List<EmotionDailyPressRankingItem> top = ranked.subList(0, Math.min(TOP_SIZE, ranked.size()));
        EmotionDailyPressRankingItem me = ranked.stream()
                .filter(EmotionDailyPressRankingItem::mine)
                .findFirst()
                .orElse(null);

        return EmotionDailyPressRankingResult.of(date, day.startAt(), day.endAt(), top, me);
    }

    public EmotionStatePressRankingResult findPressRankingByState(
            UUID devicePublicId,
            EmotionState state,
            int weeksAgo
    ) {
        RankingWeek week = RankingWeek.of(clock.instant(), weeksAgo);
        List<DeviceWeeklyPressSum> sums = deviceDailyPressRepository.findDevicePressSumsByState(
                week.startDate(), week.endDate(), state, devicePublicId);
        List<EmotionPressRankingItem> ranked =
                Ranking.rank(sums, DeviceWeeklyPressSum::pressCount, EmotionPressRankingItem::of);

        return EmotionStatePressRankingResult.of(
                state, weeksAgo, week.startAt(), week.endAt(), top(ranked), findMine(ranked));
    }

    private List<EmotionPressRankingItem> top(List<EmotionPressRankingItem> ranked) {
        return ranked.subList(0, Math.min(TOP_SIZE, ranked.size()));
    }

    private EmotionPressRankingItem findMine(List<EmotionPressRankingItem> ranked) {
        return ranked.stream()
                .filter(EmotionPressRankingItem::mine)
                .findFirst()
                .orElse(null);
    }
}
