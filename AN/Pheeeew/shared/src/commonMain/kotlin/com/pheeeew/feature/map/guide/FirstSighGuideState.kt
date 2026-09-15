package com.pheeeew.feature.map.guide

import com.pheeeew.feature.map.overlay.SighPhase

enum class FirstSighGuideStep {
    TapButton,
    Blow,
    SwipeUp,
    Hidden,
}

fun SighPhase.toFirstSighGuideStep(): FirstSighGuideStep =
    when (this) {
        SighPhase.Idle -> FirstSighGuideStep.TapButton

        SighPhase.Listening,
        SighPhase.NeedsMore,
        -> FirstSighGuideStep.Blow

        SighPhase.Quiet -> FirstSighGuideStep.SwipeUp

        SighPhase.Bursting -> FirstSighGuideStep.Hidden
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
