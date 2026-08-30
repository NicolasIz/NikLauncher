/*
 * Host tests for the GLFW bridge core.
 *
 * Compiled and run with the system compiler, not the NDK: the point is a fast
 * check that runs on every push, without a device or an emulator. The core was
 * written free of EGL and JNI precisely so this is possible.
 */

#include "../../main/cpp/glfw/nikglfw_core.h"

#include <stdio.h>
#include <string.h>

static int failures = 0;
static int checks = 0;

#define CHECK(condition, message)                                              \
    do {                                                                       \
        checks++;                                                              \
        if (!(condition)) {                                                    \
            failures++;                                                        \
            printf("  FAIL %s:%d  %s\n", __FILE__, __LINE__, (message));       \
        }                                                                      \
    } while (0)

#define CHECK_EQ_INT(actual, expected, message)                                \
    do {                                                                       \
        checks++;                                                              \
        long long a_ = (long long) (actual);                                   \
        long long e_ = (long long) (expected);                                 \
        if (a_ != e_) {                                                        \
            failures++;                                                        \
            printf("  FAIL %s:%d  %s (got %lld, expected %lld)\n", __FILE__,   \
                   __LINE__, (message), a_, e_);                               \
        }                                                                      \
    } while (0)

static NikEvent key_event(int key, int action) {
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_KEY;
    event.key = key;
    event.action = action;
    return event;
}

static NikEvent button_event(int button, int action) {
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_MOUSE_BUTTON;
    event.key = button;
    event.action = action;
    return event;
}

static NikEvent cursor_event(double x, double y) {
    NikEvent event;
    memset(&event, 0, sizeof(event));
    event.type = NIK_EVENT_CURSOR_POS;
    event.x = x;
    event.y = y;
    return event;
}

/* --- queue ------------------------------------------------------------- */

