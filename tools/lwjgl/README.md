# LWJGL natives for Android

What this builds, and what still has to be decided before the results are
usable by an installed runtime.

## What gets built

Only two libraries:

| Library            | Contents                                                    |
| ------------------ | ----------------------------------------------------------- |
| `liblwjgl.so`      | memory access, callbacks, libffi, the JNI invocation path    |
| `liblwjgl_stb.so`  | image decoding, font rasterising, the vorbis decoder         |

Nothing is built for LWJGL's `glfw`, `opengl` or `openal` modules, because
those modules contain no C. They are pure Java that `dlopen`s a library and
resolves symbols by name, which is what NikLauncher's GLFW bridge and gl4es
already provide.

libffi is cross-compiled first and linked statically, so an installed runtime
has one fewer loose shared object to resolve at startup.

## Bindings deliberately left out

Android is Linux with Bionic rather than glibc, so most of LWJGL's `linux`
sources apply as they are. Two do not:

- **io_uring** - Bionic ships no headers for it, and Android's seccomp policy
  blocks the syscalls regardless.
- **UIO** - vectored and cross-process I/O. `preadv`/`pwritev` sit behind
  feature-test macros in Bionic, and `process_vm_readv`/`writev` are blocked
  between processes on Android.

Neither is on Minecraft's path, and LWJGL resolves these natives lazily, so an
absent binding only fails if something actually calls it. Forcing them in with
`_GNU_SOURCE` would ship functions that cannot work on this platform, which is
worse than not shipping them.

## Why the JNI table is checked in CI

These libraries are compiled against the NDK's `jni.h` but run against the
desktop HotSpot inside the runtime pack. Every `(*env)->Function()` call takes
its offset from one header and dispatches through a table built by the other,
and `ThreadLocalUtil` goes further: it copies that table onto the heap and
swaps the pointer.

The layouts agree because `JNINativeInterface`'s order is fixed by the JNI
specification and Android's header simply stops earlier, at JNI 1.6. A
reordering would not be caught by any compiler, so `check-jni-layout.py` runs
in the build.

## Open question: pairing natives with LWJGL's jars

Minecraft's manifest names a particular LWJGL version, and its Java classes
must agree with these natives. LWJGL does check - `Library.java` compares the
SHA-1 recorded in the classes jar against the loaded library - but on a
mismatch it only prints to the debug stream and carries on; it does not throw,
and it returns silently when the resource is absent.

So a mismatch would not be refused. It would be discovered later, as a wrong
JNI signature, which is exactly the failure mode this project is meant to
avoid. Two options:

1. **Ship LWJGL's own jars in the pack**, exposed through
   `RuntimePack.providedClasspath`, so the Java and native sides are the same
   version by construction. `LibraryResolver` already routes the `org.lwjgl`
   groups to the runtime pack, so the wiring exists.
2. Keep Mojang's jars and rely on JNI signatures being stable across 3.3.x.

Option 1 is the one that matches this project's priorities. It costs pack size;
option 2 costs a class of bug that only appears at runtime on a device.
