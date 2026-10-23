package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_ASSETS_NOT_READY;

import com.pheeeew.collection.application.dto.CollectionCardImages;
import com.pheeeew.collection.domain.repository.CollectionAssetRepository;
import com.pheeeew.collection.domain.repository.projection.CollectionAssetPaths;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.emotion.domain.EmotionState;
import java.net.URI;

public class CollectionAssetService {

    private final CollectionAssetRepository assetRepository;
    private final URI assetBaseUri;
    private final String cardVersion;

    public CollectionAssetService(CollectionAssetRepository assetRepository, String assetBaseUrl, String cardVersion) {
        this.assetRepository = assetRepository;
        this.assetBaseUri = parseBaseUri(assetBaseUrl);
        this.cardVersion = cardVersion;
    }

    public String activeVersion() {
        if (assetBaseUri == null || !isValidVersion(cardVersion)) {
            throw new CollectionException(COLLECTION_ASSETS_NOT_READY);
        }
        var counts = assetRepository.findCountsByVersion(cardVersion);
        if (counts.characterCount() != 80 || counts.stampCount() != 80) {
            throw new CollectionException(COLLECTION_ASSETS_NOT_READY);
        }
        return cardVersion;
    }

    public CollectionCardImages findImages(String version, EmotionState state, int combinationIndex) {
        if (assetBaseUri == null || !isValidVersion(version) || state == null
                || combinationIndex < 0 || combinationIndex >= 256) {
            throw new CollectionException(COLLECTION_ASSETS_NOT_READY);
        }
        var paths = assetRepository.findImagePaths(version, state, combinationIndex / 16 + 1,
                        combinationIndex % 16 + 1)
                .orElseThrow(() -> new CollectionException(COLLECTION_ASSETS_NOT_READY));
        return resolveImages(version, paths);
    }

    public CollectionCardImages resolveImages(String version, CollectionAssetPaths paths) {
        if (assetBaseUri == null || !isValidVersion(version) || paths == null) {
            throw new CollectionException(COLLECTION_ASSETS_NOT_READY);
        }
        return CollectionCardImages.of(
                resolvePath(version, paths.characterPath()),
                resolvePath(version, paths.roundStampPath()),
                resolvePath(version, paths.straightStampPath()));
    }

    private URI parseBaseUri(String assetBaseUrl) {
        if (assetBaseUrl == null || assetBaseUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(assetBaseUrl);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null || !uri.getPath().endsWith("/")
                    || !uri.normalize().equals(uri)) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private boolean isValidVersion(String version) {
        return version != null && version.matches("[a-zA-Z0-9_-]{1,64}");
    }

    private String resolvePath(String version, String path) {
        if (path == null || !path.matches("[a-zA-Z0-9_/-]+\\.png") || !path.startsWith(version + "/")
                || path.contains("//")) {
            throw new CollectionException(COLLECTION_ASSETS_NOT_READY);
        }
        return assetBaseUri.resolve(path).toString();
    }
}
