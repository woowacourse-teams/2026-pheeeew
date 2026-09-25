package com.pheeeew.feature.component.stamp

import org.jetbrains.compose.resources.DrawableResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_stamp_circle_backdrop
import pheeeew.shared.generated.resources.group_stamp_circle_fill
import pheeeew.shared.generated.resources.group_stamp_flower_backdrop
import pheeeew.shared.generated.resources.group_stamp_flower_fill
import pheeeew.shared.generated.resources.group_stamp_flower_overlay
import pheeeew.shared.generated.resources.group_stamp_folded_memo_backdrop
import pheeeew.shared.generated.resources.group_stamp_folded_memo_fill
import pheeeew.shared.generated.resources.group_stamp_folded_memo_overlay
import pheeeew.shared.generated.resources.group_stamp_four_leaf_backdrop
import pheeeew.shared.generated.resources.group_stamp_four_leaf_fill
import pheeeew.shared.generated.resources.group_stamp_four_leaf_overlay
import pheeeew.shared.generated.resources.group_stamp_oval_backdrop
import pheeeew.shared.generated.resources.group_stamp_oval_fill
import pheeeew.shared.generated.resources.group_stamp_postage_stamp_backdrop
import pheeeew.shared.generated.resources.group_stamp_postage_stamp_fill
import pheeeew.shared.generated.resources.group_stamp_postage_stamp_overlay
import pheeeew.shared.generated.resources.group_stamp_rounded_rectangle_backdrop
import pheeeew.shared.generated.resources.group_stamp_rounded_rectangle_fill
import pheeeew.shared.generated.resources.group_stamp_tag_backdrop
import pheeeew.shared.generated.resources.group_stamp_tag_fill
import pheeeew.shared.generated.resources.group_stamp_tag_overlay
import pheeeew.shared.generated.resources.group_stamp_ticket_backdrop
import pheeeew.shared.generated.resources.group_stamp_ticket_fill
import pheeeew.shared.generated.resources.group_stamp_vertical_memo_backdrop
import pheeeew.shared.generated.resources.group_stamp_vertical_memo_fill
import pheeeew.shared.generated.resources.group_stamp_vertical_memo_overlay

/** 스탬프 모양 안에서 문구를 배치할 영역입니다. 좌표와 크기는 모양을 기준으로 정규화합니다. */
internal data class StampTextArea(
    val centerX: Float,
    val centerY: Float,
    val widthFraction: Float,
    val heightFraction: Float,
) {
    init {
        require(centerX in 0f..1f)
        require(centerY in 0f..1f)
        require(widthFraction in 0f..1f)
        require(heightFraction in 0f..1f)
    }
}

/** 한 모양을 바탕, 색 면, 장식 레이어로 나눠 렌더링하기 위한 정보입니다. */
internal data class StampShapeDefinition(
    val backdrop: DrawableResource,
    val fill: DrawableResource,
    val overlay: DrawableResource?,
    val aspectRatio: Float,
    val textArea: StampTextArea,
)

/** 안정된 모양 ID를 플랫폼 공통 벡터 리소스와 연결하는 단일 카탈로그입니다. */
internal object StampShapeCatalog {
    private val definitions =
        mapOf(
            StampShapeId.CIRCLE to definition(
                backdrop = Res.drawable.group_stamp_circle_backdrop,
                fill = Res.drawable.group_stamp_circle_fill,
                width = 40f,
                height = 41f,
                textArea = StampTextArea(0.5f, 0.48f, 0.7f, 0.42f),
            ),
            StampShapeId.TICKET to definition(
                backdrop = Res.drawable.group_stamp_ticket_backdrop,
                fill = Res.drawable.group_stamp_ticket_fill,
                width = 40f,
                height = 25.85f,
                textArea = StampTextArea(0.48f, 0.49f, 0.68f, 0.4f),
            ),
            StampShapeId.ROUNDED_RECTANGLE to definition(
                backdrop = Res.drawable.group_stamp_rounded_rectangle_backdrop,
                fill = Res.drawable.group_stamp_rounded_rectangle_fill,
                width = 40f,
                height = 40f,
                textArea = StampTextArea(0.49f, 0.47f, 0.67f, 0.42f),
            ),
            StampShapeId.OVAL to definition(
                backdrop = Res.drawable.group_stamp_oval_backdrop,
                fill = Res.drawable.group_stamp_oval_fill,
                width = 42f,
                height = 34f,
                textArea = StampTextArea(0.48f, 0.46f, 0.68f, 0.44f),
            ),
            StampShapeId.TAG to definition(
                backdrop = Res.drawable.group_stamp_tag_backdrop,
                fill = Res.drawable.group_stamp_tag_fill,
                overlay = Res.drawable.group_stamp_tag_overlay,
                width = 42f,
                height = 25.2f,
                textArea = StampTextArea(0.6f, 0.5f, 0.56f, 0.42f),
            ),
            StampShapeId.FLOWER to definition(
                backdrop = Res.drawable.group_stamp_flower_backdrop,
                fill = Res.drawable.group_stamp_flower_fill,
                overlay = Res.drawable.group_stamp_flower_overlay,
                width = 42f,
                height = 40.568f,
                textArea = StampTextArea(0.5f, 0.5f, 0.5f, 0.3f),
            ),
            StampShapeId.POSTAGE_STAMP to definition(
                backdrop = Res.drawable.group_stamp_postage_stamp_backdrop,
                fill = Res.drawable.group_stamp_postage_stamp_fill,
                overlay = Res.drawable.group_stamp_postage_stamp_overlay,
                width = 42f,
                height = 23.924f,
                textArea = StampTextArea(0.49f, 0.52f, 0.68f, 0.4f),
            ),
            StampShapeId.VERTICAL_MEMO to definition(
                backdrop = Res.drawable.group_stamp_vertical_memo_backdrop,
                fill = Res.drawable.group_stamp_vertical_memo_fill,
                overlay = Res.drawable.group_stamp_vertical_memo_overlay,
                width = 32.5f,
                height = 60f,
                textArea = StampTextArea(0.51f, 0.5f, 0.68f, 0.32f),
            ),
            StampShapeId.FOUR_LEAF to definition(
                backdrop = Res.drawable.group_stamp_four_leaf_backdrop,
                fill = Res.drawable.group_stamp_four_leaf_fill,
                overlay = Res.drawable.group_stamp_four_leaf_overlay,
                width = 42f,
                height = 42f,
                textArea = StampTextArea(0.5f, 0.51f, 0.48f, 0.3f),
            ),
            StampShapeId.FOLDED_MEMO to definition(
                backdrop = Res.drawable.group_stamp_folded_memo_backdrop,
                fill = Res.drawable.group_stamp_folded_memo_fill,
                overlay = Res.drawable.group_stamp_folded_memo_overlay,
                width = 42f,
                height = 33f,
                textArea = StampTextArea(0.46f, 0.55f, 0.62f, 0.4f),
            ),
        )

    operator fun get(shape: StampShapeId): StampShapeDefinition =
        checkNotNull(definitions[shape]) { "$shape 모양의 스탬프 정의가 없습니다." }

    private fun definition(
        backdrop: DrawableResource,
        fill: DrawableResource,
        overlay: DrawableResource? = null,
        width: Float,
        height: Float,
        textArea: StampTextArea,
    ) = StampShapeDefinition(
        backdrop = backdrop,
        fill = fill,
        overlay = overlay,
        aspectRatio = width / height,
        textArea = textArea,
    )
}
