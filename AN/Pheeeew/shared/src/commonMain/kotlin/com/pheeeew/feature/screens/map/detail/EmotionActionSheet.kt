package com.pheeeew.feature.screens.map.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.component.AppDialog
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.appDividerSubtle
import com.pheeeew.core.designsystem.theme.appError
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appSurface

@Composable
internal fun EmotionActionSheet(
    isMine: Boolean,
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    AppDialog(onDismissRequest = onCancelClick, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onCancelClick), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Surface(
                    modifier = Modifier.padding(horizontal = AppSpacing.sm.copy(value = 10.dp).dp),
                    shape = AppShapes.ActionSheet,
                    color = MaterialTheme.colorScheme.appSurface,
                    contentColor = MaterialTheme.colorScheme.appInk,
                    shadowElevation = 12.dp,
                ) {
                    Column {
                        if (isMine) {
                            ActionItem("삭제하기", MaterialTheme.colorScheme.appError, onDeleteClick)
                        } else {
                            ActionItem("신고하기", MaterialTheme.colorScheme.appError, onReportClick)
                            HorizontalDivider(color = MaterialTheme.colorScheme.appDividerSubtle)
                            ActionItem("차단하기", MaterialTheme.colorScheme.appInk, onBlockClick)
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.sm.copy(value = 10.dp).dp))
                Surface(
                    modifier = Modifier.padding(horizontal = AppSpacing.sm.copy(value = 10.dp).dp),
                    shape = AppShapes.ActionSheet,
                    color = MaterialTheme.colorScheme.appSurface,
                    contentColor = MaterialTheme.colorScheme.appInk,
                    shadowElevation = 12.dp,
                ) {
                    ActionItem("취소", MaterialTheme.colorScheme.appInk, onCancelClick)
                }
            }
        }
    }
}

@Composable
private fun ActionItem(
    text: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(
                    min = 56.dp,
                ).clickable(onClick = onClick)
                .padding(horizontal = AppSpacing.xl.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = color, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
@Preview
private fun EmotionActionSheetPreview() {
    EmotionActionSheet(isMine = true, onReportClick = {}, onBlockClick = {}, onDeleteClick = {}, onCancelClick = {})
}
