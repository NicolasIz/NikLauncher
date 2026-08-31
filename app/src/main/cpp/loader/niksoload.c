#include "niksoload.h"

#include <dirent.h>
#include <dlfcn.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>

/*
 * Bounded on purpose. A runtime pack's directory holds a few dozen objects at
 * most; anything far beyond that is not a pack we assembled, and a fixed
 * ceiling keeps this free of allocation on a path that runs while the game is
 * starting.
 */
#define MAX_LIBRARIES 96
#define MAX_NAME 128
#define MAX_PATH 512
#define MAX_PASSES 8

/* Where a pack keeps the libraries it supplies only when the device does not. */
static const char *const COMPAT_SUBDIRECTORY = "compat";

static int ends_with_so(const char *name) {
    size_t length = strlen(name);
    return length > 3 && strcmp(name + length - 3, ".so") == 0;
}

static int flags_for(int global) {
    return RTLD_NOW | (global ? RTLD_GLOBAL : RTLD_LOCAL);
}

/*
 * Copies the directory part of `path`. Returns 0 for a bare soname, which has
 * no directory and needs none.
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

int niksoload_directory(const char *directory, const char *skip, int global) {
    if (directory == NULL) return 0;

    DIR *dir = opendir(directory);
    /* A directory that is not there is not an error; not every pack has one. */
    if (dir == NULL) return 0;

    char names[MAX_LIBRARIES][MAX_NAME];
    int loaded[MAX_LIBRARIES];
    int count = 0;

    struct dirent *entry;
    while (count < MAX_LIBRARIES && (entry = readdir(dir)) != NULL) {
        if (!ends_with_so(entry->d_name)) continue;
        if (skip != NULL && strcmp(entry->d_name, skip) == 0) continue;
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

            char full[MAX_PATH];
            int written = snprintf(full, sizeof(full), "%s/%s", directory, names[i]);
            if (written < 0 || (size_t) written >= sizeof(full)) continue;

            if (dlopen(full, flags_for(global)) != NULL) {
                loaded[i] = 1;
                loaded_this_pass++;
                total++;
            }
        }
        /*
         * Nothing new: either everything that can load has, or what is left
         * depends on something outside this directory - a JDK's lib/ holds
         * libraries that need libjvm.so itself, and those are meant to stay
         * unloaded here. Another pass would repeat the same work.
         */
        if (loaded_this_pass == 0) break;
    }
    return total;
}

int niksoload_siblings(const char *path, int global) {
    char directory[MAX_NAME * 2];
    if (!directory_of(path, directory, sizeof(directory))) return 0;

    const char *slash = strrchr(path, '/');
    return niksoload_directory(directory, slash + 1, global);
}

int niksoload_compat(const char *path, int global) {
    char directory[MAX_NAME * 2];
    if (!directory_of(path, directory, sizeof(directory))) return 0;

    char compat[MAX_PATH];
    int written = snprintf(compat, sizeof(compat), "%s/%s", directory, COMPAT_SUBDIRECTORY);
    if (written < 0 || (size_t) written >= sizeof(compat)) return 0;

    DIR *dir = opendir(compat);
    if (dir == NULL) return 0;

    int loaded = 0;
    struct dirent *entry;
    while ((entry = readdir(dir)) != NULL) {
        if (!ends_with_so(entry->d_name)) continue;
        if (strlen(entry->d_name) >= MAX_NAME) continue;

        /*
         * The device's own first. An open by bare soname succeeding means the
         * platform provides this library, and ours must not shadow it - the
         * real one knows things ours never will.
         */
        void *platform = dlopen(entry->d_name, flags_for(global));
        if (platform != NULL) continue;

        char full[MAX_PATH];
        written = snprintf(full, sizeof(full), "%s/%s", compat, entry->d_name);
        if (written < 0 || (size_t) written >= sizeof(full)) continue;

        if (dlopen(full, flags_for(global)) != NULL) loaded++;
    }
    closedir(dir);
    return loaded;
}
