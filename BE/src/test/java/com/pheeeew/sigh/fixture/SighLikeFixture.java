package com.pheeeew.sigh.fixture;

import com.pheeeew.sigh.domain.SighLike;

public final class SighLikeFixture {

    private SighLikeFixture() {
    }

    public static SighLike.SighLikeBuilder 기본_좋아요_빌더() {
        return SighLike.builder()
                .sighId(42L)
                .deviceId(1L);
    }
}