static void test_queue_is_fifo(void) {
    printf("queue is first in first out\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    NikEvent a = key_event(65, NIK_GLFW_PRESS);
    NikEvent b = key_event(66, NIK_GLFW_PRESS);
    CHECK(nik_core_push(&core, &a), "first push accepted");
    CHECK(nik_core_push(&core, &b), "second push accepted");
    CHECK_EQ_INT(nik_core_pending(&core), 2, "two events queued");

    NikEvent out;
    CHECK(nik_core_pop(&core, &out), "pop succeeds");
    CHECK_EQ_INT(out.key, 65, "oldest event comes out first");
    CHECK(nik_core_pop(&core, &out), "second pop succeeds");
    CHECK_EQ_INT(out.key, 66, "then the newer one");
    CHECK(!nik_core_pop(&core, &out), "queue is empty afterwards");
}

static void test_queue_wraps_around(void) {
    printf("queue wraps around its ring buffer\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    /* Fill, drain and refill so the head passes the end of the buffer. */
    for (int cycle = 0; cycle < 3; cycle++) {
        for (int i = 0; i < NIK_GLFW_EVENT_CAPACITY; i++) {
            NikEvent event = cursor_event(i, i);
            CHECK(nik_core_push(&core, &event), "push during fill");
        }
        for (int i = 0; i < NIK_GLFW_EVENT_CAPACITY; i++) {
            NikEvent out;
            CHECK(nik_core_pop(&core, &out), "pop during drain");
            CHECK_EQ_INT((int) out.x, i, "events stay in order across the wrap");
        }
    }
    CHECK_EQ_INT(core.dropped, 0, "nothing was dropped");
}

static void test_full_queue_drops_the_oldest_discardable(void) {
    printf("a full queue drops the oldest discardable event\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    for (int i = 0; i < NIK_GLFW_EVENT_CAPACITY; i++) {
        NikEvent event = cursor_event(i, i);
        nik_core_push(&core, &event);
    }

    NikEvent newest = cursor_event(9999, 9999);
    CHECK(nik_core_push(&core, &newest), "the newest event is accepted");
    CHECK_EQ_INT(core.dropped, 1, "exactly one event was dropped");
    CHECK_EQ_INT(nik_core_pending(&core), NIK_GLFW_EVENT_CAPACITY, "queue stays full");

    NikEvent out;
    nik_core_pop(&core, &out);
    CHECK_EQ_INT((int) out.x, 1, "the oldest cursor move is the one that went");
}

static void test_a_release_is_never_dropped(void) {
    printf("a release is never dropped under pressure\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    /* One release, then fill the rest with discardable motion. */
    NikEvent release = key_event(87, NIK_GLFW_RELEASE);
    nik_core_push(&core, &release);
    for (int i = 1; i < NIK_GLFW_EVENT_CAPACITY; i++) {
        NikEvent event = cursor_event(i, i);
        nik_core_push(&core, &event);
    }

    for (int i = 0; i < 50; i++) {
        NikEvent event = cursor_event(1000 + i, 0);
        nik_core_push(&core, &event);
    }

    bool found_release = false;
    NikEvent out;
    while (nik_core_pop(&core, &out)) {
        if (out.type == NIK_EVENT_KEY && out.action == NIK_GLFW_RELEASE && out.key == 87) {
            found_release = true;
        }
    }
    CHECK(found_release, "the release survived a flood of cursor motion");
}

static void test_a_queue_of_only_releases_refuses_new_events(void) {
    printf("a queue holding only releases refuses rather than losing one\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    for (int i = 0; i < NIK_GLFW_EVENT_CAPACITY; i++) {
        NikEvent event = key_event(i % 100, NIK_GLFW_RELEASE);
        nik_core_push(&core, &event);
    }

    NikEvent extra = cursor_event(1, 1);
    CHECK(!nik_core_push(&core, &extra), "the new event is refused");
    CHECK_EQ_INT(nik_core_pending(&core), NIK_GLFW_EVENT_CAPACITY, "no release was evicted");
    CHECK(core.dropped > 0, "the refusal was counted");
}

/* --- input state ------------------------------------------------------- */

static void test_key_state_tracks_press_and_release(void) {
    printf("key state tracks press and release\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    CHECK_EQ_INT(nik_core_get_key(&core, 87), NIK_GLFW_RELEASE, "starts released");

    NikEvent press = key_event(87, NIK_GLFW_PRESS);
    nik_core_apply(&core, &press);
    CHECK_EQ_INT(nik_core_get_key(&core, 87), NIK_GLFW_PRESS, "reads pressed");

    NikEvent repeat = key_event(87, NIK_GLFW_REPEAT);
    nik_core_apply(&core, &repeat);
    CHECK_EQ_INT(nik_core_get_key(&core, 87), NIK_GLFW_PRESS, "a repeat keeps it pressed");

    NikEvent release = key_event(87, NIK_GLFW_RELEASE);
    nik_core_apply(&core, &release);
    CHECK_EQ_INT(nik_core_get_key(&core, 87), NIK_GLFW_RELEASE, "reads released");
}

static void test_out_of_range_keys_are_safe(void) {
    printf("out of range keys neither crash nor corrupt state\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    NikEvent bogus = key_event(NIK_GLFW_KEY_LAST + 500, NIK_GLFW_PRESS);
    nik_core_apply(&core, &bogus);
    CHECK_EQ_INT(nik_core_get_key(&core, NIK_GLFW_KEY_LAST + 500), NIK_GLFW_RELEASE,
                 "an impossible key reads released");
    CHECK_EQ_INT(nik_core_get_key(&core, -1), NIK_GLFW_RELEASE, "so does a negative key");
}

static void test_sticky_keys_report_a_tap_that_happened_between_polls(void) {
    printf("sticky keys report a tap that began and ended between polls\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);
    CHECK(nik_core_set_input_mode(&core, NIK_GLFW_STICKY_KEYS, 1), "sticky keys accepted");

    NikEvent press = key_event(32, NIK_GLFW_PRESS);
    NikEvent release = key_event(32, NIK_GLFW_RELEASE);
    nik_core_apply(&core, &press);
    nik_core_apply(&core, &release);

    CHECK_EQ_INT(nik_core_get_key(&core, 32), NIK_GLFW_PRESS, "the tap is reported once");
    CHECK_EQ_INT(nik_core_get_key(&core, 32), NIK_GLFW_RELEASE, "and only once");
}

static void test_turning_sticky_off_clears_latched_keys(void) {
    printf("turning sticky keys off clears anything latched\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);
    nik_core_set_input_mode(&core, NIK_GLFW_STICKY_KEYS, 1);

    NikEvent press = key_event(32, NIK_GLFW_PRESS);
    NikEvent release = key_event(32, NIK_GLFW_RELEASE);
    nik_core_apply(&core, &press);
    nik_core_apply(&core, &release);

    nik_core_set_input_mode(&core, NIK_GLFW_STICKY_KEYS, 0);
    CHECK_EQ_INT(nik_core_get_key(&core, 32), NIK_GLFW_RELEASE,
                 "a latched key does not survive the mode change");
}

static void test_mouse_buttons_track_independently(void) {
    printf("mouse buttons track independently of each other\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    NikEvent left = button_event(0, NIK_GLFW_PRESS);
    nik_core_apply(&core, &left);
    CHECK_EQ_INT(nik_core_get_mouse_button(&core, 0), NIK_GLFW_PRESS, "left is down");
    CHECK_EQ_INT(nik_core_get_mouse_button(&core, 1), NIK_GLFW_RELEASE, "right is not");
}

static void test_cursor_position_round_trips(void) {
    printf("cursor position round trips\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    double x = 0;
    double y = 0;
    nik_core_get_cursor_pos(&core, &x, &y);
    CHECK_EQ_INT((int) x, 960, "starts centred horizontally");
    CHECK_EQ_INT((int) y, 540, "starts centred vertically");

    NikEvent move = cursor_event(123.5, 456.25);
    nik_core_apply(&core, &move);
    nik_core_get_cursor_pos(&core, &x, &y);
    CHECK(x == 123.5 && y == 456.25, "reads back what was applied");

    nik_core_set_cursor_pos(&core, 10, 20);
    nik_core_get_cursor_pos(&core, &x, &y);
    CHECK(x == 10 && y == 20, "an explicit warp is honoured");
}

/* --- input modes ------------------------------------------------------- */

static void test_cursor_modes(void) {
    printf("cursor modes are validated\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    CHECK_EQ_INT(nik_core_get_input_mode(&core, NIK_GLFW_CURSOR), NIK_GLFW_CURSOR_NORMAL,
                 "starts visible");

    CHECK(nik_core_set_input_mode(&core, NIK_GLFW_CURSOR, NIK_GLFW_CURSOR_DISABLED),
          "grabbing the cursor is accepted");
    CHECK_EQ_INT(nik_core_get_input_mode(&core, NIK_GLFW_CURSOR), NIK_GLFW_CURSOR_DISABLED,
                 "and reads back");

    CHECK(!nik_core_set_input_mode(&core, NIK_GLFW_CURSOR, 12345), "a bogus mode is refused");
    CHECK_EQ_INT(nik_core_get_input_mode(&core, NIK_GLFW_CURSOR), NIK_GLFW_CURSOR_DISABLED,
                 "and leaves the mode alone");

    CHECK(!nik_core_set_input_mode(&core, 0xDEAD, 1), "an unknown input mode is refused");
}

/* --- focus loss -------------------------------------------------------- */

static void test_losing_focus_releases_everything_held(void) {
    printf("losing focus releases everything held\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    NikEvent forward = key_event(87, NIK_GLFW_PRESS);
    NikEvent sprint = key_event(341, NIK_GLFW_PRESS);
    NikEvent attack = button_event(0, NIK_GLFW_PRESS);
    nik_core_apply(&core, &forward);
    nik_core_apply(&core, &sprint);
    nik_core_apply(&core, &attack);

    nik_core_release_all(&core);

    CHECK_EQ_INT(nik_core_get_key(&core, 87), NIK_GLFW_RELEASE, "the movement key is released");
    CHECK_EQ_INT(nik_core_get_key(&core, 341), NIK_GLFW_RELEASE, "so is the modifier");
    CHECK_EQ_INT(nik_core_get_mouse_button(&core, 0), NIK_GLFW_RELEASE, "and the mouse button");

    /* The game also has to be told, not just our own state changed. */
    int releases = 0;
    NikEvent out;
    while (nik_core_pop(&core, &out)) {
        if (out.action == NIK_GLFW_RELEASE) {
            releases++;
        }
    }
    CHECK_EQ_INT(releases, 3, "a release event was queued for each held control");
}

static void test_release_all_on_an_empty_state_does_nothing(void) {
    printf("releasing with nothing held queues nothing\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    nik_core_release_all(&core);
    CHECK_EQ_INT(nik_core_pending(&core), 0, "no spurious releases");
}

/* --- window events ----------------------------------------------------- */

static void test_window_events_update_state(void) {
    printf("window events update the tracked state\n");
    NikGlfwCore core;
    nik_core_init(&core, 1920, 1080);

    NikEvent resize;
    memset(&resize, 0, sizeof(resize));
    resize.type = NIK_EVENT_WINDOW_SIZE;
    resize.x = 1280;
    resize.y = 720;
    nik_core_apply(&core, &resize);
    CHECK_EQ_INT(core.window_width, 1280, "width updated");
    CHECK_EQ_INT(core.window_height, 720, "height updated");

    NikEvent unfocus;
    memset(&unfocus, 0, sizeof(unfocus));
    unfocus.type = NIK_EVENT_WINDOW_FOCUS;
    unfocus.key = 0;
    nik_core_apply(&core, &unfocus);
    CHECK(!core.focused, "focus lost");

    NikEvent close;
    memset(&close, 0, sizeof(close));
    close.type = NIK_EVENT_WINDOW_CLOSE;
    nik_core_apply(&core, &close);
    CHECK(core.should_close, "close requested");
}

int main(void) {
    printf("NikLauncher GLFW bridge core\n\n");

    test_queue_is_fifo();
    test_queue_wraps_around();
    test_full_queue_drops_the_oldest_discardable();
    test_a_release_is_never_dropped();
    test_a_queue_of_only_releases_refuses_new_events();
    test_key_state_tracks_press_and_release();
    test_out_of_range_keys_are_safe();
    test_sticky_keys_report_a_tap_that_happened_between_polls();
    test_turning_sticky_off_clears_latched_keys();
    test_mouse_buttons_track_independently();
    test_cursor_position_round_trips();
    test_cursor_modes();
    test_losing_focus_releases_everything_held();
    test_release_all_on_an_empty_state_does_nothing();
    test_window_events_update_state();

    printf("\n%d checks, %d failures\n", checks, failures);
    return failures == 0 ? 0 : 1;
}
