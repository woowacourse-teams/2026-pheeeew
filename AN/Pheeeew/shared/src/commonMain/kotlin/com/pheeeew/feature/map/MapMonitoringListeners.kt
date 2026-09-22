package com.pheeeew.feature.map

import com.pheeeew.core.audio.BreathInputError

interface BreathMonitoringListener {
    fun onMicrophonePermissionResult(granted: Boolean)

    fun onMicrophoneStartRequested()

    fun onMicrophoneReady()

    fun onSoundDetected(
        strength: Float,
        activeThreshold: Float,
    )

    fun onBreathSample(
        active: Boolean,
        elapsedMs: Long,
        growth: Float,
    )

    fun onMicrophoneStopped(
        finalGrowth: Float,
        reason: String,
        interrupted: Boolean,
    )

    fun onBaseSizeChanged()

    fun onReleaseReady(
        growth: Float,
        minimumReleaseProgress: Float,
    )

    fun onControlTapped(
        phase: String,
        growth: Float,
        inputActive: Boolean,
    )

    fun onSwipeAttempted(
        upwardDistanceDp: Float,
        upwardVelocityDpPerSecond: Float,
        growth: Float,
        success: Boolean,
        reason: String,
    )

    fun onGestureCancelled(reason: String)

    fun onReleaseAnimationFinished()

    fun onMicrophoneFailed(error: BreathInputError)
}

interface SaveMonitoringListener {
    fun onSaveUiResultShown(outcome: String)

    fun onSavedStarVisible()
}

interface MapMonitoringListener {
    fun onVisibleSighsChanged(ids: List<String>)

    fun onStarDetailShown()
}

object NoOpBreathMonitoringListener : BreathMonitoringListener {
    override fun onMicrophonePermissionResult(granted: Boolean) = Unit

    override fun onMicrophoneStartRequested() = Unit

    override fun onMicrophoneReady() = Unit

    override fun onSoundDetected(
        strength: Float,
        activeThreshold: Float,
    ) = Unit

    override fun onBreathSample(
        active: Boolean,
        elapsedMs: Long,
        growth: Float,
    ) = Unit

    override fun onMicrophoneStopped(
        finalGrowth: Float,
        reason: String,
        interrupted: Boolean,
    ) = Unit

    override fun onBaseSizeChanged() = Unit

    override fun onReleaseReady(
        growth: Float,
        minimumReleaseProgress: Float,
    ) = Unit

    override fun onControlTapped(
        phase: String,
        growth: Float,
        inputActive: Boolean,
    ) = Unit

    override fun onSwipeAttempted(
        upwardDistanceDp: Float,
        upwardVelocityDpPerSecond: Float,
        growth: Float,
        success: Boolean,
        reason: String,
    ) = Unit

    override fun onGestureCancelled(reason: String) = Unit

    override fun onReleaseAnimationFinished() = Unit

    override fun onMicrophoneFailed(error: BreathInputError) = Unit
}

object NoOpSaveMonitoringListener : SaveMonitoringListener {
    override fun onSaveUiResultShown(outcome: String) = Unit

    override fun onSavedStarVisible() = Unit
}

object NoOpMapMonitoringListener : MapMonitoringListener {
    override fun onVisibleSighsChanged(ids: List<String>) = Unit

    override fun onStarDetailShown() = Unit
}
