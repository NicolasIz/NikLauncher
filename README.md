# NikLauncher

A Minecraft: Java Edition launcher for Android, built for the Samsung Galaxy S24 Ultra first.

NikLauncher is its own codebase and its own interface. It is not a reskin of an
existing launcher.

## Priorities

In this order, deliberately:

1. Stability
2. Temperature
3. Power draw
4. Performance
5. Peak frame rate

The goal is a phone that can play for hours without cooking itself, not a
benchmark number. Even the `Rendimiento` profile keeps a frame cap and thermal
backoff enabled.

## Module layout

| Module | What it is | Where it builds |
|---|---|---|
| `:core` | Pure Kotlin/JVM. Version resolution, manifest parsing, library and asset handling, the download engine, launch-argument construction, runtime abstractions. | Anywhere, no Android SDK needed |
| `:app` | Android app: Compose UI, storage, device detection. | Needs the Android SDK |

`:core` holds essentially all of the logic that is worth testing, and it is free
of Android dependencies on purpose - it runs under a plain JVM, so the launcher's
behaviour is verified by unit tests rather than by installing an APK and trying
things by hand.

`settings.gradle.kts` includes `:app` only when an Android SDK is present, so
`./gradlew :core:test` works on a machine that has none.

## How Minecraft actually runs on Android

Minecraft Java is a desktop program, and Android provides none of what it needs:

- **A real JVM.** ART is not Java SE. An OpenJDK built for Android arm64 is
  required. Android forbids executing binaries from an app's data directory, so
  the JVM is never launched as a `java` subprocess - `libjvm.so` is loaded with
  `dlopen()` and started through the JNI Invocation API, in-process.
- **OpenGL translation.** The game speaks desktop OpenGL; Android exposes only
  OpenGL ES and Vulkan. GL4ES covers 1.16 and older; Zink over Vulkan or LTW
  cover 1.17+.
- **A GLFW bridge.** LWJGL calls GLFW for its window, context and input. On
  Android that has to be an EGL surface plus synthetic input events.

These three are the only parts that cannot reasonably be written from scratch.
They are supplied as **runtime packs downloaded on first run**, behind
`NativeRuntimeProvider`, so the APK ships no third-party binaries and a pack can
be swapped without touching anything above that interface.

Minecraft itself is never redistributed: it is downloaded from Mojang's official
CDN with the user's own account, and sign-in uses Microsoft's official OAuth flow.

## Phases

| Phase | Scope | State |
|---|---|---|
| 1 | Build system, `:core` foundations, UI, download engine, CI producing an APK | Done |
| 2 | Version install, Java runtimes, launching Minecraft | Next |
| 3 | Touch controls, keyboard/mouse/gamepad, renderer selection | Planned |
| 4 | Thermal and performance management for the S24 Ultra | Planned |
| 5 | Fabric, Forge, NeoForge, modpacks | Planned |
| 6 | Microsoft sign-in, profiles, full settings | Planned |

## Building

```bash
./gradlew :core:test        # logic tests, no Android SDK required
./gradlew :app:assembleDebug # needs ANDROID_HOME
```

CI builds the APK on every push and uploads it as a workflow artifact.

## Target

- `arm64-v8a` only
- `minSdk` 31 (Android 12), `targetSdk` 35
- Distributed by sideload / GitHub Releases
