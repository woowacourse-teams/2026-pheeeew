package com.pheeeew.feature.screens.report

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.pheeeew.domain.repository.EmotionModerationResult
import com.pheeeew.domain.usecase.ReportEmotionUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource

@Composable
fun ReportRoute(
    emotionId: Long,
    emotionStamp: DrawableResource,
    reportEmotion: ReportEmotionUseCase,
    onBack: () -> Unit,
) {
    var uiState by remember(emotionId, emotionStamp) { mutableStateOf(ReportScreenUiState(emotionStamp)) }
    val coroutineScope = rememberCoroutineScope()

    ReportScreen(
        uiState = uiState,
        onBack = onBack,
        onReasonSelect = { reason ->
            val maxDescriptionLength = (MAX_REASON_LENGTH - reason.length - REASON_SEPARATOR.length).coerceAtLeast(0)
            uiState =
                uiState.copy(
                    selectedReason = reason,
                    description = uiState.description.take(maxDescriptionLength),
                    errorMessage = null,
                )
        },
        onDescriptionChange = { description ->
            val reasonLength = uiState.selectedReason?.length ?: 0
            val maxDescriptionLength = (MAX_REASON_LENGTH - reasonLength - REASON_SEPARATOR.length).coerceAtLeast(0)
            uiState = uiState.copy(description = description.take(maxDescriptionLength), errorMessage = null)
        },
        onSubmit = {
            val selectedReason = uiState.selectedReason ?: return@ReportScreen
            if (uiState.isSubmitting) return@ReportScreen
            val details = uiState.description.trim()
            val reason = if (details.isEmpty()) selectedReason else "$selectedReason$REASON_SEPARATOR$details"
            uiState = uiState.copy(isSubmitting = true, errorMessage = null)
            coroutineScope.launch {
                val result =
                    try {
                        reportEmotion(emotionId, reason)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        EmotionModerationResult.Unavailable
                    }
                uiState =
                    when (result) {
                        EmotionModerationResult.Success -> {
                            uiState.copy(isSubmitting = false, successMessage = "신고가 접수되었어요.")
                        }

                        EmotionModerationResult.OwnEmotion -> {
                            uiState.copy(isSubmitting = false, errorMessage = "내 감정은 신고할 수 없어요.")
                        }

                        else -> {
                            uiState.copy(
                                isSubmitting = false,
                                errorMessage = "신고를 접수하지 못했어요. 잠시 후 다시 시도해 주세요.",
                            )
                        }
                    }
            }
        },
        onSnackbarDismiss = { uiState = uiState.copy(successMessage = null) },
    )
}

private const val MAX_REASON_LENGTH = 200
private const val REASON_SEPARATOR = "\n"
