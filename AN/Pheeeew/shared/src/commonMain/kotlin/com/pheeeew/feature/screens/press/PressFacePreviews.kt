package com.pheeeew.feature.screens.press

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.emotion.component.EmotionPad
import com.pheeeew.feature.emotion.component.PressFaceCatalog
import com.pheeeew.feature.emotion.model.EmotionFaceStage
import com.pheeeew.feature.emotion.model.EmotionFaceVisual
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.painterResource

@Preview(name = "30단계 원본 카탈로그", widthDp = 960, heightDp = 1000)
@Composable
private fun PressFaceCatalogPreview() {
    AppTheme {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            EmotionKind.entries.forEach { kind ->
                Text(kind.name)
                Row {
                    EmotionFaceStage.progression.forEach { stage ->
                        Column {
                            Image(painterResource(PressFaceCatalog.resource(kind, stage)), null, Modifier.size(150.dp))
                            Text(stage.name)
                        }
                    }
                }
            }
        }
    }
}

@Preview(name = "균열 적용 프레스", widthDp = 402, heightDp = 876)
@Preview(name = "균열 적용 작은 프레스", widthDp = 320, heightDp = 640)
@Composable
private fun PressCrackScreenPreview() {
    AppTheme {
        PressScreen(
            PressUiState(isLoadingMy = false, isLoadingAll = false),
            { true },
            {},
            nearbyRanking = temporaryNearbyRanking,
            activity = temporaryPressActivity,
            faceVisuals = EmotionKind.entries.associateWith { EmotionFaceVisual(EmotionFaceStage.Fractured) },
        )
    }
}

/** Interactive Preview only: manual visual transitions, never a real press-count threshold. */
@Preview(name = "단계·폭발·복귀 조작", widthDp = 402, heightDp = 760)
@Composable
private fun PressFaceControlsPreview() {
    AppTheme {
        var selected by remember { mutableStateOf(EmotionKind.Angry) }
        var visuals by remember { mutableStateOf(emptyMap<EmotionKind, EmotionFaceVisual>()) }
        var playbackId by remember { mutableStateOf(0L) }

        fun show(stage: EmotionFaceStage) {
            playbackId++
            visuals = visuals + (selected to EmotionFaceVisual(stage, playbackId))
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("개발용 수동 조작 · 실제 점수와 무관")
            Row {
                EmotionKind.entries.forEach { kind ->
                    TextButton(onClick = { selected = kind }) { Text(kind.name.take(3)) }
                }
            }
            Row {
                TextButton(onClick = {
                    val stage = visuals[selected]?.stage ?: EmotionFaceStage.Intact
                    show(stage.next())
                }) { Text("다음 단계") }
                TextButton(onClick = { show(EmotionFaceStage.Burst) }) { Text("폭발 / 재생") }
                TextButton(onClick = { show(EmotionFaceStage.Intact) }) { Text("복귀") }
            }
            EmotionPad(counts = emptyList(), enabled = true, onEmotionTap = { true }, faceVisuals = visuals)
        }
    }
}
