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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
internal fun EmotionActionSheet(
    isMine: Boolean,
    onReportClick: () -> Unit,
    onBlockClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelClick: () -> Unit,
) {
    Dialog(onDismissRequest = onCancelClick, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().clickable(onClick = onCancelClick), contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
                Surface(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = AppColors.Surface,
                    contentColor = AppColors.GroupInk,
                    shadowElevation = 12.dp,
                ) {
                    Column {
                        ActionItem("신고하기", AppColors.Error, onReportClick)
                        HorizontalDivider(color = AppColors.Gray100)
                        ActionItem("차단하기", AppColors.GroupInk, onBlockClick)
                        if (isMine) {
                            HorizontalDivider(color = AppColors.Gray100)
                            ActionItem("삭제하기", Color(0xFFE26962), onDeleteClick)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.padding(horizontal = 10.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = AppColors.Surface,
                    contentColor = AppColors.GroupInk,
                    shadowElevation = 12.dp,
                ) {
                    ActionItem("취소", AppColors.GroupInk, onCancelClick)
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
                .padding(horizontal = 20.dp),
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
