package com.pheeeew.data.remote.emotion

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent

internal fun EmotionRegistration.toRequestDto(audioUploadId: String?): EmotionRegistrationRequestDto? {
    if (content == EmotionRegistrationContent.None) return null
    if (content is EmotionRegistrationContent.Memo && content.text.isBlank()) return null
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
                EmotionRegistrationContent.None -> return null
                is EmotionRegistrationContent.Memo -> "MEMO"
                is EmotionRegistrationContent.Audio -> "AUDIO"
            },
        memo = (content as? EmotionRegistrationContent.Memo)?.text,
        audioUploadId = resolvedAudioUploadId,
        groupId = groupId,
    )
}
