/*
 * The GLFW 3 ABI, implemented for Android.
 *
 * LWJGL loads a library called `glfw` and resolves these symbols by name, so
 * the names and signatures here are fixed by GLFW's own header - they are not
 * ours to choose. The bodies are: they run on EGL, an ANativeWindow, and the
 * event core in nikglfw_core.c.
 *
 * Only one window ever exists. Minecraft asks for one, and Android has no
 * concept of a second one anyway, so every window handle is the same pointer
 * and the monitor and joystick surfaces are the minimum that keeps LWJGL's
 * initialisation happy.
 */

#include "nikglfw_core.h"

#include <EGL/egl.h>
#include <GLES2/gl2.h>
#include <android/log.h>
#include <android/native_window.h>
#include <pthread.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define LOG_TAG "NikGLFW"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#define NIK_EXPORT __attribute__((visibility("default")))

/* --- GLFW public types -------------------------------------------------- */

typedef struct GLFWwindow GLFWwindow;
typedef struct GLFWmonitor GLFWmonitor;
typedef struct GLFWcursor GLFWcursor;

typedef void (*GLFWerrorfun)(int, const char *);
typedef void (*GLFWkeyfun)(GLFWwindow *, int, int, int, int);
typedef void (*GLFWcharfun)(GLFWwindow *, unsigned int);
typedef void (*GLFWcharmodsfun)(GLFWwindow *, unsigned int, int);
typedef void (*GLFWmousebuttonfun)(GLFWwindow *, int, int, int);
typedef void (*GLFWcursorposfun)(GLFWwindow *, double, double);
typedef void (*GLFWcursorenterfun)(GLFWwindow *, int);
typedef void (*GLFWscrollfun)(GLFWwindow *, double, double);
typedef void (*GLFWwindowsizefun)(GLFWwindow *, int, int);
typedef void (*GLFWframebuffersizefun)(GLFWwindow *, int, int);
typedef void (*GLFWwindowfocusfun)(GLFWwindow *, int);
typedef void (*GLFWwindowclosefun)(GLFWwindow *);
typedef void (*GLFWwindowposfun)(GLFWwindow *, int, int);
typedef void (*GLFWwindowiconifyfun)(GLFWwindow *, int);
typedef void (*GLFWwindowmaximizefun)(GLFWwindow *, int);
typedef void (*GLFWwindowrefreshfun)(GLFWwindow *);
typedef void (*GLFWwindowcontentscalefun)(GLFWwindow *, float, float);
typedef void (*GLFWdropfun)(GLFWwindow *, int, const char **);
typedef void (*GLFWjoystickfun)(int, int);
typedef void (*GLFWmonitorfun)(GLFWmonitor *, int);

typedef struct {
    int width;
    int height;
    int redBits;
    int greenBits;
    int blueBits;
    int refreshRate;
} GLFWvidmode;

/* --- Bridge state ------------------------------------------------------- */

static NikGlfwCore g_core;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;
static bool g_initialised = false;

/* One window, one monitor: these are handles, never dereferenced. */
static GLFWwindow *const NIK_WINDOW = (GLFWwindow *) 0x4E494B57;  /* "NIKW" */
static GLFWmonitor *const NIK_MONITOR = (GLFWmonitor *) 0x4E494B4D; /* "NIKM" */

static void *g_window_user_pointer = NULL;
static double g_time_origin = 0.0;

static EGLDisplay g_display = EGL_NO_DISPLAY;
static EGLSurface g_surface = EGL_NO_SURFACE;
static EGLContext g_context = EGL_NO_CONTEXT;
static EGLConfig g_config = NULL;
static ANativeWindow *g_native_window = NULL;

