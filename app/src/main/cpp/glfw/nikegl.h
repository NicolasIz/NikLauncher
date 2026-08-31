/*
 * The EGL entry points the bridge uses, as a table of pointers resolved at
 * runtime rather than symbols bound at link time.
 *
 * This exists so the Zink backend can work at all. Zink is desktop GL over
 * Vulkan and brings its own libEGL inside the Mesa runtime pack; if this
 * library carried a DT_NEEDED on the system libEGL, the loader would bind
 * every egl* call to the device's driver before Mesa's copy was ever opened,
 * and the pack would be dead weight. Resolving by hand lets the launcher say
 * which libEGL to use.
 *
 * The EGL_* constants stay compile-time: they are Khronos spec values, the
 * same number in every implementation.
 */

#ifndef NIKEGL_H
#define NIKEGL_H

#include <EGL/egl.h>

typedef struct NikEgl {
    EGLDisplay (*GetDisplay)(EGLNativeDisplayType display_id);
    EGLBoolean (*Initialize)(EGLDisplay dpy, EGLint *major, EGLint *minor);
    EGLBoolean (*ChooseConfig)(EGLDisplay dpy, const EGLint *attrib_list,
                               EGLConfig *configs, EGLint config_size, EGLint *num_config);
    EGLSurface (*CreateWindowSurface)(EGLDisplay dpy, EGLConfig config,
                                      EGLNativeWindowType win, const EGLint *attrib_list);
    EGLContext (*CreateContext)(EGLDisplay dpy, EGLConfig config,
                                EGLContext share_context, const EGLint *attrib_list);
    EGLBoolean (*MakeCurrent)(EGLDisplay dpy, EGLSurface draw, EGLSurface read, EGLContext ctx);
    EGLContext (*GetCurrentContext)(void);
    EGLBoolean (*DestroyContext)(EGLDisplay dpy, EGLContext ctx);
    EGLBoolean (*DestroySurface)(EGLDisplay dpy, EGLSurface surface);
    EGLBoolean (*SwapBuffers)(EGLDisplay dpy, EGLSurface surface);
    EGLBoolean (*SwapInterval)(EGLDisplay dpy, EGLint interval);
    EGLint (*GetError)(void);
    __eglMustCastToProperFunctionPointerType (*GetProcAddress)(const char *procname);
} NikEgl;

/* Zero until nikegl_load succeeds. */
extern NikEgl nikegl;

/*
 * Opens `path` and resolves the table from it, or the platform's own libEGL
 * when `path` is NULL or empty. Returns 0 on success, and is a no-op once it
 * has succeeded once. On failure the table is left zeroed and nikegl_error
 * explains why.
 */
int nikegl_load(const char *path);

int nikegl_is_loaded(void);

const char *nikegl_error(void);

#endif /* NIKEGL_H */
