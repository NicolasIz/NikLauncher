cmake_minimum_required(VERSION 3.22.1)

project(nikprobe C)

# Pinned rather than left to the compiler's default: the bridge uses POSIX
# clock_gettime, which a strict C11 dialect hides.
set(CMAKE_C_STANDARD 11)
set(CMAKE_C_EXTENSIONS ON)

# The Phase 2 probe. It answers, on the real device, the questions that decide
# whether NikLauncher's whole runtime design is viable:
#   - can we dlopen() a shared object from the app's own data directory?
#   - can we obtain executable memory, which the JVM's JIT requires?
#   - what page size is the kernel using?
add_library(nikprobe SHARED nikprobe.c)

# Android 15 devices may use 16 KB pages. Every native object we ship, and every
# runtime pack we later load, has to be aligned for that or it will not map.
target_link_options(nikprobe PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)
target_link_libraries(nikprobe ${log-lib})

# The JVM bridge. It dlopen()s the runtime pack's libjvm.so and starts the VM
# through the JNI Invocation API, which is the only way to run a downloaded
# Java runtime on Android: exec()ing a binary from the app data directory is
# forbidden.
add_library(nikjvm SHARED nikjvm.c)
target_link_options(nikjvm PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(nikjvm ${log-lib})

# The GLFW bridge. LWJGL resolves the GLFW 3 ABI by name out of a libglfw.so,
# so this library must be named exactly that - the file name is part of the
# contract, not a choice. It implements that ABI over EGL, an ANativeWindow and
# the event core, which is the only way Minecraft's input and windowing can
# work on a platform that has no GLFW.
add_library(glfw SHARED
    glfw/nikglfw_core.c
    glfw/nikglfw_api.c
    glfw/nikglfw_jni.c
)

target_link_options(glfw PRIVATE "-Wl,-z,max-page-size=16384")

find_library(egl-lib EGL)
find_library(gles-lib GLESv2)
find_library(android-lib android)
target_link_libraries(glfw ${egl-lib} ${gles-lib} ${android-lib} ${log-lib})
