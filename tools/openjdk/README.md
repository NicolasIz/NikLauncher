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
| `platform.m4` | Recognise the OS; map it to `HOTSPOT_OS=linux`, and to `linux` wherever the name becomes data |
| `flags-cflags.m4` | Target triple and OS defines; debug symbols as for gcc |
| `flags-other.m4` | Same target triple for the assembler |
| `toolchain.m4` | Skip the build-compiler version probe the NDK clang fails |
| `lib-freetype.m4` | Use the bundled freetype; there is no system one |
| `libraries.m4` | No X11, no fontconfig, no CUPS |

`libraries.m4` decides six dependencies in one block. Android needs three
turned off - X11, fontconfig and CUPS - and the other three already fall out
correctly: freetype is satisfied by the bundled copy the `lib-freetype.m4` hunk
selects, ALSA is gated on the target being exactly `xlinux` so android misses
it, and FFI is only wanted for the `zero` JVM variant.
| `spec.gmk.in` | Define `OPENJDK_TARGET_OS_VARIANT` once, for the whole build |
| `Modules.gmk`, `JdkNativeCompilation.gmk` | Take Java and native sources from the `linux` tree |
| `GensrcProperties.gmk` | Strip that extra source root back off when deriving a package |

`JdkNativeCompilation.gmk` has **two** lookups, `FindSrcDirsForLib` and
`FindSrcDirsForComponent`, and both need the linux tree. Patching only the
second builds a JDK that compiles and then fails at startup:

```
java.lang.UnsatisfiedLinkError: 'void sun.nio.ch.FileDispatcherImpl.init0()'
```

`FileDispatcherImpl.java`, which declares that native method, lives in
`linux/classes` and was picked up. `FileDispatcherImpl.c`, which implements it,
lives in `linux/native/libnio` and was not, because libraries are found through
`FindSrcDirsForLib`. The Java and native halves disagreed.
| `JvmFlags.gmk` | Put the linux and `linux_aarch64` include directories on the path |
| `java.desktop/*.gmk` | No AWT, no sound, and none of the X11 or CUPS sources |
| `rect.h` | Take the non-Xlib rectangle, as macOS does |
| `jdk-options.m4` | No serviceability agent, as on AIX and s390x |
| `JvmMapfile.gmk` | Take the linux symbol-dump branch |
| `GensrcAdlc.gmk` | Give ADLC the linux OS defines |
| `JvmOverrideFiles.gmk` | Large-file support and the clang PCH exclusions |
| `net_util_md.h` | Bionic wants `netinet/in.h` ahead of `netdb.h` |
| `elfFile.hpp` | Do not redefine `ELF_ST_TYPE` when the libc already defines it |
| `GensrcMisc.gmk` | Canonicalise the built-in OS name to `linux` |
| `os_linux.cpp` | Two Bionic gaps in diagnostic code paths |

## The JVM's OS defines are the linux ones, unchanged

An earlier version of this patch gave the JVM
`-DLINUX -D_ALLBSD_SOURCE -DANDROID`. `_ALLBSD_SOURCE` is the BSD and macOS
marker, and it does not belong on a Linux kernel. It reaches sixteen places in
HotSpot, and setting it here quietly did three wrong things:

- `elfFile.hpp` guards `#define ELF_ST_TYPE ELF64_ST_TYPE` with
  `!defined(_ALLBSD_SOURCE)`, so the define was suppressed and Bionic's own
  `ELF_ST_TYPE` was used instead. That was read as a Bionic gap and patched
  with a hand-written macro, which was dead code: it was guarded on
  `!defined(ELF_ST_TYPE)`, and Bionic had already defined it. Removing
  `_ALLBSD_SOURCE` let HotSpot's redefinition through and exposed a real
  conflict, described below.
- `os_posix.cpp` took the BSD `clock_tics_per_sec = CLK_TCK` branch instead of
  `sysconf(_SC_CLK_TCK)`. That too was patched around rather than traced.
- `arguments.cpp` guards `UseLargePages` on `!defined(_ALLBSD_SOURCE)`, so it
  was being turned off by accident rather than by decision.

The defines are now exactly upstream's linux pair:

```
CFLAGS_OS_DEF_JVM="-DLINUX -D_FILE_OFFSET_BITS=64"
```

