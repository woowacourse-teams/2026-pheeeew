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

// Only Debug may opt into proof testing. Release always generates required/prod settings.
val debugDeviceAttestationMode = providers.gradleProperty("deviceDebugAttestationMode").orElse("platform_only")
val deviceCloudProjectNumber = providers.gradleProperty("deviceCloudProjectNumber").orElse("87715710427")
val cloudProjectNumberLiteral = "${deviceCloudProjectNumber.get().toLongOrNull() ?: 0L}L"

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
        versionCode = 3
        versionName = "1.1.1"
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
            buildConfigField("String", "DEVICE_ENVIRONMENT", "\"dev\"")
            buildConfigField("String", "DEVICE_ATTESTATION_MODE", quotedConfig(debugDeviceAttestationMode.get()))
            buildConfigField("String", "MONITORING_ENVIRONMENT", "\"dev\"")
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api-dev.pheeeew.com\"",
            )
            buildConfigField("long", "DEVICE_CLOUD_PROJECT_NUMBER", cloudProjectNumberLiteral)
        }
        release {
            buildConfigField("String", "DEVICE_ENVIRONMENT", "\"prod\"")
            buildConfigField("String", "DEVICE_ATTESTATION_MODE", "\"required\"")
            buildConfigField("String", "MONITORING_ENVIRONMENT", "\"prod\"")
            isMinifyEnabled = false
            buildConfigField(
                "String",
                "API_BASE_URL",
                "\"https://api.pheeeew.com\"",
            )
            buildConfigField("long", "DEVICE_CLOUD_PROJECT_NUMBER", cloudProjectNumberLiteral)
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

// Inspect the actual variant fields used by BuildConfig, including flavor/plugin overrides.
androidComponents {
    onVariants(selector().all()) { variant ->
        val suffix = variant.name.replaceFirstChar { it.uppercaseChar() }
        val release = variant.buildType == "release"
        val fields = checkNotNull(variant.buildConfigFields) { "Device session requires BuildConfig" }
        val debuggable = android.buildTypes.getByName(checkNotNull(variant.buildType)).isDebuggable
        val validation =
            tasks.register("validate${suffix}DeviceSession") {
                inputs.property("release", release)
                inputs.property("debuggable", debuggable)
                inputs.property("applicationId", variant.applicationId)
                inputs.property("cloudProjectInput", deviceCloudProjectNumber)
                listOf(
                    "API_BASE_URL",
                    "DEVICE_ENVIRONMENT",
                    "DEVICE_ATTESTATION_MODE",
                    "DEVICE_CLOUD_PROJECT_NUMBER",
                ).forEach { key ->
                    inputs.property(key, fields.map { it[key]?.value?.toString() ?: "" })
                }
                doLast {
                    val settings = inputs.properties
                    val isRelease = settings["release"] as Boolean

                    fun field(key: String) = (settings[key] as String).removeSurrounding("\"")
                    val mode = field("DEVICE_ATTESTATION_MODE")
                    check(mode in setOf("platform_only", "required")) { "Unknown DEVICE_ATTESTATION_MODE" }
                    check(field("DEVICE_ENVIRONMENT") == if (isRelease) "prod" else "dev") {
                        "Device environment does not match build type"
                    }
                    val expectedUrl = if (isRelease) "https://api.pheeeew.com" else "https://api-dev.pheeeew.com"
                    check(field("API_BASE_URL") == expectedUrl) {
                        "Device API URL does not match build type"
                    }
                    if (isRelease) {
                        check(settings["debuggable"] == false) { "Release cannot be debuggable" }
                        check(
                            settings["applicationId"] == "com.pheeeew",
                        ) { "Release requires applicationId com.pheeeew" }
                        check(mode == "required") { "Release requires device attestation" }
                    }
                    if (mode == "required") {
                        val project = field("DEVICE_CLOUD_PROJECT_NUMBER").removeSuffix("L").toLongOrNull()
                        check(
                            project != null && project > 0,
                        ) { "A positive Play Integrity cloud project number is required" }
                        check((settings["cloudProjectInput"] as String).toLongOrNull() == project) {
                            "Invalid or inconsistent Play Integrity cloud project configuration"
                        }
                    }
                }
            }
        tasks
            .matching {
                it.name in
                    setOf(
                        "pre${suffix}Build",
                        "generate${suffix}BuildConfig",
                        "package$suffix",
                        "bundle$suffix",
                        "package${suffix}Bundle",
                    )
            }.configureEach { dependsOn(validation) }
    }
}
