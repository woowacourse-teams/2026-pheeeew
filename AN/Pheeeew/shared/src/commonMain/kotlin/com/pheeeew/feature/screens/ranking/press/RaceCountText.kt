package com.pheeeew.feature.screens.ranking.press

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.press_race_count_eok
import pheeeew.shared.generated.resources.press_race_count_exact
import pheeeew.shared.generated.resources.press_race_count_gyeong
import pheeeew.shared.generated.resources.press_race_count_jo
import pheeeew.shared.generated.resources.press_race_count_man

@Composable
internal fun raceCountText(count: Long): String {
    val compact = compactRaceCount(count)
    val resource =
        when (compact.unit) {
            RaceCountUnit.Exact -> Res.string.press_race_count_exact
            RaceCountUnit.Man -> Res.string.press_race_count_man
            RaceCountUnit.Eok -> Res.string.press_race_count_eok
            RaceCountUnit.Jo -> Res.string.press_race_count_jo
            RaceCountUnit.Gyeong -> Res.string.press_race_count_gyeong
        }
    return stringResource(resource, compact.number)
}
