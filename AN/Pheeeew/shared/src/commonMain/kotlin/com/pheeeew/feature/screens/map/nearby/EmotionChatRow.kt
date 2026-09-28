package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppBorders
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import org.jetbrains.compose.resources.painterResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_plus
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun EmotionChatRow(
    item: NearbyEmotionItemUiModel,
    selected: Boolean,
    busy: Boolean,
    playing: Boolean,
    audioLoading: Boolean,
    onOpenOnMap: () -> Unit,
    onSelect: () -> Unit,
    onDismissMenu: () -> Unit,
    onReact: (EmotionReactionType) -> Unit,
    onBlock: () -> Unit,
    onReport: (() -> Unit)?,
    onPlay: () -> Unit,
    focused: Boolean = false,
) {
    val alignment = if (item.isMine) Alignment.End else Alignment.Start
    val visibleReactions = item.reactions.filter { it.count > 0 || it.selected }
    val showAddReaction = EmotionReactionType.entries.any { type -> visibleReactions.none { it.type == type } }
    var reactionOnly by remember(item.id) { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    start = if (item.isMine) 16.dp else 0.dp,
                    end = if (item.isMine) 0.dp else 16.dp,
                ),
        horizontalAlignment = alignment,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.isMine) EmotionProfile(item)
            Text(
                if (item.isMine) "${item.nickname} · 나" else item.nickname,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (item.isMine) EmotionProfile(item)
        }
        Spacer(Modifier.height(2.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.isMine) {
                MessageTime(item, Modifier.align(Alignment.Bottom).padding(bottom = 6.dp))
            }
            Box(
                Modifier
                    .weight(1f, fill = false)
                    .padding(horizontal = 8.dp),
            ) {
                Row(
                    Modifier
                        .then(if (item.contentType == EmotionContentType.AUDIO) Modifier.fillMaxWidth() else Modifier)
                        .border(
                            AppBorders.Standard,
                            if (focused) AppColors.Primary else Color(0xFFE8E8E8),
                            RoundedCornerShape(16.dp),
                        ).background(Color.White, RoundedCornerShape(16.dp))
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onOpenOnMap,
                            onLongClick = {
                                reactionOnly = false
                                onSelect()
                            },
                        ).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (item.stamp != null && item.isMine && item.contentType != EmotionContentType.NONE) {
                        Image(painterResource(item.state.face), item.state.label, Modifier.size(32.dp))
                    }
                    Column(
                        if (item.contentType == EmotionContentType.NONE) {
                            Modifier
                        } else {
                            Modifier.weight(1f, fill = item.contentType == EmotionContentType.AUDIO)
                        },
                    ) {
                        when (item.contentType) {
                            EmotionContentType.MEMO -> {
                                Text(
                                    text = item.memo?.takeIf { it.isNotBlank() } ?: "메모 없이 남긴 감정",
                                    color = AppColors.TextPrimary,
                                    fontSize = 14.sp,
                                )
                            }

                            EmotionContentType.NONE -> {
                                Image(
                                    painterResource(item.state.face),
                                    item.state.label,
                                    Modifier.align(Alignment.CenterHorizontally).size(48.dp),
                                )
                            }

                            EmotionContentType.AUDIO -> {
                                TextButton(onClick = onPlay, enabled = !audioLoading) {
                                    Text(
                                        when {
                                            audioLoading -> "녹음 불러오는 중…"
                                            playing -> "■ 재생 중지"
                                            else -> "▶ 녹음 듣기"
                                        },
                                        color = AppColors.TextPrimary,
                                        fontSize = 14.sp,
                                    )
                                }
                            }
                        }
                    }
                    if (item.stamp != null && !item.isMine && item.contentType != EmotionContentType.NONE) {
                        Image(painterResource(item.state.face), item.state.label, Modifier.size(32.dp))
                    }
                }
                EmotionActionsMenu(
                    expanded = selected && !reactionOnly,
                    showActions = true,
                    isMine = item.isMine,
                    busy = busy,
                    reactions = item.reactions.map { it.type to it.selected },
                    onDismiss = onDismissMenu,
                    onBlock = onBlock,
                    onReport = onReport,
                    onReact = onReact,
                )
            }
            if (!item.isMine) {
                MessageTime(item, Modifier.align(Alignment.Bottom).padding(bottom = 6.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp, alignment),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            visibleReactions.forEach { reaction ->
                Row(
                    Modifier
                        .height(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (reaction.selected) Color(0xFFFFE987) else Color(0xFFF2F2F2))
                        .clickable(enabled = !busy, role = Role.Button) { onReact(reaction.type) }
                        .semantics {
                            contentDescription =
                                "${reaction.type.label} ${reaction.count}개, ${if (reaction.selected) "선택됨" else "선택 안 됨"}"
                        }.padding(horizontal = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
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
                Box(
                    Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFF2F2F2))
                        .clickable(enabled = !busy, role = Role.Button) {
                            reactionOnly = true
                            onSelect()
                        }.semantics { contentDescription = "공감 추가" },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(Res.drawable.ic_plus),
                        contentDescription = "공감 추가",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp),
                    )
                    EmotionActionsMenu(
                        expanded = selected && reactionOnly,
                        showActions = false,
                        isMine = item.isMine,
                        busy = busy,
                        reactions = item.reactions.map { it.type to it.selected },
                        onDismiss = onDismissMenu,
                        onBlock = onBlock,
                        onReport = onReport,
                        onReact = onReact,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmotionActionsMenu(
    expanded: Boolean,
    showActions: Boolean,
    isMine: Boolean,
    busy: Boolean,
    reactions: List<Pair<EmotionReactionType, Boolean>>,
    onDismiss: () -> Unit,
    onBlock: () -> Unit,
    onReport: (() -> Unit)?,
    onReact: (EmotionReactionType) -> Unit,
) {
    val cardShape = RoundedCornerShape(12.dp)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(296.dp),
        shape = RoundedCornerShape(0.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(Modifier.padding(8.dp)) {
            if (showActions && !isMine) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .shadow(5.dp, cardShape)
                        .clip(cardShape)
                        .background(Color.White),
                ) {
                    EmotionMenuAction("차단하기", busy, onBlock)
                    HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFD9D9D9))
                    EmotionMenuAction("신고하기", busy || onReport == null) { onReport?.invoke() }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(5.dp, cardShape)
                    .clip(cardShape)
                    .background(Color.White)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmotionReactionType.entries.forEach { type ->
                    val selected = reactions.any { it.first == type && it.second }
                    val actionLabel = if (selected) "취소" else "공감"
                    Box(
                        Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable(enabled = !busy, role = Role.Button) { onReact(type) }
                            .semantics { contentDescription = "${type.label} $actionLabel" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(type.glyph, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmotionMenuAction(
    label: String,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clickable(enabled = !disabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFFF1717), fontSize = 16.sp)
        EmotionWarningIcon()
    }
}

@Composable
private fun EmotionWarningIcon() {
    val red = Color(0xFFFF1717)
    Canvas(Modifier.size(18.dp)) {
        val w = size.width
        val h = size.height
        val outline =
            Path().apply {
                moveTo(w * 0.3f, h * 0.08f)
                lineTo(w * 0.7f, h * 0.08f)
                lineTo(w * 0.92f, h * 0.3f)
                lineTo(w * 0.92f, h * 0.7f)
                lineTo(w * 0.7f, h * 0.92f)
                lineTo(w * 0.3f, h * 0.92f)
                lineTo(w * 0.08f, h * 0.7f)
                lineTo(w * 0.08f, h * 0.3f)
                close()
            }
        drawPath(outline, red, style = Stroke(width = 1.8.dp.toPx()))
        drawLine(
            red,
            start = Offset(w * 0.5f, h * 0.3f),
            end = Offset(w * 0.5f, h * 0.56f),
            strokeWidth = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(red, radius = 1.dp.toPx(), center = Offset(w * 0.5f, h * 0.71f))
    }
}

@Composable
private fun EmotionProfile(item: NearbyEmotionItemUiModel) {
    if (item.stamp != null) {
        NearbyGroupStamp(item.stamp, 32.dp)
    } else {
        Image(painterResource(item.state.face), item.state.label, Modifier.size(32.dp))
    }
}

@Composable
private fun MessageTime(
    item: NearbyEmotionItemUiModel,
    modifier: Modifier = Modifier,
) {
    Text(relativeTime(item), modifier.widthIn(max = 48.dp), fontSize = 10.sp, color = Color.Gray)
}

private fun relativeTime(item: NearbyEmotionItemUiModel): String {
    val seconds = (Clock.System.now() - item.createdAt).inWholeSeconds.coerceAtLeast(0)
    return when {
        seconds < 60 -> "방금 전"
        seconds < 3600 -> "${seconds / 60}분 전"
        seconds < 86400 -> "${seconds / 3600}시간 전"
        else -> "${seconds / 86400}일 전"
    }
}

internal fun nearbyPreviewItems(): List<NearbyEmotionItemUiModel> {
    val previewStamp = StampAppearanceUiModel("산책", StampShapeId.FLOWER, 0xFFACD9EE, 0xFF252826)
    val emotion =
        NearbyEmotionItemUiModel(
            id = 1L,
            state = EmotionState.EXHAUSTED,
            nickname = "느긋한 고양이",
            createdAt = Clock.System.now() - 5.minutes,
            isMine = true,
            contentType = EmotionContentType.NONE,
            memo = null,
            reactions = emptyList(),
            stamp = null,
            audio = null,
        )
    return listOf(
        // Empty reactions: the add button should sit at the right edge of my bubble.
        emotion,
        emotion.copy(id = 2L, nickname = "산책하는 토끼", isMine = false, stamp = previewStamp),
        emotion.copy(
            id = 3L,
            state = EmotionState.FRUSTRATED,
            contentType = EmotionContentType.MEMO,
            memo = "오늘은 조금 지쳤지만, 잠깐 쉬어 가려고요.",
            stamp = previewStamp,
            reactions =
                EmotionReactionType.entries.mapIndexed { index, type ->
                    ReactionCount(type, (index + 1).toLong(), selected = index == 0)
                },
        ),
        emotion.copy(
            id = 4L,
            nickname = "조용한 여행자",
            isMine = false,
            state = EmotionState.IRRITATED,
            contentType = EmotionContentType.AUDIO,
            reactions = listOf(ReactionCount(EmotionReactionType.HEART, 2L, selected = false)),
        ),
    )
}

@Preview(name = "Nearby · 감정·메모·녹음과 공감", widthDp = 402, heightDp = 850, showBackground = true)
@Preview(name = "Nearby · 좁은 화면 줄바꿈", widthDp = 320, heightDp = 850, showBackground = true)
@Composable
private fun NearbyEmotionRowsPreview() {
    val emotions = remember { nearbyPreviewItems() }
    AppTheme {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(Color(0xFFFAFAFA)),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(emotions, key = { it.id }) { NearbyPreviewRow(it) }
        }
    }
}

@Composable
internal fun NearbyPreviewRow(item: NearbyEmotionItemUiModel) {
    var selected by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    EmotionChatRow(
        item = item,
        selected = selected,
        busy = false,
        playing = playing,
        audioLoading = false,
        onOpenOnMap = {},
        onSelect = { selected = true },
        onDismissMenu = { selected = false },
        onReact = { selected = false },
        onBlock = {},
        onReport = {},
        onPlay = { playing = !playing },
    )
}

@Preview(name = "Nearby · 공감 전체 표시 / 줄바꿈", widthDp = 320, showBackground = true)
@Composable
private fun NearbyAllReactionsPreview() {
    val emotion =
        remember {
            nearbyPreviewItems().first { it.contentType == EmotionContentType.MEMO }.copy(
                reactions = EmotionReactionType.entries.map { ReactionCount(it, 1234L, selected = false) },
            )
        }
    AppTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NearbyPreviewRow(emotion)
            NearbyPreviewRow(emotion.copy(id = 5L, isMine = false, reactions = emotion.reactions.dropLast(1)))
        }
    }
}

@Preview(name = "Nearby · 메모 길이에 따른 말풍선", widthDp = 320, showBackground = true)
@Composable
private fun NearbyMemoSizePreview() {
    val emotion =
        remember {
            nearbyPreviewItems().first { it.contentType == EmotionContentType.MEMO }.copy(reactions = emptyList())
        }
    AppTheme {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            NearbyPreviewRow(emotion.copy(memo = "휴…"))
            NearbyPreviewRow(emotion.copy(id = 7L))
            NearbyPreviewRow(
                emotion.copy(
                    id = 6L,
                    isMine = false,
                    memo = "오늘은 조금 지쳤지만 잠깐 쉬어 가려고요. 산책하면서 천천히 마음을 정리하고 싶어요.",
                ),
            )
        }
    }
}
