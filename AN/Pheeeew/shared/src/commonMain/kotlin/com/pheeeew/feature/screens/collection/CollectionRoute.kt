package com.pheeeew.feature.screens.collection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.core.network.ApiResult
import com.pheeeew.data.remote.api.CollectionToday
import com.pheeeew.data.remote.api.CollectionTodayApi

/** The server's date is authoritative, so a changed device clock cannot validate an old alert. */
@Composable
internal fun CollectionRoute(
    api: CollectionTodayApi,
    notificationDate: String?,
    refreshRevision: Long,
    onBack: () -> Unit,
) {
    var today by remember { mutableStateOf<CollectionToday?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(api, notificationDate, refreshRevision, retry, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            loading = true
            failed = false
            today = null
            when (val result = api.find()) {
                is ApiResult.Success -> today = result.value
                is ApiResult.Failure -> failed = true
            }
            loading = false
        }
    }
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TextButton(onClick = onBack) { Text("뒤로") }
            Text("오늘의 컬렉션", style = MaterialTheme.typography.headlineSmall)
            if (loading) CircularProgressIndicator()
            if (failed) {
                Text("컬렉션 진행도를 불러오지 못했어요.")
                TextButton(onClick = { retry++ }) { Text("다시 시도") }
            }
            today?.let { value ->
                if (notificationDate != null && notificationDate != value.date) {
                    Text("지난 날짜의 알림이에요. 오늘의 진행도를 보여드릴게요.")
                }
                Text(value.date)
                Text("스탬프 ${value.stamps.count} / ${value.stamps.target}")
                Text("프레스 ${value.presses.count} / ${value.presses.target}")
                Text(
                    when {
                        value.claimed -> "오늘의 카드는 이미 받았어요."
                        value.claimable -> "오늘의 카드를 받을 수 있어요."
                        else -> "스탬프와 프레스를 모아 오늘의 컬렉션을 완성해 보세요."
                    },
                )
            }
        }
    }
}
