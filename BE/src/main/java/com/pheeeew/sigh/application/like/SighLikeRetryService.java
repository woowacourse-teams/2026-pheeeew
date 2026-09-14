package com.pheeeew.sigh.application.like;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class SighLikeRetryService {

    private static final int MAX_ATTEMPTS = 3;

    private final SighLikeService sighLikeService;

    @Transactional(propagation = Propagation.NEVER)
    public boolean update(Long sighId, UUID devicePublicId, boolean liked) {
        for (int attempt = 1; ; attempt++) {
            try {
                return sighLikeService.update(sighId, devicePublicId, liked);
            } catch (ObjectOptimisticLockingFailureException exception) {
                if (attempt == MAX_ATTEMPTS) {
                    throw exception;
                }
            }
        }
    }
}
