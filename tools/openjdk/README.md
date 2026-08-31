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
| `libraries.m4` | No X11, no fontconfig, no CUPS |

`libraries.m4` decides six dependencies in one block. Android needs three
turned off - X11, fontconfig and CUPS - and the other three already fall out
correctly: freetype is satisfied by the bundled copy the `lib-freetype.m4` hunk
selects, ALSA is gated on the target being exactly `xlinux` so android misses
it, and FFI is only wanted for the `zero` JVM variant.
| `Modules.gmk`, `JdkNativeCompilation.gmk` | Take Java and native sources from the `linux` tree |
| `JvmFlags.gmk` | Put the linux and `linux_aarch64` include directories on the path |
| `java.desktop/*.gmk` | No AWT, no sound |
| `JvmMapfile.gmk` | Take the linux symbol-dump branch |
| `GensrcAdlc.gmk` | Give ADLC the linux OS defines |
| `JvmOverrideFiles.gmk` | Large-file support and the clang PCH exclusions |
| `os_posix.cpp` | Bionic has no `CLK_TCK`; ask `sysconf` as Linux does |
| `net_util_md.h` | Bionic wants `netinet/in.h` ahead of `netdb.h` |

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
elfSymbolTable.cpp:50: use of undeclared identifier 'ELF_ST_TYPE'
```

So a working Android JDK needs a source port on top of the build plumbing, and
this patch now carries the start of one. Each accommodation follows the
existing `MUSL_LIBC` branch in `os_linux.cpp`: HotSpot already has a pattern
for a libc that is not glibc, and Android joins it rather than inventing a new
mechanism.

| Site | Bionic difference |
| ---- | ----------------- |
| `libpthread_init` | No `confstr(_CS_GNU_LIBC_VERSION)`; the strings are diagnostic |
| `dll_path` | No `dlinfo(RTLD_DI_LINKMAP)` for applications; the path is diagnostic |
| `ElfSymbolTable::compare` | `ELF32_ST_TYPE`/`ELF64_ST_TYPE` exist, the width-agnostic spelling does not |

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

The patch adds one mapping in `NativeCompilation.gmk` rather than an `android`
copy of all 23, each of which would then have to be kept correct by hand:

```make
ifeq ($(OPENJDK_TARGET_OS), android)
  OPENJDK_TARGET_OS_VARIANT := linux
endif
```

and uses it at the eighteen lookup sites in that file. All eighteen were
checked to be variant lookups rather than paths before substituting.
`OPENJDK_TARGET_OS_TYPE` is left alone: it is already `unix` for android.

This is the same idea as `HOTSPOT_TARGET_OS`, which `platform.m4` maps onto
linux for exactly the same reason.

Taking the linux variants brings one thing that does not apply. `LIBS_linux`
names `-lpthread`, and several sites name `-lrt`. Bionic keeps both inside
libc, and the NDK ships no `libpthread.a` or `librt.a` at all - confirmed by
looking in the sysroot rather than assumed. So both are filtered out of the
resolved library list, in the same single place, rather than in each of the
sites that name them.

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
