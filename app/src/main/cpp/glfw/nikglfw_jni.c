/*
 * JNI surface for the GLFW bridge.
 *
 * Everything NikLauncher's Kotlin input layer produces enters the game through
 * here: touch gestures already translated into GLFW events, physical keyboard
 * and gamepad presses, and the Android Surface the EGL context is created on.
 *
 * These run on Android's UI thread while the game thread is inside
 * glfwPollEvents, so every one takes the bridge lock.
 */

#include "nikglfw_bridge.h"

#include <android/native_window_jni.h>
#include <jni.h>
#include <string.h>

static void push(const NikEvent *event) {
    pthread_mutex_lock(nikglfw_lock());
    nik_core_push(nikglfw_core(), event);
    pthread_mutex_unlock(nikglfw_lock());
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativeSetSurface(
        JNIEnv *env, jobject thiz, jobject surface) {
    (void) thiz;
    ANativeWindow *window = NULL;
    if (surface != NULL) {
        window = ANativeWindow_fromSurface(env, surface);
    }
    nikglfw_set_native_window(window);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushKey(
        JNIEnv *env, jobject thiz, jint key, jint scancode, jint action, jint mods) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_KEY;
    event.key = key;
    event.scancode = scancode;
    event.action = action;
    event.mods = mods;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushChar(
        JNIEnv *env, jobject thiz, jint codepoint, jint mods) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_CHAR;
    event.codepoint = (unsigned int) codepoint;
    event.mods = mods;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushMouseButton(
        JNIEnv *env, jobject thiz, jint button, jint action, jint mods) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_MOUSE_BUTTON;
    event.key = button;
    event.action = action;
    event.mods = mods;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushCursorPos(
        JNIEnv *env, jobject thiz, jdouble x, jdouble y) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_CURSOR_POS;
    event.x = x;
    event.y = y;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushScroll(
        JNIEnv *env, jobject thiz, jdouble x, jdouble y) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_SCROLL;
    event.x = x;
    event.y = y;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushWindowSize(
        JNIEnv *env, jobject thiz, jint width, jint height) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_WINDOW_SIZE;
    event.x = width;
    event.y = height;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushFocus(
        JNIEnv *env, jobject thiz, jboolean focused) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_WINDOW_FOCUS;
    event.key = focused ? 1 : 0;
    push(&event);

    /* Losing focus must not leave the game holding anything: the player's
     * fingers are gone but Minecraft has not been told. */
    if (!focused) {
        pthread_mutex_lock(nikglfw_lock());
        nik_core_release_all(nikglfw_core());
        pthread_mutex_unlock(nikglfw_lock());
    }
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePushClose(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_WINDOW_CLOSE;
    push(&event);
}

JNIEXPORT void JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativeReleaseAll(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(nikglfw_lock());
    nik_core_release_all(nikglfw_core());
    pthread_mutex_unlock(nikglfw_lock());
}

/* Exposed for the diagnostics screen: a non-zero count means the game thread
 * stalled long enough for input to back up. */
JNIEXPORT jlong JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativeDroppedEvents(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(nikglfw_lock());
    jlong dropped = (jlong) nikglfw_core()->dropped;
    pthread_mutex_unlock(nikglfw_lock());
    return dropped;
}

JNIEXPORT jint JNICALL
Java_com_niklauncher_app_runtime_GlfwBridge_nativePendingEvents(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(nikglfw_lock());
    jint pending = (jint) nik_core_pending(nikglfw_core());
    pthread_mutex_unlock(nikglfw_lock());
    return pending;
}
