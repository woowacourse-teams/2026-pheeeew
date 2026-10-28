package com.pheeeew.domain.repository

interface OnboardingStore {
    fun isCompleted(): Boolean

    fun complete()

    /** Reset viewing flags while preserving notification permission history and other local data. */
    fun resetCompletion()

    /** Persisted separately so dismissing the first notification prompt never repeats it on launch. */
    fun isInitialNotificationPermissionHandled(): Boolean = isCompleted()

    fun completeInitialNotificationPermission() = Unit

    /** Existing installs have already entered the app; new installs finish this after the slides. */
    fun isUsageGuideCompleted(): Boolean = isCompleted()

    fun completeUsageGuide() = Unit
}