static struct {
    GLFWerrorfun error;
    GLFWkeyfun key;
    GLFWcharfun character;
    GLFWcharmodsfun char_mods;
    GLFWmousebuttonfun mouse_button;
    GLFWcursorposfun cursor_pos;
    GLFWcursorenterfun cursor_enter;
    GLFWscrollfun scroll;
    GLFWwindowsizefun window_size;
    GLFWframebuffersizefun framebuffer_size;
    GLFWwindowfocusfun window_focus;
    GLFWwindowclosefun window_close;
    GLFWwindowposfun window_pos;
    GLFWwindowiconifyfun window_iconify;
    GLFWwindowmaximizefun window_maximize;
    GLFWwindowrefreshfun window_refresh;
    GLFWwindowcontentscalefun content_scale;
    GLFWdropfun drop;
    GLFWjoystickfun joystick;
    GLFWmonitorfun monitor;
} g_callbacks;

/* Shared with the JNI layer so Kotlin can feed events in. */
NikGlfwCore *nikglfw_core(void) {
    return &g_core;
}

pthread_mutex_t *nikglfw_lock(void) {
    return &g_lock;
}

void nikglfw_set_native_window(ANativeWindow *window) {
    pthread_mutex_lock(&g_lock);
    g_native_window = window;
    pthread_mutex_unlock(&g_lock);
}

static double now_seconds(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (double) ts.tv_sec + (double) ts.tv_nsec / 1e9;
}

/* --- Initialisation ----------------------------------------------------- */

NIK_EXPORT int glfwInit(void) {
    pthread_mutex_lock(&g_lock);
    if (!g_initialised) {
        /* A sane default until the real surface arrives; Minecraft reads the
         * framebuffer size during startup and a zero would divide by it. */
        nik_core_init(&g_core, 1280, 720);
        g_time_origin = now_seconds();
        g_initialised = true;
        LOGI("NikLauncher GLFW bridge initialised");
    }
    pthread_mutex_unlock(&g_lock);
    return 1;
}

