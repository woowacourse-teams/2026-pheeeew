package com.pheeeew.region.fixture;

import org.springframework.jdbc.core.simple.JdbcClient;

public final class RegionFixture {

    private RegionFixture() {
    }

    public static void 검증용_지역_계층을_저장한다(JdbcClient jdbc) {
        지역을_저장한다(jdbc, "11", "SIDO", null);
        지역을_저장한다(jdbc, "11010", "SIGUNGU", "11");
        지역을_저장한다(jdbc, "11010530", "EMD", "11010");
    }

    private static void 지역을_저장한다(JdbcClient jdbc, String code, String level, String parentCode) {
        // 실제 SGIS 경계가 아닌, 스키마 계약 검증용 합성 도형이다.
        jdbc.sql("""
                INSERT INTO regions (code, level, name, parent_code, boundary, display_point)
                VALUES (:code, :level, :name, :parentCode,
                        ST_GeomFromText('MULTIPOLYGON(((126 37, 128 37, 128 39, 126 39, 126 37)))', 4326),
                        ST_GeomFromText('POINT(127 38)', 4326))
                """)
                .param("code", code).param("level", level)
                .param("name", "검증용 " + level).param("parentCode", parentCode)
                .update();
    }
}
