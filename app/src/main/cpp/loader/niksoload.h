/*
 * Making a downloaded shared object openable.
 *
 * Android resolves a library's DT_NEEDED entries against the linker namespace
 * the app runs in. A runtime pack lives in the app's files directory, which is
 * not on that namespace's search path, and an LD_LIBRARY_PATH exported after
 * the process has started is never consulted - Bionic reads it once, at
 * startup. So a dlopen by absolute path fails on the first dependency it
 * cannot find, however carefully the pack was assembled: libjvm.so on the C++
 * runtime, Mesa's libEGL on libgallium_dri and libdrm.
 *
 * A library already loaded under its soname is found before any path is
 * searched. Loading the dependencies first is therefore the whole fix, and it
 * is what everything here does.
 *
 * Free of Android headers so it can be exercised on the build host, where the
 * loader behaves the same way for a directory nothing knows about.
 */

#ifndef NIKSOLOAD_H
#define NIKSOLOAD_H

/*
 * Loads every shared object in `directory`, except one named `skip` (pass NULL
 * to skip nothing). Returns how many were loaded.
 *
 * The dependency order is not encoded: passes repeat while any new library
 * loads, so a chain resolves itself however deep it goes. A library that never
 * loads is not an error here - the caller's own open reports the failure, with
 * the loader's reason for it, which is a better message than any this could
 * invent.
 *
 * `global` publishes the loaded symbols into the process's global scope. That
 * is wanted for the JVM, whose own libraries are opened later by the VM itself
 * and need the same C++ runtime; it is not wanted for a graphics pack, whose
 * symbols would otherwise collide with the system driver's.
 */
int niksoload_directory(const char *directory, const char *skip, int global);

/*
 * The same, for the directory `path` sits in, skipping `path` itself. A bare
 * soname with no directory loads nothing, which is right: that is a platform
 * library and the loader already knows where it is.
 */
int niksoload_siblings(const char *path, int global);

/*
 * Loads the libraries a pack keeps in `compat/`, preferring the device's own.
 *
 * A pack carries small implementations of platform-internal libraries that
 * Android does not hand an app - Mesa needs libcutils, libhardware and
 * libsync. But that is not true of all of them on all devices: libsync is
 * public on some. Where the device offers the library, the device's is the one
 * that should win, so each name is tried as a bare soname first and the pack's
 * copy is opened only when that fails. Returns how many of the pack's were
 * used.
 *
 * Shadowing a working platform library with a smaller one of our own would be
 * as wrong as shipping a stub, and this is what prevents it.
 */
int niksoload_compat(const char *path, int global);

#endif /* NIKSOLOAD_H */
