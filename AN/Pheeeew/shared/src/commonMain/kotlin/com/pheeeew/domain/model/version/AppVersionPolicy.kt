package com.pheeeew.domain.model.version

data class AppVersionPolicy(
    val minSupportedVersion: String,
    val latestVersion: String,
    val storeUrl: String,
)

sealed interface AppVersionDecision {
    data object Current : AppVersionDecision

    data class UpdateRequired(
        val storeUrl: String,
    ) : AppVersionDecision

    data class UpdateSuggested(
        val storeUrl: String,
    ) : AppVersionDecision
}

fun evaluateAppVersion(
    installedVersion: String,
    policy: AppVersionPolicy,
): AppVersionDecision {
    val installed = parseVersion(installedVersion)
    val minimum = parseVersion(policy.minSupportedVersion)
    val latest = parseVersion(policy.latestVersion)
    require(compareVersions(minimum, latest) <= 0) { "앱 버전 정책의 순서가 올바르지 않습니다." }

    return when {
        compareVersions(installed, minimum) < 0 -> AppVersionDecision.UpdateRequired(validStoreUrl(policy.storeUrl))
        compareVersions(installed, latest) < 0 -> AppVersionDecision.UpdateSuggested(validStoreUrl(policy.storeUrl))
        else -> AppVersionDecision.Current
    }
}

private fun parseVersion(value: String): List<String> {
    val parts = value.split('.')
    require(
        parts.size == 3 &&
            parts.all { part ->
                part.isNotEmpty() && part.all { it in '0'..'9' } && (part.length == 1 || part[0] != '0')
            },
    ) { "앱 버전 형식이 올바르지 않습니다: $value" }
    return parts
}

private fun compareVersions(
    left: List<String>,
    right: List<String>,
): Int {
    left.zip(right).forEach { (a, b) ->
        val order = a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
        if (order != 0) return order
    }
    return 0
}

private fun validStoreUrl(url: String): String {
    require(url.startsWith("https://") && url.length > "https://".length) {
        "앱 스토어 URL이 올바르지 않습니다."
    }
    return url
}
