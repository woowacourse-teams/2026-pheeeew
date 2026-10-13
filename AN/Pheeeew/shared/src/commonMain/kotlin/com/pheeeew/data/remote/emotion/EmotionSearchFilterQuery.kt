package com.pheeeew.data.remote.emotion

import com.pheeeew.domain.model.emotion.EmotionSearchFilter

internal fun EmotionSearchFilter.toQueryParameters(): Map<String, String> =
    buildMap {
        startDate?.let { put("startDate", it) }
        endDate?.let { put("endDate", it) }
        put("contentType", content.name)
        put("scope", scope.name)
        groupId?.let { put("groupId", it) }
    }
