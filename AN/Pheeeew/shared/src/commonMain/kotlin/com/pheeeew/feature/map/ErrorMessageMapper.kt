package com.pheeeew.feature.map

import com.pheeeew.core.audio.BreathInputError
import com.pheeeew.domain.exception.ApiException
import com.pheeeew.domain.model.location.LocationError
import com.pheeeew.domain.model.location.LocationState
import com.pheeeew.feature.map.map.MapError

internal fun ApiException.toUserMessage(): String =
    when (this) {
        is ApiException.Network -> "네트워크 연결이 불안정해요. 인터넷 연결을 확인한 후 다시 시도해 주세요."
        else -> message
    }

internal fun MapError.toUserMessage(): String =
    when (this) {
        MapError.RendererUnavailable -> "지도를 불러오지 못했어요. 잠시 후 재시도해 주세요."
        MapError.StyleLoadFailed -> "지도 화면을 불러오지 못했어요. 인터넷 연결 상태를 확인해 주세요."
    }

internal fun LocationError.toKoreanMessage(): String =
    when (this) {
        LocationError.PermissionDenied -> "설정에서 위치 권한을 '허용'으로 변경해주세요."
        LocationError.ServicesDisabled -> "기기 설정에서 위치 서비스를 켜주세요."
        LocationError.GpsUnavailable -> "현재 위치를 확인할 수 없습니다."
        LocationError.LocationTimeout -> "현재 위치를 확인하는 데 시간이 걸리고 있습니다."
    }

internal fun BreathInputError.toKoreanMessage(): String =
    when (this) {
        BreathInputError.PermissionDenied -> "마이크 권한이 필요해요"
        BreathInputError.MicrophoneUnavailable -> "마이크를 사용할 수 없어요"
        BreathInputError.StartFailed -> "마이크를 시작하지 못했어요. 다시 시도해주세요"
    }

internal fun MapUiState.toBannerMessage(): String? =
    when (val release = sighRelease) {
        is SighReleaseState.Error -> {
            release.message
        }

        else -> {
            errors.renderMessage
                ?: errors.refreshMessage
                ?: (location.state as? LocationState.Unavailable)?.reason?.toKoreanMessage()
        }
    }
