package com.pheeeew.sigh.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SighCreateV2Request(
        @NotNull(message = "요청 식별자는 필수입니다.")
        @Schema(
                description = "한 번의 한숨 등록 시도를 식별하는 UUID. 네트워크 재시도에는 같은 값을 사용합니다.",
                example = "5d1ad34e-1e20-4f20-a20e-3825a095fe6b"
        )
        UUID requestId,

        @NotNull(message = "위도는 필수입니다.")
        @DecimalMin(value = "-90.0", message = "위도는 -90 이상이어야 합니다.")
        @DecimalMax(value = "90.0", message = "위도는 90 이하여야 합니다.")
        @Schema(description = "실제 위치 주변 EPSG:5179 반경 300m 원에서 클라이언트가 면적 균등 추첨한 근사 좌표의 WGS84 위도. 실제 위도와 격자 중심은 전송하지 않습니다.", minimum = "-90", maximum = "90", example = "37.5657576255")
        Double latitude,

        @NotNull(message = "경도는 필수입니다.")
        @DecimalMin(value = "-180.0", message = "경도는 -180 이상이어야 합니다.")
        @DecimalMax(value = "180.0", message = "경도는 180 이하여야 합니다.")
        @Schema(description = "실제 위치 주변 EPSG:5179 반경 300m 원에서 클라이언트가 면적 균등 추첨한 근사 좌표의 WGS84 경도. 실제 경도와 격자 중심은 전송하지 않습니다.", minimum = "-180", maximum = "180", example = "126.9774258201")
        Double longitude,

        @Schema(
                description = "선택 메모. 앞뒤 공백은 제거되며 빈 문자열과 공백만 있는 값은 null로 저장됩니다.",
                maxLength = 50,
                nullable = true,
                example = "오늘은 조금 지쳤다"
        )
        String memo
) {

    @AssertTrue(message = "메모는 50자를 초과할 수 없습니다.")
    @Schema(hidden = true)
    public boolean isMemoLengthValid() {
        return memo == null || memo.strip().length() <= 50;
    }
}
