package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_ALREADY_CLAIMED_TODAY;
import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_CLAIM_DATE_INVALID;
import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_REQUIREMENTS_NOT_MET;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.collection.application.dto.CollectionCardResult;
import com.pheeeew.collection.domain.CollectionCard;
import com.pheeeew.collection.domain.CollectionClaimRequirement;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.domain.repository.CollectionDrawStateRepository;
import com.pheeeew.collection.domain.repository.CollectionProgressRepository;
import com.pheeeew.collection.domain.repository.projection.CollectionActivityCounts;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.domain.EmotionState;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CollectionClaimService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final DeviceRepository deviceRepository;
    private final CollectionCardRepository collectionCardRepository;
    private final CollectionDrawStateRepository collectionDrawStateRepository;
    private final CollectionProgressRepository collectionProgressRepository;
    private final CollectionAssetService assetService;
    private final Clock clock;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public CollectionCardResult claim(UUID devicePublicId, LocalDate date, EmotionState state) {
        Long deviceId = deviceRepository.findByPublicIdForUpdate(devicePublicId).map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));

        var existing = collectionCardRepository.findByDeviceIdAndCardDate(deviceId, date);
        if (existing.isPresent() && existing.get().getState() == state) {
            return toResult(existing.get());
        }

        validateDate(date, clock.instant());
        if (existing.isPresent()) {
            throw new CollectionException(COLLECTION_ALREADY_CLAIMED_TODAY);
        }
        validateRequirements(date, deviceId);
        String cardVersion = assetService.activeVersion();

        collectionDrawStateRepository.saveIfAbsent(deviceId, state);
        var drawState = collectionDrawStateRepository.findByDeviceIdAndState(deviceId, state)
                .orElseThrow(() -> new IllegalStateException("추첨 상태를 초기화하지 못했습니다."));
        var used = new HashSet<>(collectionCardRepository.findUsedCombinations(deviceId, state, drawState.getCycle()));

        Instant claimedAt = clock.instant();
        validateDate(date, claimedAt);
        var draw = drawState.draw(used, RANDOM);
        var card = CollectionCard.builder()
                .deviceId(deviceId)
                .cardDate(date)
                .state(state)
                .cycle(draw.cycle())
                .combinationIndex(draw.combinationIndex())
                .cardVersion(cardVersion)
                .claimedAt(claimedAt)
                .build();

        return toResult(collectionCardRepository.saveAndFlush(card));
    }

    private CollectionCardResult toResult(CollectionCard card) {
        var images = assetService.findImages(card.getCardVersion(), card.getState(), card.getCombinationIndex());
        return CollectionCardResult.of(card, images);
    }

    private void validateDate(LocalDate date, Instant now) {
        if (!date.equals(LocalDate.ofInstant(now, clock.getZone()))) {
            throw new CollectionException(COLLECTION_CLAIM_DATE_INVALID);
        }
    }

    private void validateRequirements(LocalDate date, Long deviceId) {
        var counts = collectionProgressRepository.findByDateAndDeviceId(date, deviceId);
        long stampCount = counts.stream().mapToLong(CollectionActivityCounts::stampCount).sum();
        long pressCount = counts.stream().mapToLong(CollectionActivityCounts::pressCount).sum();
        if (!CollectionClaimRequirement.isMet(stampCount, pressCount)) {
            throw new CollectionException(COLLECTION_REQUIREMENTS_NOT_MET);
        }
    }
}
