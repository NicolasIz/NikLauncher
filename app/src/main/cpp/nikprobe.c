#include <jni.h>
#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <unistd.h>

/*
 * Marker resolved through dlsym() after the library is re-opened from the app
 * data directory. Its value proves we did not merely open the file but actually
 * mapped and executed code from it.
 */
#define NIK_PROBE_MARKER 0x4E494B4C /* "NIKL" */

__attribute__((visibility("default")))
int niklauncher_probe_marker(void) {
    return NIK_PROBE_MARKER;
}

JNIEXPORT jint JNICALL
Java_com_niklauncher_app_data_NativeProbe_nativePageSize(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return (jint) getpagesize();
}

/*
 * Opens a shared object by absolute path and calls a known symbol inside it.
 *
 * This is the exact mechanism the launcher will use for libjvm.so: Android
 * forbids exec()ing a binary from the app data directory, so a downloaded
 * runtime pack can only be reached by dlopen() plus the JNI Invocation API.
 * If this fails, the runtime-pack design does not work on this device.
 */
JNIEXPORT jstring JNICALL
Java_com_niklauncher_app_data_NativeProbe_nativeDlopen(JNIEnv *env, jobject thiz, jstring j_path) {
    (void) thiz;
    char result[512];
    const char *path = (*env)->GetStringUTFChars(env, j_path, NULL);
    if (path == NULL) {
        return (*env)->NewStringUTF(env, "FAIL: could not read path");
    }

    dlerror();
    void *handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        const char *error = dlerror();
        snprintf(result, sizeof(result), "FAIL dlopen: %s", error ? error : "unknown");
    } else {
        int (*marker)(void) = (int (*)(void)) dlsym(handle, "niklauncher_probe_marker");
        if (marker == NULL) {
            const char *error = dlerror();
            snprintf(result, sizeof(result), "FAIL dlsym: %s", error ? error : "unknown");
        } else if (marker() != NIK_PROBE_MARKER) {
            snprintf(result, sizeof(result), "FAIL: marker returned an unexpected value");
        } else {
            snprintf(result, sizeof(result), "OK");
        }
        dlclose(handle);
    }

    (*env)->ReleaseStringUTFChars(env, j_path, path);
    return (*env)->NewStringUTF(env, result);
}

/*
 * Checks whether this process can get executable memory.
 *
 * A JVM cannot JIT without it. Two patterns are probed separately: the W^X-safe
 * one (map read/write, then flip to read/execute), and a direct read/write/exec
 * mapping. Knowing which of the two the device allows tells us how the JVM has
 * to be configured, or whether it must fall back to interpreted mode.
 */
JNIEXPORT jstring JNICALL
Java_com_niklauncher_app_data_NativeProbe_nativeExecutableMemory(JNIEnv *env, jobject thiz) {
    (void) thiz;
    char result[256];
    size_t size = (size_t) getpagesize();

    int rw_then_rx = 0;
    void *p = mmap(NULL, size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (p != MAP_FAILED) {
        memset(p, 0, size);
        rw_then_rx = (mprotect(p, size, PROT_READ | PROT_EXEC) == 0);
        munmap(p, size);
    }

    int rwx = 0;
    void *q = mmap(NULL, size, PROT_READ | PROT_WRITE | PROT_EXEC, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (q != MAP_FAILED) {
        rwx = 1;
        munmap(q, size);
    }

    snprintf(result, sizeof(result), "rw->rx=%s rwx=%s",
             rw_then_rx ? "yes" : "no", rwx ? "yes" : "no");
    return (*env)->NewStringUTF(env, result);
}
