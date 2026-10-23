package com.pheeeew.collection.infra;

import com.pheeeew.collection.application.CollectionAssetService;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CollectionAssetConfiguration {

    @Bean
    public CollectionAssetService collectionAssetService(
            CollectionAssetRepository assetRepository,
            @Value("${pheeeew.collection.asset-base-url:}") String assetBaseUrl,
            @Value("${pheeeew.collection.card-version:}") String cardVersion
    ) {
        return new CollectionAssetService(assetRepository, assetBaseUrl, cardVersion);
    }
}
