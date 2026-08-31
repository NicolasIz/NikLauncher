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

echo "Building the EGL resolver test"
"$cc" "${flags[@]}" \
  "$root/app/src/test/cpp/test_nikegl_resolve.c" \
  "$root/app/src/main/cpp/glfw/nikegl_resolve.c" \
  -ldl -o "$out/test_nikegl_resolve"

"$out/test_nikegl_resolve" "$out/libegl_complete.so" "$out/libegl_incomplete.so" \
  "$chain/libEGL.so"
