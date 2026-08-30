cmake_minimum_required(VERSION 3.22.1)

project(nikprobe C)

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
