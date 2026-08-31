#include "nikegl.h"

#include "nikegl_resolve.h"

#include <stddef.h>
#include <stdio.h>
#include <string.h>

NikEgl nikegl;

/*
 * Same order as the fields of NikEgl. The static assert below is what keeps
 * that true: the table is filled by writing through &nikegl as an array of
 * pointers, so a field added here without a name added there - or the two put
 * in different orders - would silently bind calls to the wrong function.
 */
static const char *const NAMES[] = {
    "eglGetDisplay",
    "eglInitialize",
    "eglChooseConfig",
    "eglCreateWindowSurface",
    "eglCreateContext",
    "eglMakeCurrent",
    "eglGetCurrentContext",
    "eglDestroyContext",
    "eglDestroySurface",
    "eglSwapBuffers",
    "eglSwapInterval",
    "eglGetError",
    "eglGetProcAddress",
};

#define NIKEGL_COUNT ((int) (sizeof(NAMES) / sizeof(NAMES[0])))

_Static_assert(sizeof(NikEgl) == (size_t) NIKEGL_COUNT * sizeof(void *),
               "NikEgl must be exactly one function pointer per name in NAMES");

static void *g_handle = NULL;
static char g_error[256];

int nikegl_is_loaded(void) {
    return g_handle != NULL;
}

const char *nikegl_error(void) {
    return g_error[0] == '\0' ? NULL : g_error;
}

int nikegl_load(const char *path) {
    if (g_handle != NULL) {
        return 0;
    }
    g_error[0] = '\0';

    const char *name = nikegl_library_name(path);

    /*
     * Before the open, not after. A pack's libEGL names libraries that sit
     * beside it in a directory the loader does not search, so opening it
     * cold fails on the first DT_NEEDED it cannot find. Loading them first
     * puts them in the namespace under their sonames, which is where the
     * loader looks before it searches any path.
     *
     * Compat before siblings, because Mesa's own libraries name libcutils and
     * libsync at load time and would otherwise fail before we got to them.
     * Both cost nothing for the platform's own libEGL, which is named rather
     * than pathed.
     */
    nikegl_preload_compat(name);
    nikegl_preload_siblings(name);

    void *handle = nikegl_open(name);
    if (handle == NULL) {
        const char *why = nikegl_last_error();
        snprintf(g_error, sizeof(g_error), "cannot open %s: %s",
                 name, why != NULL ? why : "unknown error");
        return -1;
    }

    int missing = nikegl_resolve(handle, NAMES, (void **) &nikegl, NIKEGL_COUNT);
    if (missing >= 0) {
        snprintf(g_error, sizeof(g_error), "%s does not export %s", name, NAMES[missing]);
        memset(&nikegl, 0, sizeof(nikegl));
        nikegl_close(handle);
        return -1;
    }

    g_handle = handle;
    return 0;
}
