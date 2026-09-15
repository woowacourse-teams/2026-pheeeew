package com.pheeeew.feature.map.guide

import com.pheeeew.feature.map.SighReleaseState
import com.pheeeew.feature.map.overlay.SighPhase

enum class FirstSighGuideStep {
    TapButton,
    Memo,
    Blow,
    SwipeUp,
    Hidden,
}

fun firstSighGuideStepFor(
    releaseState: SighReleaseState,
    phase: SighPhase,
): FirstSighGuideStep =
    when (releaseState) {
        SighReleaseState.Idle -> {
            FirstSighGuideStep.TapButton
        }

        is SighReleaseState.EditingMemo -> {
            FirstSighGuideStep.Memo
        }

        is SighReleaseState.AwaitingBreath -> {
            when (phase) {
                SighPhase.Quiet -> FirstSighGuideStep.SwipeUp
                SighPhase.Bursting -> FirstSighGuideStep.Hidden
                else -> FirstSighGuideStep.Blow
            }
        }

        is SighReleaseState.Submitting,
        is SighReleaseState.Error,
        -> {
            FirstSighGuideStep.Hidden
        }
    }

/**
 * A missing guide preference means one of two things:
 * - onboarding is also incomplete: a fresh install must run the guide;
 * - onboarding is complete: this is an existing install from before the guide shipped.
 */
fun resolveFirstSighGuideCompleted(
    hasCompletedOnboarding: Boolean,
    storedGuideCompletion: Boolean?,
): Boolean = storedGuideCompletion ?: hasCompletedOnboarding
