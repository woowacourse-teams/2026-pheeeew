package com.pheeeew.report.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.report.exception.BlockErrorCode.BLOCK_AUTHOR_UNKNOWN;
import static com.pheeeew.report.exception.BlockErrorCode.BLOCK_SAVE_FAILED;
import static com.pheeeew.report.exception.BlockErrorCode.BLOCK_SELF_NOT_ALLOWED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.domain.DeviceBlock;
import com.pheeeew.report.domain.repository.DeviceBlockRepository;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.EmotionRepository;
import com.pheeeew.sigh.exception.SighException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class DeviceBlockService {

    private static final int PAGE_SIZE = 50;

    private final DeviceBlockRepository deviceBlockRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    public BlockSaveResult save(Long emotionId, UUID devicePublicId) {
        Sigh emotion = findEmotion(emotionId);
        Long blockedDeviceId = findAuthorDeviceId(emotion);
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);
        validateNotSelf(blockerDeviceId, blockedDeviceId);

        Optional<DeviceBlock> existingBlock =
                deviceBlockRepository.findByBlockerDeviceIdAndBlockedDeviceId(blockerDeviceId, blockedDeviceId);
        if (existingBlock.isPresent()) {
            return BlockSaveResult.of(toResult(existingBlock.get(), emotion), false);
        }

        return saveNewBlock(blockerDeviceId, blockedDeviceId, emotion);
    }

    @Transactional
    public void delete(Long blockId, UUID devicePublicId) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        deviceBlockRepository.deleteByIdAndBlockerDeviceId(blockId, blockerDeviceId);
    }

    public BlockListResult findAll(UUID devicePublicId, String encodedCursor) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);
        long lastId = BlockListCursorCodec.decodeOrInitial(encodedCursor);

        List<BlockProjection> projections = deviceBlockRepository.findAllByBlockerDeviceId(
                blockerDeviceId,
                lastId,
                PAGE_SIZE + 1
        );

        boolean hasNext = projections.size() > PAGE_SIZE;
        if (hasNext) {
            projections = projections.subList(0, PAGE_SIZE);
        }

        List<BlockResult> items = projections.stream()
                .map(BlockResult::from)
                .toList();
        String nextCursor = createNextCursor(items, hasNext);

        return BlockListResult.of(items, hasNext, nextCursor);
    }

    private Sigh findEmotion(Long emotionId) {
        return emotionRepository.findById(emotionId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
    }

    private Long findAuthorDeviceId(Sigh emotion) {
        Long authorDeviceId = emotion.getDeviceId();
        if (authorDeviceId == null) {
            throw new BlockException(BLOCK_AUTHOR_UNKNOWN);
        }

        return authorDeviceId;
    }

    private Long findBlockerDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private void validateNotSelf(Long blockerDeviceId, Long blockedDeviceId) {
        if (blockerDeviceId.equals(blockedDeviceId)) {
            throw new BlockException(BLOCK_SELF_NOT_ALLOWED);
        }
    }

    private BlockResult toResult(DeviceBlock block, Sigh requestedEmotion) {
        if (block.getOriginEmotionId().equals(requestedEmotion.getId())) {
            return BlockResult.of(block, requestedEmotion);
        }

        return BlockResult.of(block, findEmotion(block.getOriginEmotionId()));
    }

    private BlockSaveResult saveNewBlock(Long blockerDeviceId, Long blockedDeviceId, Sigh emotion) {
        DeviceBlock block = DeviceBlock.builder()
                .blockerDeviceId(blockerDeviceId)
                .blockedDeviceId(blockedDeviceId)
                .originEmotionId(emotion.getId())
                .build();

        try {
            return BlockSaveResult.of(BlockResult.of(deviceBlockRepository.saveAndFlush(block), emotion), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingBlock(blockerDeviceId, blockedDeviceId, emotion, cause);
        }
    }

    private BlockSaveResult findExistingBlock(
            Long blockerDeviceId,
            Long blockedDeviceId,
            Sigh emotion,
            DataIntegrityViolationException cause
    ) {
        return deviceBlockRepository.findByBlockerDeviceIdAndBlockedDeviceId(blockerDeviceId, blockedDeviceId)
                .map(block -> BlockSaveResult.of(toResult(block, emotion), false))
                .orElseThrow(() -> new BlockException(BLOCK_SAVE_FAILED, cause));
    }

    private String createNextCursor(List<BlockResult> items, boolean hasNext) {
        if (!hasNext) {
            return null;
        }

        return BlockListCursorCodec.encode(items.getLast().blockId());
    }
}
