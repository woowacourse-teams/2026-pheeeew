package com.pheeeew.feature.screens.map.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppShapes

@Composable
internal fun EmotionReactionGrid(
    reactions: List<EmotionReactionUiModel>,
    onReactionClick: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reactions.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { reaction ->

                    Row(
                        Modifier
                            .weight(1f)
                            .clip(AppShapes.Pill)
                            .clickable(enabled = enabled, role = Role.Button) { onReactionClick(reaction.id) }
                            .background(if (reaction.isSelected) AppColors.Primary else AppColors.Gray100)
                            .then(
                                if (reaction.isSelected) {
                                    Modifier.border(
                                        1.dp,
                                        AppColors.GroupInk,
                                        AppShapes.Pill,
                                    )
                                } else {
                                    Modifier
                                },
                            ).padding(vertical = 8.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, alignment = Alignment.CenterHorizontally),
                    ) {
                        Text(reaction.emoji, fontSize = 18.sp)

                        Text(
                            reaction.count.toString(),
                            color = if (reaction.isSelected) AppColors.TextPrimary else AppColors.TextSecondary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Preview(name = "감정 반응", widthDp = 326, showBackground = true)
@Composable
private fun EmotionReactionGridPreview() {
    EmotionReactionGrid(EmotionDetailPreviewData.reactions, {}, true, Modifier.fillMaxWidth())
}
