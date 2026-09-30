package com.pheeeew.feature.screens.map

import com.pheeeew.domain.model.LocationError

internal enum class MapFeedbackAction(
    val label: String,
) {
    RetryMap("다시 시도"),
    RetryPins("다시 시도"),
    RetryLocation("다시 시도"),
    OpenSettings("설정"),
    OpenLocationSettings("설정"),
}

internal data class MapFeedbackUiModel(
    val message: String,
    val action: MapFeedbackAction? = null,
)

/** Show the underlying connection problem once instead of stacking its map/API symptoms. */
internal fun MapUiModel.primaryFeedback(): MapFeedbackUiModel? =
    when {
        isOffline -> {
            MapFeedbackUiModel("인터넷 연결이 끊겼어요. 연결 상태를 확인해주세요.")
        }

        mapError != null -> {
            MapFeedbackUiModel("지도를 불러오지 못했어요. 다시 시도해 주세요.", MapFeedbackAction.RetryMap)
        }

        emotionPinsError != null -> {
            MapFeedbackUiModel(
                if (hasPartialEmotionPins) "일부 감정을 불러오지 못했어요. 다시 시도해 주세요." else emotionPinsError,
                MapFeedbackAction.RetryPins,
            )
        }

        locationError != null -> {
            when (locationError) {
                LocationError.PermissionDenied -> {
                    MapFeedbackUiModel(
                        "현재 위치를 보려면 위치 권한을 허용해 주세요.",
                        MapFeedbackAction.OpenSettings,
                    )
                }

                LocationError.ServicesDisabled -> {
                    MapFeedbackUiModel(
                        "기기의 위치 서비스를 켜 주세요.",
                        MapFeedbackAction.OpenLocationSettings,
                    )
                }

                LocationError.LocationTimeout -> {
                    MapFeedbackUiModel(
                        "현재 위치 확인이 지연되고 있어요. 다시 시도해 주세요.",
                        MapFeedbackAction.RetryLocation,
                    )
                }

                LocationError.GpsUnavailable -> {
                    MapFeedbackUiModel(
                        "현재 위치를 확인하지 못했어요. 다시 시도해 주세요.",
                        MapFeedbackAction.RetryLocation,
                    )
                }
            }
        }

        else -> {
            null
        }
    }
