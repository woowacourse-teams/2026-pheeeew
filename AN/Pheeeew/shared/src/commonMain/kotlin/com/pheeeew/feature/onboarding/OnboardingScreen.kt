package com.pheeeew.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pheeeew.core.audio.rememberBreathInput
import com.pheeeew.core.designsystem.theme.AppColors
import com.pheeeew.core.designsystem.theme.AppTheme
import com.pheeeew.core.permission.LocationPermissionStatus
import com.pheeeew.core.permission.LocationServicesSettingsDialog
import com.pheeeew.core.permission.PermissionSettingsDialog
import com.pheeeew.core.permission.PermissionSettingsTarget
import com.pheeeew.feature.splash.TwinklingStars
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun OnboardingScreen(
    onRequestLocationPermission: suspend () -> LocationPermissionStatus,
    onOpenLocationSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { PAGE_COUNT })
    val coroutineScope = rememberCoroutineScope()
    val breathInput = rememberBreathInput()
    var isRequestingPermissions by remember { mutableStateOf(false) }
    var permissionResult by remember { mutableStateOf<OnboardingPermissionResult?>(null) }
    val page = pagerState.currentPage
    val indicatorPosition = pagerState.currentPage + pagerState.currentPageOffsetFraction

    Box(
        modifier = modifier.fillMaxSize().background(AppColors.Navy800),
    ) {
        TwinklingStars(
            modifier = Modifier.align(Alignment.TopCenter),
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            when (pageIndex) {
                0 -> Onboarding1()
                1 -> Onboarding2()
                else -> Onboarding3()
            }
        }

        BottomControls(
            page = page,
            indicatorPosition = indicatorPosition,
            onClick = {
                if (page == LAST_PAGE_INDEX) {
                    if (!isRequestingPermissions) {
                        isRequestingPermissions = true
                        coroutineScope.launch {
                            val result =
                                requestOnboardingPermissions(
                                    requestLocationPermission = onRequestLocationPermission,
                                    requestMicrophonePermission = { breathInput.requestPermission() },
                                )
                            if (result.allPermissionsGranted) {
                                onFinished()
                            } else {
                                permissionResult = result
                                isRequestingPermissions = false
                            }
                        }
                    }
                } else {
                    coroutineScope.launch { pagerState.animateScrollToPage(page + 1) }
                }
            },
            enabled = !isRequestingPermissions,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        permissionResult?.let { result ->
            val deniedPermissions = result.deniedPermissions
            when {
                deniedPermissions != null -> {
                    PermissionSettingsDialog(
                        target = deniedPermissions,
                        onOpenSettings = {
                            permissionResult = null
                            onOpenAppSettings()
                        },
                        onDismiss = {
                            permissionResult = null
                            onFinished()
                        },
                    )
                }

                result.locationStatus == LocationPermissionStatus.ServicesDisabled -> {
                    LocationServicesSettingsDialog(
                        onOpenSettings = {
                            permissionResult = null
                            onOpenLocationSettings()
                        },
                        onDismiss = {
                            permissionResult = null
                            onFinished()
                        },
                    )
                }

                else -> {
                    Unit
                }
            }
        }
    }
}

@Composable
internal fun OnboardingTitle(
    text: String,
    textAlign: TextAlign,
    fontSize: TextUnit = 22.sp,
    lineHeight: TextUnit = 38.sp,
    letterSpacing: TextUnit = 1.7.sp,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = AppColors.Cream100,
        fontFamily = AppTheme.typography.screenTitle.fontFamily,
        fontSize = fontSize,
        fontWeight = FontWeight.Normal,
        lineHeight = lineHeight,
        letterSpacing = letterSpacing,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
internal fun SupportingText(
    text: String,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = AppTheme.colors.onSurfaceVariant,
        fontFamily = FontFamily.Default,
        fontSize = 11.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 20.sp,
        textAlign = textAlign,
        modifier = modifier,
    )
}

@Composable
private fun BottomControls(
    page: Int,
    indicatorPosition: Float,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp),
    ) {
        PageIndicator(position = indicatorPosition)
        Spacer(Modifier.height(16.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(AppColors.Blue100, RoundedCornerShape(24.dp))
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    ),
        ) {
            Text(
                text = if (page == LAST_PAGE_INDEX) "시작하기" else "다음",
                color = AppColors.Navy800,
                style = AppTheme.typography.button,
                letterSpacing = 1.2.sp,
            )
        }
    }
}

@Composable
private fun PageIndicator(position: Float) {
    val selectedColor = AppColors.Tan200
    val unselectedColor = AppColors.Blue200.copy(alpha = 0.45f)

    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(PAGE_COUNT) { index ->
            val selectedFraction = (1f - abs(position - index)).coerceIn(0f, 1f)
            val indicatorWidth = 4.dp + (10.dp * selectedFraction)
            Box(
                modifier =
                    Modifier
                        .size(width = indicatorWidth, height = 4.dp)
                        .background(
                            color = lerp(unselectedColor, selectedColor, selectedFraction),
                            shape = CircleShape,
                        ),
            )
        }
    }
}

private const val PAGE_COUNT = 3
private const val LAST_PAGE_INDEX = PAGE_COUNT - 1

internal suspend fun requestOnboardingPermissions(
    requestLocationPermission: suspend () -> LocationPermissionStatus,
    requestMicrophonePermission: suspend () -> Boolean,
): OnboardingPermissionResult {
    val locationStatus = requestLocationPermissionSafely(requestLocationPermission)
    val microphoneGranted = requestMicrophonePermissionSafely(requestMicrophonePermission)
    return OnboardingPermissionResult(locationStatus, microphoneGranted)
}

internal data class OnboardingPermissionResult(
    val locationStatus: LocationPermissionStatus,
    val microphoneGranted: Boolean,
) {
    val allPermissionsGranted: Boolean
        get() = locationStatus == LocationPermissionStatus.Granted && microphoneGranted

    val deniedPermissions: PermissionSettingsTarget?
        get() {
            val locationDenied =
                locationStatus == LocationPermissionStatus.Denied ||
                    locationStatus == LocationPermissionStatus.PermanentlyDenied
            return when {
                locationDenied && !microphoneGranted -> PermissionSettingsTarget.LocationAndMicrophone
                locationDenied -> PermissionSettingsTarget.Location
                !microphoneGranted -> PermissionSettingsTarget.Microphone
                else -> null
            }
        }
}

private suspend fun requestLocationPermissionSafely(
    requestPermission: suspend () -> LocationPermissionStatus,
): LocationPermissionStatus =
    try {
        requestPermission()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        LocationPermissionStatus.Denied
    }

private suspend fun requestMicrophonePermissionSafely(requestPermission: suspend () -> Boolean): Boolean =
    try {
        requestPermission()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        false
    }

@Composable
internal fun OnboardingPagePreview(
    page: Int,
    content: @Composable () -> Unit,
) {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize().background(AppColors.Navy800)) {
            TwinklingStars(modifier = Modifier.align(Alignment.TopCenter))
            content()
            BottomControls(
                page = page,
                indicatorPosition = page.toFloat(),
                onClick = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Preview
@Composable
private fun OnboardingScreenPreview() {
    AppTheme {
        OnboardingScreen(
            onRequestLocationPermission = { LocationPermissionStatus.Granted },
            onOpenLocationSettings = {},
            onOpenAppSettings = {},
            onFinished = {},
        )
    }
}
