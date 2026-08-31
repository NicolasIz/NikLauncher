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
