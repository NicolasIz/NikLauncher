cmake_minimum_required(VERSION 3.22.1)

project(lwjgl_android C)

#
# Builds LWJGL's native libraries for Android.
#
# LWJGL's own build is Ant driven and assumes a desktop toolchain, so this
# compiles the sources directly instead. That is workable because the JNI glue
# LWJGL generates from its Kotlin templates is committed to the repository:
# only a compiler is needed, not their generator.
#
# Two modules matter for Minecraft:
#   lwjgl      - the core: memory access, callbacks, the JNI invocation path
#   lwjgl_stb  - image and font decoding, which the game uses for every texture
#
# The GLFW, OpenGL and OpenAL bindings need nothing here: they are pure Java
# that dlopen()s a library and resolves symbols by name, which is exactly what
# NikLauncher's GLFW bridge and gl4es already provide.
#

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_EXTENSIONS ON)

if(NOT DEFINED LWJGL_DIR)
    message(FATAL_ERROR "LWJGL_DIR must point at an LWJGL checkout")
endif()
if(NOT DEFINED FFI_ROOT)
    message(FATAL_ERROR "FFI_ROOT must point at a libffi built for this ABI")
endif()

set(CORE ${LWJGL_DIR}/modules/lwjgl/core/src)

# Hand-written glue.
set(CORE_SOURCES
    ${CORE}/main/c/common_tools.c
    ${CORE}/main/c/org_lwjgl_system_Callback.c
    ${CORE}/main/c/org_lwjgl_system_MemoryUtil.c
    ${CORE}/main/c/org_lwjgl_system_SharedLibraryUtil.c
    ${CORE}/main/c/org_lwjgl_system_ThreadLocalUtil.c
)

# Generated glue, platform neutral.
list(APPEND CORE_SOURCES
    ${CORE}/generated/c/org_lwjgl_system_JNI.c
    ${CORE}/generated/c/org_lwjgl_system_MemoryAccessJNI.c
    ${CORE}/generated/c/org_lwjgl_system_jni_JNINativeInterface.c
    ${CORE}/generated/c/org_lwjgl_system_libc_LibCErrno.c
    ${CORE}/generated/c/org_lwjgl_system_libc_LibCLocale.c
    ${CORE}/generated/c/org_lwjgl_system_libc_LibCStdio.c
    ${CORE}/generated/c/org_lwjgl_system_libc_LibCStdlib.c
    ${CORE}/generated/c/org_lwjgl_system_libc_LibCString.c
    ${CORE}/generated/c/org_lwjgl_system_libffi_FFICIF.c
    ${CORE}/generated/c/org_lwjgl_system_libffi_FFIClosure.c
    ${CORE}/generated/c/org_lwjgl_system_libffi_LibFFI.c
)

# Generated glue for Linux. Android is Linux with Bionic rather than glibc, so
# most of this applies; io_uring is deliberately left out, because Bionic does
# not ship its headers and Android's seccomp policy blocks the syscalls anyway.
list(APPEND CORE_SOURCES
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_DynamicLinkLoader.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_FCNTL.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_MMAN.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_PThread.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_Socket.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_Stat.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_UIO.c
    ${CORE}/generated/c/linux/org_lwjgl_system_linux_UNISTD.c
)

add_library(lwjgl SHARED ${CORE_SOURCES})

target_include_directories(lwjgl PRIVATE
    ${CORE}/main/c
    ${CORE}/main/c/linux
    ${CORE}/main/c/libffi
    ${CORE}/main/c/libffi/aarch64
    ${FFI_ROOT}/include
)

target_compile_definitions(lwjgl PRIVATE LWJGL_LINUX)

# libffi is linked statically so an installed runtime carries one fewer loose
# shared object to resolve at startup.
target_link_libraries(lwjgl ${FFI_ROOT}/lib/libffi.a dl m)
target_link_options(lwjgl PRIVATE "-Wl,-z,max-page-size=16384")

#
# stb: image decoding, font rasterising and the vorbis decoder.
#
set(STB ${LWJGL_DIR}/modules/lwjgl/stb/src)

file(GLOB STB_GENERATED ${STB}/generated/c/*.c)

add_library(lwjgl_stb SHARED
    ${STB_GENERATED}
    ${STB}/main/c/stb_vorbis.c
)

target_include_directories(lwjgl_stb PRIVATE
    ${CORE}/main/c
    ${STB}/main/c
)

target_compile_definitions(lwjgl_stb PRIVATE LWJGL_LINUX)
target_link_options(lwjgl_stb PRIVATE "-Wl,-z,max-page-size=16384")
target_link_libraries(lwjgl_stb m)