NIK_EXPORT void glfwTerminate(void) {
    pthread_mutex_lock(&g_lock);
    g_initialised = false;
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT void glfwGetVersion(int *major, int *minor, int *revision) {
    if (major) *major = 3;
    if (minor) *minor = 3;
    if (revision) *revision = 0;
}

NIK_EXPORT const char *glfwGetVersionString(void) {
    return "3.3.0 NikLauncher Android bridge";
}

NIK_EXPORT GLFWerrorfun glfwSetErrorCallback(GLFWerrorfun callback) {
    GLFWerrorfun previous = g_callbacks.error;
    g_callbacks.error = callback;
    return previous;
}

NIK_EXPORT int glfwGetError(const char **description) {
    if (description) *description = NULL;
    return 0;
}

/* --- Window ------------------------------------------------------------- */

NIK_EXPORT void glfwDefaultWindowHints(void) {}
NIK_EXPORT void glfwWindowHint(int hint, int value) { (void) hint; (void) value; }
NIK_EXPORT void glfwWindowHintString(int hint, const char *value) { (void) hint; (void) value; }

static bool create_egl_context(void) {
    g_display = eglGetDisplay(EGL_DEFAULT_DISPLAY);
    if (g_display == EGL_NO_DISPLAY) {
        LOGE("eglGetDisplay failed");
        return false;
    }
    if (!eglInitialize(g_display, NULL, NULL)) {
        LOGE("eglInitialize failed: 0x%x", eglGetError());
        return false;
    }

    /* Minecraft needs a depth buffer; 24 bits with an 8-bit stencil is what
     * the desktop game asks GLFW for, and every Adreno supports it. */
    const EGLint config_attributes[] = {
        EGL_SURFACE_TYPE, EGL_WINDOW_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_RED_SIZE, 8,
        EGL_GREEN_SIZE, 8,
        EGL_BLUE_SIZE, 8,
        EGL_ALPHA_SIZE, 8,
        EGL_DEPTH_SIZE, 24,
        EGL_STENCIL_SIZE, 8,
        EGL_NONE,
    };

    EGLint config_count = 0;
    if (!eglChooseConfig(g_display, config_attributes, &g_config, 1, &config_count) ||
        config_count == 0) {
        LOGE("no EGL config with a 24 bit depth buffer: 0x%x", eglGetError());
        return false;
    }

    if (g_native_window == NULL) {
        LOGE("no ANativeWindow attached; the surface must be set before the game starts");
        return false;
    }

    g_surface = eglCreateWindowSurface(g_display, g_config, g_native_window, NULL);
    if (g_surface == EGL_NO_SURFACE) {
        LOGE("eglCreateWindowSurface failed: 0x%x", eglGetError());
        return false;
    }

    const EGLint context_attributes[] = {EGL_CONTEXT_CLIENT_VERSION, 2, EGL_NONE};
    g_context = eglCreateContext(g_display, g_config, EGL_NO_CONTEXT, context_attributes);
    if (g_context == EGL_NO_CONTEXT) {
        LOGE("eglCreateContext failed: 0x%x", eglGetError());
        return false;
    }

    LOGI("EGL context created");
    return true;
}

NIK_EXPORT GLFWwindow *glfwCreateWindow(int width, int height, const char *title,
                                        GLFWmonitor *monitor, GLFWwindow *share) {
    (void) title;
    (void) monitor;
    (void) share;

    pthread_mutex_lock(&g_lock);
    g_core.window_width = width > 0 ? width : g_core.window_width;
    g_core.window_height = height > 0 ? height : g_core.window_height;
    bool ok = create_egl_context();
    pthread_mutex_unlock(&g_lock);

    return ok ? NIK_WINDOW : NULL;
}

NIK_EXPORT void glfwDestroyWindow(GLFWwindow *window) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    if (g_display != EGL_NO_DISPLAY) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        if (g_context != EGL_NO_CONTEXT) eglDestroyContext(g_display, g_context);
        if (g_surface != EGL_NO_SURFACE) eglDestroySurface(g_display, g_surface);
        g_context = EGL_NO_CONTEXT;
        g_surface = EGL_NO_SURFACE;
    }
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT void glfwMakeContextCurrent(GLFWwindow *window) {
    if (window == NULL) {
        eglMakeCurrent(g_display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
        return;
    }
    if (!eglMakeCurrent(g_display, g_surface, g_surface, g_context)) {
        LOGE("eglMakeCurrent failed: 0x%x", eglGetError());
    }
}

NIK_EXPORT GLFWwindow *glfwGetCurrentContext(void) {
    return eglGetCurrentContext() == EGL_NO_CONTEXT ? NULL : NIK_WINDOW;
}

NIK_EXPORT void glfwSwapBuffers(GLFWwindow *window) {
    (void) window;
    if (g_display != EGL_NO_DISPLAY && g_surface != EGL_NO_SURFACE) {
        eglSwapBuffers(g_display, g_surface);
    }
}

NIK_EXPORT void glfwSwapInterval(int interval) {
    if (g_display != EGL_NO_DISPLAY) {
        eglSwapInterval(g_display, interval);
    }
}

NIK_EXPORT void glfwGetWindowSize(GLFWwindow *window, int *width, int *height) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    if (width) *width = g_core.window_width;
    if (height) *height = g_core.window_height;
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT void glfwGetFramebufferSize(GLFWwindow *window, int *width, int *height) {
    /* The surface is the framebuffer here: there is no separate scaling. */
    glfwGetWindowSize(window, width, height);
}

NIK_EXPORT void glfwSetWindowSize(GLFWwindow *window, int width, int height) {
    (void) window; (void) width; (void) height;
    /* The window is the screen; the game does not get to resize it. */
}

NIK_EXPORT void glfwGetWindowPos(GLFWwindow *window, int *x, int *y) {
    (void) window;
    if (x) *x = 0;
    if (y) *y = 0;
}

NIK_EXPORT void glfwSetWindowPos(GLFWwindow *window, int x, int y) {
    (void) window; (void) x; (void) y;
}

NIK_EXPORT void glfwGetWindowContentScale(GLFWwindow *window, float *xscale, float *yscale) {
    (void) window;
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

NIK_EXPORT void glfwGetWindowFrameSize(GLFWwindow *window, int *left, int *top,
                                       int *right, int *bottom) {
    (void) window;
    if (left) *left = 0;
    if (top) *top = 0;
    if (right) *right = 0;
    if (bottom) *bottom = 0;
}

NIK_EXPORT void glfwSetWindowTitle(GLFWwindow *window, const char *title) {
    (void) window; (void) title;
}

NIK_EXPORT void glfwSetWindowIcon(GLFWwindow *window, int count, const void *images) {
    (void) window; (void) count; (void) images;
}

NIK_EXPORT void glfwShowWindow(GLFWwindow *window) { (void) window; }
NIK_EXPORT void glfwHideWindow(GLFWwindow *window) { (void) window; }
NIK_EXPORT void glfwFocusWindow(GLFWwindow *window) { (void) window; }
NIK_EXPORT void glfwRequestWindowAttention(GLFWwindow *window) { (void) window; }
NIK_EXPORT void glfwIconifyWindow(GLFWwindow *window) { (void) window; }
NIK_EXPORT void glfwRestoreWindow(GLFWwindow *window) { (void) window; }
NIK_EXPORT void glfwMaximizeWindow(GLFWwindow *window) { (void) window; }

NIK_EXPORT int glfwWindowShouldClose(GLFWwindow *window) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    int result = g_core.should_close ? 1 : 0;
    pthread_mutex_unlock(&g_lock);
    return result;
}

NIK_EXPORT void glfwSetWindowShouldClose(GLFWwindow *window, int value) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    g_core.should_close = value != 0;
    pthread_mutex_unlock(&g_lock);
}

