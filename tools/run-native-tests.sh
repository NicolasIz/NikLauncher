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
