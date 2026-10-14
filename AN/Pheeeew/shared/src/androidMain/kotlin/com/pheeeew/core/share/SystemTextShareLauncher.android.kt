package com.pheeeew.core.share

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemTextShareLauncher(): SystemTextShareLauncher {
    val context = LocalContext.current
    return remember(context) {
        SystemTextShareLauncher { text ->
            runCatching {
                val sendIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                    }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }.isSuccess
        }
    }
}
