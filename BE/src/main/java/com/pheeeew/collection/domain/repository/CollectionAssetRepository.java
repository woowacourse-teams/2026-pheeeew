package com.pheeeew.collection.domain.repository;

import com.pheeeew.collection.domain.repository.projection.CollectionAssetCounts;
import com.pheeeew.collection.domain.repository.projection.CollectionAssetPaths;
import com.pheeeew.emotion.domain.EmotionState;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@RequiredArgsConstructor
@Repository
public class CollectionAssetRepository {

    private final JdbcClient jdbc;

    public CollectionAssetCounts findCountsByVersion(String version) {
        return jdbc.sql("""
                SELECT (SELECT COUNT(*) FROM collection_character_assets WHERE card_version = :version) AS character_count,
                       (SELECT COUNT(*) FROM collection_stamp_assets WHERE card_version = :version) AS stamp_count
                """)
                .param("version", version)
                .query((row, index) -> CollectionAssetCounts.of(
                        row.getLong("character_count"), row.getLong("stamp_count")))
                .single();
    }

    public Optional<CollectionAssetPaths> findImagePaths(
            String version, EmotionState state, int characterNumber, int stampNumber
    ) {
        return jdbc.sql("""
                SELECT characters.image_path, stamps.round_image_path, stamps.straight_image_path
                FROM collection_character_assets characters
                JOIN collection_stamp_assets stamps
                  ON stamps.card_version = characters.card_version AND stamps.state = characters.state
                WHERE characters.card_version = :version AND characters.state = :state
                  AND characters.asset_number = :character AND stamps.asset_number = :stamp
                """)
                .param("version", version)
                .param("state", state.name())
                .param("character", characterNumber)
                .param("stamp", stampNumber)
                .query((row, index) -> CollectionAssetPaths.of(
                        row.getString("image_path"), row.getString("round_image_path"),
                        row.getString("straight_image_path")))
                .optional();
    }
}
