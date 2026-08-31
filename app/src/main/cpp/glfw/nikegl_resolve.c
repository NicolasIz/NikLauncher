#include "nikegl_resolve.h"

#include <dlfcn.h>
#include <stddef.h>

/*
 * The platform's own EGL. Used when the launcher has not named one, which is
 * the case for the gl4es and LTW backends: those run on the device's GLES
 * driver and want the system loader to find it as usual.
 */
static const char *const SYSTEM_EGL = "libEGL.so";

static const char *g_error = NULL;

const char *nikegl_library_name(const char *configured) {
    if (configured == NULL || configured[0] == '\0') {
        return SYSTEM_EGL;
    }
    return configured;
}

void *nikegl_open(const char *path) {
    /*
     * RTLD_LOCAL so that a pack's libEGL does not publish its symbols into the
     * global namespace, where they would collide with the system EGL that
     * other libraries in the process are already bound to.
     */
    void *handle = dlopen(path, RTLD_NOW | RTLD_LOCAL);
    if (handle == NULL) {
        g_error = dlerror();
    }
    return handle;
}

void nikegl_close(void *handle) {
    if (handle != NULL) {
        dlclose(handle);
    }
}

int nikegl_resolve(void *handle, const char *const *names, void **slots, int count) {
    if (handle == NULL || names == NULL || slots == NULL) {
        return 0;
    }
    for (int i = 0; i < count; i++) {
        dlerror();
        /*
         * Converting the object pointer dlsym returns into a function pointer
         * is not something ISO C defines, but POSIX requires dlsym to be used
         * exactly this way, and every ABI this code targets has one pointer
         * representation.
         */
        void *symbol = dlsym(handle, names[i]);
        if (symbol == NULL) {
            const char *why = dlerror();
            if (why != NULL) {
                g_error = why;
            }
            return i;
        }
        slots[i] = symbol;
    }
    return -1;
}

const char *nikegl_last_error(void) {
    return g_error;
}

