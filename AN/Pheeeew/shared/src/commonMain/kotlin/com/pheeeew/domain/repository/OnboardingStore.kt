package com.pheeeew.domain.repository

interface OnboardingStore {
    fun isCompleted(): Boolean

    fun complete()

    /** Reset viewing flags while preserving notification permission history and other local data. */
    fun resetCompletion()

    /** Persisted separately so dismissing the first notification prompt never repeats it on launch. */
    fun isInitialNotificationPermissionHandled(): Boolean = isCompleted()

    fun completeInitialNotificationPermission() = Unit
}
