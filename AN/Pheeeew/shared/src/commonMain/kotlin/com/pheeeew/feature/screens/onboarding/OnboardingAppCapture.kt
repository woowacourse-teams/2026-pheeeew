package com.pheeeew.feature.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.pheeeew.feature.component.AppBottomNavigationBar
import com.pheeeew.feature.component.AppDestination
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

/** Approved captures keep their app layout. The collection's old four-tab bar is covered by the real three-tab bar. */
@Composable
internal fun OnboardingAppCapture(
    screen: DrawableResource,
    description: String,
    modifier: Modifier = Modifier,
    collection: Boolean = false,
) {
    val aspectRatio = 1080f / if (collection) 2130f else 2070f
    BoxWithConstraints(modifier.aspectRatio(aspectRatio)) {
        Image(painterResource(screen), description, Modifier.matchParentSize(), contentScale = ContentScale.Fit)
        if (collection) {
            val scale = maxWidth / 390.dp
            val density = LocalDensity.current
            Box(
                Modifier
                    .align(
                        Alignment.BottomCenter,
                    ).fillMaxWidth()
                    .height(maxHeight * 0.105f)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalDensity provides Density(density.density * scale, 1f)) {
                    AppBottomNavigationBar(
                        selectedDestination = AppDestination.Collection,
                        onDestinationSelected = {},
                        enabled = false,
                        modifier = Modifier.consumeWindowInsets(WindowInsets.navigationBars),
                    )
                }
            }
        }
    }
}
