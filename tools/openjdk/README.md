# OpenJDK 21 for Android arm64

`android-aarch64.patch` teaches OpenJDK 21 to build for `aarch64-linux-android`.

## Where it comes from

The OpenJDK Mobile Project already carries Android support upstream, and it is
small - 21 lines across 14 files, only two of them source. The reason it can be
that small is one line in `platform.m4`:

```
if test "x$OPENJDK_$1_OS" = xandroid; then
  HOTSPOT_$1_OS=linux
fi
```

HotSpot then builds its Linux sources against Bionic, and everything else is
accommodation around that.

## Why it is not just used as-is

Mobile has one branch, `master`, currently JDK 28. In that tree
`sun.misc.Unsafe`'s memory accessors carry `@Deprecated(since="23",
forRemoval=true)` - 103 deprecation annotations in one file. Minecraft, Fabric,
Forge and LWJGL's own `MemoryUtil` all reach for exactly those methods.

Shipping a development-tip JDK to run a game whose ecosystem targets 17 or 21
trades a working runtime for a newer one. This project's stated order is
stability first, so the support is ported back to 21u instead.

## What the port changes

| Area | Change |
| ---- | ------ |
| `platform.m4` | Recognise the OS, and map it to `HOTSPOT_OS=linux` |
| `flags-cflags.m4` | Target triple and OS defines; debug symbols as for gcc |
| `flags-other.m4` | Same target triple for the assembler |
| `toolchain.m4` | Skip the build-compiler version probe the NDK clang fails |
| `lib-freetype.m4` | Use the bundled freetype; there is no system one |
| `libraries.m4` | No X11, and no CUPS - Android has no printing system |
| `Modules.gmk`, `JdkNativeCompilation.gmk` | Take Java and native sources from the `linux` tree |
| `JvmFlags.gmk` | Put the linux and `linux_aarch64` include directories on the path |
| `java.desktop/*.gmk` | No AWT, no sound |
| `os_posix.cpp` | Bionic has no `CLK_TCK`; ask `sysconf` as Linux does |
| `net_util_md.h` | Bionic wants `netinet/in.h` ahead of `netdb.h` |

One deliberate difference from upstream. Mobile hardcodes
`-target aarch64-linux-android32`; the patch reads `ANDROID_API_LEVEL` instead,
defaulting to 32. The API level a runtime is built against decides the minimum
Android version a pack will run on, so it belongs in one variable rather than
buried as a literal in the build system.

## Ordering matters

In `platform.m4` the `*android*` case must come **before** `*linux*`. The target
triple `aarch64-linux-android` matches both patterns, and a `case` takes the
first match - put it second and the port silently does nothing.

## Checking the patch after an upstream bump

`git apply --check` in CI catches a patch that no longer fits. To validate the
autoconf changes themselves without a full build:

```sh
autoconf -I make/autoconf make/autoconf/configure.ac > /tmp/configure.sh
bash -n /tmp/configure.sh
grep -c android /tmp/configure.sh
```

That regenerates the configure script from the patched macros and syntax-checks
it, which is how the unbalanced `if` in `toolchain.m4` was caught before it
reached CI.
