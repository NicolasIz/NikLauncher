/*
 * Host tests for the EGL symbol resolver.
 *
 * The resolver is written free of EGL headers precisely so it can be exercised
 * here, on a machine that has none. The test builds real shared objects and
 * opens them, rather than faking dlopen, so what is checked is the mechanism
 * that will run on the device: that all thirteen names resolve, that a library
 * missing one is reported by name, and that a library that is not there at all
 * fails rather than half-loading.
 */

#include "../../main/cpp/glfw/nikegl_resolve.h"

#include <stdio.h>
#include <string.h>

static int failures = 0;

static void check(int condition, const char *what) {
    if (!condition) {
        printf("  FAIL %s\n", what);
        failures++;
    }
}

/* The same thirteen, in the same order as nikegl.c. */
static const char *const NAMES[] = {
    "eglGetDisplay", "eglInitialize", "eglChooseConfig", "eglCreateWindowSurface",
    "eglCreateContext", "eglMakeCurrent", "eglGetCurrentContext", "eglDestroyContext",
    "eglDestroySurface", "eglSwapBuffers", "eglSwapInterval", "eglGetError",
    "eglGetProcAddress",
};
#define COUNT ((int) (sizeof(NAMES) / sizeof(NAMES[0])))

static void test_library_name(void) {
    printf("library name selection\n");
    check(strcmp(nikegl_library_name(NULL), "libEGL.so") == 0,
          "NULL falls back to the platform's own");
    check(strcmp(nikegl_library_name(""), "libEGL.so") == 0,
          "empty falls back to the platform's own");
    const char *configured = "/data/app/pack/libEGL.so";
    check(nikegl_library_name(configured) == configured,
          "a configured path is used, and not copied");
}

static void test_resolves_all(const char *path) {
    printf("a library exporting all %d\n", COUNT);
    void *handle = nikegl_open(path);
    check(handle != NULL, "opens");
    if (handle == NULL) return;

    void *slots[COUNT];
    memset(slots, 0, sizeof(slots));
    check(nikegl_resolve(handle, NAMES, slots, COUNT) == -1, "reports nothing missing");
    for (int i = 0; i < COUNT; i++) {
        if (slots[i] == NULL) {
            printf("  FAIL %s left null\n", NAMES[i]);
            failures++;
        }
    }
    /* Distinct symbols must land on distinct addresses, or the table would
     * bind several calls to one function without anything noticing. */
    for (int i = 0; i < COUNT; i++) {
        for (int j = i + 1; j < COUNT; j++) {
            if (slots[i] == slots[j]) {
                printf("  FAIL %s and %s resolved to the same address\n", NAMES[i], NAMES[j]);
                failures++;
            }
        }
    }
    nikegl_close(handle);
}

static void test_reports_the_missing_one(const char *path) {
    printf("a library missing eglSwapInterval\n");
    void *handle = nikegl_open(path);
    check(handle != NULL, "opens");
    if (handle == NULL) return;

    void *slots[COUNT];
    memset(slots, 0, sizeof(slots));
    int missing = nikegl_resolve(handle, NAMES, slots, COUNT);
    check(missing == 10, "returns the index of the missing symbol");
    if (missing >= 0 && missing < COUNT) {
        check(strcmp(NAMES[missing], "eglSwapInterval") == 0, "which names eglSwapInterval");
    }
    check(nikegl_last_error() != NULL, "and leaves an error to report");
    nikegl_close(handle);
}

static void test_absent_library(void) {
    printf("a library that is not there\n");
    void *handle = nikegl_open("/nonexistent/libEGL.so");
    check(handle == NULL, "does not open");
    check(nikegl_last_error() != NULL, "and leaves an error to report");
}

/*
 * The dependency chain, which is the whole reason preloading exists.
 *
 * `chain/libEGL.so` needs libmid, which needs libbase, and none of them is on
 * any search path the loader knows - exactly the shape of a runtime pack in
 * the app's files directory. Opening the target alone must fail; opening it
 * after the siblings are loaded must succeed. The chain is three deep and
 * built in the wrong alphabetical order on purpose, so a single pass over the
 * directory is not enough and the repeated passes are actually exercised.
 */
static void test_preloads_a_chain(const char *chained_egl) {
    printf("a library whose dependencies are only in its own directory\n");

    check(nikegl_open(chained_egl) == NULL,
          "does not open on its own: its dependencies are not findable");

    int loaded = nikegl_preload_siblings(chained_egl);
    check(loaded == 2, "loads both siblings, in whatever order they need");

    void *handle = nikegl_open(chained_egl);
    check(handle != NULL, "opens once the chain is loaded");
    if (handle == NULL) return;

    void *slots[COUNT] = {0};
    check(nikegl_resolve(handle, NAMES, slots, COUNT) == -1,
          "and every entry point still resolves through it");
    nikegl_close(handle);
}

static void test_preload_of_a_bare_name(void) {
    printf("a bare soname\n");
    check(nikegl_preload_siblings("libEGL.so") == 0,
          "loads nothing: that is the platform's own and the loader finds it");
    check(nikegl_preload_siblings(NULL) == 0, "NULL is not a crash");
    check(nikegl_preload_siblings("/nonexistent-directory/libEGL.so") == 0,
          "a directory that is not there loads nothing");
}

int main(int argc, char **argv) {
    if (argc < 4) {
        printf("usage: %s <complete.so> <incomplete.so> <chained-egl.so>\n", argv[0]);
        return 2;
    }
    test_library_name();
    test_resolves_all(argv[1]);
    test_reports_the_missing_one(argv[2]);
    test_absent_library();
    test_preloads_a_chain(argv[3]);
    test_preload_of_a_bare_name();

    if (failures > 0) {
        printf("\n%d check(s) failed\n", failures);
        return 1;
    }
    printf("\nnikegl resolver: all checks passed\n");
    return 0;
}
