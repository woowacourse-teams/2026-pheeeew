package com.pheeeew.feature.screens.settings

import com.pheeeew.core.update.AppUpdateState
import com.pheeeew.domain.model.version.AppVersionDecision

internal fun shouldShowOptionalAppUpdatePrompt(
    suggestedUpdate: AppVersionDecision.UpdateSuggested?,
    suggestionDismissed: Boolean,
    updateState: AppUpdateState,
    installationDeferred: Boolean,
    installationAttempted: Boolean,
): Boolean =
    (suggestedUpdate != null && !suggestionDismissed) ||
        (updateState == AppUpdateState.DOWNLOADED && !installationDeferred) ||
        (installationAttempted && updateState != AppUpdateState.IDLE)
