package com.pheeeew.region.domain.repository;

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
                SELECT code FROM regions
                WHERE level = 'EMD'
                  AND ST_Covers(boundary, ST_SetSRID(ST_MakePoint(:longitude, :latitude), 4326))
                ORDER BY code
                LIMIT 1
                """).param("longitude", longitude).param("latitude", latitude).query(String.class).optional();
    }
}
