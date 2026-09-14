package com.pheeeew.report.fixture;

import com.pheeeew.report.domain.SighReport;
import java.time.Instant;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class SighReportFixture {

    private static final Long DEFAULT_SIGH_ID = 42L;

    private SighReportFixture() {
    }

    public static SighReport.SighReportBuilder 기본_신고_빌더() {
        return SighReport.builder()
                .sighId(DEFAULT_SIGH_ID)
                .reporterDeviceId(신고자_기기_식별자())
                .reason(기본_신고_사유());
    }

    public static SighReport 저장된_기본_신고(Long id, Instant createdAt) {
        SighReport report = 기본_신고_빌더().build();
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(report, "createdAt", createdAt);
        return report;
    }

    public static Long 신고자_기기_식별자() {
        return 1L;
    }

    public static UUID 신고자_기기_공개_식별자() {
        return UUID.fromString("5d1ad34e-1e20-4f20-a20e-3825a095fe6b");
    }

    public static UUID 없는_기기_공개_식별자() {
        return UUID.fromString("1f9b0c6a-7d4e-4a1b-9c2d-8e3f5a6b7c8d");
    }

    public static String 기본_신고_사유() {
        return "광고성 게시물입니다";
    }

    public static String 신고_사유(int length) {
        return "가".repeat(length);
    }
}
