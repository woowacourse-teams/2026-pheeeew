package com.pheeeew.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object AppColors {
    val GroupInk = Color(0xFF202323)
    val GroupBackground = Color(0xFFFAFAFA)
    val RankingContent = Color(0xFF15181B)
    val RankingAccent = Color(0xFF9DE8D0)
    val RankingSurface = Color(0xFFF3F4F5)
    val RankingSecondaryContent = Color(0xFF808080)
    val Background = Color.White

    val Surface = Color(0xffFFFFFF)

    val Primary = Color(0xffFFE164)

    val Border = Color(0xFF000000)
    val BorderLight = Color(0xFFE8E8E8)

    val TextPrimary = Color(0xFF000000)
    val TextSecondary = Color(0xFF7D837A)

    val Gray50 = Color(0xFFF5F5F5)
    val Gray100 = Color(0xFFE5E5E5)
    val RecordSheetInputSurface = Color(0xFFECECEC)
    val RecordSheetAction = Color(0xFF202323)
    val RecordSheetRecording = Color(0xFFE26962)

    val Error = Color(0xFFE26962)

    val LightDivider = Color(0xFFE8EAEA)

    val CollectionCardBackground = Color(0xFFFBF7EC)

    val CollectionCardDivider = Color(0xFFD4CDBE)

    val White = Color.White
    val Black = Color.Black
    val Transparent = Color.Transparent
    val Gray = RankingSecondaryContent
    val LightGray = Color.LightGray
    val Red = Color.Red

    object Core {
        val SheetDragHandle = Color(0xFFD9D8D1)
        val ActionDivider = Color(0xFFD9D9D9)
        val ActionDanger = Color(0xFFFF1717)
        val SnackbarAccent = Color(0xFFFFE36E)
    }

    object Common {
        val MutedText = Color(0xFF777C78)
    }

    object Feedback {
        val ErrorInk = Color(0xFF252826)
        val SecondaryText = Color(0xFF70766F)
    }

    object Emotion {
        val Ink = Color(0xFF242725)
        val CountText = Common.MutedText
        val PromptInk = Color(0xFF292B2A)
        val ReactionSelected = Color(0xFFFFE987)
        val ReactionIdle = Color(0xFFF2F2F2)
        val ActionDanger = Core.ActionDanger

        object Mood {
            val Frustrated = Color(0xFFF6BBAA)
            val Irritated = Color(0xFFF0BFD0)
            val Exhausted = Color(0xFFB6A1DC)
            val Discouraged = Color(0xFFBBD9E9)
            val Angry = Color(0xFFEAA397)
        }

        object Feedback {
            val Blocked = Color(0xFFF2B6A3)
            val Annoyed = Color(0xFFE8B5C4)
            val Tired = Color(0xFFCDC7E4)
            val Defeated = Color(0xFFAFCBDD)
            val Angry = Color(0xFFEA9E92)
        }

        object TapPalette {
            val Frustrated = Color(0xFFF8D3C0)
            val Annoyed = Color(0xFFF3C7D6)
            val Tired = Color(0xFFE1D9F0)
            val Discouraged = Color(0xFFCCE5F2)
            val Angry = Color(0xFFF5BEB3)
        }

        object PressPalette {
            val All = Color(0xFFE6E4F4)
            val Frustrated = TapPalette.Frustrated
            val Annoyed = Color(0xFFF3D7DF)
            val Tired = Color(0xFFE2DFEE)
            val Discouraged = Color(0xFFD9E7F0)
            val Angry = Color(0xFFF2D1CB)
        }

        object Bubble {
            val Ripple = Color(0xA6F7FFFF)
        }
    }

    object Map {
        val Background = Color(0xFFECEAE5)
        val ControlTint = Color(0xFF2670F8)
        val AudioInactive = Color(0xFFA3ABA5)
        val NearbySheetBackground = Color(0xFFE4EBE4)
        val NearbyFeedbackSurface = Color(0xFFF3F0E8)
        val DetailDivider = Color(0xFFE7E9E5)
        val DetailPlaceholder = Color(0xFFCCCCCC)
        val RecordPreviewBackground = Color(0xFFC5E4B7)

        object Pin {
            val Pulse = Color(0xFF398CFF)
            val Danger = Color(0xFFFF3B30)
        }

        object Cluster {
            val LabelInk = Color(0xFF293540)
            val Halo = Color(0x2699A5D0)
            val FillStart = Color(0xB8DFF3FF)
            val Highlight = Color(0x4DFFFFFF)
            val FillEnd = Color(0xA6DFD3F5)
            val BadgeBackground = Color(0x99BC9CEB)
            val BadgeAccent = Color(0xFFFF625F)
        }

        object EmotionBubble {
            val Tint = Color(0xFF6A7778)
        }
    }

    object Press {
        val RankingMuted = Color(0xFF767D78)
        val NearbyMuted = Color(0xFF858B85)
        val SummaryText = Color(0xFF505650)
        val RunningCharacter = Color(0xFFB9BDB8)
        val SubtleSurface = Color(0xFFF0F1EB)
        val RankingCardBorder = Color(0xFFDFE3D9)
        val NearbyCardBorder = Color(0xFFE0E3DB)
        val NearbyCardSurface = Color(0xFFEFF0EB)
        val EmotionFilterSurface = Color(0xFFF0F0EC)
        val HighlightAccent = Color(0xFFFFE466)
        val MyRaceCardAccent = Color(0xFFFFE66B)
        val MyRaceRowSurface = Color(0xFFF2F3ED)
        val RaceProgress = Color(0xFFE0E4DA)
        val RaceHighlight = Color(0xFFFFE76A)
        val RaceTopThreeSurface = Color(0xFFF4F4F0)
        val DateAccent = Color(0xFFFFF1A2)
        val RankBadgeSurface = Color(0xFFFFF9DD)
        val EmptyRankingHighlight = Color(0xFFFFF1B8)
        val EmptyRankingSurface = Color(0xFFFFFAE8)
        val EmptyRankingBorder = Color(0xFFAAA58F)
        val EmptyRankingPillBorder = Color(0xFFC9C1A0)
        val MyRaceAccent = Color(0xFFF4D052)
        val MyRaceHighlightSurface = Color(0xFFFFFAF0)

        object LiveIndicator {
            val Active = Color(0xFF2DAB6E)
            val Inactive = Color(0xFF8A908A)
            val ActiveSurface = Color(0xFFCEE5D5)
            val InactiveSurface = Color(0xFFDFE2DC)
        }
    }

    object Onboarding {
        val Background = Color(0xFFFCFCFA)
        val PagerInactive = Color(0xFFD1D7C9)
        val DeviceSurface = Color(0xFFE7ECE3)
        val DeviceBorder = Color(0xFFD5DCD0)
        val CaptureSurface = Color(0xFFF9FBF7)
    }

    object SoapBubble {
        val AmbientGlow = Color(0x24EAFBFF)
        val RadialGlowStart = Color(0xACFFFFFF)
        val RadialGlowEnd = Color(0x48FFFFFF)
        val BlueGlow = Color(0x4495E3F0)
        val PinkGlow = Color(0x30FFB8E3)
        val OuterRim = Color(0x537699A1)
        val RimLight = Color(0x85EFFAFF)
        val PolarReflection = Color(0x99FFFFFF)
        val BlueReflection = Color(0x4DC0CEFF)
        val FaintReflection = Color(0x12FFFFFF)
        val SurfaceBase = Color(0xFFF9FFFF)
        val SurfaceSky = Color(0xFFA7D8F5)
        val SurfaceLavender = Color(0xFFD6CBFF)
        val SurfaceRose = Color(0xFFFFDDDD)
        val SurfacePeriwinkle = Color(0xFFD9D9FF)
        val SurfaceOcean = Color(0xFFA5DDEF)
        val SurfaceMint = Color(0xFFE4FFDD)
        val SurfaceIce = Color(0xFFD8FBFF)
    }

    object Splash {
        val Background = Color(0xFFFFFDFD)
        val Title = Color(0xFF202522)
        val LightWashStart = Color(0x7095C9DD)
        val LightWashEnd = Color(0x40FFFFFF)
        val DeepWashStart = Color(0xB398BED2)
        val DeepWashEnd = Color(0xCCFFFFFF)
    }

    object Report {
        val Error = Color(0xFFE5484D)
        val SelectedSurface = Color(0xFFFFF8DA)
    }

    object Settings {
        val IconInk = Color(0xFF252A2C)
        val IconSurface = Color(0xFFF3F4F4)
        val IconSecondary = Color(0xFF848B8C)
        val IconFooter = Color(0xFF9AA0A0)
        val IconError = Color(0xFFB3261E)
    }
}
