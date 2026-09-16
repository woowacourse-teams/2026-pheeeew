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

    private final SighBlockRepository sighBlockRepository;
    private final SighRepository sighRepository;
    private final DeviceRepository deviceRepository;

    public BlockSaveResult save(Long sighId, UUID devicePublicId) {
        Sigh sigh = findSigh(sighId);
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        Optional<SighBlock> existingBlock = sighBlockRepository.findByBlockerDeviceIdAndSighId(blockerDeviceId, sighId);
        if (existingBlock.isPresent()) {
            return BlockSaveResult.of(BlockResult.of(existingBlock.get(), sigh), false);
        }

        return saveNewBlock(blockerDeviceId, sigh);
    }

    @Transactional
    public void delete(Long sighId, UUID devicePublicId) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        sighBlockRepository.deleteByBlockerDeviceIdAndSighId(blockerDeviceId, sighId);
    }

    public BlockListResult findAll(UUID devicePublicId, String encodedCursor) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);
        long lastId = BlockListCursorCodec.decodeOrInitial(encodedCursor);

        List<BlockProjection> projections = sighBlockRepository.findAllByBlockerDeviceId(
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

    private Long findBlockerDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private BlockSaveResult saveNewBlock(Long blockerDeviceId, Sigh sigh) {
        SighBlock block = SighBlock.builder()
                .blockerDeviceId(blockerDeviceId)
                .sighId(sigh.getId())
                .build();

        try {
            return BlockSaveResult.of(BlockResult.of(sighBlockRepository.saveAndFlush(block), sigh), true);
        } catch (DataIntegrityViolationException cause) {
            return findExistingBlock(blockerDeviceId, sigh, cause);
        }
    }

    private BlockSaveResult findExistingBlock(Long blockerDeviceId, Sigh sigh, DataIntegrityViolationException cause) {
        return sighBlockRepository.findByBlockerDeviceIdAndSighId(blockerDeviceId, sigh.getId())
                .map(block -> BlockSaveResult.of(BlockResult.of(block, sigh), false))
                .orElseThrow(() -> new BlockException(BLOCK_SAVE_FAILED, cause));
    }

    private String createNextCursor(List<BlockResult> items, boolean hasNext) {
        if (!hasNext) {
            return null;
        }

        return BlockListCursorCodec.encode(items.getLast().blockId());
    }
}
