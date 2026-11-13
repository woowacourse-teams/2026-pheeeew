package com.pheeeew.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.pheeeew.core.designsystem.theme.appColors

@Immutable
data class AppThemeColors(
    val ranking: Ranking = Ranking(),
    val recordSheet: RecordSheet = RecordSheet(),
    val core: Core = Core(),
    val feedback: Feedback = Feedback(),
    val emotion: Emotion = Emotion(),
    val map: Map = Map(),
    val press: Press = Press(),
    val onboarding: Onboarding = Onboarding(),
    val soapBubble: SoapBubble = SoapBubble(),
    val splash: Splash = Splash(),
    val report: Report = Report(),
    val settings: Settings = Settings(),
) {
    @Immutable
    data class Ranking(
        val content: Color = AppPalette.RankingContent,
        val accent: Color = AppPalette.RankingAccent,
        val surface: Color = AppPalette.RankingSurface,
        val secondaryContent: Color = AppPalette.RankingSecondaryContent,
    )

    @Immutable
    data class RecordSheet(
        val inputSurface: Color = AppPalette.RecordSheetInputSurface,
        val action: Color = AppPalette.RecordSheetAction,
        val recording: Color = AppPalette.RecordSheetRecording,
    )

    @Immutable
    data class Core(
        val sheetDragHandle: Color = AppPalette.Core.SheetDragHandle,
        val actionDivider: Color = AppPalette.Core.ActionDivider,
        val actionDanger: Color = AppPalette.Core.ActionDanger,
        val snackbarAccent: Color = AppPalette.Core.SnackbarAccent,
    )

    @Immutable
    data class Feedback(
        val errorInk: Color = AppPalette.Feedback.ErrorInk,
        val secondaryText: Color = AppPalette.Feedback.SecondaryText,
    )

    @Immutable
    data class Emotion(
        val ink: Color = AppPalette.Emotion.Ink,
        val countText: Color = AppPalette.Common.MutedText,
        val promptInk: Color = AppPalette.Emotion.PromptInk,
        val reactionSelected: Color = AppPalette.Emotion.ReactionSelected,
        val reactionIdle: Color = AppPalette.Emotion.ReactionIdle,
        val actionDanger: Color = AppPalette.Core.ActionDanger,
        val mood: Mood = Mood(),
        val feedback: EmotionFeedback = EmotionFeedback(),
        val tapPalette: TapPalette = TapPalette(),
        val pressPalette: PressPalette = PressPalette(),
        val bubble: EmotionBubble = EmotionBubble(),
    ) {
        @Immutable
        data class Mood(
            val frustrated: Color = AppPalette.Emotion.Mood.Frustrated,
            val irritated: Color = AppPalette.Emotion.Mood.Irritated,
            val exhausted: Color = AppPalette.Emotion.Mood.Exhausted,
            val discouraged: Color = AppPalette.Emotion.Mood.Discouraged,
            val angry: Color = AppPalette.Emotion.Mood.Angry,
        )

        @Immutable
        data class EmotionFeedback(
            val blocked: Color = AppPalette.Emotion.Feedback.Blocked,
            val annoyed: Color = AppPalette.Emotion.Feedback.Annoyed,
            val tired: Color = AppPalette.Emotion.Feedback.Tired,
            val defeated: Color = AppPalette.Emotion.Feedback.Defeated,
            val angry: Color = AppPalette.Emotion.Feedback.Angry,
        )

        @Immutable
        data class TapPalette(
            val frustrated: Color = AppPalette.Emotion.TapPalette.Frustrated,
            val annoyed: Color = AppPalette.Emotion.TapPalette.Annoyed,
            val tired: Color = AppPalette.Emotion.TapPalette.Tired,
            val discouraged: Color = AppPalette.Emotion.TapPalette.Discouraged,
            val angry: Color = AppPalette.Emotion.TapPalette.Angry,
        )

        @Immutable
        data class PressPalette(
            val all: Color = AppPalette.Emotion.PressPalette.All,
            val frustrated: Color = AppPalette.Emotion.TapPalette.Frustrated,
            val annoyed: Color = AppPalette.Emotion.PressPalette.Annoyed,
            val tired: Color = AppPalette.Emotion.PressPalette.Tired,
            val discouraged: Color = AppPalette.Emotion.PressPalette.Discouraged,
            val angry: Color = AppPalette.Emotion.PressPalette.Angry,
        )

        @Immutable
        data class EmotionBubble(
            val ripple: Color = AppPalette.Emotion.Bubble.Ripple,
        )
    }

    @Immutable
    data class Map(
        val background: Color = AppPalette.Map.Background,
        val controlTint: Color = AppPalette.Map.ControlTint,
        val audioInactive: Color = AppPalette.Map.AudioInactive,
        val nearbySheetBackground: Color = AppPalette.Map.NearbySheetBackground,
        val nearbyFeedbackSurface: Color = AppPalette.Map.NearbyFeedbackSurface,
        val detailDivider: Color = AppPalette.Map.DetailDivider,
        val detailPlaceholder: Color = AppPalette.Map.DetailPlaceholder,
        val recordPreviewBackground: Color = AppPalette.Map.RecordPreviewBackground,
        val pin: Pin = Pin(),
        val cluster: Cluster = Cluster(),
        val emotionBubble: MapEmotionBubble = MapEmotionBubble(),
    ) {
        @Immutable
        data class Pin(
            val pulse: Color = AppPalette.Map.Pin.Pulse,
            val danger: Color = AppPalette.Map.Pin.Danger,
        )

        @Immutable
        data class Cluster(
            val labelInk: Color = AppPalette.Map.Cluster.LabelInk,
            val halo: Color = AppPalette.Map.Cluster.Halo,
            val fillStart: Color = AppPalette.Map.Cluster.FillStart,
            val highlight: Color = AppPalette.Map.Cluster.Highlight,
            val fillEnd: Color = AppPalette.Map.Cluster.FillEnd,
            val badgeBackground: Color = AppPalette.Map.Cluster.BadgeBackground,
            val badgeAccent: Color = AppPalette.Map.Cluster.BadgeAccent,
        )

        @Immutable
        data class MapEmotionBubble(
            val tint: Color = AppPalette.Map.EmotionBubble.Tint,
        )
    }

    @Immutable
    data class Press(
        val rankingMuted: Color = AppPalette.Press.RankingMuted,
        val nearbyMuted: Color = AppPalette.Press.NearbyMuted,
        val summaryText: Color = AppPalette.Press.SummaryText,
        val runningCharacter: Color = AppPalette.Press.RunningCharacter,
        val subtleSurface: Color = AppPalette.Press.SubtleSurface,
        val rankingCardBorder: Color = AppPalette.Press.RankingCardBorder,
        val nearbyCardBorder: Color = AppPalette.Press.NearbyCardBorder,
        val nearbyCardSurface: Color = AppPalette.Press.NearbyCardSurface,
        val emotionFilterSurface: Color = AppPalette.Press.EmotionFilterSurface,
        val highlightAccent: Color = AppPalette.Press.HighlightAccent,
        val myRaceCardAccent: Color = AppPalette.Press.MyRaceCardAccent,
        val myRaceRowSurface: Color = AppPalette.Press.MyRaceRowSurface,
        val raceProgress: Color = AppPalette.Press.RaceProgress,
        val raceHighlight: Color = AppPalette.Press.RaceHighlight,
        val raceTopThreeSurface: Color = AppPalette.Press.RaceTopThreeSurface,
        val dateAccent: Color = AppPalette.Press.DateAccent,
        val rankBadgeSurface: Color = AppPalette.Press.RankBadgeSurface,
        val emptyRankingHighlight: Color = AppPalette.Press.EmptyRankingHighlight,
        val emptyRankingSurface: Color = AppPalette.Press.EmptyRankingSurface,
        val emptyRankingBorder: Color = AppPalette.Press.EmptyRankingBorder,
        val emptyRankingPillBorder: Color = AppPalette.Press.EmptyRankingPillBorder,
        val myRaceAccent: Color = AppPalette.Press.MyRaceAccent,
        val myRaceHighlightSurface: Color = AppPalette.Press.MyRaceHighlightSurface,
        val liveIndicator: LiveIndicator = LiveIndicator(),
    ) {
        @Immutable
        data class LiveIndicator(
            val active: Color = AppPalette.Press.LiveIndicator.Active,
            val inactive: Color = AppPalette.Press.LiveIndicator.Inactive,
            val activeSurface: Color = AppPalette.Press.LiveIndicator.ActiveSurface,
            val inactiveSurface: Color = AppPalette.Press.LiveIndicator.InactiveSurface,
        )
    }

    @Immutable
    data class Onboarding(
        val background: Color = AppPalette.Onboarding.Background,
        val pagerInactive: Color = AppPalette.Onboarding.PagerInactive,
        val deviceSurface: Color = AppPalette.Onboarding.DeviceSurface,
        val deviceBorder: Color = AppPalette.Onboarding.DeviceBorder,
        val captureSurface: Color = AppPalette.Onboarding.CaptureSurface,
    )

    @Immutable
    data class SoapBubble(
        val ambientGlow: Color = AppPalette.SoapBubble.AmbientGlow,
        val radialGlowStart: Color = AppPalette.SoapBubble.RadialGlowStart,
        val radialGlowEnd: Color = AppPalette.SoapBubble.RadialGlowEnd,
        val blueGlow: Color = AppPalette.SoapBubble.BlueGlow,
        val pinkGlow: Color = AppPalette.SoapBubble.PinkGlow,
        val outerRim: Color = AppPalette.SoapBubble.OuterRim,
        val rimLight: Color = AppPalette.SoapBubble.RimLight,
        val polarReflection: Color = AppPalette.SoapBubble.PolarReflection,
        val blueReflection: Color = AppPalette.SoapBubble.BlueReflection,
        val faintReflection: Color = AppPalette.SoapBubble.FaintReflection,
        val surfaceBase: Color = AppPalette.SoapBubble.SurfaceBase,
        val surfaceSky: Color = AppPalette.SoapBubble.SurfaceSky,
        val surfaceLavender: Color = AppPalette.SoapBubble.SurfaceLavender,
        val surfaceRose: Color = AppPalette.SoapBubble.SurfaceRose,
        val surfacePeriwinkle: Color = AppPalette.SoapBubble.SurfacePeriwinkle,
        val surfaceOcean: Color = AppPalette.SoapBubble.SurfaceOcean,
        val surfaceMint: Color = AppPalette.SoapBubble.SurfaceMint,
        val surfaceIce: Color = AppPalette.SoapBubble.SurfaceIce,
    )

    @Immutable
    data class Splash(
        val background: Color = AppPalette.Splash.Background,
        val title: Color = AppPalette.Splash.Title,
        val lightWashStart: Color = AppPalette.Splash.LightWashStart,
        val lightWashEnd: Color = AppPalette.Splash.LightWashEnd,
        val deepWashStart: Color = AppPalette.Splash.DeepWashStart,
        val deepWashEnd: Color = AppPalette.Splash.DeepWashEnd,
    )

    @Immutable
    data class Report(
        val error: Color = AppPalette.Report.Error,
        val selectedSurface: Color = AppPalette.Report.SelectedSurface,
    )

    @Immutable
    data class Settings(
        val iconInk: Color = AppPalette.Settings.IconInk,
        val iconSurface: Color = AppPalette.Settings.IconSurface,
        val iconSecondary: Color = AppPalette.Settings.IconSecondary,
        val iconFooter: Color = AppPalette.Settings.IconFooter,
        val iconError: Color = AppPalette.Settings.IconError,
    )
}

private val LocalAppColors = staticCompositionLocalOf { AppThemeColors() }

val MaterialTheme.appColors: AppThemeColors
    @Composable
    @ReadOnlyComposable
    get() = LocalAppColors.current

@Composable
internal fun ProvideAppColors(content: @Composable () -> Unit) {
    val appColors = remember { AppThemeColors() }
    CompositionLocalProvider(LocalAppColors provides appColors, content = content)
}
