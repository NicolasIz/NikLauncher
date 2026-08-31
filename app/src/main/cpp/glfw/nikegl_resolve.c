#include "nikegl_resolve.h"

#include <dirent.h>
#include <dlfcn.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

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

/*
 * Bounded on purpose. A runtime pack's backend directory holds a couple of
 * dozen objects; anything far beyond that is not a pack we assembled, and a
 * fixed ceiling keeps this free of allocation on a path that runs while the
 * game is starting.
 */
#define MAX_SIBLINGS 64
#define MAX_NAME 128
#define MAX_PASSES 8

/* Where a pack keeps the libraries it only supplies when the device does not. */
static const char *const COMPAT_SUBDIRECTORY = "compat";

static int ends_with_so(const char *name) {
    size_t length = strlen(name);
    return length > 3 && strcmp(name + length - 3, ".so") == 0;
}

/*
 * Copies the directory part of `path` into `directory`. Returns 0 for a bare
 * soname, which has no directory and needs none.
 */
static int directory_of(const char *path, char *directory, size_t size) {
    if (path == NULL) return 0;
    const char *slash = strrchr(path, '/');
    if (slash == NULL) return 0;

    size_t length = (size_t) (slash - path);
    if (length == 0 || length >= size) return 0;
    memcpy(directory, path, length);
    directory[length] = '\0';
    return 1;
}

int nikegl_preload_compat(const char *path) {
    char directory[MAX_NAME];
    if (!directory_of(path, directory, sizeof(directory))) return 0;

    char compat[MAX_NAME * 2];
    int written = snprintf(compat, sizeof(compat), "%s/%s", directory, COMPAT_SUBDIRECTORY);
    if (written < 0 || (size_t) written >= sizeof(compat)) return 0;

    DIR *dir = opendir(compat);
    /* A pack without a compat directory is not an error; older packs have none. */
    if (dir == NULL) return 0;

    int loaded = 0;
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (!ends_with_so(entry->d_name)) continue;
        if (strlen(entry->d_name) >= MAX_NAME) continue;

        /*
         * The device's own first. A successful open by bare soname means the
         * platform provides this library and ours must not shadow it - the
         * real one knows things ours never will.
         */
        if (dlopen(entry->d_name, RTLD_NOW | RTLD_LOCAL) != NULL) continue;

        char full[MAX_NAME * 3];
        written = snprintf(full, sizeof(full), "%s/%s", compat, entry->d_name);
        if (written < 0 || (size_t) written >= sizeof(full)) continue;

        if (dlopen(full, RTLD_NOW | RTLD_LOCAL) != NULL) loaded++;
    }
    closedir(dir);
    return loaded;
}

int nikegl_preload_siblings(const char *path) {
    if (path == NULL) return 0;

    const char *slash = strrchr(path, '/');
    /* A bare soname: the platform's own, and the loader knows where it is. */
    if (slash == NULL) return 0;

    size_t directory_length = (size_t) (slash - path);
    if (directory_length == 0 || directory_length >= MAX_NAME) return 0;

    char directory[MAX_NAME];
    memcpy(directory, path, directory_length);
    directory[directory_length] = '\0';
    const char *target = slash + 1;

    DIR *dir = opendir(directory);
    if (dir == NULL) return 0;

    char names[MAX_SIBLINGS][MAX_NAME];
    int loaded[MAX_SIBLINGS];
    int count = 0;

    struct dirent *entry;
    while (count < MAX_SIBLINGS && (entry = readdir(dir)) != NULL) {
        if (!ends_with_so(entry->d_name)) continue;
        /* The target is opened by the caller, with its own error reporting. */
        if (strcmp(entry->d_name, target) == 0) continue;
        if (strlen(entry->d_name) >= MAX_NAME) continue;
        strcpy(names[count], entry->d_name);
        loaded[count] = 0;
        count++;
    }
    closedir(dir);

    int total = 0;
    for (int pass = 0; pass < MAX_PASSES; pass++) {
        int loaded_this_pass = 0;
        for (int i = 0; i < count; i++) {
            if (loaded[i]) continue;

            char full[MAX_NAME * 2 + 2];
            int written = snprintf(full, sizeof(full), "%s/%s", directory, names[i]);
            if (written < 0 || (size_t) written >= sizeof(full)) continue;

            /*
             * RTLD_LOCAL, as for the target: a pack's libraries must not
             * publish their symbols where the system driver's are already
             * bound. Being in the namespace is what satisfies a later
             * DT_NEEDED, and that does not depend on the symbols being global.
             */
            if (dlopen(full, RTLD_NOW | RTLD_LOCAL) != NULL) {
                loaded[i] = 1;
                loaded_this_pass++;
                total++;
            }
        }
        /*
         * Nothing new: either everything that can load has, or what is left
         * depends on something outside this directory. Either way another
         * pass would do the same work again. The caller's own open reports
         * the failure, with the loader's reason for it.
         */
        if (loaded_this_pass == 0) break;
    }
    return total;
}
