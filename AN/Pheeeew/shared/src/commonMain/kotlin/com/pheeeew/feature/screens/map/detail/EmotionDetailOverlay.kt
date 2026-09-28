package com.pheeeew.feature.screens.map.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.pheeeew.core.audio.rememberAudioPlayback
import com.pheeeew.core.designsystem.component.ConfirmDialog
import com.pheeeew.domain.repository.EmotionModerationRepository
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.repository.audio.EmotionAudioRepository
import com.pheeeew.domain.usecase.BlockUserUseCase
import com.pheeeew.domain.usecase.DeleteEmotionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun EmotionDetailOverlay(
    state: EmotionDetailLoadUiModel,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    audioRepository: EmotionAudioRepository,
    onReactionClick: (String) -> Unit,
    blockUser: BlockUserUseCase,
    deleteEmotion: DeleteEmotionUseCase,
    onReportClick: (Long, DrawableResource) -> Unit,
    onBlockSucceeded: () -> Unit,
    onDeleteSucceeded: () -> Unit,
) {
    when (state) {
        EmotionDetailLoadUiModel.Closed -> {
            Unit
        }

        is EmotionDetailLoadUiModel.Ready -> {
            key(state.id) {
                EmotionDetailReadyOverlay(
                    state = state,
                    onDismiss = onDismiss,
                    onRetry = onRetry,
                    audioRepository = audioRepository,
                    onReactionClick = onReactionClick,
                    blockUser = blockUser,
                    deleteEmotion = deleteEmotion,
                    onReportClick = onReportClick,
                    onBlockSucceeded = onBlockSucceeded,
                    onDeleteSucceeded = onDeleteSucceeded,
                )
            }
        }

        else -> {
            Unit
        }
    }
}

@Composable
private fun EmotionDetailReadyOverlay(
    state: EmotionDetailLoadUiModel.Ready,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    audioRepository: EmotionAudioRepository,
    onReactionClick: (String) -> Unit,
    blockUser: BlockUserUseCase,
    deleteEmotion: DeleteEmotionUseCase,
    onReportClick: (Long, DrawableResource) -> Unit,
    onBlockSucceeded: () -> Unit,
    onDeleteSucceeded: () -> Unit,
) {
    var showActions by remember { mutableStateOf(false) }
    var showBlockConfirmation by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var isBlocking by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }
    var actionMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val audio = state.detail.content as? EmotionDetailContentUiModel.Audio
    if (audio == null) {
        EmotionDetailDialog(
            state.detail,
            onDismiss,
            { if (!isBlocking && !isDeleting) showActions = true },
            {},
            onReactionClick,
            notice = actionMessage,
            onNoticeDismiss = { actionMessage = null },
        )
    } else {
        key(audio.playbackUrl) {
            val playback = rememberAudioPlayback()
            val playbackState by playback.state.collectAsState()
            var preparing by remember { mutableStateOf(true) }
            var loadError by remember { mutableStateOf<String?>(null) }
            LaunchedEffect(playback, audio.playbackUrl) {
                try {
                    playback.prepare(audioRepository.download(audio.playbackUrl))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    loadError = "녹음을 불러올 수 없어요. 다시 시도해주세요"
                } finally {
                    preparing = false
                }
            }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) { playback.stop() }
            val error = loadError ?: playbackState.error
            val content =
                audio.copy(
                    durationMillis = playbackState.durationMillis.takeIf { it > 0 },
                    positionMillis = playbackState.positionMillis,
                    waveform = playbackState.waveform,
                    isPlaying = playbackState.isPlaying,
                    isPreparing = preparing,
                    error = error,
                )
            EmotionDetailDialog(
                state.detail.copy(content = content),
                onDismiss,
                { if (!isBlocking && !isDeleting) showActions = true },
                { if (error != null) onRetry() else playback.togglePlayback() },
                onReactionClick,
                notice = actionMessage,
                onNoticeDismiss = { actionMessage = null },
            )
        }
    }

    if (showActions) {
        EmotionActionSheet(
            isMine = state.isMine,
            onReportClick = {
                showActions = false
                if (!state.isMine) onReportClick(state.id, state.detail.emotion.icon)
            },
            onBlockClick = {
                showActions = false
                if (!state.isMine) showBlockConfirmation = true
            },
            onDeleteClick = {
                showActions = false
                showDeleteConfirmation = true
            },
            onCancelClick = { showActions = false },
        )
    }
    if (showBlockConfirmation) {
        ConfirmDialog(
            title = "해당 사용자를 차단하시겠습니까?",
            content = "차단 이후 해당 사용자가 올린 감정은 더 이상 보이지 않습니다.",
            confirmText = "차단하기",
            cancelText = "취소",
            onConfirm = {
                if (!isBlocking) {
                    showBlockConfirmation = false
                    isBlocking = true
                    scope.launch {
                        val result =
                            try {
                                blockUser(state.id)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                EmotionModerationResult.Unavailable
                            }
                        isBlocking = false
                        when (result) {
                            EmotionModerationResult.Success -> onBlockSucceeded()
                            else -> actionMessage = result.blockMessage()
                        }
                    }
                }
            },
            onCancel = { showBlockConfirmation = false },
        )
    }
    if (showDeleteConfirmation && state.isMine) {
        ConfirmDialog(
            title = "해당 감정을 삭제하시겠습니까?",
            content = "삭제한 감정은 지도와 목록에서 더 이상 보이지 않습니다.",
            confirmText = "삭제하기",
            cancelText = "취소",
            onConfirm = {
                if (!isDeleting) {
                    showDeleteConfirmation = false
                    isDeleting = true
                    scope.launch {
                        val result =
                            try {
                                deleteEmotion(state.id)
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                EmotionModerationResult.Unavailable
                            }
                        isDeleting = false
                        when (result) {
                            EmotionModerationResult.Success -> onDeleteSucceeded()
                            else -> actionMessage = result.deleteMessage()
                        }
                    }
                }
            },
            onCancel = { showDeleteConfirmation = false },
        )
    }
}

