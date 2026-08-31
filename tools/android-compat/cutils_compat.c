/*
 * The parts of libcutils that Mesa calls.
 *
 * Android has libcutils and will not let an app open it. These are real
 * implementations of the five symbols Mesa's libEGL and libgallium_dri name,
 * built from public NDK APIs, so the pack carries something that works rather
 * than something that merely links.
 */

#include <android/trace.h>
#include <stdint.h>
#include <string.h>
#include <sys/system_properties.h>

/* From <cutils/trace.h>: the bit that says a trace is about graphics. */
#define ATRACE_TAG_GRAPHICS (1 << 1)

/*
 * AOSP's contract, which is not quite the obvious one: on a property that is
 * not set, the default is copied into the caller's buffer and its length
 * returned, rather than the call failing. Mesa reads driver overrides this
 * way, so getting it wrong would silently change which driver is chosen.
 */
int property_get(const char *key, char *value, const char *default_value) {
    if (value == NULL) {
        return 0;
    }
    if (key != NULL) {
        int length = __system_property_get(key, value);
        if (length > 0) {
            return length;
        }
    }
    if (default_value == NULL) {
        value[0] = '\0';
        return 0;
    }
    size_t length = strlen(default_value);
    if (length > PROP_VALUE_MAX - 1) {
        length = PROP_VALUE_MAX - 1;
    }
    memcpy(value, default_value, length);
    value[length] = '\0';
    return (int) length;
}

/*
 * Tracing, for real rather than dropped. ATrace is the public form of the same
 * mechanism, so a Mesa trace point shows up in a systrace capture beside the
 * platform's own - which is exactly what will be wanted when the question
 * becomes where the frame time is going.
 */
void atrace_begin_body(const char *name) {
    ATrace_beginSection(name != NULL ? name : "mesa");
}

void atrace_end_body(void) {
    ATrace_endSection();
}

void atrace_init(void) {
    /* ATrace needs no setting up; the real one reads a system property here. */
}

/*
 * Never ATRACE_TAG_NOT_READY: that means "ask again later", and a caller that
 * believes it will keep calling atrace_init and re-checking on every frame.
 * The honest answers are "graphics tracing is on" and "nothing is on".
 */
uint64_t atrace_get_enabled_tags(void) {
    return ATrace_isEnabled() ? (uint64_t) ATRACE_TAG_GRAPHICS : 0u;
}
