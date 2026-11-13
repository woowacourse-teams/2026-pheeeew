package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appBackground
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appPrimary
import com.pheeeew.core.designsystem.theme.appSurface
import com.pheeeew.core.designsystem.theme.appSurfaceDisabled
import com.pheeeew.core.designsystem.theme.appTextPrimary
import com.pheeeew.core.notification.NotificationAuthorization
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPreferences
import com.pheeeew.feature.screens.settings.components.SettingsCard
import com.pheeeew.feature.screens.settings.components.SettingsCardContentInset
import com.pheeeew.feature.screens.settings.components.SettingsDivider
import com.pheeeew.feature.screens.settings.components.SettingsHeader
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.notification_all_toggle
import pheeeew.shared.generated.resources.notification_collection_toggle
import pheeeew.shared.generated.resources.notification_permission_title
import pheeeew.shared.generated.resources.notification_rank_toggle
import pheeeew.shared.generated.resources.notification_settings_failed
import pheeeew.shared.generated.resources.notification_settings_retry

@Composable
internal fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    onBackClick: () -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onToggle: (NotificationKind, Boolean) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.appBackground)
                .statusBarsPadding()
                .navigationBarsPadding(),
    ) {
        SettingsHeader(onBackClick = onBackClick, title = stringResource(Res.string.notification_permission_title))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(AppSpacing.xxxl.copy(value = 30.dp).dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .padding(horizontal = AppSpacing.screen.dp)
                        .semantics(mergeDescendants = true) {}
                        .toggleable(
                            value = NotificationKind.entries.all { state.checked(it) },
                            enabled = state.preferences != null,
                            role = Role.Switch,
                            onValueChange = onToggleAll,
                        ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    text = stringResource(Res.string.notification_all_toggle),
                )
                Spacer(modifier = Modifier.width(AppSpacing.lg.dp))
                Switch(
                    modifier =
                        Modifier.clearAndSetSemantics {}.drawWithContent {
                            scale(
                                0.85f,
                            ) { this@drawWithContent.drawContent() }
                        },
                    checked = NotificationKind.entries.all { state.checked(it) },
                    enabled = state.preferences != null,
                    onCheckedChange = null,
                    thumbContent = {
                        NotificationSwitchThumb(checked = NotificationKind.entries.all { state.checked(it) })
                    },
                    colors =
                        SwitchDefaults.colors(
                            checkedTrackColor = MaterialTheme.colorScheme.appPrimary,
                            checkedThumbColor = MaterialTheme.colorScheme.appSurface,
                            checkedBorderColor = MaterialTheme.colorScheme.appTextPrimary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.appSurface,
                            uncheckedTrackColor = MaterialTheme.colorScheme.appSurface,
                            uncheckedBorderColor = MaterialTheme.colorScheme.appTextPrimary,
                            disabledCheckedTrackColor = MaterialTheme.colorScheme.appSurfaceDisabled,
                            disabledCheckedThumbColor = MaterialTheme.colorScheme.appSurface,
                            disabledUncheckedTrackColor = MaterialTheme.colorScheme.appSurface,
                            disabledUncheckedThumbColor = MaterialTheme.colorScheme.appSurface,
                        ),
                )
            }
            SettingsCard {
                NotificationSettingsRows(state, onToggle, onRetry)
            }
        }
    }
}

@Composable
private fun NotificationSettingsRows(
    state: NotificationSettingsUiState,
    onToggle: (NotificationKind, Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    SettingsDivider()
    NotificationKind.entries.forEachIndexed { index, kind ->
        if (index > 0) SettingsDivider()
        NotificationToggleRow(
            title =
                stringResource(
                    when (kind) {
                        NotificationKind.COLLECTION -> Res.string.notification_collection_toggle
                        NotificationKind.RANK_DROP -> Res.string.notification_rank_toggle
                    },
                ),
            checked = state.checked(kind),
            enabled = state.preferences != null,
            onCheckedChange = { enabled -> onToggle(kind, enabled) },
        )
    }
    Column(
        modifier =
            Modifier
                .padding(horizontal = SettingsCardContentInset)
                .padding(bottom = AppSpacing.md.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm.dp),
    ) {
        if (state.failed && state.preferences == null) {
            Text(stringResource(Res.string.notification_settings_failed), fontSize = 12.sp)
            TextButton(onClick = onRetry, enabled = !state.busy) {
                Text(stringResource(Res.string.notification_settings_retry))
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .semantics(mergeDescendants = true) {}
                .toggleable(
                    value = checked,
                    interactionSource = null,
                    indication = ripple(color = MaterialTheme.appColors.settings.iconSecondary),
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(horizontal = SettingsCardContentInset),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.cardGap.dp),
    ) {
        Text(title, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.appInk, fontSize = 14.sp)
        Switch(
            modifier =
                Modifier.clearAndSetSemantics {}.drawWithContent {
                    scale(
                        0.85f,
                    ) { this@drawWithContent.drawContent() }
                },
            checked = checked,
            enabled = enabled,
            onCheckedChange = null,
            thumbContent = { NotificationSwitchThumb(checked = checked) },
            colors =
                SwitchDefaults.colors(
                    checkedTrackColor = MaterialTheme.colorScheme.appPrimary,
                    checkedThumbColor = MaterialTheme.colorScheme.appSurface,
                    checkedBorderColor = MaterialTheme.colorScheme.appTextPrimary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.appSurface,
                    uncheckedTrackColor = MaterialTheme.colorScheme.appSurface,
                    uncheckedBorderColor = MaterialTheme.colorScheme.appTextPrimary,
                ),
        )
    }
}

@Composable
private fun NotificationSwitchThumb(checked: Boolean) {
    Box(
        Modifier.size(24.dp).border(
            width = AppBorders.Standard,
            color = MaterialTheme.colorScheme.appTextPrimary,
            shape = CircleShape,
        ),
    )
}

@Preview(showSystemUi = true, device = "spec:width=390dp,height=844dp")
@Composable
private fun NotificationSettingsScreenPreview() {
    SettingsTheme {
        NotificationSettingsScreen(
            state =
                NotificationSettingsUiState(
                    preferences = NotificationPreferences(collectionEnabled = true, rankDropEnabled = false),
                    authorization = NotificationAuthorization.AUTHORIZED,
                    busy = false,
                ),
            onBackClick = {},
            onToggleAll = {},
            onToggle = { _, _ -> },
            onRetry = {},
        )
    }
}
