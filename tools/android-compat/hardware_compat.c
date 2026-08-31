/*
 * hw_get_module, which is the one symbol here that cannot be implemented.
 *
 * It hands out HAL modules - gralloc, above all - and reaching gralloc is
 * exactly what Android does not allow an app. So the answer is -ENOSYS, and
 * that is not a workaround: it is true, and Mesa is written for it.
 * u_gralloc_fallback_create logs "No gralloc hwmodule detected (video buffers
 * won't be supported)" and returns a working gralloc anyway, so Zink gets its
 * buffer information from elsewhere and only video buffers are lost, which
 * Minecraft never asks for.
 *
 * Leaving *module NULL matters as much as the return value. Mesa's fallback
 * allocates its state zeroed and, on the way out, calls dlclose on whatever
 * that pointer holds; and Mesa's own stub - which returns success without
 * setting it - makes the line after the call dereference an uninitialised
 * pointer. Both hazards are closed by writing NULL and reporting failure.
 */

#include <errno.h>
#include <stddef.h>

/*
 * The real declaration names struct hw_module_t, which lives in an AOSP header
 * no NDK ships. A pointer is a pointer: this has the same C linkage and the
 * same ABI, and it keeps the build free of headers we would have to vendor.
 */
int hw_get_module(const char *id, const void **module);

int hw_get_module(const char *id, const void **module) {
    (void) id;
    if (module != NULL) {
        *module = NULL;
    }
    return -ENOSYS;
}
