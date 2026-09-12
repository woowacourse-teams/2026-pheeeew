package com.pheeeew.device.infra.attestation;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("prod")
@Component
public class PlayIntegrityProductionGuard {

    public PlayIntegrityProductionGuard(PlayIntegrityProperties playIntegrityProperties) {
        if (playIntegrityProperties.skipVerification()) {
            throw new IllegalStateException("운영 프로필에서는 무결성 증명 검증을 건너뛸 수 없습니다.");
        }
    }
}
