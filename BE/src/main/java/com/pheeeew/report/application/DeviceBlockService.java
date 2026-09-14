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
public class DeviceBlockService {

    private static final int PAGE_SIZE = 50;

    private final DeviceBlockRepository deviceBlockRepository;
    private final SighRepository sighRepository;
    private final DeviceRepository deviceRepository;

    public BlockSaveResult save(Long sighId, UUID devicePublicId) {
        Sigh sigh = findSigh(sighId);
        Long blockedDeviceId = findAuthorDeviceId(sigh);
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);
        validateNotSelf(blockerDeviceId, blockedDeviceId);

        Optional<DeviceBlock> existingBlock =
                deviceBlockRepository.findByBlockerDeviceIdAndBlockedDeviceId(blockerDeviceId, blockedDeviceId);
        if (existingBlock.isPresent()) {
            return BlockSaveResult.of(toResult(existingBlock.get(), sigh), false);
        }

        return saveNewBlock(blockerDeviceId, blockedDeviceId, sigh);
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

    private Sigh findSigh(Long sighId) {
        return sighRepository.findById(sighId)
                .orElseThrow(() -> new SighException(SIGH_NOT_FOUND));
    }

    private Long findAuthorDeviceId(Sigh sigh) {
        Long authorDeviceId = sigh.getDeviceId();
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

    private BlockResult toResult(DeviceBlock block, Sigh requestedSigh) {
        if (block.getOriginSighId().equals(requestedSigh.getId())) {
            return BlockResult.of(block, requestedSigh);
        }

        return BlockResult.of(block, findSigh(block.getOriginSighId()));
    }

    private BlockSaveResult saveNewBlock(Long blockerDeviceId, Long blockedDeviceId, Sigh sigh) {
        DeviceBlock block = DeviceBlock.builder()
                .blockerDeviceId(blockerDeviceId)
                .blockedDeviceId(blockedDeviceId)
                .originSighId(sigh.getId())
                .build();

        try {
            return BlockSaveResult.of(BlockResult.of(deviceBlockRepository.saveAndFlush(block), sigh), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingBlock(blockerDeviceId, blockedDeviceId, sigh, cause);
        }
    }

    private BlockSaveResult findExistingBlock(
            Long blockerDeviceId,
            Long blockedDeviceId,
            Sigh sigh,
            DataIntegrityViolationException cause
    ) {
        return deviceBlockRepository.findByBlockerDeviceIdAndBlockedDeviceId(blockerDeviceId, blockedDeviceId)
                .map(block -> BlockSaveResult.of(toResult(block, sigh), false))
                .orElseThrow(() -> new BlockException(BLOCK_SAVE_FAILED, cause));
    }

    private String createNextCursor(List<BlockResult> items, boolean hasNext) {
        if (!hasNext) {
            return null;
        }

        return BlockListCursorCodec.encode(items.getLast().blockId());
    }
}
