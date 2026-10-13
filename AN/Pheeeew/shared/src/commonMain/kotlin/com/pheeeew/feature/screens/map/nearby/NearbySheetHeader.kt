package com.pheeeew.feature.screens.map.nearby

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.map_refresh_nearby
import pheeeew.shared.generated.resources.map_refresh_nearby_action

@Composable
internal fun NearbySheetHeader(
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    val refreshDescription = stringResource(Res.string.map_refresh_nearby)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "감정 목록",
            color = AppColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = refreshDescription }
                .clickable(enabled = !loading, role = Role.Button, onClick = onRefresh)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.map_refresh_nearby_action),
                color = AppColors.TextSecondary.copy(alpha = if (loading) 0.4f else 1f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}
