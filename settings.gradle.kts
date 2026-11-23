pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        // Robolectric's native-graphics runtime is a 160 MB jar that Maven Central often refuses
        // with HTTP 429 on shared CI runners. Google's mirror of Maven Central serves the same
        // artifacts; it is used for Robolectric's groups only, everything else comes from Central.
        maven("https://maven-central.storage-download.googleapis.com/maven2/") {
            name = "MavenCentralGoogleMirror"
            content { includeGroupByRegex("org\\.robolectric(\\..*)?") }
        }
        mavenCentral()
    }
}

rootProject.name = "TeeVClean"
include(":app")