and both workaround hunks are gone - the patch touches two source files now
rather than four. Nothing needs `-DANDROID` either: the NDK clang predefines
`__ANDROID__`, which is what the two remaining guards test, so they cannot go
quietly dead if a flag is ever dropped. `_GNU_SOURCE` is likewise predefined
for C++ on this target, which is what keeps `os.cpp` on the `tm_gmtoff`
timezone path - checked with `clang --target=aarch64-linux-android31 -x c++
-dM -E`, not assumed.

The JDK half still needs `-DLINUX` written out. Upstream appends
`-D$OPENJDK_TARGET_OS_UPPERCASE`, which gives `-DANDROID` here, and the JDK's
native sources are the linux ones and test `LINUX`.

## Debug symbols are off

`--with-native-debug-symbols` defaults to `external`, which compiles
everything with `-g` and then asks `NativeCompilation.gmk` for an objcopy
recipe it only knows how to write for linux, windows, aix and macosx. For
android no branch matches, `$1_DEBUGINFO_FILES` comes out empty, and the rule
below it degenerates to an empty target list - which GNU make accepts silently
as a no-op, so this was never going to fail, it was just going to build large
libraries with no symbols extracted from them. The pack is downloaded to a
phone, so `none` is both smaller and honest about what is happening.

## The target triple is not in the OS defines

Upstream puts `-target aarch64-linux-android32` inside `CFLAGS_OS_DEF_JVM` and
`CFLAGS_OS_DEF_JDK`. This patch does not, and that is deliberate.

Those variables are also applied when building the **buildjdk** - the JDK
compiled for the *host* so it can run build tools during a cross-compile. A
target triple there reaches the host compiler, which fails with:

```
g++: error: unrecognized command-line option '-target'
```

So the triple belongs in the compiler invocation instead. The build uses the
NDK's per-API wrapper, `aarch64-linux-android<API>-clang`, which carries its own
`-target`. That also makes the API level a single visible choice in the
workflow rather than a literal buried in the build system - and the API level
decides the minimum Android version an installed pack will run on.

For the same reason the build passes `BUILD_CC=clang BUILD_CXX=clang++`. The
build system takes one toolchain type for both host and target, so with
`--with-toolchain-type=clang` the buildjdk is handed clang-only flags such as
`-flimit-debug-info`; compiling it with gcc turns those into errors.

## What upstream's Android support is, and is not

The 21 lines in the OpenJDK Mobile Project are **build-system plumbing only**.
They teach configure and the makefiles that `android` is a target and that
HotSpot should compile its linux sources. They contain no accommodation for
Bionic itself.

That distinction was not visible from reading, only from building. Once the
build reached HotSpot for the target it hit real glibc assumptions:

```
os_linux.cpp:596: error: "glibc too old (< 2.3.2)"
os_linux.cpp:605: use of undeclared identifier '_CS_GNU_LIBC_VERSION'
os_linux.cpp:1907: no member named 'dlinfo' in the global namespace
```

A fourth error in that batch was around `ELF_ST_TYPE`, and it was not a
missing symbol at all - see below. Worth remembering when the next one of
these appears: not every error from a cross build is the libc's fault.

### ELF_ST_TYPE is a conflict, not a gap

`elfFile.hpp` redefines the width-agnostic spelling in terms of the
width-specific one:

```c
#if !defined(_ALLBSD_SOURCE) || defined(__APPLE__)
#define ELF_ST_TYPE ELF64_ST_TYPE
#endif
```

The guard assumes no libc defines `ELF_ST_TYPE` itself. Bionic does, in
`<linux/elf.h>`:

```c
#define ELF_ST_TYPE(x) ((x) & 0xf)      // line 103
... ELF64_ST_TYPE ...                   // line 107, in terms of the above
```

So HotSpot's line points `ELF_ST_TYPE` at `ELF64_ST_TYPE`, which Bionic points
back at `ELF_ST_TYPE`. The preprocessor refuses to expand a macro inside its
own expansion, stops, and leaves a bare identifier:

```
elfFile.hpp:50:9: warning: 'ELF_ST_TYPE' macro redefined
elfSymbolTable.cpp:50:19: error: use of undeclared identifier 'ELF64_ST_TYPE'
```

The fix is to not redefine what the platform already spells, at both the 64-
and 32-bit sites. Bionic's `((x) & 0xf)` is the same low nibble HotSpot wanted,
and is correct for both ELF widths.

This is a genuine upstream portability bug rather than something android-
specific: any libc that defines `ELF_ST_TYPE` would hit it.

