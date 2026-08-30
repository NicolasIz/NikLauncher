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
        // Content filters keep pure-JVM dependencies off Google's Maven, so
        // :core resolves entirely from Maven Central.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "NikLauncher"

include(":core")

// :app needs the Android SDK. Including it unconditionally would break `:core`
// builds on machines without one, so we detect the SDK and skip the module when
// it is absent. CI always has it, so CI always builds the APK.
val androidSdkPresent: Boolean =
    sequenceOf("ANDROID_HOME", "ANDROID_SDK_ROOT").any { !System.getenv(it).isNullOrBlank() } ||
        file("local.properties").takeIf { it.exists() }
            ?.readLines()
            ?.any { it.trimStart().startsWith("sdk.dir") } == true

if (androidSdkPresent) {
    include(":app")
} else {
    logger.lifecycle("NikLauncher: no Android SDK detected - skipping :app; :core still builds.")
}
