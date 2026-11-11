package com.pheeeew.core.designsystem.component

import androidx.compose.runtime.Composable

/** Renders system bar content for a dark screen while this is composed, then restores the previous style. */
@Composable
internal expect fun DarkSystemBarsEffect()
