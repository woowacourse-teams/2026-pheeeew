package com.pheeeew.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateRequest(
        @NotNull(message = "컬렉션 발급 알림 수신 여부는 필수입니다.")
        @Schema(description = "컬렉션 카드를 받은 다음 날 오전 10시(한국 시간)에 오는 알림을 받을지 정합니다. "
                + "보낼 때 이 값을 다시 확인합니다.", example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean collectionIssuedEnabled,
        @NotNull(message = "순위 하락 알림 수신 여부는 필수입니다.")
        @Schema(description = "개인 전국 일별 프레스 순위가 5위 이내에서 내려가면 약 1시간 뒤 오는 알림을 받을지 정합니다. "
                + "보낼 때 이 값을 다시 확인합니다.", example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean rankDroppedEnabled
) {
}
