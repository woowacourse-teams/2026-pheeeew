import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    android {
        namespace = "com.pheeeew.developertools"
        compileSdk =
            libs.versions.android.compileSdk
                .get()
                .toInt()
        minSdk =
            libs.versions.android.minSdk
                .get()
                .toInt()
        compilerOptions { jvmTarget = JvmTarget.JVM_11 }
        withHostTest {}
    }
    // Debug replaces Shared with one static framework exporting the app library.
    // There is no Release framework in this module and no second Kotlin runtime.
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework(listOf(NativeBuildType.DEBUG)) {
            baseName = "Shared"
            isStatic = true
            export(project(":shared"))
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
        commonMain.dependencies {
            api(project(":shared"))
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.coroutines.core)
        }
    }
}
