package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.feature.screens.settings.components.NicknameDialogButton
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.app_update
import pheeeew.shared.generated.resources.app_update_downloaded
import pheeeew.shared.generated.resources.app_update_failed
import pheeeew.shared.generated.resources.app_update_install
import pheeeew.shared.generated.resources.app_update_later
import pheeeew.shared.generated.resources.app_update_open_store
import pheeeew.shared.generated.resources.app_update_progress
import pheeeew.shared.generated.resources.app_update_ready_title
import pheeeew.shared.generated.resources.app_update_required_message
import pheeeew.shared.generated.resources.app_update_required_title
import pheeeew.shared.generated.resources.app_update_retry
import pheeeew.shared.generated.resources.app_update_store_error
import pheeeew.shared.generated.resources.app_update_suggested_message
import pheeeew.shared.generated.resources.app_update_suggested_title

/** Shared by the app and the debug reproduction screen; policy alone owns entry blocking. */
@Composable
fun AppUpdatePrompt(
    required: Boolean,
    state: AppUpdateState,
    storeOpenError: Boolean,
    onUpdate: () -> Unit,
    onOpenStore: () -> Unit,
    onRetry: () -> Unit,
    onLater: () -> Unit,
) {
    val busy =
        state in
            setOf(
                AppUpdateState.CHECKING,
                AppUpdateState.LAUNCHING,
                AppUpdateState.DOWNLOADING,
                AppUpdateState.INSTALLING,
            )
    val fallback = state in setOf(AppUpdateState.CANCELED, AppUpdateState.FAILED, AppUpdateState.UNAVAILABLE)
    val message =
        when {
            storeOpenError -> Res.string.app_update_store_error
            state == AppUpdateState.DOWNLOADED -> Res.string.app_update_downloaded
            busy -> Res.string.app_update_progress
            fallback -> Res.string.app_update_failed
            required -> Res.string.app_update_required_message
            else -> Res.string.app_update_suggested_message
        }
    val title =
        when {
            state == AppUpdateState.DOWNLOADED -> Res.string.app_update_ready_title
            required -> Res.string.app_update_required_title
            else -> Res.string.app_update_suggested_title
        }
    val confirmText = if (state == AppUpdateState.DOWNLOADED) Res.string.app_update_install else Res.string.app_update
    AppDialog(
        onDismissRequest = { if (!required) onLater() },
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = !required,
                dismissOnClickOutside = !required,
            ),
    ) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier
                    .widthIn(max = 356.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(AppColors.Surface)
                    .padding(24.dp),
            ) {
                Text(
                    stringResource(title),
                    color = AppColors.GroupInk,
                    fontFamily = notoSansKrFontFamily(),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    stringResource(message),
                    color = AppColors.TextSecondary,
                    fontFamily = notoSansKrFontFamily(),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                )
                Spacer(Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!required) {
                        NicknameDialogButton(
                            text = stringResource(Res.string.app_update_later),
                            modifier = Modifier.weight(1f),
                            onClick = onLater,
                        )
                    }
                    NicknameDialogButton(
                        text = stringResource(confirmText),
                        modifier = if (required) Modifier.fillMaxWidth() else Modifier.weight(1f),
                        primary = true,
                        enabled = !busy,
                        isLoading = busy,
                        onClick = onUpdate,
                    )
                }
                if (fallback) {
                    Spacer(Modifier.height(8.dp))
                    NicknameDialogButton(
                        text = stringResource(Res.string.app_update_open_store),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onOpenStore,
                    )
                }
                if (required) {
                    Spacer(Modifier.height(8.dp))
                    NicknameDialogButton(
                        text = stringResource(Res.string.app_update_retry),
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onRetry,
                    )
                }
            }
        }
    }
}
