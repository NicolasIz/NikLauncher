/*
 * The parts of libsync that Mesa calls.
 *
 * libsync is public on some devices and not on others, which is why the
 * bridge tries the platform's own before this one: where the real library is
 * reachable it is the one that should win. This exists for the devices where
 * it is not, and it is the same implementation - a sync fence is a file
 * descriptor and the operations on it are poll and two ioctls, all of which
 * an app may do.
 */

#include <errno.h>
#include <linux/sync_file.h>
#include <poll.h>
#include <stddef.h>
#include <string.h>
#include <sys/ioctl.h>
#include <stdint.h>

/* timeout in milliseconds, negative for no timeout - as AOSP's. */
int sync_wait(int fd, int timeout) {
    struct pollfd entry;
    entry.fd = fd;
    entry.events = POLLIN;
    entry.revents = 0;

    int ready;
    do {
        ready = poll(&entry, 1, timeout);
    } while (ready == -1 && (errno == EINTR || errno == EAGAIN));

    if (ready == 0) {
        /* A fence that did not signal in time is distinct from a broken one. */
        errno = ETIME;
        return -1;
    }
    if (ready < 0) {
        return -1;
    }
    if (entry.revents & (POLLERR | POLLNVAL)) {
        errno = EINVAL;
        return -1;
    }
    return 0;
}

/*
 * Returns a new fence that signals when both of its inputs have. The caller
 * owns it and closes it; the two inputs are untouched.
 */
int sync_merge(const char *name, int fd, int fd2) {
    struct sync_merge_data data;
    memset(&data, 0, sizeof(data));
    data.fd2 = fd2;
    if (name != NULL) {
        strncpy(data.name, name, sizeof(data.name) - 1);
    }

    int result;
    do {
        result = ioctl(fd, SYNC_IOC_MERGE, &data);
    } while (result == -1 && (errno == EINTR || errno == EAGAIN));

    if (result < 0) {
        return result;
    }
    return (int) data.fence;
}

/*
 * Describing a fence means allocating a variable-length structure whose exact
 * layout callers walk. Mesa uses it only to name fences when it is logging
 * them, and treats NULL as "cannot describe this one" - so returning NULL
 * costs nothing real and avoids getting an allocation contract subtly wrong.
 */
void *sync_file_info(int32_t fd) {
    (void) fd;
    return NULL;
}

void sync_file_info_free(void *info) {
    (void) info;
}
