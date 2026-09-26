package com.pheeeew.emotion.infra;

import com.pheeeew.emotion.application.EmotionNicknameGenerator;
import koreannickname.KoreanNicknameGenerator;
import org.springframework.stereotype.Component;

@Component
public class KoreanEmotionNicknameGenerator implements EmotionNicknameGenerator {

    private final KoreanNicknameGenerator generator = KoreanNicknameGenerator.create();

    @Override
    public String generate() {
        return generator.generate();
    }
}
