plugins {
    alias(libs.plugins.androidTest)
}

android {
    namespace = "com.pheeeew.groupdetailbenchmark"
    compileSdk =
        libs.versions.android.compileSdk
            .get()
            .toInt()

    defaultConfig {
        minSdk = 28
        targetProjectPath = ":groupDetailBenchmarkTarget"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            matchingFallbacks += listOf("debug")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro)
    implementation(libs.androidx.testExt.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
}
