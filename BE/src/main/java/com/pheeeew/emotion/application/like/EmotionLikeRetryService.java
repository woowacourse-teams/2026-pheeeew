package com.pheeeew.emotion.application.like;

import com.pheeeew.emotion.application.like.dto.EmotionLikeResult;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionLikeRetryService {

    private static final int MAX_ATTEMPTS = 3;

    private final EmotionLikeService emotionLikeService;

    @Transactional(propagation = Propagation.NEVER)
    public EmotionLikeResult update(Long emotionId, UUID devicePublicId, boolean liked) {
        for (int attempt = 1; ; attempt++) {
            try {
                return emotionLikeService.update(emotionId, devicePublicId, liked);
            } catch (ObjectOptimisticLockingFailureException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
