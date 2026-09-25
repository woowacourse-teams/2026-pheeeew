package com.pheeeew.feature.screens.group.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.component.CreateTextField
import com.pheeeew.feature.screens.group.create.component.StampEditor
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_description_error_long
import pheeeew.shared.generated.resources.group_create_description_label
import pheeeew.shared.generated.resources.group_create_description_placeholder
import pheeeew.shared.generated.resources.group_create_name_error_duplicate
import pheeeew.shared.generated.resources.group_create_name_error_long
import pheeeew.shared.generated.resources.group_create_name_error_required
import pheeeew.shared.generated.resources.group_create_name_label
import pheeeew.shared.generated.resources.group_create_name_placeholder

@Composable
internal fun GroupCreateForm(
    uiState: GroupCreateUiState,
    formRules: GroupFormRules,
    enabled: Boolean,
    onNameChanged: (String) -> Unit,
    onDescriptionChanged: (String) -> Unit,
    onStampLabelChanged: (String) -> Unit,
    onStampShapeChanged: (StampShapeId) -> Unit,
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
        StampEditor(
            stamp = uiState.draft.stamp,
            labelLength = formRules.count(uiState.draft.stamp.label),
            rules = formRules,
            labelError = uiState.fieldErrors.stampLabel,
            enabled = enabled,
            onLabelChanged = onStampLabelChanged,
            onShapeChanged = onStampShapeChanged,
            onColorClick = onOpenColorSheet,
        )
    }
}

@Composable
private fun GroupCreateFieldError?.toNameErrorText(): String? =
    when (this) {
        GroupCreateFieldError.Required,
        GroupCreateFieldError.TooShort,
        -> stringResource(Res.string.group_create_name_error_required)

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
