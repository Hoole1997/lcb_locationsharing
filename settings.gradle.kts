pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://jitpack.io")
        maven("https://artifact.bytedance.com/repository/pangle/")
        maven("https://repo.itextsupport.com/android")
        maven("https://repo.dgtverse.cn/repository/maven-public/")
        maven("https://dl-maven-android.mintegral.com/repository/mbridge_android_sdk_oversea")
        maven("https://android-sdk.is.com/")
        maven("https://jfrog.anythinktech.com/artifactory/overseas_sdk")
        maven("https://artifacts.applovin.com/android")
        maven("https://repo.dgtverse.cn/repository/maven-public")
    }
}

rootProject.name = "LCB_Template"
include(":app")
include(":bill")
include(":core")
include(":metrics")