So a working Android JDK needs a source port on top of the build plumbing, and
this patch now carries the start of one. Each accommodation follows the
existing `MUSL_LIBC` branch in `os_linux.cpp`: HotSpot already has a pattern
for a libc that is not glibc, and Android joins it rather than inventing a new
mechanism.

| Site | Bionic difference |
| ---- | ----------------- |
| `libpthread_init` | No `confstr(_CS_GNU_LIBC_VERSION)`; the strings are diagnostic |
| `dll_path` | No `dlinfo(RTLD_DI_LINKMAP)` for applications; the path is diagnostic |

### The linker flag

The build passes `--with-extra-ldflags=-Wl,--undefined-version`, which is not
part of the patch but belongs with it.

NDK 27 ships lld 18, and lld changed its default to `--no-undefined-version`.
HotSpot's export mapfile is generated by scraping symbols out of the object
files with `nm`, so it can name vtables for function-local classes that the
linker later folds away. Older linkers warned about that; lld 18 makes it an
error:

```
ld.lld: error: version script assignment of 'SUNWprivate_1.1' to symbol
'_ZTVZN16SATBMarkQueueSet23abandon_partial_markingEvE...' failed: symbol not defined
```

The flag restores the older behaviour. It does not hide a real missing symbol -
the mapfile names what the objects contained, and the linker is entitled to
discard what nothing references.

### Feature macros

Upstream also sets `CFLAGS_OS_DEF_JDK="-DLINUX -D__USE_BSD"` for Android, where
linux gets `-D_GNU_SOURCE -D_REENTRANT -D_LARGEFILE64_SOURCE`. This patch uses
the linux set, because those are the sources being compiled.

`__USE_BSD` is a libc-internal macro that a build should never define by hand.
Dropping `_GNU_SOURCE` is the more damaging half: the preprocessor then cannot
see constants that generated code needs, and `UnixConstants.java` comes out
containing `X = X`, which javac rejects as a self-reference in an initializer.

**This list is not known to be complete.** Each build round reveals only the
errors the compiler reached before stopping.

## Android's build variables resolve to linux

Build variables throughout the JDK are written per operating system -
`LIBS_linux`, `CFLAGS_macosx`, `LDFLAGS_aix` - and `NativeCompilation.gmk`
picks them up by suffixing the target OS. With the target named `android`,
every one of those is silently skipped.

There are 23 such variables in the tree. The failure that exposed it was the
`java` launcher linking with no libraries at all:

```
LIBS_linux := -ljli -lpthread $(LIBDL),      # LauncherCommon.gmk
/usr/bin/ld: undefined reference to `JLI_Launch' ...
```

Nothing was missing from the build - `libjli.so` was produced from exactly the
right eight sources. `-ljli` was simply never on the link line.

The patch adds one mapping rather than an `android` copy of all 23, each of
which would then have to be kept correct by hand. It sits in `spec.gmk.in`,
beside the definition of `OPENJDK_TARGET_OS` itself, so every makefile in the
build has it:

```make
ifeq ($(OPENJDK_TARGET_OS), android)
  OPENJDK_TARGET_OS_VARIANT := linux
else
  OPENJDK_TARGET_OS_VARIANT := $(OPENJDK_TARGET_OS)
endif
```

`NativeCompilation.gmk` uses it at the eighteen lookup sites in that file. All
eighteen were checked to be variant lookups rather than paths before
substituting. `OPENJDK_TARGET_OS_TYPE` is left alone: it is already `unix` for
android.

A sweep for `_$(OPENJDK_TARGET_OS)` across the whole make tree finds exactly
one lookup outside that file, `PLATFORM_MODULES_$(OPENJDK_TARGET_OS)` in
`Modules.gmk`. That one needs nothing: only `PLATFORM_MODULES_windows` is ever
defined, so android and linux both read an empty value.

This is the same idea as `HOTSPOT_TARGET_OS`, which `platform.m4` maps onto
linux for exactly the same reason.

Taking the linux variants brings one thing that does not apply. `LIBS_linux`
names `-lpthread`, and several sites name `-lrt`. Bionic keeps both inside
libc, and the NDK ships no `libpthread.a` or `librt.a` at all - confirmed by
looking in the sysroot rather than assumed. So both are filtered out of the
resolved library list, in the same single place, rather than in each of the
sites that name them.

## The linux source root has to come back off again

Adding `linux/classes` to `SRC_SUBDIRS` gets the linux Java sources compiled,
but `SRC_SUBDIRS` is not only a search path. `GensrcProperties.gmk` turns a
`.properties` file into a `ListResourceBundle` class, and works out the
package to generate it into by stripping the source root off the file's path:

```make
$(subst /$(OPENJDK_TARGET_OS)/classes,,
$(subst /$(OPENJDK_TARGET_OS_TYPE)/classes,,
$(subst /share/classes,, $($1_SRC_FILES))))
```

Those three patterns are exactly the entries of `SRC_SUBDIRS` upstream. A
fourth root it does not know about is not stripped, so the bundle is generated
into a package nobody looks in. Nothing fails at that point - the build is
perfectly happy to generate a class in the wrong place.

It surfaced a long way from the cause, in the buildjdk's own `jlink`:

```
jdk.tools.jlink.plugin.Plugin: Provider ...StripNativeDebugSymbolsPlugin
    could not be instantiated
