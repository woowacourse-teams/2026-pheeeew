package com.pheeeew.emotion.presentation;

import com.pheeeew.auth.presentation.annotation.CurrentDevice;
import com.pheeeew.emotion.application.command.EmotionPressService;
import com.pheeeew.emotion.presentation.dto.EmotionPressRequest;
import com.pheeeew.emotion.presentation.dto.EmotionPressResponse;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RequestMapping("/api/v2/emotions")
@RestController
public class EmotionPressController implements EmotionPressControllerApi {

    private final EmotionPressService emotionPressService;

    @Override
    @PostMapping("/presses")
    public EmotionPressResponse press(
            @RequestBody EmotionPressRequest request,
            @CurrentDevice UUID devicePublicId
    ) {
        return EmotionPressResponse.from(emotionPressService.press(
                devicePublicId, request.longitude(), request.latitude(), request.counts()));
    }
}
