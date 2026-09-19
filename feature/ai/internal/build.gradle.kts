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
    }
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
