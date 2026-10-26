package com.pheeeew.notification.presentation.dto;

import com.pheeeew.notification.domain.NotificationSettings;
import io.swagger.v3.oas.annotations.media.Schema;

public record NotificationSettingsResponse(
        @Schema(description = "컬렉션 발급 가능 알림 수신 여부입니다. 바꾼 적이 없으면 true 입니다.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean collectionClaimableEnabled,
        @Schema(description = "순위 하락 알림 수신 여부입니다. 바꾼 적이 없으면 true 입니다.",
                example = "true", requiredMode = Schema.RequiredMode.REQUIRED)
        boolean rankChangedEnabled
) {

    public static NotificationSettingsResponse from(NotificationSettings settings) {
        return new NotificationSettingsResponse(settings.collectionClaimableEnabled(), settings.rankChangedEnabled());
    }
}