Caused by: java.lang.InternalError:
    Cannot find jlink plugin resource bundle (strip-native-debug-symbols)
```

`jdk.jlink/linux/classes` holds that plugin, its `module-info.java.extra`
registering it as a service, and its bundle. The first two arrived, the third
did not, so `jlink` loaded a plugin whose static initialiser could not find its
own strings - and `jlink` is what builds the image, so the build stopped.

The fix strips `$(OPENJDK_TARGET_OS_VARIANT)/classes` as well, guarded so that
nothing changes on a platform where the variant is the target. Checked by
lifting the arithmetic out of the patched file and running it over a real file
list for both targets: `linux` output is byte-identical to upstream's, and
`android` now yields `gensrc/jdk.jlink/jdk/tools/jlink/resources/` rather than
`gensrc/jdk.jlink/linux/classes/jdk/tools/jlink/resources/`.

`jdk.jpackage/linux/classes` has four more properties files with the same
shape, so it would have hit this too had the build reached it.

## The JDK's own idea of which OS it is

HotSpot compiled for the target, the interim image linked, and its `java`
died on the first class it initialised:

```
java.lang.IllegalArgumentException: No enum constant
    jdk.internal.util.OperatingSystem.ANDROID
  at jdk.internal.util.OperatingSystem.initOS(OperatingSystem.java:134)
```

`OperatingSystem` is an enum of LINUX, MACOS, WINDOWS and AIX, and its value
is baked in at build time: `PlatformProps.java.template` gets
`@@OPENJDK_TARGET_OS@@` substituted, and `initOS` calls `valueOf` on it.

Adding an `ANDROID` constant is the wrong fix. The enum is switched over
exhaustively without a default in several places - the class javadoc shows one
- so a fifth constant breaks each of them. And it would be wrong on its own
terms: `LINUX` is documented as "operating systems based on the Linux kernel",
which is what this is, and java.base here is compiled from the linux sources,
so `TARGET_OS_IS_LINUX` is what the rest of the code expects to be true.

`GensrcMisc.gmk` already normalizes the name for exactly this reason, because
the enum spells macOS differently from the build:

```make
ifeq ($(OPENJDK_TARGET_OS), macosx)
  OPENJDK_TARGET_OS_CANONICAL = macos
else
  OPENJDK_TARGET_OS_CANONICAL := $(OPENJDK_TARGET_OS_VARIANT)
endif
```

Only the `else` changed, from `OPENJDK_TARGET_OS` to the variant. Checked for
all five names: linux, windows and aix map to themselves, macosx still maps to
macos, and android maps to linux. `PlatformProps.java.template` is the only
template in the tree that substitutes an OS name at all, and this is the only
site that canonicalises one, so one line covers it.

### And again, in the ModuleTarget attribute

Fixing `PlatformProps` got the interim `java` running. jlink then failed on
the same enum from a different direction:

```
jdk.tools.jlink.plugin.PluginException: ModuleTarget is malformed:
    No enum constant jdk.internal.util.OperatingSystem.ANDROID
  at jdk.tools.jlink.builder.DefaultImageBuilder.storeFiles
