plugins {
    id(ThunderbirdPlugins.Library.kmp)
}

kotlin {
    android {
        namespace = "net.thunderbird.feature.ai.internal"
    }
    sourceSets {
        commonMain.dependencies {
            implementation(projects.feature.ai.api)
            implementation(libs.koin.core)
        }
    }
}

codeCoverage {
    branchCoverage = 0
    lineCoverage = 0
}
