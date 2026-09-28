package com.pheeeew.data.remote.emotion

import com.pheeeew.domain.model.emotion.EmotionRegistration
import com.pheeeew.domain.model.emotion.EmotionRegistrationContent

internal fun EmotionRegistration.toRequestDto(): EmotionRegistrationRequestDto? {
    // The current contract requires an uploaded audio ID; a local path must never be sent as that ID.
    // Keep audio request preparation here so the revised contract does not affect the recording UI.
    if (content is EmotionRegistrationContent.Audio) return null
    return EmotionRegistrationRequestDto(
        requestId = requestId,
        state = state.name,
        longitude = coordinate.longitude,
        latitude = coordinate.latitude,
        rotationDegrees = rotationDegrees,
        contentType = if (content is EmotionRegistrationContent.Memo) "MEMO" else "NONE",
        memo = (content as? EmotionRegistrationContent.Memo)?.text,
        audioUploadId = null,
        groupId = groupId,
    )
}
