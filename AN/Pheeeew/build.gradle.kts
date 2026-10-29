import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.TestExecutable

plugins {
    alias(libs.plugins.metro) apply false
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidMultiplatformLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.sentryAndroid) apply false
    alias(libs.plugins.googleServices) apply false
}

// Xcode's Swift Package dependency does not supply frameworks to standalone Gradle test executables.
// Keep the app's native SDK pin; prepare its verified simulator slice only when native tests link.
val iosTestFrameworkDirectory = layout.buildDirectory.dir("ios-test-frameworks")
val prepareIosTestFrameworks by tasks.registering(Exec::class) {
    group = "verification"
    description = "Prepare the app-pinned Sentry simulator framework for Kotlin/Native tests."
    val script = layout.projectDirectory.file("scripts/prepare-ios-test-frameworks.py")
    val appProject = layout.projectDirectory.file("iosApp/iosApp.xcodeproj/project.pbxproj")
    inputs.files(script, appProject)
    outputs.dir(iosTestFrameworkDirectory)
    commandLine(
        "python3",
        script.asFile.absolutePath,
        "--project",
        appProject.asFile.absolutePath,
        "--output",
        iosTestFrameworkDirectory.get().asFile.absolutePath,
    )
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions.configure<KotlinMultiplatformExtension> {
            targets.withType<KotlinNativeTarget>().configureEach {
                if (name == "iosSimulatorArm64") {
                    binaries.withType<TestExecutable>().configureEach {
                        val sentrySimulatorDirectory =
                            iosTestFrameworkDirectory.get().dir("Sentry.xcframework/ios-arm64_x86_64-simulator")
                        linkerOpts("-F${sentrySimulatorDirectory.asFile.absolutePath}")
                        linkTaskProvider.configure {
                            dependsOn(prepareIosTestFrameworks)
                            inputs.dir(iosTestFrameworkDirectory)
                        }
                    }
                }
            }
        }
    }

    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.8.0")
        filter {
            exclude { element -> element.file.path.contains("/build/") }
            exclude { element -> element.file.path.contains("/generated/") }
        }
    }

    tasks.withType<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>().configureEach {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

tasks.register("ktlintFormat") {
    dependsOn(subprojects.map { "${it.path}:ktlintFormat" })
}
