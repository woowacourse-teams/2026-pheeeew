package com.pheeeew.notification.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingsUpdateRequest(
        @NotNull(message = "컬렉션 발급 가능 알림 수신 여부는 필수입니다.")
        @Schema(description = "컬렉션 카드를 받을 수 있게 된 지 1시간 뒤 오는 발급 가능 알림과 "
                + "매일 23시(한국 시간)에 오는 리마인더를 받을지 정합니다. 보낼 때 이 값을 다시 확인합니다.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean collectionClaimableEnabled,
        @NotNull(message = "순위 하락 알림 수신 여부는 필수입니다.")
        @Schema(description = "개인 일별 프레스 순위를 매시 정각(한국 시간)에 1시간 전과 비교해, "
                + "5위 안이던 순위가 내려가면 오는 알림을 받을지 정합니다. 보낼 때 이 값을 다시 확인합니다.",
                example = "false", requiredMode = Schema.RequiredMode.REQUIRED)
        Boolean rankChangedEnabled
) {
}
