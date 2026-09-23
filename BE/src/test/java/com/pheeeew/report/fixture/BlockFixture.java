package com.pheeeew.report.fixture;

import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.EmotionBlock;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class BlockFixture {

    private static final Long 기본_차단자_기기_식별자 = 1L;
    private static final Long 기본_차단_대상_기기_식별자 = 2L;
    private static final Long 기본_한숨_식별자 = 42L;

    private BlockFixture() {
    }

    public static EmotionBlock.EmotionBlockBuilder 기본_한숨_차단_빌더() {
        return EmotionBlock.builder()
                .blockerDeviceId(기본_차단자_기기_식별자)
                .emotionId(기본_한숨_식별자);
    }

    public static DeviceBlock.DeviceBlockBuilder 기본_사용자_차단_빌더() {
        return DeviceBlock.builder()
                .blockerDeviceId(기본_차단자_기기_식별자)
                .blockedDeviceId(기본_차단_대상_기기_식별자)
                .originSighId(기본_한숨_식별자);
    }

    public static EmotionBlock 저장된_한숨_차단(Long id, Instant createdAt) {
        EmotionBlock block = 기본_한숨_차단_빌더().build();
        ReflectionTestUtils.setField(block, "id", id);
        ReflectionTestUtils.setField(block, "createdAt", createdAt);
        return block;
    }

    public static DeviceBlock 저장된_사용자_차단(Long id, Instant createdAt) {
        DeviceBlock block = 기본_사용자_차단_빌더().build();
        ReflectionTestUtils.setField(block, "id", id);
        ReflectionTestUtils.setField(block, "createdAt", createdAt);
        return block;
    }

    public static Long 기본_한숨_식별자() {
        return 기본_한숨_식별자;
    }
}
