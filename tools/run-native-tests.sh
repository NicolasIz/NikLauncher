#!/usr/bin/env bash
# Compiles and runs the host tests for the native code.
#
# These cover the parts written free of Android headers - the GLFW bridge core
# above all - so a regression in the input queue is caught in seconds on every
# push, instead of on a device after a six-minute APK build.
set -euo pipefail

root=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT

cc=${CC:-gcc}
flags=(-std=c11 -Wall -Wextra -Werror -O1 -g)

echo "Building native host tests with $cc"
"$cc" "${flags[@]}" \
  "$root/app/src/test/cpp/test_nikglfw_core.c" \
  "$root/app/src/main/cpp/glfw/nikglfw_core.c" \
  -o "$out/test_nikglfw_core"

"$out/test_nikglfw_core"

# The EGL resolver. Two real shared objects are built for it rather than a
# fake dlopen, so the test exercises the loader the device will use: one
# exporting all thirteen entry points, one deliberately missing eglSwapInterval
# so the "which symbol" reporting is checked rather than assumed.
egl_names=(eglGetDisplay eglInitialize eglChooseConfig eglCreateWindowSurface
           eglCreateContext eglMakeCurrent eglGetCurrentContext eglDestroyContext
           eglDestroySurface eglSwapBuffers eglSwapInterval eglGetError
           eglGetProcAddress)

emit_stub_source() {
  local skip=$1
  for name in "${egl_names[@]}"; do
    [ "$name" = "$skip" ] && continue
    # Distinct bodies: identical ones can be folded together by the linker,
    # and the test checks that separate names resolve to separate addresses.
    echo "int $name(void) { return ${#name}; }"
  done
}

emit_stub_source "" > "$out/egl_complete.c"
emit_stub_source eglSwapInterval > "$out/egl_incomplete.c"

for stub in complete incomplete; do
  "$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
    "$out/egl_$stub.c" -o "$out/libegl_$stub.so"
done

# A three-deep dependency chain in a directory the loader knows nothing about,
# which is the shape of a runtime pack in the app's files directory. The
# middle library is named so that a single alphabetical pass would try it
# before what it depends on, so the resolver's repeated passes are exercised
# rather than accidentally satisfied by the order readdir happens to give.
chain="$out/chain"
mkdir -p "$chain"
cat > "$out/base.c" <<'SRC'
int nik_base_value(void) { return 7; }
SRC
cat > "$out/mid.c" <<'SRC'
int nik_base_value(void);
int nik_mid_value(void) { return nik_base_value() + 1; }
SRC
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/base.c" -Wl,-soname,libzz_base.so -o "$chain/libzz_base.so"
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/mid.c" -Wl,-soname,libaa_mid.so -o "$chain/libaa_mid.so" \
  -L "$chain" -l:libzz_base.so -Wl,--no-as-needed

{
  emit_stub_source ""
  echo "int nik_mid_value(void);"
  echo "int nik_chain_value(void) { return nik_mid_value(); }"
} > "$out/egl_chained.c"
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/egl_chained.c" -o "$chain/libEGL.so" \
  -L "$chain" -l:libaa_mid.so -Wl,--no-as-needed

# The compat directory, and the rule that its contents lose to the platform's
# own. A stand-in "platform" library is put on the loader's search path, and a
# different library with the same name is put in compat/ - so the rule can be
# checked by which of the two actually ended up loaded, not merely by whether
# preloading returned without complaint.
mkdir -p "$chain/compat" "$out/fakeplatform"
cat > "$out/platform_marker.c" <<'SRC'
int nik_platform_marker(void) { return 1; }
SRC
cat > "$out/compat_marker.c" <<'SRC'
int nik_compat_marker(void) { return 2; }
SRC
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/platform_marker.c" -Wl,-soname,libnik_shared.so \
  -o "$out/fakeplatform/libnik_shared.so"
# Same name as the platform's, different contents: this is the one that must
# lose. In a real pack the file name and the soname are equal, which is what
# the runtime-packs workflow asserts for every compatibility library.
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/compat_marker.c" -Wl,-soname,libnik_shared.so \
  -o "$chain/compat/libnik_shared.so"
# No platform copy of this one, so it is the one that must be loaded.
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/compat_marker.c" -Wl,-soname,libnik_absent.so \
  -o "$chain/compat/libnik_absent.so"

# A JDK's layout: libjvm.so in lib/server/, the C++ runtime one level up in
# lib/. This is the arrangement the runtime pack actually has, and the one a
# sibling-only scan would miss.
mkdir -p "$out/jdk/lib/server"
cat > "$out/cxx.c" <<'SRC'
int nik_cxx_value(void) { return 3; }
SRC
cat > "$out/jvm.c" <<'SRC'
int nik_cxx_value(void);
int JNI_CreateJavaVM(void) { return nik_cxx_value(); }
SRC
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/cxx.c" -Wl,-soname,libnik_cxx_shared.so -o "$out/jdk/lib/libnik_cxx_shared.so"
"$cc" -std=c11 -Wall -Wextra -Werror -O0 -fPIC -shared \
  "$out/jvm.c" -Wl,-soname,libjvm.so -o "$out/jdk/lib/server/libjvm.so" \
  -L "$out/jdk/lib" -l:libnik_cxx_shared.so -Wl,--no-as-needed

echo "Building the EGL resolver test"
"$cc" "${flags[@]}" \
  "$root/app/src/test/cpp/test_nikegl_resolve.c" \
  "$root/app/src/main/cpp/glfw/nikegl_resolve.c" \
  "$root/app/src/main/cpp/loader/niksoload.c" \
  -ldl -o "$out/test_nikegl_resolve"

# The stand-in platform library is reachable by bare soname only because it is
# on the search path the loader read at start-up - which is exactly the
# position a real platform library is in on a device.
LD_LIBRARY_PATH="$out/fakeplatform" \
  "$out/test_nikegl_resolve" "$out/libegl_complete.so" "$out/libegl_incomplete.so" \
  "$chain/libEGL.so" "$out/jdk/lib/server/libjvm.so"
