package com.pheeeew.feature.screens.pressevent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.CircularLoadingIndicator
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appInk
import com.pheeeew.core.designsystem.theme.appPageBackground
import com.pheeeew.feature.component.emotion.EmotionListLoadError
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_frustrated

@Composable
internal fun PressEventEntryScreen(
    status: PressEventEntryStatus,
    onBack: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.appPageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) {
            EventBackButton(onBack, MaterialTheme.colorScheme.appInk, Modifier.align(Alignment.CenterStart))
            Text("이벤트", color = MaterialTheme.colorScheme.appInk, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (status == PressEventEntryStatus.Loading) {
                CircularLoadingIndicator()
            } else {
                val disabled = status == PressEventEntryStatus.Disabled
                EmotionListLoadError(
                    title = if (disabled) "현재 진행중인 이벤트가 없어요." else "진행 중인 이벤트를 불러오지 못했어요",
                    message =
                        when (status) {
                            PressEventEntryStatus.Disabled -> "다음 이벤트를 기다려주세요"
                            PressEventEntryStatus.Offline -> "네트워크 연결을 확인해주세요"
                            else -> "다음에 다시 시도해주세요"
                        },
                    hasItems = false,
                    onRetry = onBack,
                    actionLabel = "확인",
                    icon = if (disabled) Res.drawable.ic_emotion_discouraged else Res.drawable.ic_emotion_frustrated,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Preview(name = "이벤트 · 종료", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventDisabledPreview() {
    AppTheme { PressEventEntryScreen(PressEventEntryStatus.Disabled, {}) }
}

@Preview(name = "이벤트 · 오프라인", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventOfflinePreview() {
    AppTheme { PressEventEntryScreen(PressEventEntryStatus.Offline, {}) }
}

@Preview(name = "이벤트 · 조회 실패", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventFailurePreview() {
    AppTheme { PressEventEntryScreen(PressEventEntryStatus.Failed, {}) }
}

@Preview(name = "이벤트 · 확인 중", widthDp = 360, heightDp = 800)
@Composable
private fun PressEventLoadingPreview() {
    AppTheme { PressEventEntryScreen(PressEventEntryStatus.Loading, {}) }
}
