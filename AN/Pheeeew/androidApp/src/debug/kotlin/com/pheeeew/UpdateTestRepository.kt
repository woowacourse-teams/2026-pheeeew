package com.pheeeew

import android.content.Context
import com.pheeeew.domain.model.version.AppVersionPolicy
import com.pheeeew.domain.repository.AppVersionRepository

/** Overrides never flow into the real policy cache. Only the isolated test package installs this wrapper. */
internal class UpdateTestRepository(
    context: Context,
    private val remote: AppVersionRepository,
) : AppVersionRepository {
    private val preferences = context.getSharedPreferences("update_test", Context.MODE_PRIVATE)

    override suspend fun getPolicy(): AppVersionPolicy =
        when (preferences.getString("policy", "server")) {
            "current" -> policy("2.5.0", "2.5.0")
            "suggested" -> policy("2.5.0", "2.6.0")
            "required" -> policy("2.6.0", "2.6.0")
            "offline" -> error("Test policy lookup failure")
            else -> remote.getPolicy()
        }

    override fun cachedPolicy(): AppVersionPolicy? =
        if (preferences.getString("policy", "server") == "server") remote.cachedPolicy() else null

    private fun policy(
        minimum: String,
        latest: String,
    ) = AppVersionPolicy(
        minimum,
        latest,
        "https://play.google.com/store/apps/details?id=${BuildConfig.APPLICATION_ID}",
    )
}
