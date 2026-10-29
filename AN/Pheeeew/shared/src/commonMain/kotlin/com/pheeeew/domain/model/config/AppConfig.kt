package com.pheeeew.domain.model.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.Instant

@Serializable
data class AppConfig(
    val dailyPressRankingEnabled: Boolean = true,
    val noticeMessage: String = "",
)

@Serializable
data class AppConfigSnapshot(
    val revision: Long,
    val dailyPressRankingEnabled: Boolean,
    val noticeMessage: String,
    val updatedAt: String,
) {
    val config: AppConfig get() = AppConfig(dailyPressRankingEnabled, noticeMessage)

    fun validated(): AppConfigSnapshot =
        apply {
            require(revision > 0)
            // Unicode code points; Android and iOS use the same common implementation.
            require(noticeMessage.count { !it.isLowSurrogate() } <= 200)
            require(updatedAt.endsWith("Z"))
            Instant.parse(updatedAt)
        }
}

enum class AppConfigSource { DEFAULT, CACHE, SERVER, OVERRIDE }

data class AppConfigState(
    val initialized: Boolean = false,
    val snapshot: AppConfigSnapshot? = null,
    val normalSource: AppConfigSource = AppConfigSource.DEFAULT,
    val override: AppConfig? = null,
    val refreshing: Boolean = false,
    val lastFetchSucceededAt: String? = null,
    val lastAppliedAt: String? = null,
    val error: String? = null,
    val storageError: Boolean = false,
) {
    val config: AppConfig get() = override ?: snapshot?.config ?: AppConfig()
    val source: AppConfigSource get() = if (override != null) AppConfigSource.OVERRIDE else normalSource
}

/** JSON decoders may coerce quoted booleans; enforce the public wire types explicitly. */
fun JsonObject.toAppConfigSnapshot(): AppConfigSnapshot {
    fun field(
        name: String,
        string: Boolean,
    ): JsonPrimitive {
        val value = get(name) as? JsonPrimitive ?: error("Missing config field")
        require(value.isString == string)
        return value
    }
    return AppConfigSnapshot(
        revision = field("revision", false).content.toLong(),
        dailyPressRankingEnabled =
            when (field("dailyPressRankingEnabled", false).content) {
                "true" -> true
                "false" -> false
                else -> error("Invalid config boolean")
            },
        noticeMessage = field("noticeMessage", true).content,
        updatedAt = field("updatedAt", true).content,
    ).validated()
}
