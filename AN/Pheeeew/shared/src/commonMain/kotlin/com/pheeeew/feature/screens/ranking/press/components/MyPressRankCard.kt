package com.pheeeew.feature.screens.ranking.press.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressGroupRank
import com.pheeeew.feature.screens.ranking.press.samplePressGroupRanks
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_detail_rank_number
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated
import pheeeew.shared.generated.resources.press_all_emotions

@Composable
internal fun MyPressRankCard(
    group: PressGroupRank,
    emotion: PressEmotion,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val faceResource =
        when (emotion) {
            PressEmotion.All -> Res.drawable.press_all_emotions
            PressEmotion.Frustrated -> Res.drawable.ic_emotion_frustrated
            PressEmotion.Annoyed -> Res.drawable.ic_emotion_irritated
            PressEmotion.Tired -> Res.drawable.ic_emotion_exhausted
            PressEmotion.Discouraged -> Res.drawable.ic_emotion_discouraged
            PressEmotion.Angry -> Res.drawable.ic_emotion_angry
        }
    val cardColor =
        when (emotion) {
            PressEmotion.All -> Color(0xFFE6E4F4)
            PressEmotion.Frustrated -> Color(0xFFF8D3C0)
            PressEmotion.Annoyed -> Color(0xFFF3D7DF)
            PressEmotion.Tired -> Color(0xFFE2DFEE)
            PressEmotion.Discouraged -> Color(0xFFD9E7F0)
            PressEmotion.Angry -> Color(0xFFF2D1CB)
        }

    PressRankingCardSurface(
        // Keep the raised foreground inside the bounds used by the crossfade layer.
        modifier = modifier.padding(vertical = 2.dp),
        color = cardColor,
        shape =
            androidx.compose.foundation.shape
                .RoundedCornerShape(24.dp),
        onClick = onClick,
        restingOffset = (-1).dp,
        pressedOffset = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(faceResource),
                contentDescription = null,
                modifier = Modifier.size(78.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = group.groupName,
                    color = AppColors.RankingContent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.group_detail_rank_number, group.rank),
                    color = AppColors.RankingContent,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text =
                        if (emotion == PressEmotion.All) {
                            "${group.count}번의 마음"
                        } else {
                            "${group.count}번의 ${emotion.phrase} 마음"
                        },
                    color = AppColors.RankingContent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Preview(name = "내 프레스 랭킹")
@Composable
private fun MyPressRankCardPreview() {
    AppTheme {
        MyPressRankCard(samplePressGroupRanks[1], PressEmotion.Frustrated, Modifier.padding(20.dp))
    }
}
