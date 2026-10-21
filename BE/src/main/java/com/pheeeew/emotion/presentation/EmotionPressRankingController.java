package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.query.EmotionPressRankingService;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.presentation.dto.EmotionDailyPressRankingResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressRankingResponse;
import com.pheeeew.emotion.presentation.dto.EmotionStatePressRankingResponse;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/emotions")
@RestController
public class EmotionPressRankingController implements EmotionPressRankingControllerApi {

    private final EmotionPressRankingService emotionPressRankingService;

    @Override
    @GetMapping("/press-rankings")
    public EmotionPressRankingResponse findPressRanking(
            @RequestParam(defaultValue = "0") int weeksAgo,
            @CurrentDevice UUID devicePublicId
    ) {
        return EmotionPressRankingResponse.from(
                emotionPressRankingService.findPressRanking(devicePublicId, weeksAgo)
        );
    }

    @Override
    @GetMapping("/press-rankings/daily")
    public EmotionDailyPressRankingResponse findDailyPressRanking(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @CurrentDevice UUID devicePublicId
    ) {
        return EmotionDailyPressRankingResponse.from(
                emotionPressRankingService.findDailyPressRanking(devicePublicId, date)
        );
    }

    @Override
    @GetMapping("/press-rankings/states/{state}")
    public EmotionStatePressRankingResponse findPressRankingByState(
            @PathVariable EmotionState state,
            @RequestParam(defaultValue = "0") int weeksAgo,
            @CurrentDevice UUID devicePublicId
    ) {
        return EmotionStatePressRankingResponse.from(
                emotionPressRankingService.findPressRankingByState(devicePublicId, state, weeksAgo)
        );
    }
}
