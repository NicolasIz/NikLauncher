#include "nikglfw_core.h"

#include <string.h>

/*
 * Internal third key state, matching GLFW's own sticky handling: a key that
 * was pressed and released between two polls is reported as pressed once, so a
 * fast tap is never missed.
 */
#define NIK_STICK 3

void nik_core_init(NikGlfwCore *core, int width, int height) {
    memset(core, 0, sizeof(*core));
    core->cursor_mode = NIK_GLFW_CURSOR_NORMAL;
    core->window_width = width;
    core->window_height = height;
    core->cursor_x = width / 2.0;
    core->cursor_y = height / 2.0;
    core->focused = true;
}

/*
 * Whether an event may be thrown away when the queue is full.
 *
 * A release never may. Losing one leaves Minecraft believing a key or button
 * is still held, with nothing on screen to explain why the player keeps
 * walking or mining. Cursor motion, scrolling and characters are safe to lose:
 * the next event supersedes them.
 */
static bool is_discardable(const NikEvent *event) {
    if (event->type == NIK_EVENT_KEY || event->type == NIK_EVENT_MOUSE_BUTTON) {
        return event->action != NIK_GLFW_RELEASE;
    }
    if (event->type == NIK_EVENT_WINDOW_CLOSE || event->type == NIK_EVENT_WINDOW_FOCUS) {
        return false;
    }
    return true;
}

static bool evict_oldest_discardable(NikGlfwCore *core) {
    for (size_t i = 0; i < core->count; i++) {
        size_t index = (core->head + i) % NIK_GLFW_EVENT_CAPACITY;
        if (!is_discardable(&core->buffer[index])) {
            continue;
        }
        /* Close the gap so the queue stays in order. */
        for (size_t j = i; j + 1 < core->count; j++) {
            size_t destination = (core->head + j) % NIK_GLFW_EVENT_CAPACITY;
            size_t source = (core->head + j + 1) % NIK_GLFW_EVENT_CAPACITY;
            core->buffer[destination] = core->buffer[source];
        }
        core->count--;
        core->dropped++;
        return true;
    }
    return false;
}

bool nik_core_push(NikGlfwCore *core, const NikEvent *event) {
    if (core->count == NIK_GLFW_EVENT_CAPACITY && !evict_oldest_discardable(core)) {
        /* Every queued event is one we refuse to lose, so refuse the new one
         * instead and let the counter record it. */
        core->dropped++;
        return false;
    }

    size_t tail = (core->head + core->count) % NIK_GLFW_EVENT_CAPACITY;
    core->buffer[tail] = *event;
    core->count++;
    return true;
}

bool nik_core_pop(NikGlfwCore *core, NikEvent *out) {
    if (core->count == 0) {
        return false;
    }
    *out = core->buffer[core->head];
    core->head = (core->head + 1) % NIK_GLFW_EVENT_CAPACITY;
    core->count--;
    return true;
}

void nik_core_apply(NikGlfwCore *core, const NikEvent *event) {
    switch (event->type) {
    case NIK_EVENT_KEY:
        if (event->key >= 0 && event->key <= NIK_GLFW_KEY_LAST) {
            if (event->action == NIK_GLFW_RELEASE) {
                core->keys[event->key] =
                    (core->sticky_keys && core->keys[event->key] == NIK_GLFW_PRESS)
                        ? NIK_STICK
                        : NIK_GLFW_RELEASE;
            } else if (event->action == NIK_GLFW_PRESS) {
                core->keys[event->key] = NIK_GLFW_PRESS;
            }
            /* A repeat leaves the state alone: the key was already down. */
        }
        break;

    case NIK_EVENT_MOUSE_BUTTON:
        if (event->key >= 0 && event->key <= NIK_GLFW_MOUSE_BUTTON_LAST) {
            if (event->action == NIK_GLFW_RELEASE) {
                core->mouse_buttons[event->key] =
                    (core->sticky_mouse_buttons && core->mouse_buttons[event->key] == NIK_GLFW_PRESS)
                        ? NIK_STICK
                        : NIK_GLFW_RELEASE;
            } else if (event->action == NIK_GLFW_PRESS) {
                core->mouse_buttons[event->key] = NIK_GLFW_PRESS;
            }
        }
        break;

    case NIK_EVENT_CURSOR_POS:
        core->cursor_x = event->x;
        core->cursor_y = event->y;
        break;

    case NIK_EVENT_WINDOW_SIZE:
        core->window_width = (int) event->x;
        core->window_height = (int) event->y;
        break;

    case NIK_EVENT_WINDOW_FOCUS:
        core->focused = event->key != 0;
        break;

    case NIK_EVENT_WINDOW_CLOSE:
        core->should_close = true;
        break;

    case NIK_EVENT_CHAR:
    case NIK_EVENT_SCROLL:
        /* Nothing to remember; these are pure notifications. */
        break;
    }
}