```

`java.base`'s `module-info.class` carries a ModuleTarget attribute naming the
platform it was built for, written from `--target-platform` in
`CreateJmods.gmk` and ultimately from `OPENJDK_MODULE_TARGET_PLATFORM` in
`platform.m4`. `DefaultImageBuilder` parses it back through the same enum.

That function has the same shape as the `GensrcMisc.gmk` one - a macosx
special case, else the raw name - and needed the same android case.

Sweeping for the rest of that class turned up two more, one of them silent:

| Value | Was | Now |
| ----- | --- | --- |
| `OPENJDK_MODULE_TARGET_PLATFORM` | `android-aarch64`, rejected by jlink | `linux-aarch64` |
| `RELEASE_FILE_OS_NAME` | **empty** - android matched none of the four branches, so the shipped `release` file said `OS_NAME=""` | `Linux` |
| `OPENJDK_TARGET_OS_INCLUDE_SUBDIR` | `android`, so `jni_md.h` shipped in `include/android/` | `include/linux/` |

The release-file one would never have failed the build. It would have shipped.

Checked by lifting the three generated blocks out of `configure` itself and
running them for all five OS names: linux, windows and aix are untouched,
macosx still gives macos/Darwin/darwin, android now gives linux/Linux/linux.

## java2d's rectangle came from Xlib

With the image building, the target's own libraries started compiling, and
`libawt` stopped on:

```
rect.h:32:10: fatal error: 'X11/Xlib.h' file not found
  ... building libawt/Blit.o
```

Two separate reasons, and the second is the interesting one.

`Awt2dLibraries.gmk` excludes `awt_Font.c`, `CUPSfuncs.c`, `fontpath.c` and
`X11Color.c` on `linux macosx aix`. Those four are the only `.c` files in
`unix/native/common/awt`, and two of them include X11 headers directly.
Android was not in the list, so all four were being compiled.

That alone would not have been enough. `rect.h` reaches for Xlib to name a
rectangle:

```c
#ifndef MACOSX
#include <X11/Xlib.h>
typedef XRectangle RECT_T;
#else
typedef struct { int x; int y; int width; int height; } RECT_T;
#endif
```

`Region.h` includes `rect.h`, and every shared java2d source includes
`Region.h` - so on a platform with no Xlib the entire rasterizer stops
compiling over a type name. Headless does not help: this is not the windowing
system, it is `Blit`.

macOS already has a branch that avoids it, which settles both the question of
whether it works and what the replacement should look like. Android takes the
same one. It costs nothing here: `BitmapToYXBandedRectangles` is implemented in
`rect.c`, which is only compiled on windows, and called only from windows code
and from `libawt_xawt`, which android does not build. `RECT_T` survives as the
type of a prototype and nothing else.

Verified with `clang -H`, which prints the include tree so an Xlib include
cannot hide - this machine has X11 headers installed, so a plain "it compiles"
would have proved nothing:

| | `Xlib.h` included | `RECT_T` |
| - | - | - |
| `-D__ANDROID__` | 0 | plain struct |
| upstream | 1 | `XRectangle` |
| `-DMACOSX` | 0 | plain struct |

## The serviceability agent is not shipped

`jdk.hotspot.agent` failed on `prstatus_t`, one of glibc's `<sys/procfs.h>`
core-file types that Bionic does not have.

It is turned off rather than ported, through `INCLUDE_SA`, the switch upstream
already uses to say "some platforms don't have the serviceability agent" for
AIX and s390x. This is not a feature dropped to get a green build: the agent
attaches to a JVM over ptrace or reads a core file, and it does so from a
second `java` process. An Android app may not ptrace another process and may
not exec a JVM from its data directory, so a ported agent would compile and
then never run. Porting glibc's procfs structures to buy that is not a
trade worth making.

Everything else in the image is unaffected - the agent is a separate module
and a separate tool, not something the JVM needs to run.

## Three files the Mobile Project never needed

`JvmMapfile.gmk`, `GensrcAdlc.gmk` and `JvmOverrideFiles.gmk` are not in
upstream's Android support, because JDK 28 restructured or does not have them.
Porting to 21 therefore is not just "apply what Mobile has" - files that exist
in 21 and not in 28 need their own handling.

Only `JvmMapfile.gmk` announced itself, with
`*** Unknown target OS android`. The other two would have taken no branch at
all and built quietly: ADLC would have generated C2's machine description with
no OS define, and libjvm would have lost large-file support in the two files
that do file I/O plus the precompiled-header exclusions clang needs for the
fdlibm sources. Those were found by reading every `isTargetOs, linux` dispatch
under `make/hotspot`, not by a build failure.

Three others in that sweep are genuinely harmless and were deliberately left
alone: `CompileJvm.gmk` only acts when `HOTSPOT_TARGET_CPU_ARCH` is `arm`,
`JvmFeatures.gmk` only under the `minimal` JVM variant, and `GensrcDtrace.gmk`
only when dtrace is enabled.

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