#define NIK_GLFW_FOCUSED 0x00020001
#define NIK_GLFW_VISIBLE 0x00020004

NIK_EXPORT int glfwGetWindowAttrib(GLFWwindow *window, int attribute) {
    (void) window;
    switch (attribute) {
    case NIK_GLFW_FOCUSED:
        return g_core.focused ? 1 : 0;
    case NIK_GLFW_VISIBLE:
        return 1;
    default:
        return 0;
    }
}

NIK_EXPORT void glfwSetWindowAttrib(GLFWwindow *window, int attribute, int value) {
    (void) window; (void) attribute; (void) value;
}

NIK_EXPORT void glfwSetWindowUserPointer(GLFWwindow *window, void *pointer) {
    (void) window;
    g_window_user_pointer = pointer;
}

NIK_EXPORT void *glfwGetWindowUserPointer(GLFWwindow *window) {
    (void) window;
    return g_window_user_pointer;
}

NIK_EXPORT GLFWmonitor *glfwGetWindowMonitor(GLFWwindow *window) {
    (void) window;
    return NULL; /* Never reports fullscreen: the game must not try to change mode. */
}

NIK_EXPORT void glfwSetWindowMonitor(GLFWwindow *window, GLFWmonitor *monitor, int x, int y,
                                     int width, int height, int refreshRate) {
    (void) window; (void) monitor; (void) x; (void) y;
    (void) width; (void) height; (void) refreshRate;
}

/* --- Events ------------------------------------------------------------- */

static void dispatch(const NikEvent *event) {
    switch (event->type) {
    case NIK_EVENT_KEY:
        if (g_callbacks.key) {
            g_callbacks.key(NIK_WINDOW, event->key, event->scancode, event->action, event->mods);
        }
        break;
    case NIK_EVENT_CHAR:
        if (g_callbacks.character) {
            g_callbacks.character(NIK_WINDOW, event->codepoint);
        }
        if (g_callbacks.char_mods) {
            g_callbacks.char_mods(NIK_WINDOW, event->codepoint, event->mods);
        }
        break;
    case NIK_EVENT_MOUSE_BUTTON:
        if (g_callbacks.mouse_button) {
            g_callbacks.mouse_button(NIK_WINDOW, event->key, event->action, event->mods);
        }
        break;
    case NIK_EVENT_CURSOR_POS:
        if (g_callbacks.cursor_pos) {
            g_callbacks.cursor_pos(NIK_WINDOW, event->x, event->y);
        }
        break;
    case NIK_EVENT_SCROLL:
        if (g_callbacks.scroll) {
            g_callbacks.scroll(NIK_WINDOW, event->x, event->y);
        }
        break;
    case NIK_EVENT_WINDOW_SIZE:
        if (g_callbacks.window_size) {
            g_callbacks.window_size(NIK_WINDOW, (int) event->x, (int) event->y);
        }
        if (g_callbacks.framebuffer_size) {
            g_callbacks.framebuffer_size(NIK_WINDOW, (int) event->x, (int) event->y);
        }
        break;
    case NIK_EVENT_WINDOW_FOCUS:
        if (g_callbacks.window_focus) {
            g_callbacks.window_focus(NIK_WINDOW, event->key);
        }
        break;
    case NIK_EVENT_WINDOW_CLOSE:
        if (g_callbacks.window_close) {
            g_callbacks.window_close(NIK_WINDOW);
        }
        break;
    }
}

