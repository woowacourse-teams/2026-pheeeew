package com.pheeeew.sigh.infra;

import com.pheeeew.sigh.application.EmotionNicknameGenerator;
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
