package com.pheeeew.emotion.application.command;

import static com.pheeeew.emotion.exception.EmotionErrorCode.EMOTION_AUDIO_UPLOAD_UNAVAILABLE;

import com.pheeeew.emotion.application.AudioUploadLinker;
import com.pheeeew.emotion.domain.Audio;
import com.pheeeew.emotion.domain.EmotionContent;
import com.pheeeew.emotion.exception.EmotionException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class EmotionContentResolver {

    private final ObjectProvider<AudioUploadLinker> audioUploadLinker;

    @Transactional(propagation = Propagation.MANDATORY)
    public EmotionContent resolve(String memo, String audioUploadId, Long deviceId, UUID requestId) {
        EmotionContent content = EmotionContent.builder().memo(memo).build();
        if (audioUploadId == null) {
            return content;
        }
        if (audioUploadId.isBlank()) {
            throw new IllegalArgumentException("녹음 업로드 식별자는 비어 있을 수 없습니다.");
        }
        if (content.getMemo() != null) {
            throw new IllegalArgumentException("메모와 녹음은 함께 등록할 수 없습니다.");
        }

        AudioUploadLinker linker = audioUploadLinker.getIfAvailable();
        if (linker == null) {
            throw new EmotionException(EMOTION_AUDIO_UPLOAD_UNAVAILABLE);
        }
        String objectKey = linker.claim(audioUploadId, deviceId, requestId);

        return EmotionContent.builder()
                .audio(Audio.builder()
                        .objectKey(objectKey)
                        .build())
                .build();
    }
}
