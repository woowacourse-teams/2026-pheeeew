package com.pheeeew.report.application.query;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.BlockListCursorCodec;
import com.pheeeew.report.application.dto.BlockListResult;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.report.domain.repository.projection.BlockProjection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class EmotionBlockQueryService {

    private static final int PAGE_SIZE = 50;

    private final EmotionBlockRepository emotionBlockRepository;
    private final DeviceRepository deviceRepository;

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

    private Long findBlockerDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private String createNextCursor(List<BlockResult> items, boolean hasNext) {
        if (!hasNext) {
            return null;
        }

        return BlockListCursorCodec.encode(items.getLast().blockId());
    }
}
