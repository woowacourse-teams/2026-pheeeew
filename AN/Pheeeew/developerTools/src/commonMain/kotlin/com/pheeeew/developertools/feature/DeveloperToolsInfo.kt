package com.pheeeew.developertools.feature

/** Supplied by the platform entry point using the active network configuration. */
data class DeveloperToolsInfo(
    val platform: String,
    val version: String,
    val buildNumber: String,
    val buildConfiguration: String,
    val serverEnvironment: String,
    val apiBaseUrl: String,
)
