package com.pheeeew.data.remote.emotion

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent

internal fun EmotionRegistration.toRequestDto(audioUploadId: String?): EmotionRegistrationRequestDto? {
    val resolvedAudioUploadId =
        when (content) {
            is EmotionRegistrationContent.Audio -> audioUploadId?.takeIf { it.isNotBlank() } ?: return null
            else -> null
        }
    return EmotionRegistrationRequestDto(
        requestId = requestId,
        state = state.name,
        longitude = coordinate.longitude,
        latitude = coordinate.latitude,
        rotationDegrees = rotationDegrees,
        contentType =
            when (content) {
                EmotionRegistrationContent.None -> "NONE"
                is EmotionRegistrationContent.Memo -> "MEMO"
                is EmotionRegistrationContent.Audio -> "AUDIO"
            },
        memo = (content as? EmotionRegistrationContent.Memo)?.text,
        audioUploadId = resolvedAudioUploadId,
        groupId = groupId,
    )
}
