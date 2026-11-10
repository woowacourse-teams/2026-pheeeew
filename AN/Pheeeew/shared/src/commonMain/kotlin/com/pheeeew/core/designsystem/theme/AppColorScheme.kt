package com.pheeeew.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color

/** Common app roles extend Material without changing its built-in defaults. */
val ColorScheme.appInk: Color get() = AppPalette.GroupInk
val ColorScheme.appPageBackground: Color get() = AppPalette.GroupBackground
val ColorScheme.appBackground: Color get() = AppPalette.Background
val ColorScheme.appSurface: Color get() = AppPalette.Surface
val ColorScheme.appOnAccent: Color get() = AppPalette.White
val ColorScheme.appScrim: Color get() = AppPalette.Black
val ColorScheme.appPrimary: Color get() = AppPalette.Primary
val ColorScheme.appBorder: Color get() = AppPalette.Border
val ColorScheme.appBorderLight: Color get() = AppPalette.BorderLight
val ColorScheme.appTextPrimary: Color get() = AppPalette.TextPrimary
val ColorScheme.appTextSecondary: Color get() = AppPalette.TextSecondary
val ColorScheme.appSurfaceSubtle: Color get() = AppPalette.Gray50
val ColorScheme.appSurfaceDisabled: Color get() = AppPalette.Gray100
val ColorScheme.appDividerSubtle: Color get() = AppPalette.Gray100
val ColorScheme.appMutedText: Color get() = AppPalette.Common.MutedText
val ColorScheme.appDisabledText: Color get() = AppPalette.Gray
val ColorScheme.appDisabledContent: Color get() = AppPalette.LightGray
val ColorScheme.appError: Color get() = AppPalette.Error
val ColorScheme.appDestructiveAction: Color get() = AppPalette.Red
val ColorScheme.appLightDivider: Color get() = AppPalette.LightDivider
