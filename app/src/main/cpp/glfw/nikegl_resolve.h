/*
 * Opening a shared object and pulling named symbols out of it.
 *
 * Deliberately free of EGL and Android headers, for the same reason
 * nikglfw_core.c is: it can then be compiled and exercised on the build host,
 * where no EGL headers exist, instead of only on a device. The typed EGL
 * function table that sits on top of this lives in nikegl.h.
 */

#ifndef NIKEGL_RESOLVE_H
#define NIKEGL_RESOLVE_H

/*
 * Which shared object to open: the one configured by the launcher when it
 * named a path, otherwise the platform's own libEGL. Never returns NULL, and
 * returns a pointer into `configured` when it uses it, so the caller keeps
 * ownership of the string.
 */
const char *nikegl_library_name(const char *configured);

void *nikegl_open(const char *path);
void nikegl_close(void *handle);

/*
 * Loads the shared objects sitting beside `path`, so that opening `path`
 * itself can resolve what it depends on. Returns how many were loaded.
 *
 * Android resolves a library's DT_NEEDED entries against the namespace the
 * app runs in. A runtime pack lives in the app's files directory, which is
 * not on that namespace's search path, and an LD_LIBRARY_PATH exported after
 * the process started is not consulted - so Mesa's libEGL, which names
 * libgallium_dri, libdrm and the C++ runtime, fails to open with "library not
 * found" even though all of them are in the same folder. A library already
 * loaded under its soname is found before any path is searched, so loading
 * the siblings first is what makes the dependencies resolvable.
 *
 * The dependency order is not known here and is deliberately not encoded:
 * passes repeat while any new library loads, so a chain resolves itself
 * however deep it goes. Passing a bare name rather than a path loads nothing,
 * which is right - that is the platform's own library and the loader already
 * knows where to look.
 */
int nikegl_preload_siblings(const char *path);

/*
 * Loads the compatibility libraries a pack carries in `compat/`, preferring
 * the device's own. Returns how many of ours were loaded.
 *
 * A pack ships small implementations of the platform-internal libraries Mesa
 * is linked against - libcutils, libhardware, libsync - because Android does
 * not hand those to an app. But "does not hand those to an app" is not true
 * of all of them on all devices: libsync is public on some. Where the device
 * offers the library, the device's is the one that should win, so each name
 * is tried as a bare soname first and ours is opened only when that fails.
 *
 * Shadowing a working platform library with our own smaller one would be the
 * same mistake as shipping Mesa's no-op stubs, and this is what avoids it.
 * Called before the siblings: Mesa's own libraries name these at load time.
 */
int nikegl_preload_compat(const char *path);

/*
 * Fills slots[0..count) with the addresses of names[0..count).
 *
 * Returns -1 when every symbol resolved, otherwise the index of the first one
 * that did not - the index rather than a bare failure, so the caller can name
 * the missing symbol instead of reporting that "something" was absent.
 */
int nikegl_resolve(void *handle, const char *const *names, void **slots, int count);

/* The most recent dlopen or dlsym failure, or NULL if there has not been one. */
const char *nikegl_last_error(void);

#endif /* NIKEGL_RESOLVE_H */
