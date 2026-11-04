package com.pheeeew.collection.application;

import static com.pheeeew.collection.exception.CollectionErrorCode.COLLECTION_CARD_NOT_FOUND;
import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;

import com.pheeeew.collection.application.dto.CollectionEmotionPageResult;
import com.pheeeew.collection.application.dto.CollectionEmotionResult;
import com.pheeeew.collection.application.dto.CollectionEmotionCursor;
import com.pheeeew.collection.infra.CollectionEmotionCursorCodec;
import com.pheeeew.collection.domain.repository.CollectionCardRepository;
import com.pheeeew.collection.exception.CollectionException;
import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CollectionEmotionQueryService {

    private static final int PAGE_SIZE = 50;

    private final DeviceRepository devices;
    private final CollectionCardRepository cards;
    private final EmotionRepository emotions;
    private final ObjectProvider<AudioUrlIssuer> audioUrlIssuer;
    private final Clock clock;
    private final CollectionEmotionCursorCodec cursorCodec;

    public CollectionEmotionPageResult findEmotions(UUID devicePublicId, UUID cardId, String encodedCursor) {
        Long deviceId = devices.findByPublicId(devicePublicId).map(Device::getId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
        var card = cards.findByPublicIdAndDeviceId(cardId, deviceId)
                .orElseThrow(() -> new CollectionException(COLLECTION_CARD_NOT_FOUND));
        var filter = EmotionSearchFilter.of(card.getCardDate(), card.getCardDate(), null, EmotionSearchScope.MY, null);
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        var cursor = encodedCursor == null
                ? CollectionEmotionCursor.initial(devicePublicId, cardId, card.getCardDate(), now)
                : cursorCodec.decode(encodedCursor);
        if (encodedCursor != null) {
            cursor.validateRequest(devicePublicId, cardId, card.getCardDate(), now);
        }

        var found = emotions.findVisiblePageWithoutBounds(cursor.snapshotAt(), cursor.lastCreatedAt(), cursor.lastId(),
                deviceId, filter, false, PAGE_SIZE + 1);
        boolean hasNext = found.size() > PAGE_SIZE;
        var page = hasNext ? found.subList(0, PAGE_SIZE) : found;
        var items = page.stream().map(emotion -> CollectionEmotionResult.of(emotion, issuePlaybackUrl(emotion))).toList();
        String nextCursor = hasNext
                ? cursorCodec.encode(cursor.next(page.getLast().getCreatedAt(), page.getLast().getId())) : null;
        return CollectionEmotionPageResult.of(items, hasNext, nextCursor);
    }

    private PlaybackUrl issuePlaybackUrl(Emotion emotion) {
        if (!emotion.getContent().hasAudio()) {
            return null;
        }
        AudioUrlIssuer issuer = audioUrlIssuer.getIfAvailable();
        if (issuer == null) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE);
        }

        try {
            PlaybackUrl result = issuer.issuePlayback(emotion.getContent().getAudio().getObjectKey());
            if (result == null || result.playbackUrl() == null || result.playbackUrl().isBlank()
                    || result.expiresAt() == null || !result.expiresAt().isAfter(Instant.now(clock))) {
                throw new IllegalStateException("유효한 재생 URL과 만료 시각이 필요합니다.");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new EmotionException(EMOTION_AUDIO_PLAYBACK_UNAVAILABLE, exception);
        }
    }
}
