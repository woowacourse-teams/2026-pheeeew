package com.pheeeew.feature.screens.group.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppColors

@Composable
internal fun GroupListSkeleton(
    modifier: Modifier = Modifier,
    memberRows: Boolean = false,
) {
    Column(
        modifier.fillMaxWidth().semantics {
            contentDescription = "목록을 불러오는 중"
        },
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(3) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(if (memberRows) 64.dp else 96.dp)
                    .background(AppColors.Surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(
                            if (memberRows) 32.dp else 44.dp,
                        ).background(AppColors.BorderLight, RoundedCornerShape(10.dp)),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                .7f,
                            ).height(13.dp)
                            .background(AppColors.BorderLight, RoundedCornerShape(4.dp)),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth(
                                .4f,
                            ).height(10.dp)
                            .background(AppColors.BorderLight, RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}
