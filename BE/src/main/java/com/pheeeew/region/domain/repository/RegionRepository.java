package com.pheeeew.region.domain.repository;

import com.pheeeew.emotion.domain.repository.query.EmotionSearchBounds;
import com.pheeeew.region.domain.Region;
import com.pheeeew.region.domain.RegionLevel;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class RegionRepository {

    private final JdbcClient jdbc;

    public boolean areBoundariesVerified() {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM region_datasets
                    WHERE dataset_key = 'SGIS_2025_2Q' AND boundaries_verified_at IS NOT NULL
                )
                """).query(Boolean.class).single();
    }

    public Optional<String> findEmdCode(double longitude, double latitude) {
        return jdbc.sql("""
                SELECT code FROM public.find_emd_region_code(
                    ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326)
                ) AS classification(code)
                WHERE code IS NOT NULL
                """).param("longitude", longitude).param("latitude", latitude).query(String.class).optional();
    }

    public boolean isAggregationReady() {
        return jdbc.sql("""
                SELECT EXISTS (
                    SELECT 1 FROM region_datasets
                    WHERE dataset_key = 'SGIS_2025_2Q'
                      AND boundaries_verified_at IS NOT NULL AND backfill_verified_at IS NOT NULL
                )
                """).query(Boolean.class).single();
    }

    public List<Region> findIntersectingRegions(EmotionSearchBounds bounds, RegionLevel level) {
        return jdbc.sql("""
                WITH bounds AS (
                    SELECT ST_MakeEnvelope(:west, :south,
                        CASE WHEN :west < :east THEN :east ELSE 180.0 END, :north, 4326) AS area
                    UNION ALL
                    SELECT ST_MakeEnvelope(-180.0, :south, :east, :north, 4326) AS area
                    WHERE :west > :east
                )
                SELECT region.code, region.level, region.name, region.parent_code,
                       ST_X(region.display_point) AS longitude, ST_Y(region.display_point) AS latitude
                FROM regions region
                WHERE region.level = :level
                  AND EXISTS (
                      SELECT 1 FROM bounds
                      WHERE region.boundary && bounds.area AND ST_Intersects(region.boundary, bounds.area)
                  )
                ORDER BY region.code
                """).param("west", bounds.minLongitude()).param("south", bounds.minLatitude())
                .param("east", bounds.maxLongitude()).param("north", bounds.maxLatitude()).param("level", level.name())
                .query((row, index) -> Region.of(row.getString("code"), RegionLevel.valueOf(row.getString("level")),
                        row.getString("name"), row.getString("parent_code"), row.getDouble("longitude"), row.getDouble("latitude")))
                .list();
    }
}
