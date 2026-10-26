package com.pheeeew.feature.screens.ranking.press

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.pheeeew.data.remote.api.DailyPressRankingApi
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.daily_notification_rank_context
import pheeeew.shared.generated.resources.daily_notification_rank_me
import pheeeew.shared.generated.resources.daily_notification_rank_me_empty
import pheeeew.shared.generated.resources.press_my_rank_failed
import pheeeew.shared.generated.resources.press_my_rank_loading

@Composable
internal fun DailyPressRankingRoute(
    api: DailyPressRankingApi,
    date: String,
    notificationId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val model = remember(api, scope) { DailyPressRankingState(api::find, scope) }
    val state by model.state.collectAsStateWithLifecycle()
    val owner = LocalLifecycleOwner.current
    DisposableEffect(model) { onDispose { model.close() } }
    LaunchedEffect(model, date, notificationId, owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (!model.select(date, notificationId)) model.refresh()
        }
    }
    val ranking = if (state.date == date) state.ranking else PressRankingUiState()
    PressRankingScreen(
        onBack = onBack,
        uiState = ranking,
        onRetry = model::retry,
        onRefresh = model::refresh,
        modifier = modifier,
        contextContent = {
            Text(stringResource(Res.string.daily_notification_rank_context, date), Modifier.padding(horizontal = 24.dp))
        },
        myRankContent = {
            // Historical results must not be labelled as today's race or invite activity for a past day.
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFFFFE66B),
                shape = RoundedCornerShape(18.dp),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        when (ranking.status) {
                            PressRankingStatus.Loading -> {
                                stringResource(Res.string.press_my_rank_loading)
                            }

                            PressRankingStatus.Failed -> {
                                stringResource(Res.string.press_my_rank_failed)
                            }

                            PressRankingStatus.Ready -> {
                                ranking.me?.let {
                                    stringResource(
                                        Res.string.daily_notification_rank_me,
                                        it.rank.toString(),
                                        it.count.toString(),
                                    )
                                } ?: stringResource(Res.string.daily_notification_rank_me_empty)
                            }
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        },
    )
}
