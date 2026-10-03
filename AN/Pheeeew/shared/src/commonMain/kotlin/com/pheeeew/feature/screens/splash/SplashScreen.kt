package com.pheeeew.feature.screens.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.feature.component.SoapBubble
import com.pheeeew.feature.component.stamp.GroupStamp
import com.pheeeew.feature.component.stamp.StampAppearanceUiModel
import com.pheeeew.feature.component.stamp.StampShapeId
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import pheeeew.shared.generated.resources.Res
import pheeeew.shared.generated.resources.onboarding_brand
import pheeeew.shared.generated.resources.splash_accessibility
import pheeeew.shared.generated.resources.ic_emotion_angry
import pheeeew.shared.generated.resources.ic_emotion_discouraged
import pheeeew.shared.generated.resources.ic_emotion_exhausted
import pheeeew.shared.generated.resources.ic_emotion_frustrated
import pheeeew.shared.generated.resources.ic_emotion_irritated
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val SPLASH_DURATION_MILLIS = 2_000
private val SplashBackground = Color(0xFFFFFDFD)

/** Plays once, then holds the finished artwork while the startup checks finish. */
@Composable
internal fun SplashScreen(
    animationCompleted: Boolean,
    onAnimationCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(if (animationCompleted) 1f else 0f) }
    val currentOnCompleted = rememberUpdatedState(onAnimationCompleted)
    LaunchedEffect(Unit) {
        if (!animationCompleted) {
            progress.animateTo(1f, tween(SPLASH_DURATION_MILLIS, easing = LinearEasing))
            currentOnCompleted.value()
        }
    }
    SplashArtwork(progress = progress.value, modifier = modifier)
}