NIK_EXPORT void glfwPollEvents(void) {
    /*
     * Events are drained under the lock but dispatched outside it: a callback
     * runs arbitrary game code, and holding the lock across it would let the
     * input thread block on Minecraft's frame handling.
     */
    for (;;) {
        NikEvent event;
        pthread_mutex_lock(&g_lock);
        bool have = nik_core_pop(&g_core, &event);
        if (have) {
            nik_core_apply(&g_core, &event);
        }
        pthread_mutex_unlock(&g_lock);

        if (!have) {
            break;
        }
        dispatch(&event);
    }
}

NIK_EXPORT void glfwWaitEvents(void) { glfwPollEvents(); }
NIK_EXPORT void glfwWaitEventsTimeout(double timeout) { (void) timeout; glfwPollEvents(); }
NIK_EXPORT void glfwPostEmptyEvent(void) {}

/* --- Input -------------------------------------------------------------- */

NIK_EXPORT int glfwGetKey(GLFWwindow *window, int key) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    int state = nik_core_get_key(&g_core, key);
    pthread_mutex_unlock(&g_lock);
    return state;
}

NIK_EXPORT int glfwGetMouseButton(GLFWwindow *window, int button) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    int state = nik_core_get_mouse_button(&g_core, button);
    pthread_mutex_unlock(&g_lock);
    return state;
}

NIK_EXPORT void glfwGetCursorPos(GLFWwindow *window, double *x, double *y) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    nik_core_get_cursor_pos(&g_core, x, y);
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT void glfwSetCursorPos(GLFWwindow *window, double x, double y) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    nik_core_set_cursor_pos(&g_core, x, y);
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT int glfwGetInputMode(GLFWwindow *window, int mode) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    int value = nik_core_get_input_mode(&g_core, mode);
    pthread_mutex_unlock(&g_lock);
    return value;
}

