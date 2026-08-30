/*
 * Core state for NikLauncher's GLFW bridge.
 *
 * Minecraft reaches input and windowing through LWJGL, which resolves the
 * GLFW 3 C ABI by name out of a libglfw.so. On Android there is no GLFW, so
 * this bridge exports that ABI and implements it over EGL, ANativeWindow and
 * the synthetic events NikLauncher's input layer produces.
 *
 * Everything in this header is deliberately free of EGL, JNI and Android
 * headers: it is the part whose correctness can be checked by unit tests on a
 * desktop compiler, which is where a stuck key or a dropped release would
 * otherwise only show up while playing.
 */

#ifndef NIKGLFW_CORE_H
#define NIKGLFW_CORE_H

#include <stdbool.h>
#include <stddef.h>

/* --- The GLFW constants this bridge implements ------------------------- */

#define NIK_GLFW_RELEASE 0
#define NIK_GLFW_PRESS 1
#define NIK_GLFW_REPEAT 2

#define NIK_GLFW_KEY_UNKNOWN (-1)
#define NIK_GLFW_KEY_LAST 348
#define NIK_GLFW_MOUSE_BUTTON_LAST 7

#define NIK_GLFW_CURSOR 0x00033001
#define NIK_GLFW_STICKY_KEYS 0x00033002
#define NIK_GLFW_STICKY_MOUSE_BUTTONS 0x00033003
#define NIK_GLFW_RAW_MOUSE_MOTION 0x00033005

#define NIK_GLFW_CURSOR_NORMAL 0x00034001
#define NIK_GLFW_CURSOR_HIDDEN 0x00034002
#define NIK_GLFW_CURSOR_DISABLED 0x00034003

/* Capacity is generous but bounded: input must never grow without limit if
 * the game thread stalls, or a long hitch would turn into an allocation
 * failure at the worst possible moment. */
#define NIK_GLFW_EVENT_CAPACITY 512

/* --- Events ------------------------------------------------------------ */

typedef enum {
    NIK_EVENT_KEY,
    NIK_EVENT_CHAR,
    NIK_EVENT_MOUSE_BUTTON,
    NIK_EVENT_CURSOR_POS,
    NIK_EVENT_SCROLL,
    NIK_EVENT_WINDOW_SIZE,
    NIK_EVENT_WINDOW_FOCUS,
    NIK_EVENT_WINDOW_CLOSE,
} NikEventType;

typedef struct {
    NikEventType type;
    int key;        /* key code, mouse button, or focus flag */
    int scancode;
    int action;     /* PRESS / RELEASE / REPEAT */
    int mods;
    unsigned int codepoint;
    double x;       /* cursor x, scroll x offset, or window width */
    double y;       /* cursor y, scroll y offset, or window height */
} NikEvent;

/* --- Core state -------------------------------------------------------- */

typedef struct {
    NikEvent buffer[NIK_GLFW_EVENT_CAPACITY];
    size_t head;   /* next slot to read */
    size_t count;

    /* Counts every event discarded because the queue was full, so a stall
     * shows up as a number rather than as input that mysteriously vanished. */
    unsigned long dropped;

    unsigned char keys[NIK_GLFW_KEY_LAST + 1];
    unsigned char mouse_buttons[NIK_GLFW_MOUSE_BUTTON_LAST + 1];

    double cursor_x;
    double cursor_y;

    int cursor_mode;
    bool sticky_keys;
    bool sticky_mouse_buttons;
    bool raw_mouse_motion;

    int window_width;
    int window_height;
    bool focused;
    bool should_close;
} NikGlfwCore;

/* --- Lifecycle --------------------------------------------------------- */

void nik_core_init(NikGlfwCore *core, int width, int height);

/* --- Producing events -------------------------------------------------- */

/*
 * Appends an event.
 *
 * Returns true when the event was stored. When the queue is full the oldest
 * *discardable* event is evicted to make room: a release is never dropped,
 * because losing one leaves the game holding a key or mouse button down with
 * nothing on screen to explain it. If every queued event is a release, the
 * incoming event is refused instead.
 */
bool nik_core_push(NikGlfwCore *core, const NikEvent *event);

/* --- Consuming events -------------------------------------------------- */

/* Removes and copies the oldest event. Returns false when the queue is empty. */
bool nik_core_pop(NikGlfwCore *core, NikEvent *out);

/*
 * Applies an event to the tracked input state.
 *
 * Kept separate from [nik_core_pop] so the dispatcher can update state and
 * invoke the GLFW callback for the same event without the state depending on
 * callback behaviour.
 */
void nik_core_apply(NikGlfwCore *core, const NikEvent *event);

size_t nik_core_pending(const NikGlfwCore *core);

/* --- Queries the GLFW API answers ------------------------------------- */

int nik_core_get_key(NikGlfwCore *core, int key);
int nik_core_get_mouse_button(NikGlfwCore *core, int button);
void nik_core_get_cursor_pos(const NikGlfwCore *core, double *x, double *y);
void nik_core_set_cursor_pos(NikGlfwCore *core, double x, double y);

int nik_core_get_input_mode(const NikGlfwCore *core, int mode);

/* Returns true when the mode was recognised and applied. */
bool nik_core_set_input_mode(NikGlfwCore *core, int mode, int value);

/*
 * Releases every held key and button, emitting the release events the game
 * still needs to see.
 *
 * Called when the window loses focus: without it, a key held as the player
 * switches away stays down forever from Minecraft's point of view.
 */
void nik_core_release_all(NikGlfwCore *core);

#endif /* NIKGLFW_CORE_H */
