package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.query.EmotionPressRankingService;
import com.pheeeew.emotion.presentation.dto.EmotionPressRankingResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
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
}
