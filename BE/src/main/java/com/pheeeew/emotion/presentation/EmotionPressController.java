package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.application.query.EmotionPressQueryService;
import com.pheeeew.emotion.presentation.dto.EmotionPressDailyResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressRequest;
import com.pheeeew.emotion.presentation.dto.EmotionPressResponse;
import com.pheeeew.emotion.presentation.dto.EmotionPressTotalResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/emotions")
@RestController
public class EmotionPressController implements EmotionPressControllerApi {

    private final EmotionPressService emotionPressService;
    private final EmotionPressQueryService emotionPressQueryService;

    @Override
    @PostMapping("/presses")
    public EmotionPressResponse press(
            @RequestBody EmotionPressRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        return EmotionPressResponse.from(emotionPressService.press(devicePublicId, request.counts()));
    }

    @Override
    @GetMapping("/presses/me")
    public EmotionPressDailyResponse findMyDailyPresses(
            @RequestParam(defaultValue = "0") int daysAgo,
            @CurrentDevice UUID devicePublicId
    ) {
        return EmotionPressDailyResponse.from(
                emotionPressQueryService.findMyDailyPresses(devicePublicId, daysAgo)
        );
    }

    @Override
    @GetMapping("/presses/total")
    public EmotionPressTotalResponse findDailyTotal(
            @RequestParam(defaultValue = "0") int daysAgo
    ) {
        return EmotionPressTotalResponse.from(emotionPressQueryService.findDailyTotal(daysAgo));
    }
}