NIK_EXPORT void glfwSetInputMode(GLFWwindow *window, int mode, int value) {
    (void) window;
    pthread_mutex_lock(&g_lock);
    nik_core_set_input_mode(&g_core, mode, value);
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT int glfwRawMouseMotionSupported(void) { return 1; }

NIK_EXPORT const char *glfwGetKeyName(int key, int scancode) {
    (void) key; (void) scancode;
    return NULL;
}

NIK_EXPORT int glfwGetKeyScancode(int key) { return key; }

NIK_EXPORT GLFWcursor *glfwCreateStandardCursor(int shape) {
    (void) shape;
    return NULL; /* There is no cursor to shape; Minecraft tolerates NULL. */
}

NIK_EXPORT GLFWcursor *glfwCreateCursor(const void *image, int xhot, int yhot) {
    (void) image; (void) xhot; (void) yhot;
    return NULL;
}

NIK_EXPORT void glfwDestroyCursor(GLFWcursor *cursor) { (void) cursor; }
NIK_EXPORT void glfwSetCursor(GLFWwindow *window, GLFWcursor *cursor) {
    (void) window; (void) cursor;
}

NIK_EXPORT const char *glfwGetClipboardString(GLFWwindow *window) {
    (void) window;
    return "";
}

NIK_EXPORT void glfwSetClipboardString(GLFWwindow *window, const char *string) {
    (void) window; (void) string;
}

/* --- Callback registration ---------------------------------------------- */

#define NIK_DEFINE_CALLBACK_SETTER(name, type, field)                          \
    NIK_EXPORT type name(GLFWwindow *window, type callback) {                  \
        (void) window;                                                         \
        type previous = g_callbacks.field;                                     \
        g_callbacks.field = callback;                                          \
        return previous;                                                       \
    }

NIK_DEFINE_CALLBACK_SETTER(glfwSetKeyCallback, GLFWkeyfun, key)
NIK_DEFINE_CALLBACK_SETTER(glfwSetCharCallback, GLFWcharfun, character)
NIK_DEFINE_CALLBACK_SETTER(glfwSetCharModsCallback, GLFWcharmodsfun, char_mods)
NIK_DEFINE_CALLBACK_SETTER(glfwSetMouseButtonCallback, GLFWmousebuttonfun, mouse_button)
NIK_DEFINE_CALLBACK_SETTER(glfwSetCursorPosCallback, GLFWcursorposfun, cursor_pos)
NIK_DEFINE_CALLBACK_SETTER(glfwSetCursorEnterCallback, GLFWcursorenterfun, cursor_enter)
NIK_DEFINE_CALLBACK_SETTER(glfwSetScrollCallback, GLFWscrollfun, scroll)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowSizeCallback, GLFWwindowsizefun, window_size)
NIK_DEFINE_CALLBACK_SETTER(glfwSetFramebufferSizeCallback, GLFWframebuffersizefun, framebuffer_size)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowFocusCallback, GLFWwindowfocusfun, window_focus)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowCloseCallback, GLFWwindowclosefun, window_close)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowPosCallback, GLFWwindowposfun, window_pos)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowIconifyCallback, GLFWwindowiconifyfun, window_iconify)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowMaximizeCallback, GLFWwindowmaximizefun, window_maximize)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowRefreshCallback, GLFWwindowrefreshfun, window_refresh)
NIK_DEFINE_CALLBACK_SETTER(glfwSetWindowContentScaleCallback, GLFWwindowcontentscalefun, content_scale)
NIK_DEFINE_CALLBACK_SETTER(glfwSetDropCallback, GLFWdropfun, drop)

NIK_EXPORT GLFWjoystickfun glfwSetJoystickCallback(GLFWjoystickfun callback) {
    GLFWjoystickfun previous = g_callbacks.joystick;
    g_callbacks.joystick = callback;
    return previous;
}

NIK_EXPORT GLFWmonitorfun glfwSetMonitorCallback(GLFWmonitorfun callback) {
    GLFWmonitorfun previous = g_callbacks.monitor;
    g_callbacks.monitor = callback;
    return previous;
}

/* --- Monitors ----------------------------------------------------------- */

NIK_EXPORT GLFWmonitor *glfwGetPrimaryMonitor(void) { return NIK_MONITOR; }

NIK_EXPORT GLFWmonitor **glfwGetMonitors(int *count) {
    static GLFWmonitor *monitors[1];
    monitors[0] = NIK_MONITOR;
    if (count) *count = 1;
    return monitors;
}

NIK_EXPORT const GLFWvidmode *glfwGetVideoMode(GLFWmonitor *monitor) {
    (void) monitor;
    static GLFWvidmode mode;
    pthread_mutex_lock(&g_lock);
    mode.width = g_core.window_width;
    mode.height = g_core.window_height;
    pthread_mutex_unlock(&g_lock);
    mode.redBits = 8;
    mode.greenBits = 8;
    mode.blueBits = 8;
    /* Reported as 60 rather than the panel's 120: Minecraft uses this to size
     * its frame pacing, and claiming 120 would invite the game to chase a rate
     * this project deliberately does not target. */
    mode.refreshRate = 60;
    return &mode;
}

NIK_EXPORT const GLFWvidmode *glfwGetVideoModes(GLFWmonitor *monitor, int *count) {
    if (count) *count = 1;
    return glfwGetVideoMode(monitor);
}

NIK_EXPORT void glfwGetMonitorPos(GLFWmonitor *monitor, int *x, int *y) {
    (void) monitor;
    if (x) *x = 0;
    if (y) *y = 0;
}

