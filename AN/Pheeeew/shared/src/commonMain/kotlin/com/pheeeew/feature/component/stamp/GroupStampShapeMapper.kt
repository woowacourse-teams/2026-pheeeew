package com.pheeeew.feature.component.stamp

import com.pheeeew.domain.model.group.GroupStampFrame

/** Keeps API frame names separate from the Feature's Figma asset IDs. */
fun GroupStampFrame.toUiShape(): StampShapeId =
    when (this) {
        GroupStampFrame.CIRCLE -> StampShapeId.CIRCLE
        GroupStampFrame.STUB -> StampShapeId.TICKET
        GroupStampFrame.SQUIRCLE -> StampShapeId.ROUNDED_RECTANGLE
        GroupStampFrame.OVAL -> StampShapeId.OVAL
        GroupStampFrame.TAG -> StampShapeId.TAG
        GroupStampFrame.SCALLOP -> StampShapeId.FLOWER
        GroupStampFrame.STAMP -> StampShapeId.POSTAGE_STAMP
        GroupStampFrame.PAGE -> StampShapeId.VERTICAL_MEMO
        GroupStampFrame.CLOVER -> StampShapeId.FOUR_LEAF
        GroupStampFrame.VOUCHER -> StampShapeId.FOLDED_MEMO
    }

fun StampShapeId.toDomainFrame(): GroupStampFrame =
    when (this) {
        StampShapeId.CIRCLE -> GroupStampFrame.CIRCLE
        StampShapeId.TICKET -> GroupStampFrame.STUB
        StampShapeId.ROUNDED_RECTANGLE -> GroupStampFrame.SQUIRCLE
        StampShapeId.OVAL -> GroupStampFrame.OVAL
        StampShapeId.TAG -> GroupStampFrame.TAG
        StampShapeId.FLOWER -> GroupStampFrame.SCALLOP
        StampShapeId.POSTAGE_STAMP -> GroupStampFrame.STAMP
        StampShapeId.VERTICAL_MEMO -> GroupStampFrame.PAGE
        StampShapeId.FOUR_LEAF -> GroupStampFrame.CLOVER
        StampShapeId.FOLDED_MEMO -> GroupStampFrame.VOUCHER
    }
