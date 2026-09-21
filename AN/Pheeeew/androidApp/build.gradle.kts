import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.net.URI
import java.util.Properties

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))
    implementation("androidx.lifecycle:lifecycle-process:2.10.0")

    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.security.crypto)

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

val monitoringProperties =
    Properties().apply {
        val local = rootProject.file("monitoring.local.properties")
        if (local.exists()) local.inputStream().use { load(it) }
    }

fun monitoringValue(key: String): String =
    providers.environmentVariable(key).orNull ?: monitoringProperties.getProperty(key, "").trim()

fun quotedConfig(value: String): String =
    "\"" +
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n") + "\""

android {
    namespace = "com.pheeeew"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        applicationId = "com.pheeeew"
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        targetSdk =
            libs.versions.android.targetSdk
                .get()
                .toInt()
        listOf("POSTHOG_PROJECT_TOKEN", "POSTHOG_HOST", "SENTRY_DSN").forEach { key ->
            buildConfigField("String", key, quotedConfig(monitoringValue(key)))
        }
        buildConfigField("boolean", "MONITORING_ENABLED", (monitoringValue("MONITORING_ENABLED") == "true").toString())
        versionCode = 2
        versionName = "1.1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildTypes {
        debug {
            buildConfigField("String", "MONITORING_ENVIRONMENT", "\"dev\"")
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api-dev.pheeeew.com\"",
            )
            buildConfigField("long", "DEVICE_CLOUD_PROJECT_NUMBER", "87715710427L")
        }
        release {
            buildConfigField("String", "MONITORING_ENVIRONMENT", "\"prod\"")
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api.pheeeew.com\"",
            )
            buildConfigField("long", "DEVICE_CLOUD_PROJECT_NUMBER", "87715710427L")
            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt",
                ),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Fail before packaging an enabled production build with missing collection settings.
val validateReleaseMonitoring by tasks.registering {
    val settings =
        listOf("POSTHOG_PROJECT_TOKEN", "POSTHOG_HOST", "SENTRY_DSN", "MONITORING_ENABLED")
            .associateWith(::monitoringValue)
    inputs.properties(settings)
    doLast {
        val values = inputs.properties
        val enabled = values["MONITORING_ENABLED"] as String
        check(enabled in setOf("true", "false")) { "Set MONITORING_ENABLED explicitly for release (true or false)." }
        if (enabled == "true") {
            listOf("POSTHOG_PROJECT_TOKEN", "POSTHOG_HOST", "SENTRY_DSN").forEach { key ->
                val value = values[key] as String
                check(
                    value.isNotBlank() &&
                        value.none {
                            it == '\n' || it == '\r'
                        },
                ) { "Missing or invalid release monitoring setting: $key" }
            }
            listOf("POSTHOG_HOST", "SENTRY_DSN").forEach { key ->
                val uri = runCatching { URI(values[key] as String) }.getOrNull()
                check(uri?.scheme == "https" && !uri.host.isNullOrBlank()) { "Invalid HTTPS monitoring endpoint: $key" }
            }
        }
    }
}
tasks.matching { it.name == "preReleaseBuild" || it.name == "generateReleaseBuildConfig" }.configureEach {
    dependsOn(validateReleaseMonitoring)
}
