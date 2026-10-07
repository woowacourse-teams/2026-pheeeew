package com.pheeeew.report.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.report.exception.BlockErrorCode.BLOCK_SAVE_FAILED;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_FOUND;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.report.application.dto.BlockResult;
import com.pheeeew.report.application.dto.BlockSaveResult;
import com.pheeeew.report.domain.EmotionBlock;
import com.pheeeew.report.domain.repository.EmotionBlockRepository;
import com.pheeeew.report.exception.BlockException;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.exception.EmotionException;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADR-0004에 따라 저장 경로 전체를 트랜잭션으로 감싸지 않는다.
 * 삽입 실패 후 기존 차단을 독립된 트랜잭션과 영속성 컨텍스트에서 재조회한다.
 */
@RequiredArgsConstructor
@Service
public class EmotionBlockCommandService {

    private final EmotionBlockRepository emotionBlockRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;

    public BlockSaveResult save(Long emotionId, UUID devicePublicId) {
        Emotion emotion = findEmotion(emotionId);
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        Optional<EmotionBlock> existingBlock = emotionBlockRepository.findByBlockerDeviceIdAndEmotionId(blockerDeviceId, emotionId);
        if (existingBlock.isPresent()) {
            return BlockSaveResult.of(toResult(existingBlock.get(), emotion), false);
        }

        return saveNewBlock(blockerDeviceId, emotion);
    }

    @Transactional
    public void delete(Long emotionId, UUID devicePublicId) {
        Long blockerDeviceId = findBlockerDeviceId(devicePublicId);

        emotionBlockRepository.deleteByBlockerDeviceIdAndEmotionId(blockerDeviceId, emotionId);
    }

    private Emotion findEmotion(Long emotionId) {
        return emotionRepository.findById(emotionId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_FOUND));
    }

    private Long findBlockerDeviceId(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private BlockResult toResult(EmotionBlock block, Emotion emotion) {
        String authorNickname = null;
        if (!emotion.isAnonymous()) {
            authorNickname = deviceRepository.findById(emotion.getDeviceId())
                    .map(Device::getNickname).orElse(null);
        }

        return BlockResult.of(block, emotion, authorNickname);
    }

    private BlockSaveResult saveNewBlock(Long blockerDeviceId, Emotion emotion) {
        EmotionBlock block = EmotionBlock.builder()
                .blockerDeviceId(blockerDeviceId)
                .emotionId(emotion.getId())
                .build();

        EmotionBlock saved;
        try {
            saved = emotionBlockRepository.saveAndFlush(block);
        } catch (DataIntegrityViolationException cause) {
            return findExistingBlock(blockerDeviceId, emotion, cause);
        }

        return BlockSaveResult.of(toResult(saved, emotion), true);
    }

    private BlockSaveResult findExistingBlock(Long blockerDeviceId, Emotion emotion, DataIntegrityViolationException cause) {
        return emotionBlockRepository.findByBlockerDeviceIdAndEmotionId(blockerDeviceId, emotion.getId())
                .map(block -> BlockSaveResult.of(toResult(block, emotion), false))
                .orElseThrow(() -> new BlockException(BLOCK_SAVE_FAILED, cause));
    }
}
