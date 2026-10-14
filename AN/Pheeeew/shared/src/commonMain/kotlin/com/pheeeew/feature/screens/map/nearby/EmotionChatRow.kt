package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.domain.model.emotion.EmotionContentType
import com.pheeeew.domain.model.emotion.EmotionReactionType
import com.pheeeew.domain.model.emotion.EmotionState
import com.pheeeew.domain.model.emotion.ReactionCount
import com.pheeeew.feature.component.emotion.EmotionActionsMenuMode
import com.pheeeew.feature.component.emotion.EmotionChatContentUiModel
import com.pheeeew.feature.component.emotion.EmotionChatItem
import com.pheeeew.feature.component.emotion.EmotionChatItemUiModel
import com.pheeeew.feature.component.emotion.face
import com.pheeeew.feature.component.emotion.label
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun EmotionChatRow(
    item: NearbyEmotionItemUiModel,
    menuMode: EmotionActionsMenuMode?,
    busy: Boolean,
    playing: Boolean,
    audioLoading: Boolean,
    onOpenOnMap: () -> Unit,
    onOpenMenu: (EmotionActionsMenuMode) -> Unit,
    onDismissMenu: () -> Unit,
    onReact: (EmotionReactionType) -> Unit,
    onBlock: () -> Unit,
    onReport: (() -> Unit)?,
    onPlay: () -> Unit,
    focused: Boolean,
) {
    EmotionChatItem(
        item = item.toChatItemUiModel(),
        menuMode = menuMode,
        busy = busy,
        playing = playing,
        audioLoading = audioLoading,
        focused = focused,
        onOpenOnMap = onOpenOnMap,
        onOpenMenu = onOpenMenu,
        onDismissMenu = onDismissMenu,
        onReact = onReact,
        onBlock = onBlock,
        onReport = onReport,
        onPlay = onPlay,
    )
}

private fun NearbyEmotionItemUiModel.toChatItemUiModel() =
    EmotionChatItemUiModel(
        nickname = nickname,
        isMine = isMine,
        emotionIcon = state.face,
        emotionDescription = state.label,
        timeLabel = relativeTime(this),
        content =
            when (contentType) {
                EmotionContentType.NONE -> EmotionChatContentUiModel.EmotionOnly
                EmotionContentType.MEMO -> EmotionChatContentUiModel.Text(memo)
                EmotionContentType.AUDIO -> EmotionChatContentUiModel.Audio(null)
            },
        reactions = reactions,
        stamp = stamp,
    )

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
    var menuMode by remember { mutableStateOf<EmotionActionsMenuMode?>(null) }
    var playing by remember { mutableStateOf(false) }
    EmotionChatRow(
        item = item,
        menuMode = menuMode,
        busy = false,
        playing = playing,
        audioLoading = false,
        onOpenOnMap = {},
        onOpenMenu = { menuMode = it },
        onDismissMenu = { menuMode = null },
        onReact = { menuMode = null },
        onBlock = { menuMode = null },
        onReport = { menuMode = null },
        onPlay = { playing = !playing },
        focused = false,
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
