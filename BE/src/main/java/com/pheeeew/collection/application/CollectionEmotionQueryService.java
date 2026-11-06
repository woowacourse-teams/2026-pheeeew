package com.pheeeew.collection.application;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_PLAYBACK_UNAVAILABLE;

import com.pheeeew.collection.application.dto.CollectionDailyEmotionCursor;
import com.pheeeew.collection.application.dto.CollectionEmotionPageResult;
import com.pheeeew.collection.application.dto.CollectionEmotionResult;
import com.pheeeew.collection.infra.CollectionEmotionCursorCodec;
import com.pheeeew.device.domain.Device;
import com.pheeeew.emotion.application.AudioUrlIssuer.PlaybackUrl;
import com.pheeeew.emotion.application.AudioUrlIssuer;
import com.pheeeew.emotion.application.dto.EmotionEmojiResult;
import com.pheeeew.emotion.domain.EmojiType;
import com.pheeeew.emotion.domain.Emotion;
import com.pheeeew.emotion.domain.repository.EmotionEmojiRepository;
import com.pheeeew.emotion.domain.repository.EmotionRepository;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchFilter;
import com.pheeeew.emotion.domain.repository.query.EmotionSearchScope;
import com.pheeeew.emotion.exception.EmotionException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class CollectionEmotionQueryService {

    private static final int PAGE_SIZE = 50;

    private final EmotionRepository emotions;
    private final EmotionEmojiRepository emotionEmojis;
    private final ObjectProvider<AudioUrlIssuer> audioUrlIssuer;
    private final Clock clock;
    private final CollectionEmotionCursorCodec cursorCodec;

    CollectionEmotionPageResult findByDate(Device device, LocalDate date, String encodedCursor) {
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
        var cursor = encodedCursor == null
                ? CollectionDailyEmotionCursor.initial(device.getPublicId(), date, now)
                : cursorCodec.decodeDaily(encodedCursor);
        if (encodedCursor != null) {
            cursor.validateRequest(device.getPublicId(), date, now);
        }

        var filter = EmotionSearchFilter.of(date, date, null, EmotionSearchScope.MY, null);
        var found = emotions.findVisiblePageWithoutBounds(cursor.snapshotAt(), cursor.lastCreatedAt(), cursor.lastId(),
                device.getId(), filter, false, PAGE_SIZE + 1);

        boolean hasNext = found.size() > PAGE_SIZE;
        var page = hasNext ? found.subList(0, PAGE_SIZE) : found;

        var emojiCounts = findEmojisForPage(page, device.getId());
        var items = page.stream().map(emotion -> CollectionEmotionResult.of(emotion, issuePlaybackUrl(emotion),
                List.copyOf(emojiCounts.get(emotion.getId()).values()))).toList();
        String nextCursor = hasNext
                ? cursorCodec.encodeDaily(cursor.next(page.getLast().getCreatedAt(), page.getLast().getId())) : null;

        return CollectionEmotionPageResult.of(items, hasNext, nextCursor);
    }

    private Map<Long, EnumMap<EmojiType, EmotionEmojiResult>> findEmojisForPage(List<Emotion> page, Long deviceId) {
        Map<Long, EnumMap<EmojiType, EmotionEmojiResult>> counts = new HashMap<>();
        for (Emotion emotion : page) {
            var defaults = new EnumMap<EmojiType, EmotionEmojiResult>(EmojiType.class);
            for (EmojiType type : EmojiType.values()) {
                defaults.put(type, EmotionEmojiResult.of(type, 0, false));
            }
            counts.put(emotion.getId(), defaults);
        }
        if (!page.isEmpty()) {
            for (var count : emotionEmojis.findCountsForPage(page.stream().map(Emotion::getId).toList(), deviceId)) {
                EmojiType type = EmojiType.valueOf(count.getEmojiType());
                counts.get(count.getEmotionId()).put(type,
                        EmotionEmojiResult.of(type, count.getSelectionCount(), count.getSelected()));
            }
        }
        return counts;
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
