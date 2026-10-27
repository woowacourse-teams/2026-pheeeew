package com.pheeeew.feature.screens.press.component

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.feature.emotion.component.rememberTapReducedMotion
import com.pheeeew.feature.screens.press.PressNearbyRankingUiState
import com.pheeeew.feature.screens.press.nearbyRankingItemKey
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_nearby_my_place
import pheeeew.shared.generated.resources.press_nearby_start_hint

/** Stable participant keys let placement animations retarget from the current position on rapid updates. */
@Composable
internal fun PressNearbyRankingRows(state: PressNearbyRankingUiState) {
    val scope = rememberCoroutineScope()
    val reduced = rememberTapReducedMotion() || scope.coroutineContext[MotionDurationScale]?.scaleFactor == 0f
    val rowHeight = 38.dp * maxOf(1f, LocalDensity.current.fontScale)
    val easing = CubicBezierEasing(0.22f, 0.7f, 0.25f, 1f)
    LazyColumn(
        modifier = Modifier.fillMaxWidth().height(rowHeight * 3 + 6.dp),
        userScrollEnabled = false,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        itemsIndexed(state.rows, key = { index, row -> nearbyRankingItemKey(state.date, row, index) }) { _, row ->
            NearbyRankRow(
                row,
                modifier =
                    Modifier.height(rowHeight).animateItem(
                        fadeInSpec = if (reduced) null else tween(150),
                        placementSpec = if (reduced) null else tween(300, easing = easing),
                        fadeOutSpec = if (reduced) null else tween(150),
                    ),
                reducedMotion = reduced,
            )
        }
        if (state.participationPending) {
            item(key = "${state.date}:participation-pending") {
                PendingRankRow(
                    Modifier.height(rowHeight).animateItem(
                        fadeInSpec = if (reduced) null else tween(150),
                        placementSpec = if (reduced) null else tween(300, easing = easing),
                        fadeOutSpec = if (reduced) null else tween(150),
                    ),
                )
            }
        }
    }
}

@Composable
private fun PendingRankRow(modifier: Modifier) {
    val ink = Color(0xFF202323)
    val muted = Color(0xFF858B85)
    Row(
        modifier
            .fillMaxWidth()
            .background(Color(0xFFFFF9DD), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(26.dp).border(1.dp, muted, CircleShape), contentAlignment = Alignment.Center) {
            Text("−", color = muted, fontSize = 12.sp)
        }
        Text(
            stringResource(Res.string.press_nearby_my_place),
            Modifier.weight(1f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            stringResource(Res.string.press_nearby_start_hint),
            fontSize = 12.sp,
            color = muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
