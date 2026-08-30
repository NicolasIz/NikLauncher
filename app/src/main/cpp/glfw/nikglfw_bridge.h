/*
 * The seam between the GLFW ABI layer and the JNI layer.
 *
 * Kept separate so the JNI code never reaches into the ABI file's statics
 * directly: everything crossing that boundary goes through these three.
 */

#ifndef NIKGLFW_BRIDGE_H
#define NIKGLFW_BRIDGE_H

#include "nikglfw_core.h"

#include <android/native_window.h>
#include <pthread.h>

NikGlfwCore *nikglfw_core(void);

/* Guards the core; the input thread and the game thread both touch it. */
pthread_mutex_t *nikglfw_lock(void);

/*
 * Hands over the ANativeWindow the EGL surface will be created on. Must be
 * called before the game creates its window, since eglCreateWindowSurface
 * needs it.
 */
void nikglfw_set_native_window(ANativeWindow *window);

#endif /* NIKGLFW_BRIDGE_H */