private fun EmotionModerationResult.blockMessage(): String =
    when (this) {
        EmotionModerationResult.OwnEmotion -> "내가 작성한 감정은 사용자 차단을 할 수 없어요."
        EmotionModerationResult.AuthorUnknown -> "작성자 정보를 알 수 없어 사용자 차단을 할 수 없어요."
        EmotionModerationResult.NotFound -> "차단할 감정을 찾을 수 없습니다."
        EmotionModerationResult.NetworkUnavailable -> "인터넷 연결 상태를 확인해주세요."
        else -> "차단에 실패했습니다. 잠시 후 다시 시도해주세요."
    }

private fun EmotionModerationResult.deleteMessage(): String =
    when (this) {
        EmotionModerationResult.NotFound -> "삭제할 감정을 찾을 수 없거나 삭제 권한이 없습니다."
        EmotionModerationResult.NetworkUnavailable -> "인터넷 연결 상태를 확인해주세요."
        else -> "삭제에 실패했습니다. 잠시 후 다시 시도해주세요."
    }

@Preview(name = "감정 상세 오버레이", widthDp = 424, heightDp = 640)
@Composable
private fun EmotionDetailOverlayPreview() {
    EmotionDetailOverlay(
        state =
            EmotionDetailLoadUiModel.Ready(
                1L,
                EmotionDetailPreviewData.model(EmotionDetailContentUiModel.Empty),
                false,
            ),
        onDismiss = {},
        onRetry = {},
        audioRepository = EmotionAudioRepository { error("Preview does not fetch audio") },
        onReactionClick = {},
        blockUser = BlockUserUseCase(PreviewModerationRepository),
        deleteEmotion = DeleteEmotionUseCase(PreviewModerationRepository),
        onReportClick = { _, _ -> },
        onBlockSucceeded = {},
        onDeleteSucceeded = {},
    )
}

private object PreviewModerationRepository : EmotionModerationRepository {
    override suspend fun report(
        emotionId: Long,
        reason: String,
    ) = EmotionModerationResult.Success

    override suspend fun blockEmotion(emotionId: Long) = EmotionModerationResult.Success

    override suspend fun blockUser(emotionId: Long) = EmotionModerationResult.Success

    override suspend fun delete(emotionId: Long) = EmotionModerationResult.Success
}
