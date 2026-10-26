package com.pheeeew.domain.repository

interface OnboardingStore {
    fun isCompleted(): Boolean

    fun complete()

    /** Existing installs have already entered the app; new installs finish this after the slides. */
    fun isUsageGuideCompleted(): Boolean = isCompleted()

    fun completeUsageGuide() = Unit
}
