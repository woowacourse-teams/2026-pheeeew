package com.pheeeew.feature.screens.group.create.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.feature.component.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.screens.group.create.ColorConversion
import com.pheeeew.feature.screens.group.create.model.StampColorSelection
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_create_color_apply
import pheeeew.shared.generated.resources.group_create_color_cancel
import pheeeew.shared.generated.resources.group_create_color_close
import pheeeew.shared.generated.resources.group_create_color_description
import pheeeew.shared.generated.resources.group_create_color_hex
import pheeeew.shared.generated.resources.group_create_color_hex_label
import pheeeew.shared.generated.resources.group_create_color_preview
import pheeeew.shared.generated.resources.group_create_color_title
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StampColorSheet(
    appearance: StampAppearanceUiModel,
    selection: StampColorSelection,
    onSelectionChanged: (StampColorSelection) -> Unit,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }
    val dismissAndClose = {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                if (sheetState.isVisible) sheetState.hide()
                onDismiss()
            }
        }
    }
    val dismissAndApply = {
        if (!isClosing) {
            isClosing = true
            scope.launch {
                if (sheetState.isVisible) sheetState.hide()
                onApply()
            }
        }
    }
    ModalBottomSheet(
        onDismissRequest = dismissAndClose,
        sheetState = sheetState,
        dragHandle = null,
        containerColor = Color.White,
        scrimColor = Color(0x61202323),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 20.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 12.dp)
                        .size(width = 44.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD9DEDD)),
            )
            val closeDescription = stringResource(Res.string.group_create_color_close)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(Res.string.group_create_color_title),
                        color = AppColors.GroupInk,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(Res.string.group_create_color_description),
                        modifier = Modifier.padding(top = 5.dp),
                        color = AppColors.RankingSecondaryContent,
                        fontSize = 12.sp,
                    )
                }
                IconButton(
                    onClick = dismissAndClose,
                    modifier = Modifier.size(48.dp).semantics { contentDescription = closeDescription },
                ) {
                    Box(
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFF0F1EE)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val strokeWidth = 1.5.dp.toPx()
                            drawLine(
                                color = Color(0xFF454A48),
                                start = Offset(0f, 0f),
                                end = Offset(size.width, size.height),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                            )
                            drawLine(
                                color = Color(0xFF454A48),
                                start = Offset(size.width, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round,
                            )
                        }
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .height(124.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF0F1EE)),
            ) {
                Text(
                    text = stringResource(Res.string.group_create_color_preview),
                    modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp),
                    color = AppColors.RankingSecondaryContent,
                    fontSize = 11.sp,
                )
                GroupStamp(
                    appearance = appearance.copy(fillArgb = ColorConversion.toArgb(selection)),
                    size = 96.dp,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.group_create_color_hex_label),
                    color = AppColors.GroupInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        Res.string.group_create_color_hex,
                        ColorConversion.toRgbHex(ColorConversion.toArgb(selection)),
                    ),
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF3F4F3))
                            .border(1.dp, Color(0xFFE3E6E5), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    color = AppColors.GroupInk,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            StampColorPicker(
                selection = selection,
                onSelectionChanged = onSelectionChanged,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = dismissAndClose,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(Res.string.group_create_color_cancel), color = AppColors.GroupInk)
                }
                Button(
                    onClick = dismissAndApply,
                    modifier = Modifier.weight(2.1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.GroupInk),
                ) {
                    Text(stringResource(Res.string.group_create_color_apply), color = Color.White)
                }
            }
        }
    }
}
