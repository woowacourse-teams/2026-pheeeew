package com.pheeeew.report.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.report.exception.BlockErrorCode.BLOCK_SAVE_FAILED;
import static com.pheeeew.sigh.exception.SighErrorCode.SIGH_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.domain.SighBlock;
import com.pheeeew.report.domain.repository.SighBlockRepository;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.sigh.domain.Sigh;
import com.pheeeew.sigh.domain.repository.SighRepository;
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
public class SighBlockService {

    private static final int PAGE_SIZE = 50;

    private final SighBlockRepository emotionBlockRepository;
    private final SighRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    public BlockSaveResult save(Long emotionId, UUID devicePublicId) {
        Sigh emotion = findEmotion(emotionId);
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        Optional<SighBlock> existingBlock = emotionBlockRepository.findByBlockerDeviceIdAndSighId(blockerDeviceId, emotionId);
        if (existingBlock.isPresent()) {
            return BlockSaveResult.of(BlockResult.of(existingBlock.get(), emotion), false);
        }

        return saveNewBlock(blockerDeviceId, emotion);
    }

    @Transactional
    public void delete(Long emotionId, UUID devicePublicId) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        emotionBlockRepository.deleteByBlockerDeviceIdAndSighId(blockerDeviceId, emotionId);
    }

    public BlockListResult findAll(UUID devicePublicId, String encodedCursor) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);
        long lastId = BlockListCursorCodec.decodeOrInitial(encodedCursor);

        List<BlockProjection> projections = emotionBlockRepository.findAllByBlockerDeviceId(
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

    private Long findBlockerDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private BlockSaveResult saveNewBlock(Long blockerDeviceId, Sigh emotion) {
        SighBlock block = SighBlock.builder()
                .blockerDeviceId(blockerDeviceId)
                .sighId(emotion.getId())
                .build();

        try {
            return BlockSaveResult.of(BlockResult.of(emotionBlockRepository.saveAndFlush(block), emotion), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingBlock(blockerDeviceId, emotion, cause);
        }
    }

    private BlockSaveResult findExistingBlock(Long blockerDeviceId, Sigh emotion, DataIntegrityViolationException cause) {
        return emotionBlockRepository.findByBlockerDeviceIdAndSighId(blockerDeviceId, emotion.getId())
                .map(block -> BlockSaveResult.of(BlockResult.of(block, emotion), false))
                .orElseThrow(() -> new BlockException(BLOCK_SAVE_FAILED, cause));
    }

    private String createNextCursor(List<BlockResult> items, boolean hasNext) {
        if (!hasNext) {
            return null;
        }

        return BlockListCursorCodec.encode(items.getLast().blockId());
    }
}
