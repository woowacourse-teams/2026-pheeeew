package com.pheeeew.data.remote.api

/** Shared paths let Debug failure scenarios follow the same request contracts as the APIs. */
object ApiEndpointPaths {
    const val EMOTION_MAP = "/api/v2/emotions/map"
    const val EMOTION_REGIONS = "/api/v2/emotions/map/regions"
    const val EMOTIONS = "/api/v1/emotions"
    const val EMOTION_REGISTRATION = "/api/v3/emotions"
    const val AUDIO_UPLOAD_URL = "/api/v1/audio-uploads"
    const val GROUP_STAMPS = "/api/v2/groups/stamps"
    const val EMOTION_BLOCK = "/api/v2/blocks/emotions"
    const val DEVICE_BLOCK = "/api/v2/blocks/devices"
    const val REPORT = "/api/v2/reports"
}
