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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppShapes
import com.pheeeew.core.designsystem.theme.AppSpacing
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.designsystem.theme.appColors
import com.pheeeew.feature.screens.ranking.press.PressEmotion
import com.pheeeew.feature.screens.ranking.press.PressPersonRank
import com.pheeeew.feature.screens.ranking.press.samplePressPersonRanks
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
    person: PressPersonRank,
    emotion: PressEmotion,
    modifier: Modifier = Modifier,
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
            PressEmotion.All -> MaterialTheme.appColors.emotion.pressPalette.all
            PressEmotion.Frustrated -> MaterialTheme.appColors.emotion.tapPalette.frustrated
            PressEmotion.Annoyed -> MaterialTheme.appColors.emotion.pressPalette.annoyed
            PressEmotion.Tired -> MaterialTheme.appColors.emotion.pressPalette.tired
            PressEmotion.Discouraged -> MaterialTheme.appColors.emotion.pressPalette.discouraged
            PressEmotion.Angry -> MaterialTheme.appColors.emotion.pressPalette.angry
        }

    PressRankingCardSurface(
        // Keep the raised foreground inside the bounds used by the crossfade layer.
        modifier = modifier.padding(vertical = AppSpacing.xs.copy(value = 2.dp).dp),
        color = cardColor,
        shape =
            AppShapes.SectionCard,
        restingOffset = (-1).dp,
        pressedOffset = 2.dp,
    ) {
        Row(
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = AppSpacing.xl.dp,
                    vertical = AppSpacing.lg.copy(value = 18.dp).dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(faceResource),
                contentDescription = null,
                modifier = Modifier.size(78.dp),
            )
            Spacer(Modifier.width(AppSpacing.lg.dp))
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs.copy(value = 1.dp).dp)) {
                Text(
                    text = "내 순위 · ${person.nickname}",
                    color = MaterialTheme.appColors.ranking.content,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(Res.string.group_detail_rank_number, person.rank),
                    color = MaterialTheme.appColors.ranking.content,
                    fontSize = 36.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text =
                        if (emotion == PressEmotion.All) {
                            "${person.count}번의 마음"
                        } else {
                            "${person.count}번의 ${emotion.phrase} 마음"
                        },
                    color = MaterialTheme.appColors.ranking.content,
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
        MyPressRankCard(samplePressPersonRanks[1], PressEmotion.Frustrated, Modifier.padding(AppSpacing.xl.dp))
    }
}
