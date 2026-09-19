plugins {
    id(ThunderbirdPlugins.Library.kmp)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    android {
        namespace = "net.thunderbird.feature.ai.internal"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.configstore.api)
            implementation(projects.feature.ai.api)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        androidHostTest.dependencies {
            implementation(libs.androidx.test.core)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.robolectric)
        }
        androidMain.dependencies {
            implementation(libs.koin.android)
        }
    }
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
