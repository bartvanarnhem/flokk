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
        mavenCentral()
        // Hosts the termux terminal-emulator/terminal-view modules, which are not on Maven Central.
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "flokk-android"
include(":app")
