package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.notification.NotificationKind
import com.pheeeew.core.notification.NotificationPermissionController
import com.pheeeew.core.notification.NotificationPreferencesStore
import com.pheeeew.core.notification.allowsNotifications
import com.pheeeew.feature.screens.settings.components.SettingsDivider
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.notification_collection_toggle
import pheeeew.shared.generated.resources.notification_permission_loading
import pheeeew.shared.generated.resources.notification_rank_toggle
import pheeeew.shared.generated.resources.notification_settings_failed
import pheeeew.shared.generated.resources.notification_settings_retry
import pheeeew.shared.generated.resources.notification_system_blocked

@Composable
internal fun NotificationSettingsRows(
    permissions: NotificationPermissionController,
    store: NotificationPreferencesStore,
) {
    val model = remember(permissions, store) { NotificationSettingsState(permissions, store) }
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(model, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { model.refresh() }
    }

    NotificationKind.entries.forEachIndexed { index, kind ->
        if (index > 0) SettingsDivider()
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(
                    when (kind) {
                        NotificationKind.COLLECTION -> Res.string.notification_collection_toggle
                        NotificationKind.RANK_DROP -> Res.string.notification_rank_toggle
                    },
                ),
                modifier = Modifier.weight(1f),
                color = AppColors.GroupInk,
                fontSize = 14.sp,
            )
            Switch(
                checked = state.checked(kind),
                enabled = !state.busy && state.preferences != null,
                onCheckedChange = { enabled -> scope.launch { model.toggle(kind, enabled) } },
                colors = SwitchDefaults.colors(checkedTrackColor = AppColors.GroupInk),
            )
        }
    }
    Column(Modifier.padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.busy) Text(stringResource(Res.string.notification_permission_loading), fontSize = 12.sp)
        if (!state.busy && state.authorization != null && !state.authorization.allowsNotifications()) {
            Text(
                stringResource(Res.string.notification_system_blocked),
                fontSize = 12.sp,
                color = AppColors.TextSecondary,
            )
        }
        if (state.failed) {
            Text(stringResource(Res.string.notification_settings_failed), fontSize = 12.sp)
            TextButton(onClick = { scope.launch { model.refresh() } }, enabled = !state.busy) {
                Text(stringResource(Res.string.notification_settings_retry))
            }
        }
    }
}