size_t nik_core_pending(const NikGlfwCore *core) {
    return core->count;
}

int nik_core_get_key(NikGlfwCore *core, int key) {
    if (key < 0 || key > NIK_GLFW_KEY_LAST) {
        return NIK_GLFW_RELEASE;
    }
    if (core->keys[key] == NIK_STICK) {
        core->keys[key] = NIK_GLFW_RELEASE;
        return NIK_GLFW_PRESS;
    }
    return core->keys[key];
}

int nik_core_get_mouse_button(NikGlfwCore *core, int button) {
    if (button < 0 || button > NIK_GLFW_MOUSE_BUTTON_LAST) {
        return NIK_GLFW_RELEASE;
    }
    if (core->mouse_buttons[button] == NIK_STICK) {
        core->mouse_buttons[button] = NIK_GLFW_RELEASE;
        return NIK_GLFW_PRESS;
    }
    return core->mouse_buttons[button];
}

void nik_core_get_cursor_pos(const NikGlfwCore *core, double *x, double *y) {
    if (x != NULL) {
        *x = core->cursor_x;
    }
    if (y != NULL) {
        *y = core->cursor_y;
    }
}

void nik_core_set_cursor_pos(NikGlfwCore *core, double x, double y) {
    core->cursor_x = x;
    core->cursor_y = y;
}

int nik_core_get_input_mode(const NikGlfwCore *core, int mode) {
    switch (mode) {
    case NIK_GLFW_CURSOR:
        return core->cursor_mode;
    case NIK_GLFW_STICKY_KEYS:
        return core->sticky_keys ? 1 : 0;
    case NIK_GLFW_STICKY_MOUSE_BUTTONS:
        return core->sticky_mouse_buttons ? 1 : 0;
    case NIK_GLFW_RAW_MOUSE_MOTION:
        return core->raw_mouse_motion ? 1 : 0;
    default:
        return 0;
    }
}

bool nik_core_set_input_mode(NikGlfwCore *core, int mode, int value) {
    switch (mode) {
    case NIK_GLFW_CURSOR:
        if (value != NIK_GLFW_CURSOR_NORMAL && value != NIK_GLFW_CURSOR_HIDDEN &&
            value != NIK_GLFW_CURSOR_DISABLED) {
            return false;
        }
        core->cursor_mode = value;
        return true;

    case NIK_GLFW_STICKY_KEYS:
        core->sticky_keys = value != 0;
        if (!core->sticky_keys) {
            /* Clear any latched state, or a key would report pressed long
             * after sticky handling was turned off. */
            for (int key = 0; key <= NIK_GLFW_KEY_LAST; key++) {
                if (core->keys[key] == NIK_STICK) {
                    core->keys[key] = NIK_GLFW_RELEASE;
                }
            }
        }
        return true;

    case NIK_GLFW_STICKY_MOUSE_BUTTONS:
        core->sticky_mouse_buttons = value != 0;
        if (!core->sticky_mouse_buttons) {
            for (int button = 0; button <= NIK_GLFW_MOUSE_BUTTON_LAST; button++) {
                if (core->mouse_buttons[button] == NIK_STICK) {
                    core->mouse_buttons[button] = NIK_GLFW_RELEASE;
                }
            }
        }
        return true;

    case NIK_GLFW_RAW_MOUSE_MOTION:
        core->raw_mouse_motion = value != 0;
        return true;

    default:
        return false;
    }
}

void nik_core_release_all(NikGlfwCore *core) {
    NikEvent event;

    for (int key = 0; key <= NIK_GLFW_KEY_LAST; key++) {
        if (core->keys[key] == NIK_GLFW_RELEASE) {
            continue;
        }
        if (core->keys[key] == NIK_GLFW_PRESS) {
            memset(&event, 0, sizeof(event));
            event.type = NIK_EVENT_KEY;
            event.key = key;
            event.action = NIK_GLFW_RELEASE;
            nik_core_push(core, &event);
        }
        /* Cleared even if the queue refused the event, so a later getKey
         * cannot keep reporting a key the player is no longer holding. */
        core->keys[key] = NIK_GLFW_RELEASE;
    }

    for (int button = 0; button <= NIK_GLFW_MOUSE_BUTTON_LAST; button++) {
        if (core->mouse_buttons[button] == NIK_GLFW_RELEASE) {
            continue;
        }
        if (core->mouse_buttons[button] == NIK_GLFW_PRESS) {
            memset(&event, 0, sizeof(event));
            event.type = NIK_EVENT_MOUSE_BUTTON;
            event.key = button;
            event.action = NIK_GLFW_RELEASE;
            nik_core_push(core, &event);
        }
        core->mouse_buttons[button] = NIK_GLFW_RELEASE;
    }
}
