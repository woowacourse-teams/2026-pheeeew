package com.pheeeew.collection.infra;

import com.pheeeew.collection.application.CollectionAssetService;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.common.infra.s3.S3Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Utilities;

@EnableConfigurationProperties(S3Properties.class)
@Configuration
public class CollectionAssetConfiguration {

    @Bean
    public CollectionAssetService collectionAssetService(
            CollectionAssetRepository assetRepository,
            S3Properties s3Properties,
            @Value("${pheeeew.collection.card-version:}") String cardVersion
    ) {
        String assetBaseUrl = S3Utilities.builder()
                .region(Region.of(s3Properties.region()))
                .build()
                .getUrl(request -> request.bucket(s3Properties.bucket())
                        .key(s3Properties.keyPrefix() + "collection-cards/"))
                .toExternalForm();
        return new CollectionAssetService(assetRepository, assetBaseUrl, cardVersion);
    }
}
