package com.pheeeew.domain.repository

interface OnboardingStore {
    fun isCompleted(): Boolean

    fun complete()

    fun isFeatureGuideCompleted(guideId: String): Boolean

    fun completeFeatureGuide(guideId: String)

    /** Reset feature-guide viewing flags while preserving onboarding completion. */
    fun resetFeatureGuideHistory()

    /** Reset onboarding and feature-guide viewing flags while preserving other local data. */
    fun resetCompletion()
}
