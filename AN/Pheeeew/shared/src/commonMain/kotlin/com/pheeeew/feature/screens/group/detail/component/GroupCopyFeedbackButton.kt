package com.pheeeew.feature.screens.group.detail.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_copy_code
import pheeeew.shared.generated.resources.group_detail_copy_icon
import pheeeew.shared.generated.resources.group_detail_copy_succeeded

@Composable
internal fun GroupCopyFeedbackButton(
    copied: Boolean,
    successKey: Any? = copied,
    keepSuccessVisible: Boolean = false,
    onCopy: () -> Unit,
) {
    var showSuccess by remember { mutableStateOf(copied) }
    LaunchedEffect(copied, successKey) {
        showSuccess = copied
        if (copied && !keepSuccessVisible) {
            delay(1500)
            showSuccess = false
        }
    }
    val label =
        stringResource(if (showSuccess) Res.string.group_detail_copy_succeeded else Res.string.group_detail_copy_code)
    Box(
        Modifier.size(48.dp).clickable(role = Role.Button, onClick = onCopy).semantics {
            contentDescription = label
        },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(showSuccess) { success ->
            if (success) {
                Text("✓", color = AppColors.GroupInk, fontSize = 24.sp)
            } else {
                Image(painterResource(Res.drawable.group_detail_copy_icon), null, Modifier.size(40.dp).padding(10.dp))
            }
        }
    }
}
