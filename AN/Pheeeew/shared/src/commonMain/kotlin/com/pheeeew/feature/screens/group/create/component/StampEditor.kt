package com.pheeeew.feature.screens.group.create.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.GroupCreateFieldError
import com.pheeeew.feature.screens.group.create.GroupFormRules
import com.pheeeew.feature.screens.group.create.model.StampTextColorOption
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_color_choose
import pheeeew.shared.generated.resources.group_create_color_label
import pheeeew.shared.generated.resources.group_create_label_error_long
import pheeeew.shared.generated.resources.group_create_label_error_required
import pheeeew.shared.generated.resources.group_create_label_error_short
import pheeeew.shared.generated.resources.group_create_label_label
import pheeeew.shared.generated.resources.group_create_label_placeholder
import pheeeew.shared.generated.resources.group_create_shape_circle
import pheeeew.shared.generated.resources.group_create_shape_flower
import pheeeew.shared.generated.resources.group_create_shape_folded_memo
import pheeeew.shared.generated.resources.group_create_shape_four_leaf
import pheeeew.shared.generated.resources.group_create_shape_oval
import pheeeew.shared.generated.resources.group_create_shape_postage
import pheeeew.shared.generated.resources.group_create_shape_rounded_rectangle
import pheeeew.shared.generated.resources.group_create_shape_tag
import pheeeew.shared.generated.resources.group_create_shape_ticket
import pheeeew.shared.generated.resources.group_create_shape_vertical_memo
import pheeeew.shared.generated.resources.group_create_stamp_preview
import pheeeew.shared.generated.resources.group_create_stamp_shape
import pheeeew.shared.generated.resources.group_create_text_color_black
import pheeeew.shared.generated.resources.group_create_text_color_label
import pheeeew.shared.generated.resources.group_create_text_color_white

@Composable
internal fun StampEditor(
    stamp: StampAppearanceUiModel,
    labelLength: Int,
    rules: GroupFormRules,
    labelError: GroupCreateFieldError?,
    enabled: Boolean,
    onLabelChanged: (String) -> Unit,
    onShapeChanged: (StampShapeId) -> Unit,
    onTextColorChanged: (StampTextColorOption) -> Unit,
    onColorClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorPickerLabel = stringResource(Res.string.group_create_color_choose)
    Column(modifier = modifier.fillMaxWidth()) {
        CreateTextField(
            label = stringResource(Res.string.group_create_label_label),
            value = stamp.label,
            placeholder = stringResource(Res.string.group_create_label_placeholder),
            maximumLength = rules.stampLabelMax,
            currentLength = labelLength,
            errorText = labelError.toLabelErrorText(),
            onValueChange = onLabelChanged,
            enabled = enabled,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = stringResource(Res.string.group_create_stamp_shape),
            color = AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(
                items = StampShapeId.entries,
                key = { shape -> shape.name },
            ) { shape ->
                StampShapeOption(
                    shape = shape,
                    appearance = stamp,
                    selected = stamp.shape == shape,
                    enabled = enabled,
                    onClick = { onShapeChanged(shape) },
                    modifier = Modifier.width(58.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(124.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF4F5F4)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.group_create_stamp_preview),
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp),
                color = AppColors.RankingSecondaryContent,
                fontSize = 11.sp,
            )
            GroupStamp(appearance = stamp, size = 96.dp)
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.group_create_text_color_label),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            StampTextColorOption.entries.forEach { option ->
                StampTextColorChip(
                    option = option,
                    selected = stamp.textArgb == option.argb,
                    enabled = enabled,
                    onClick = { onTextColorChanged(option) },
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.group_create_color_label),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(enabled = enabled, role = Role.Button, onClick = onColorClick)
                        .semantics { contentDescription = colorPickerLabel }
                        .border(1.dp, AppColors.GroupInk, CircleShape)
                        .padding(4.dp)
                        .background(Color(stamp.fillArgb.toInt()), CircleShape),
            )
        }
    }
}

@Composable
private fun StampTextColorChip(
    option: StampTextColorOption,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(percent = 50)
    val label = stringResource(option.labelResource())
    Box(
        modifier =
            modifier
                .size(width = 40.dp, height = 26.dp)
                .clip(chipShape)
                .background(if (selected) Color(0xFFE8F7F2) else Color.Transparent)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) AppColors.GroupInk else Color(0xFFDCE1DC),
                    shape = chipShape,
                ).clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                }.padding(3.dp)
                .clip(chipShape)
                .background(Color(option.argb.toInt())),
    )
}

@Composable
private fun StampShapeOption(
    shape: StampShapeId,
    appearance: StampAppearanceUiModel,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(shape.labelResource())
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) Color(0xFFE8F7F2) else Color(0xFFF8F9F9))
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) AppColors.GroupInk else Color(0xFFE2E6E5),
                    shape = RoundedCornerShape(10.dp),
                ).clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick)
                .semantics {
                    contentDescription = label
                    this.selected = selected
                }.padding(vertical = 6.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GroupStamp(appearance = appearance.copy(label = "", shape = shape), size = 44.dp)
    }
}

@Composable
private fun GroupCreateFieldError?.toLabelErrorText(): String? =
    when (this) {
        GroupCreateFieldError.Required -> stringResource(Res.string.group_create_label_error_required)
        GroupCreateFieldError.TooShort -> stringResource(Res.string.group_create_label_error_short)
        GroupCreateFieldError.TooLong -> stringResource(Res.string.group_create_label_error_long)
        GroupCreateFieldError.Duplicate, null -> null
    }

private fun StampShapeId.labelResource() =
    when (this) {
        StampShapeId.CIRCLE -> Res.string.group_create_shape_circle
        StampShapeId.TICKET -> Res.string.group_create_shape_ticket
        StampShapeId.ROUNDED_RECTANGLE -> Res.string.group_create_shape_rounded_rectangle
        StampShapeId.OVAL -> Res.string.group_create_shape_oval
        StampShapeId.TAG -> Res.string.group_create_shape_tag
        StampShapeId.FLOWER -> Res.string.group_create_shape_flower
        StampShapeId.POSTAGE_STAMP -> Res.string.group_create_shape_postage
        StampShapeId.VERTICAL_MEMO -> Res.string.group_create_shape_vertical_memo
        StampShapeId.FOUR_LEAF -> Res.string.group_create_shape_four_leaf
        StampShapeId.FOLDED_MEMO -> Res.string.group_create_shape_folded_memo
    }

private fun StampTextColorOption.labelResource() =
    when (this) {
        StampTextColorOption.BLACK -> Res.string.group_create_text_color_black
        StampTextColorOption.WHITE -> Res.string.group_create_text_color_white
    }
