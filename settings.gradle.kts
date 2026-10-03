pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://artifactory.img.ly/artifactory/imgly")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://maven.aliyun.com/repository/public")
        maven(url = "https://maven.aliyun.com/repository/jcenter")
        maven(url = "https://jcenter.bintray.com")
        maven { setUrl("https://jitpack.io") }
        maven(url = "https://artifactory.img.ly/artifactory/imgly")
        mavenLocal()
    }
}

rootProject.name = "Simple-Gallery"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
include(":app")

// Simple-Commons is vendored into this repo (Simple-Commons/commons) and built from source via
// Gradle composite build. The app's `com.simplemobiletools.commons:release:5.34.26` dependency is
// substituted with the local project, so no mavenLocal publish is needed anymore.
includeBuild("Simple-Commons") {
    dependencySubstitution {
        substitute(module("com.simplemobiletools.commons:release")).using(project(":commons"))
    }
}