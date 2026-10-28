package com.pheeeew.data.press.delivery

import com.pheeeew.domain.press.time.PressDelayMillis

/** 직렬 POST 사이에 적용할 전송 전용 간격을 보유한다. */
internal class PressDeliveryPolicy(
    val minRequestIntervalMillis: PressDelayMillis = PressDelayMillis(100L),
) {
    init {
        require(minRequestIntervalMillis.value > 0L)
    }
}
