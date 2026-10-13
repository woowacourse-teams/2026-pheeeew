// Lucian UX/UI Prompt #011 · MIT License (c) 2026 Seokwoooo · https://lucianlabs.dev/uxui-prompt/#011
package com.pheeeew.feature.screens.group.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.component.raisedButtonBorder
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.navigation.FlowBackHandler
import com.pheeeew.feature.component.AppBottomNavigationBarOverlaySpace
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.group_home_actions_close
import pheeeew.shared.generated.resources.group_home_actions_open
import pheeeew.shared.generated.resources.group_home_create
import pheeeew.shared.generated.resources.group_home_join

@Composable
internal fun GroupHomeFloatingActions(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(
        if (expanded) 135f else 0f,
        tween(600, easing = CubicBezierEasing(.34f, 1.45f, .64f, 1f)),
    )
    val label =
        stringResource(if (expanded) Res.string.group_home_actions_close else Res.string.group_home_actions_open)
    FlowBackHandler(expanded) { expanded = false }
    Box(
        modifier.onPreviewKeyEvent {
            if (expanded && it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
                expanded = false
                true
            } else {
                false
            }
        },
    ) {
        if (expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(AppColors.GroupBackground.copy(alpha = .75f))
                    .clickable(
                        interactionSource =
                            remember {
                                MutableInteractionSource()
                            },
                        indication = null,
                        onClick = {
                            expanded =
                                false
                        },
                    ).semantics { contentDescription = label },
            )
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = AppBottomNavigationBarOverlaySpace),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val labels =
                listOf(stringResource(Res.string.group_home_create), stringResource(Res.string.group_home_join))
            val actions = listOf(onCreateClick, onJoinClick)
            labels.forEachIndexed { index, text ->
                AnimatedVisibility(
                    expanded,
                    enter =
                        fadeIn(tween(250, (1 - index) * 45)) +
                            slideInVertically(
                                tween(500, (1 - index) * 45, CubicBezierEasing(.34f, 1.45f, .64f, 1f)),
                            ) { it },
                    exit = fadeOut(tween(150)) + slideOutVertically(tween(250)) { it },
                ) {
                    val interaction = remember { MutableInteractionSource() }
                    val shape = RoundedCornerShape(16.dp)
                    Box(
                        Modifier
                            .height(48.dp)
                            .raisedButtonBorder(shape, interactionSource = interaction)
                            .clip(
                                shape,
                            ).background(
                                AppColors.Surface,
                            ).clickable(
                                interactionSource = interaction,
                                indication = null,
                                role = Role.Button,
                                onClick = {
                                    expanded = false
                                    actions[index]()
                                },
                            ).padding(horizontal = 20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, color = AppColors.GroupInk, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .size(56.dp)
                    .raisedButtonBorder(CircleShape, interactionSource = interaction)
                    .clip(
                        CircleShape,
                    ).background(
                        AppColors.Primary,
                    ).clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClick = {
                            expanded =
                                !expanded
                        },
                    ).semantics {
                        contentDescription = label
                        stateDescription = label
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+",
                    Modifier.graphicsLayer {
                        rotationZ = rotation
                    },
                    color = AppColors.GroupInk,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Normal,
                )
            }
        }
    }
}