NIK_EXPORT void glfwGetMonitorWorkarea(GLFWmonitor *monitor, int *x, int *y,
                                       int *width, int *height) {
    (void) monitor;
    if (x) *x = 0;
    if (y) *y = 0;
    pthread_mutex_lock(&g_lock);
    if (width) *width = g_core.window_width;
    if (height) *height = g_core.window_height;
    pthread_mutex_unlock(&g_lock);
}

NIK_EXPORT void glfwGetMonitorPhysicalSize(GLFWmonitor *monitor, int *width, int *height) {
    (void) monitor;
    if (width) *width = 150;
    if (height) *height = 70;
}

NIK_EXPORT void glfwGetMonitorContentScale(GLFWmonitor *monitor, float *xscale, float *yscale) {
    (void) monitor;
    if (xscale) *xscale = 1.0f;
    if (yscale) *yscale = 1.0f;
}

NIK_EXPORT const char *glfwGetMonitorName(GLFWmonitor *monitor) {
    (void) monitor;
    return "Android display";
}

NIK_EXPORT void glfwSetMonitorUserPointer(GLFWmonitor *monitor, void *pointer) {
    (void) monitor; (void) pointer;
}

NIK_EXPORT void *glfwGetMonitorUserPointer(GLFWmonitor *monitor) {
    (void) monitor;
    return NULL;
}

/* --- Time --------------------------------------------------------------- */

NIK_EXPORT double glfwGetTime(void) { return now_seconds() - g_time_origin; }

NIK_EXPORT void glfwSetTime(double time) { g_time_origin = now_seconds() - time; }

NIK_EXPORT uint64_t glfwGetTimerValue(void) {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t) ts.tv_sec * 1000000000ULL + (uint64_t) ts.tv_nsec;
}

NIK_EXPORT uint64_t glfwGetTimerFrequency(void) { return 1000000000ULL; }

/* --- Context ------------------------------------------------------------ */

NIK_EXPORT void *glfwGetProcAddress(const char *name) {
    return (void *) eglGetProcAddress(name);
}

NIK_EXPORT int glfwExtensionSupported(const char *extension) {
    const char *extensions = (const char *) glGetString(GL_EXTENSIONS);
    return (extensions != NULL && strstr(extensions, extension) != NULL) ? 1 : 0;
}

/* --- Joysticks ---------------------------------------------------------- */

/*
 * Reported as absent. NikLauncher handles gamepads itself, translating them
 * into the keyboard and mouse input Minecraft actually listens for, so letting
 * the game also see a joystick would double every press.
 */
NIK_EXPORT int glfwJoystickPresent(int joystick) { (void) joystick; return 0; }

NIK_EXPORT const float *glfwGetJoystickAxes(int joystick, int *count) {
    (void) joystick;
    if (count) *count = 0;
    return NULL;
}

NIK_EXPORT const unsigned char *glfwGetJoystickButtons(int joystick, int *count) {
    (void) joystick;
    if (count) *count = 0;
    return NULL;
}

NIK_EXPORT const char *glfwGetJoystickName(int joystick) { (void) joystick; return NULL; }
NIK_EXPORT const char *glfwGetJoystickGUID(int joystick) { (void) joystick; return NULL; }
NIK_EXPORT int glfwJoystickIsGamepad(int joystick) { (void) joystick; return 0; }
NIK_EXPORT const char *glfwGetGamepadName(int joystick) { (void) joystick; return NULL; }
NIK_EXPORT int glfwGetGamepadState(int joystick, void *state) {
    (void) joystick; (void) state;
    return 0;
}
NIK_EXPORT int glfwUpdateGamepadMappings(const char *string) { (void) string; return 1; }
NIK_EXPORT void glfwSetJoystickUserPointer(int joystick, void *pointer) {
    (void) joystick; (void) pointer;
}
NIK_EXPORT void *glfwGetJoystickUserPointer(int joystick) { (void) joystick; return NULL; }

/* --- Vulkan ------------------------------------------------------------- */

NIK_EXPORT int glfwVulkanSupported(void) { return 0; }

NIK_EXPORT const char **glfwGetRequiredInstanceExtensions(unsigned int *count) {
    if (count) *count = 0;
    return NULL;
}
