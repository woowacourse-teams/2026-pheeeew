package com.pheeeew.domain.repository

interface OnboardingStore {
    fun isCompleted(): Boolean

    fun complete()
}
