package com.pheeeew.feature.emotion.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.notoSansKrFontFamily
import com.pheeeew.feature.emotion.model.EmotionKind
import org.jetbrains.compose.resources.painterResource

private val TapInk = Color(0xFF242725)

/** Draws the short-lived reactions independently from the input controls. */
@Composable
internal fun EmotionFeedbackLayer(
    particles: List<TapParticle<EmotionKind>>,
    frameTime: State<Double>,
    padPosition: State<Offset>,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val font = notoSansKrFontFamily()
    Box(modifier = modifier) {
        particles.forEach { particle ->
            key(particle.id) {
                val reaction = particle.reaction
                val emotion = TapCatalog.emotion(particle.key)
                // Alpha compositing clips to the layer bounds, so include the sticker shadow.
                val shadowPadding =
                    when (reaction.kind) {
                        TapReactionKind.Text, TapReactionKind.Plus -> 4.dp
                        else -> 0.dp
                    }
                Box(
                    modifier =
                        Modifier
                            .requiredSize(
                                particle.width.dp + shadowPadding * 2,
                                particle.height.dp + shadowPadding * 2,
                            ).graphicsLayer {
                                val transform = particle.flight.sample(frameTime.value - particle.started)
                                val left = particle.flight.left - padPosition.value.x - shadowPadding.value
                                val top = particle.flight.top - padPosition.value.y - shadowPadding.value
                                translationX = ((left + transform.x) * density.density).toFloat()
                                translationY = ((top + transform.y) * density.density).toFloat()
                                scaleX = transform.scale.toFloat()
                                scaleY = scaleX
                                rotationZ = transform.rotation.toFloat()
                                alpha = transform.alpha.toFloat()
                            }.padding(shadowPadding)
                            .testTag("tap-particle"),
                    contentAlignment = Alignment.Center,
                ) {
                    when (reaction.kind) {
                        TapReactionKind.Face -> {
                            Image(painterResource(emotion.face), null, Modifier.fillMaxSize())
                        }

                        TapReactionKind.Emoji -> {
                            BasicText(
                                reaction.value,
                                style = stickerStyle(reaction, font),
                            )
                        }

                        TapReactionKind.Text,
                        TapReactionKind.Plus,
                        -> {
                            Canvas(Modifier.fillMaxSize()) {
                                val d = density.density
                                val radius = CornerRadius(12 * d)
                                drawRoundRect(TapInk, Offset(1 * d, 4 * d), size, radius)
                                drawRoundRect(
                                    if (reaction.kind == TapReactionKind.Plus) Color(0xFFFFE36E) else emotion.color,
                                    cornerRadius = radius,
                                )
                                drawRoundRect(
                                    TapInk,
                                    Offset(d, d),
                                    Size(size.width - 2 * d, size.height - 2 * d),
                                    CornerRadius(11 * d),
                                    style = Stroke(2 * d),
                                )
                            }
                            BasicText(
                                text = reaction.value,
                                modifier =
                                    Modifier.offset(
                                        y =
                                            if (reaction.kind == TapReactionKind.Plus) {
                                                (-.5).dp
                                            } else {
                                                (-1).dp
                                            },
                                    ),
                                style = stickerStyle(reaction, font),
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun stickerStyle(
    reaction: TapReaction,
    font: FontFamily,
): TextStyle =
    if (reaction.kind == TapReactionKind.Emoji) {
        TextStyle(fontSize = 35.sp, lineHeight = 42.sp)
    } else {
        TextStyle(
            color = TapInk,
            fontFamily = font,
            fontWeight = FontWeight.Black,
            fontSize = 19.sp,
            lineHeight = 20.9.sp,
            letterSpacing = (-.6).sp,
        )
    }
