package com.pheeeew.block.fixture;

import com.pheeeew.block.domain.DeviceBlock;
import com.pheeeew.block.domain.SighBlock;
import java.time.Instant;
import org.springframework.test.util.ReflectionTestUtils;

public final class BlockFixture {

    private static final Long 기본_차단자_기기_식별자 = 1L;
    private static final Long 기본_차단_대상_기기_식별자 = 2L;
    private static final Long 기본_한숨_식별자 = 42L;

    private BlockFixture() {
    }

    public static SighBlock.SighBlockBuilder 기본_한숨_차단_빌더() {
        return SighBlock.builder()
                .blockerDeviceId(기본_차단자_기기_식별자)
                .sighId(기본_한숨_식별자);
    }

    public static DeviceBlock.DeviceBlockBuilder 기본_사용자_차단_빌더() {
        return DeviceBlock.builder()
                .blockerDeviceId(기본_차단자_기기_식별자)
                .blockedDeviceId(기본_차단_대상_기기_식별자)
                .originSighId(기본_한숨_식별자);
    }

    public static SighBlock 저장된_한숨_차단(Long id, Instant createdAt) {
        SighBlock block = 기본_한숨_차단_빌더().build();
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
