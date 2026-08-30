// Plugins are declared per-module rather than in a root `plugins {}` block.
// A root block would force Gradle to resolve the Android Gradle Plugin from
// Google's Maven at configuration time, which makes it impossible to build or
// test :core on a machine without Android SDK access.
tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
