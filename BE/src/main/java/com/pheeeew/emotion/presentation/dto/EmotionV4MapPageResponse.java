package com.pheeeew.emotion.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pheeeew.emotion.application.dto.EmotionMapClusterView;
import com.pheeeew.emotion.application.dto.EmotionMapPageView;
import com.pheeeew.emotion.domain.EmotionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name = "EmotionV4MapPageResponse")
public record EmotionV4MapPageResponse(
        @Schema(description = "현재 페이지 핀. 묶음으로 응답하면 빈 배열입니다.")
        List<EmotionV4MapResponse> items,

        @Schema(description = "다음 페이지 존재 여부. 묶음으로 응답하면 false입니다.", example = "true")
        boolean hasNext,

        @Schema(description = "다음 페이지 조회에 사용할 서버 발급 커서", nullable = true, example = "opaque-cursor")
        String nextCursor,

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @Schema(description = "H3 셀 묶음. zoom을 전달한 첫 페이지가 limit을 넘을 때만 포함하며 그 외에는 필드를 생략합니다.",
                nullable = true)
        List<Cluster> clusters
) {

    public static EmotionV4MapPageResponse from(EmotionMapPageView page) {
        return new EmotionV4MapPageResponse(page.items().stream().map(EmotionV4MapResponse::from).toList(),
                page.hasNext(), page.nextCursor(),
                page.clusters().isEmpty() ? null : page.clusters().stream().map(Cluster::from).toList());
    }

    @Schema(name = "EmotionV4MapCluster")
    public record Cluster(
            @Schema(example = "Feature") String type,
            @Schema(description = "H3 셀 번호의 16진 문자열", example = "8730e1c9bffffff") String id,
            @Schema(description = "셀 중심. 핀이 늘어도 바뀌지 않습니다.") PointGeometry geometry,
            Properties properties
    ) {

        public static Cluster from(EmotionMapClusterView view) {
            return new Cluster("Feature", view.cell(), PointGeometry.of(view.longitude(), view.latitude()),
                    new Properties(view.count(), view.representativeState()));
        }

        @Schema(name = "EmotionV4MapClusterProperties")
        public record Properties(
                @Schema(description = "화면과 필터 조건에 맞는 셀 안의 핀 개수. 차단한 감정과 작성자는 제외합니다.", minimum = "1")
                long totalCount,
                @Schema(description = "최빈 감정. 동률은 ANGRY, DISCOURAGED, EXHAUSTED, FRUSTRATED, IRRITATED 순서입니다."
                        + " 모든 대상의 상태가 null이면 null입니다.", nullable = true)
                EmotionState representativeState
        ) {
        }
    }
}
