package com.pheeeew.feature.screens.group.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_join_confirm_action
import pheeeew.shared.generated.resources.group_join_confirm_cancel
import pheeeew.shared.generated.resources.group_join_confirm_message
import pheeeew.shared.generated.resources.group_join_confirm_title

@Composable
internal fun GroupJoinConfirmationDialog(
    groupName: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    AppDialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(20.dp), color = AppColors.Surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    stringResource(Res.string.group_join_confirm_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.GroupInk,
                )
                Text(
                    stringResource(Res.string.group_join_confirm_message, groupName),
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = AppColors.TextSecondary,
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.group_join_confirm_cancel), color = AppColors.TextSecondary)
                    }
                    TextButton(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                        Text(stringResource(Res.string.group_join_confirm_action), color = AppColors.GroupInk)
                    }
                }
            }
        }
    }
}
