package com.pheeeew.feature.component.emotion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.ReactionCount
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.emotion_chat_add_reaction
import pheeeew.shared.generated.resources.ic_plus

@Composable
internal fun EmotionReactionRow(
    isMine: Boolean,
    reactions: List<ReactionCount>,
    menuMode: EmotionActionsMenuMode?,
    busy: Boolean,
    onReact: ((EmotionReactionType) -> Unit)?,
    onOpenMenu: ((EmotionActionsMenuMode) -> Unit)?,
    onDismissMenu: () -> Unit,
    onBlock: (() -> Unit)?,
    onReport: (() -> Unit)?,
) {
    val addReactionDescription = stringResource(Res.string.emotion_chat_add_reaction)
    val visibleReactions = reactions.filter { it.count > 0 || it.selected }
    val showAddReaction = EmotionReactionType.entries.any { type -> visibleReactions.none { it.type == type } }
    val reactionMenuMode = menuMode?.takeIf { it == EmotionActionsMenuMode.Reactions }
    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, if (isMine) Alignment.End else Alignment.Start),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        visibleReactions.forEach { reaction ->
            Row(
                Modifier
                    .height(24.dp)
                    .background(if (reaction.selected) Color(0xFFFFE987) else Color(0xFFF2F2F2), RoundedCornerShape(50))
                    .clickable(enabled = !busy && onReact != null, role = Role.Button) {
                        onReact?.invoke(reaction.type)
                    }.semantics {
                        contentDescription =
                            "${reaction.type.label} ${reaction.count}개, ${if (reaction.selected) "선택됨" else "선택 안 됨"}"
                    }.padding(horizontal = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(reaction.type.glyph, fontSize = 14.sp)
                Text(
                    reaction.count.toString(),
                    color = Color.Black,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        if (showAddReaction) {
            Box {
                Box(
                    Modifier
                        .size(24.dp)
                        .background(Color(0xFFF2F2F2), CircleShape)
                        .clickable(
                            enabled = !busy && onOpenMenu != null,
                            role = Role.Button,
                        ) { onOpenMenu?.invoke(EmotionActionsMenuMode.Reactions) }
                        .semantics { contentDescription = addReactionDescription },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_plus),
                        contentDescription = addReactionDescription,
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                }
                EmotionActionsMenu(
                    mode = reactionMenuMode,
                    isMine = isMine,
                    busy = busy,
                    reactions = reactions.map { it.type to it.selected },
                    onDismiss = onDismissMenu,
                    onReact = onReact,
                    onBlock = onBlock,
                    onReport = onReport,
                )
            }
        }
    }
}

@Preview(name = "공감 행 · 선택 및 추가", widthDp = 320, showBackground = true)
@Composable
private fun EmotionReactionRowPreview() {
    AppTheme {
        EmotionReactionRow(
            isMine = false,
            reactions =
                listOf(
                    ReactionCount(EmotionReactionType.HEART, 2, selected = true),
                    ReactionCount(EmotionReactionType.CRY, 1, selected = false),
                ),
            menuMode = null,
            busy = false,
            onReact = {},
            onOpenMenu = {},
            onDismissMenu = {},
            onBlock = null,
            onReport = null,
        )
    }
}
