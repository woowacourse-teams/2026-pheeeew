package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionReaction
import org.jetbrains.compose.resources.painterResource
import kotlin.time.Clock

@Composable
internal fun EmotionChatRow(
    item: NearbyEmotionItemUiModel,
    selected: Boolean,
    busy: Boolean,
    playing: Boolean,
    audioLoading: Boolean,
    onSelect: () -> Unit,
    onDismissMenu: () -> Unit,
    onReact: (EmotionReaction) -> Unit,
    onBlock: () -> Unit,
    onReport: (() -> Unit)?,
    onPlay: () -> Unit,
) {
    val alignment = if (item.isMine) Alignment.End else Alignment.Start
    Column(Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!item.isMine) EmotionProfile(item)
            Text(
                if (item.isMine) "${item.nickname} · 나" else item.nickname,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            if (item.isMine) EmotionProfile(item)
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = if (item.isMine) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (item.isMine) {
                MessageTime(item, Modifier.align(Alignment.Bottom).padding(bottom = 6.dp))
            }
            Box(Modifier.weight(1f, fill = false).widthIn(max = 280.dp).padding(horizontal = 8.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .border(
                            if (selected) 2.dp else 1.dp,
                            if (selected) Color(0xFFFFB000) else Color(0xFFE8E8E8),
                            RoundedCornerShape(16.dp),
                        ).background(Color.White, RoundedCornerShape(16.dp))
                        .combinedClickable(onClick = {}, onLongClick = onSelect)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (item.stamp != null && item.isMine) {
                        Image(painterResource(item.state.face), item.state.label, Modifier.size(32.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        when (item.contentType) {
                            EmotionContentType.MEMO -> {
                                Text(item.memo?.takeIf { it.isNotBlank() } ?: "메모 없이 남긴 감정", color = Color(0xFF252826))
                            }

                            EmotionContentType.NONE -> {
                                Text("메모 없이 남긴 ${item.state.label}", color = Color.Gray)
                            }

                            EmotionContentType.AUDIO -> {
                                TextButton(onClick = onPlay, enabled = !audioLoading) {
                                    Text(
                                        when {
                                            audioLoading -> "녹음 불러오는 중…"
                                            playing -> "■ 재생 중지"
                                            else -> "▶ 녹음 듣기"
                                        },
                                    )
                                }
                            }
                        }
                    }
                    if (item.stamp != null && !item.isMine) {
                        Image(painterResource(item.state.face), item.state.label, Modifier.size(32.dp))
                    }
                }
                DropdownMenu(expanded = selected, onDismissRequest = onDismissMenu) {
                    if (!item.isMine) {
                        DropdownMenuItem(
                            text = { Text("차단하기", color = MaterialTheme.colorScheme.error) },
                            onClick = onBlock,
                            enabled = !busy,
                        )
                        DropdownMenuItem(
                            text = { Text("신고하기", color = MaterialTheme.colorScheme.error) },
                            onClick = { onReport?.invoke() },
                            enabled = !busy && onReport != null,
                        )
                        HorizontalDivider()
                    }
                    Row(Modifier.padding(4.dp)) {
                        EmotionReaction.entries.forEach { type ->
                            val selectedReaction = item.reactions.any { it.type == type && it.selected }
                            val actionLabel = if (selectedReaction) "취소" else "공감"
                            TextButton(
                                onClick = { onReact(type) },
                                enabled = !busy,
                                contentPadding = PaddingValues(0.dp),
                                modifier =
                                    Modifier
                                        .size(44.dp)
                                        .semantics {
                                            contentDescription = "${type.label} $actionLabel"
                                        },
                            ) {
                                Text(type.glyph, fontSize = 23.sp)
                            }
                        }
                    }
                }
            }
            if (!item.isMine) {
                MessageTime(item, Modifier.align(Alignment.Bottom).padding(bottom = 6.dp))
            }
        }
        FlowRow(
            Modifier.widthIn(max = 280.dp).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item.reactions.filter { it.count > 0 || it.selected }.forEach { reaction ->
                FilterChip(
                    selected = reaction.selected,
                    onClick = { onReact(reaction.type) },
                    enabled = !busy,
                    label = { Text("${reaction.type.glyph} ${reaction.count}", fontSize = 12.sp) },
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                "${reaction.type.label} ${reaction.count}개, ${if (reaction.selected) "선택됨" else "선택 안 됨"}"
                        },
                )
            }
            TextButton(
                onClick = onSelect,
                enabled = !busy,
                modifier =
                    Modifier.semantics {
                        contentDescription = "공감 추가"
                    },
            ) { Text("+") }
        }
    }
}

@Composable
private fun EmotionProfile(item: NearbyEmotionItemUiModel) {
    if (item.stamp != null) {
        NearbyGroupStamp(item.stamp, 36.dp)
    } else {
        Image(painterResource(item.state.face), item.state.label, Modifier.size(36.dp))
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
