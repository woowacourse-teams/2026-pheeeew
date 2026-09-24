package com.pheeeew.emotion.application.command;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_NOT_VISIBLE;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_REQUEST_ID_CONFLICT;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_SAVE_FAILED;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.EmotionNicknameGenerator;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.domain.EmotionState;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.projection.EmotionDetailProjection;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@RequiredArgsConstructor
@Service
public class EmotionCommandService {

    private static final GeometryFactory WGS84 = new GeometryFactory(new PrecisionModel(), 4326);

    private final EmotionEmojiRepository emotionEmojiRepository;
    private final EmotionRepository emotionRepository;
    private final DeviceRepository deviceRepository;
    private final EmotionContentResolver contentResolver;
    private final EmotionNicknameGenerator nicknameGenerator;
    private final PlatformTransactionManager transactionManager;

    /**
     * ADR-0004에 따라 선조회와 실패 후 재조회를 저장 트랜잭션 밖에서 수행한다.
     * 녹음 연결과 감정 삽입만 독립 트랜잭션으로 묶어 함께 커밋하거나 롤백한다.
     */
    public Emotion save(
            UUID requestId, EmotionState state, double longitude, double latitude, double rotationDegrees,
            String memo, String audioUploadId, UUID devicePublicId
    ) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        Optional<Emotion> existing = findRegisteredEmotion(requestId, deviceId);
        if (existing.isPresent()) {
            return existing.get();
        }

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return transaction.execute(status -> saveNewEmotion(requestId, state, longitude, latitude,
                    rotationDegrees, memo, audioUploadId, deviceId));
        } catch (DataIntegrityViolationException cause) {
            return findRegisteredEmotion(requestId, deviceId)
                    .orElseThrow(() -> new EmotionException(EMOTION_SAVE_FAILED, cause));
        }
    }

    private Emotion saveNewEmotion(
            UUID requestId, EmotionState state, double longitude, double latitude, double rotationDegrees,
            String memo, String audioUploadId, Long deviceId
    ) {
        if (requestId == null || state == null) {
            throw new IllegalArgumentException("요청 식별자와 감정 상태는 필수입니다.");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || !Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("선택 위치는 유효한 WGS84 좌표여야 합니다.");
        }
        EmotionContent content = contentResolver.resolve(memo, audioUploadId, deviceId, requestId);

        return emotionRepository.saveAndFlush(Emotion.builder()
                .requestId(requestId)
                .state(state)
                .location(WGS84.createPoint(new Coordinate(longitude, latitude)))
                .rotationDegrees(rotationDegrees)
                .memo(content.getMemo())
                .audio(content.getAudio())
                .nickname(nicknameGenerator.generate())
                .deviceId(deviceId)
                .build());
    }

    private Optional<Emotion> findRegisteredEmotion(UUID requestId, Long deviceId) {
        return emotionRepository.findByRequestId(requestId, deviceId)
                .map(EmotionDetailProjection::getEmotion)
                .map(emotion -> {
                    if (!Objects.equals(emotion.getDeviceId(), deviceId)) {
                        throw new EmotionException(EMOTION_REQUEST_ID_CONFLICT);
                    }
                    return emotion;
                });
    }

    @Transactional
    public void updateEmoji(Long emotionId, UUID devicePublicId, EmojiType emojiType, boolean selected) {
        Long deviceId = deviceRepository.findByPublicId(devicePublicId)
                .map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        emotionRepository.findVisibleById(emotionId, deviceId)
                .orElseThrow(() -> new EmotionException(EMOTION_NOT_VISIBLE));

        if (selected) {
            emotionEmojiRepository.saveIfAbsent(emotionId, deviceId, emojiType.name(), Instant.now());
        } else {
            emotionEmojiRepository.deleteSelection(emotionId, deviceId, emojiType.name());
        }
    }
}
