package com.pheeeew.feature.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppAlertDialog
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.update.AppUpdateState
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.app_update
import pheeeew.shared.generated.resources.app_update_downloaded
import pheeeew.shared.generated.resources.app_update_failed
import pheeeew.shared.generated.resources.app_update_install
import pheeeew.shared.generated.resources.app_update_later
import pheeeew.shared.generated.resources.app_update_open_store
import pheeeew.shared.generated.resources.app_update_progress
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
    if (required) {
        AppDialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        ) {
            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(Res.string.app_update_required_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(message), fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = onUpdate, enabled = !busy) {
                        Text(
                            stringResource(
                                if (state ==
                                    AppUpdateState.DOWNLOADED
                                ) {
                                    Res.string.app_update_install
                                } else {
                                    Res.string.app_update
                                },
                            ),
                        )
                    }
                    if (fallback) {
                        TextButton(
                            onClick = onOpenStore,
                        ) { Text(stringResource(Res.string.app_update_open_store)) }
                    }
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.app_update_retry)) }
                }
            }
        }
        return
    }
    AppAlertDialog(
        onDismissRequest = { if (!required) onLater() },
        properties = DialogProperties(dismissOnBackPress = !required, dismissOnClickOutside = !required),
        title = {
            Text(
                stringResource(
                    if (required) Res.string.app_update_required_title else Res.string.app_update_suggested_title,
                ),
            )
        },
        text = { Text(stringResource(message)) },
        confirmButton = {
            Column {
                TextButton(onClick = onUpdate, enabled = !busy) {
                    Text(
                        stringResource(
                            if (state ==
                                AppUpdateState.DOWNLOADED
                            ) {
                                Res.string.app_update_install
                            } else {
                                Res.string.app_update
                            },
                        ),
                    )
                }
                if (fallback) {
                    TextButton(onClick = onOpenStore) { Text(stringResource(Res.string.app_update_open_store)) }
                }
                if (required) {
                    TextButton(onClick = onRetry) { Text(stringResource(Res.string.app_update_retry)) }
                }
            }
        },
        dismissButton = {
            if (!required) TextButton(onClick = onLater) { Text(stringResource(Res.string.app_update_later)) }
        },
    )
}