@Composable
private fun SplashArtwork(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val splashDescription = stringResource(Res.string.splash_accessibility)
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(SplashBackground)
                .clipToBounds()
                .clearAndSetSemantics { contentDescription = splashDescription },
        contentAlignment = Alignment.Center,
    ) {
        // Fit the reference composition uniformly, including on tablets and landscape screens.
        val scale = minOf(maxWidth.value / 352f, maxHeight.value / 772f)
        val fontScale = LocalDensity.current.fontScale
        val faces = remember { splashFaces() }
        val stampsProgress = segment(progress, 0.04f, 0.4f)
        val bubbleProgress = segment(progress, 0.43f, 0.63f)
        val titleProgress = segment(progress, 0.62f, 0.82f)

        Box(Modifier.size((352f * scale).dp, (772f * scale).dp)) {
            SplashConfetti(progress, Modifier.fillMaxSize())
            splashStamps.forEachIndexed { index, stamp ->
                GroupStamp(
                    appearance = stamp.appearance,
                    size = (stamp.size * scale).dp,
                    modifier =
                        Modifier
                            .offset(
                                x = ((stamp.x - stamp.size / 2f) * scale).dp,
                                y = ((stamp.y - stamp.size / 2f) * scale).dp,
                            ).graphicsLayer {
                                val reveal = segment(progress, 0.04f + index * 0.007f, 0.19f + index * 0.007f)
                                alpha = reveal
                                // Stamps burst from the center and settle at their reference positions.
                                translationX = ((176f - stamp.x) * (1f - stampsProgress) * scale).dp.toPx()
                                translationY = ((386f - stamp.y) * (1f - stampsProgress) * scale).dp.toPx()
                                rotationZ = stamp.rotation + (1f - stampsProgress) * if (index % 2 == 0) 55f else -55f
                                scaleX = 0.55f + 0.45f * reveal + 0.12f * sin(stampsProgress * PI).toFloat()
                                scaleY = scaleX
                            },
                )
            }

            Box(
                modifier =
                    Modifier
                        .offset((90f * scale).dp, (338f * scale).dp)
                        .size((174f * scale).dp)
                        .graphicsLayer {
                            alpha = bubbleProgress
                            val pop = 1f + 0.08f * sin(bubbleProgress * PI).toFloat()
                            scaleX = (0.45f + 0.55f * bubbleProgress) * pop
                            scaleY = scaleX
                            translationY = ((1f - bubbleProgress) * 22f * scale).dp.toPx()
                        },
            ) {
                SoapBubble(Modifier.fillMaxSize())
                BubbleReflections(Modifier.fillMaxSize())
                faces.forEachIndexed { index, face ->
                    Image(
                        painter = painterResource(face.icon),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .offset((face.x * scale).dp, (face.y * scale).dp)
                                .size((62f * scale).dp)
                                .graphicsLayer {
                                    val settle = segment(progress, 0.47f + index * 0.018f, 0.77f + index * 0.018f)
                                    translationY = ((1f - settle) * -18f * scale).dp.toPx()
                                    rotationZ = face.rotation + (1f - settle) * if (index % 2 == 0) 18f else -18f
                                },
                    )
                }
            }

            Box(
                modifier =
                    Modifier
                        .offset((86f * scale).dp, (233f * scale).dp)
                        .size((180f * scale).dp, (88f * scale).dp)
                        .graphicsLayer {
                            alpha = titleProgress
                            scaleX = 0.86f + 0.14f * titleProgress
                            scaleY = scaleX
                            translationY = ((1f - titleProgress) * 10f * scale).dp.toPx()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(Res.string.onboarding_brand),
                    color = Color(0xFF202522),
                    fontWeight = FontWeight.Bold,
                    fontSize = (64f * scale / fontScale).sp,
                    lineHeight = (80f * scale / fontScale).sp,
                    letterSpacing = (-2f * scale / fontScale).sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Extra broad reflections make the shared bubble legible at the larger splash size. */
@Composable
private fun BubbleReflections(modifier: Modifier) {
    val leftReflection =
        remember {
            Path().apply {
                moveTo(12f, 102f)
                cubicTo(3f, 67f, 26f, 25f, 51f, 15f)
                cubicTo(64f, 9f, 56f, 21f, 45f, 29f)
                cubicTo(26f, 46f, 17f, 74f, 12f, 102f)
                close()
            }
        }
    Canvas(modifier) {
        val artworkScale = size.width / 174f
        scale(artworkScale, artworkScale, Offset.Zero) {
            drawArc(
                brush =
                    Brush.linearGradient(
                        listOf(Color(0x7095C9DD), Color(0x40FFFFFF)),
                        start = Offset(0f, 130f),
                        end = Offset(100f, 0f),
                    ),
                startAngle = 130f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(4f, 4f),
                size = Size(166f, 166f),
                style = Stroke(5f),
            )
            drawPath(
                path = leftReflection,
                brush =
                    Brush.linearGradient(
                        listOf(Color(0xB398BED2), Color(0xCCFFFFFF)),
                        start = Offset(25f, 22f),
                        end = Offset(12f, 100f),
                    ),
            )
            drawArc(
                color = Color.White.copy(alpha = 0.9f),
                startAngle = 166f,
                sweepAngle = 79f,
                useCenter = false,
                topLeft = Offset(15f, 12f),
                size = Size(145f, 147f),
                style = Stroke(4f),
            )
            drawArc(
                color = Color.White.copy(alpha = 0.8f),
                startAngle = -23f,
                sweepAngle = 80f,
                useCenter = false,
                topLeft = Offset(10f, 10f),
                size = Size(153f, 153f),
                style = Stroke(6f),
            )
        }
    }
}

@Composable
private fun SplashConfetti(
    progress: Float,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val reveal = segment(progress, 0.07f, 0.24f)
        val opacity = reveal * (1f - segment(progress, 0.24f, 0.4f))
        if (opacity <= 0f) return@Canvas
        val scale = size.width / 352f
        repeat(24) { index ->
            val angle = index * PI * 2.0 / 24.0
            val radius = (35f + reveal * 100f + index % 3 * 8f) * scale
            drawCircle(
                color = Color(splashStamps[index % splashStamps.size].appearance.fillArgb.toInt()),
                radius = (if (index % 3 == 0) 2.3f else 1.5f) * scale,
                center =
                    Offset(
                        x = size.width / 2f + cos(angle).toFloat() * radius,
                        y = size.height / 2f + sin(angle).toFloat() * radius,
                    ),
                alpha = opacity * 0.7f,
            )
        }
    }
}

private fun segment(
    progress: Float,
    start: Float,
    end: Float,
): Float = FastOutSlowInEasing.transform(((progress - start) / (end - start)).coerceIn(0f, 1f))

private data class SplashStamp(
    val x: Float,
    val y: Float,
    val size: Float,
    val rotation: Float,
    val appearance: StampAppearanceUiModel,
)

private fun stamp(
    shape: StampShapeId,
    color: Long,
    x: Float,
    y: Float,
    size: Float,
    rotation: Float = 0f,
) = SplashStamp(x, y, size, rotation, StampAppearanceUiModel("", shape, color, 0xFF252725L))

private val splashStamps =
    listOf(
        stamp(StampShapeId.FOUR_LEAF, 0xFFBDE39CL, 178f, 116f, 36f),
        stamp(StampShapeId.FOLDED_MEMO, 0xFFF8C29FL, 89f, 179f, 35f),
        stamp(StampShapeId.VERTICAL_MEMO, 0xFF7934F5L, 269f, 182f, 54f, 15f),
        stamp(StampShapeId.FLOWER, 0xFFF3B6CDL, 301f, 275f, 35f),
        stamp(StampShapeId.POSTAGE_STAMP, 0xFFF5B6C7L, 52f, 307f, 36f),
        stamp(StampShapeId.ROUNDED_RECTANGLE, 0xFFFF72E6L, 305f, 429f, 38f, 20f),
        stamp(StampShapeId.OVAL, 0xFFFFEC26L, 55f, 464f, 35f, 12f),
        stamp(StampShapeId.CIRCLE, 0xFF9CE8CFL, 104f, 579f, 35f),
        stamp(StampShapeId.TAG, 0xFFA5DFECL, 282f, 560f, 36f, 21f),
        stamp(StampShapeId.TICKET, 0xFF2469FFL, 203f, 631f, 35f, 8f),
    )

private data class SplashFace(
    val icon: DrawableResource,
    val x: Float,
    val y: Float,
    val rotation: Float = 0f,
)

private fun splashFaces() =
    listOf(
        SplashFace(Res.drawable.ic_emotion_angry, 56f, 22f),
        SplashFace(Res.drawable.ic_emotion_irritated, 19f, 53f, -8f),
        SplashFace(Res.drawable.ic_emotion_frustrated, 91f, 49f, 8f),
        SplashFace(Res.drawable.ic_emotion_exhausted, 76f, 92f, -5f),
        SplashFace(Res.drawable.ic_emotion_discouraged, 35f, 92f, 8f),
    )

@Preview(name = "히유 스플래시", widthDp = 352, heightDp = 772)
@Preview(name = "히유 스플래시 · 가로", widthDp = 780, heightDp = 360)
@Composable
private fun SplashScreenPreview() {
    AppTheme { SplashArtwork(progress = 1f) }
}
