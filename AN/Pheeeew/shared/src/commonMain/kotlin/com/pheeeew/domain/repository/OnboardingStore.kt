package com.pheeeew.domain.repository

interface OnboardingStore {
    fun isCompleted(): Boolean

    fun complete()

    /** Reset viewing flags while preserving notification permission history and other local data. */
    fun resetCompletion()
}
