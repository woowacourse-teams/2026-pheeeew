package com.pheeeew.feature.screens.group.create

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.component.CreateTextField
import com.pheeeew.feature.screens.group.create.component.StampEditor
import com.pheeeew.feature.screens.group.create.model.StampTextColorOption
import com.pheeeew.feature.screens.group.model.GroupJoinType
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_description_error_long
import pheeeew.shared.generated.resources.group_create_description_label
import pheeeew.shared.generated.resources.group_create_description_placeholder
import pheeeew.shared.generated.resources.group_create_join_type_label
import pheeeew.shared.generated.resources.group_create_join_type_private
import pheeeew.shared.generated.resources.group_create_join_type_private_description
import pheeeew.shared.generated.resources.group_create_join_type_public
import pheeeew.shared.generated.resources.group_create_join_type_public_description
import pheeeew.shared.generated.resources.group_create_name_error_duplicate
import pheeeew.shared.generated.resources.group_create_name_error_long
import pheeeew.shared.generated.resources.group_create_name_error_required
import pheeeew.shared.generated.resources.group_create_name_error_short
import pheeeew.shared.generated.resources.group_create_name_label
import pheeeew.shared.generated.resources.group_create_name_placeholder

@Composable
internal fun GroupCreateForm(
    uiState: GroupCreateUiState,
    formRules: GroupFormRules,
    enabled: Boolean,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onJoinTypeChanged: (GroupJoinType) -> Unit,
    onStampLabelChanged: (String) -> Unit,
    onStampShapeChanged: (StampShapeId) -> Unit,
    onStampTextColorChanged: (StampTextColorOption) -> Unit,
    onOpenColorSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        CreateTextField(
            label = stringResource(Res.string.group_create_name_label),
            value = uiState.draft.name,
            placeholder = stringResource(Res.string.group_create_name_placeholder),
            maximumLength = formRules.groupNameMax,
            currentLength = formRules.count(uiState.draft.name),
            errorText = uiState.fieldErrors.name.toNameErrorText(),
            onValueChange = onNameChanged,
            enabled = enabled,
            imeAction = ImeAction.Next,
        )
        CreateTextField(
            label = stringResource(Res.string.group_create_description_label),
            value = uiState.draft.description,
            placeholder = stringResource(Res.string.group_create_description_placeholder),
            maximumLength = formRules.descriptionMax,
            currentLength = formRules.count(uiState.draft.description),
            errorText = uiState.fieldErrors.description.toDescriptionErrorText(),
            onValueChange = onDescriptionChanged,
            enabled = enabled,
            minLines = 3,
            maxLines = 3,
            imeAction = ImeAction.Next,
        )
        GroupJoinTypeSelector(
            selected = uiState.draft.joinType,
            enabled = enabled,
            onSelected = onJoinTypeChanged,
        )
        StampEditor(
            stamp = uiState.draft.stamp,
            labelLength = formRules.count(uiState.draft.stamp.label),
            rules = formRules,
            labelError = uiState.fieldErrors.stampLabel,
            enabled = enabled,
            onLabelChanged = onStampLabelChanged,
            onShapeChanged = onStampShapeChanged,
            onTextColorChanged = onStampTextColorChanged,
            onColorClick = onOpenColorSheet,
        )
    }
}

@Composable
internal fun GroupJoinTypeSelector(
    selected: GroupJoinType,
    enabled: Boolean,
    onSelected: (GroupJoinType) -> Unit,
) {
    Column {
        Text(
            text = stringResource(Res.string.group_create_join_type_label),
            color = AppColors.GroupInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GroupJoinType.entries.forEach { type ->
                GroupJoinTypeOption(
                    type = type,
                    selected = selected == type,
                    enabled = enabled,
                    onClick = { onSelected(type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            text =
                stringResource(
                    if (selected == GroupJoinType.PUBLIC) {
                        Res.string.group_create_join_type_public_description
                    } else {
                        Res.string.group_create_join_type_private_description
                    },
                ),
            modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            color = AppColors.RankingSecondaryContent,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun GroupJoinTypeOption(
    type: GroupJoinType,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier =
            modifier
                .height(44.dp)
                .clip(shape)
                .background(Color(0xFFF8F9F9))
                .border(
                    width = AppBorders.Standard,
                    color = if (selected) AppColors.GroupInk else Color(0xFFD9DEDD),
                    shape = shape,
                ).selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .border(
                        width = AppBorders.Standard,
                        color = if (selected) AppColors.GroupInk else Color(0xFF9AA3A1),
                        shape = CircleShape,
                    ).padding(3.dp)
                    .background(
                        color = if (selected) AppColors.GroupInk else Color.Transparent,
                        shape = CircleShape,
                    ),
        )
        Text(
            text =
                stringResource(
                    if (type == GroupJoinType.PUBLIC) {
                        Res.string.group_create_join_type_public
                    } else {
                        Res.string.group_create_join_type_private
                    },
                ),
            color = AppColors.GroupInk,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun GroupCreateFieldError?.toNameErrorText(): String? =
    when (this) {
        GroupCreateFieldError.Required,
        -> stringResource(Res.string.group_create_name_error_required)

        GroupCreateFieldError.TooShort -> stringResource(Res.string.group_create_name_error_short)

        GroupCreateFieldError.TooLong -> stringResource(Res.string.group_create_name_error_long)

        GroupCreateFieldError.Duplicate -> stringResource(Res.string.group_create_name_error_duplicate)

        null -> null
    }

@Composable
private fun GroupCreateFieldError?.toDescriptionErrorText(): String? =
    when (this) {
        GroupCreateFieldError.TooLong -> stringResource(Res.string.group_create_description_error_long)
        else -> null
    }
