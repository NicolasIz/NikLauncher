# Android compatibility libraries

Mesa's `libEGL.so` and `libgallium_dri.so` are linked against `libcutils.so`,
`libhardware.so` and `libsync.so`. Android has all three, and hands an app
none of them: they are platform-internal, and have been refused to apps since
Android 7. Without them the very first `dlopen` fails and the Zink backend
cannot start at all.

Mesa builds stand-ins for exactly this situation, under `src/android_stub`,
and they are not usable here. Every one of them returns 0 without doing
anything - including `hw_get_module`, which reports success while leaving the
module pointer untouched. Mesa's fallback gralloc reads that as "gralloc
found" and dereferences it on the next line. They exist so a build without an
AOSP tree can link; they were never meant to run.

So these are ours: small, real implementations of the handful of symbols Mesa
actually calls, built from public NDK APIs only.

| symbol | what we do |
| --- | --- |
| `property_get` | `__system_property_get`, with the default-value semantics AOSP's has |
| `atrace_begin_body` / `atrace_end_body` | the NDK's `ATrace_beginSection` / `ATrace_endSection` - real tracing, visible in a systrace |
| `atrace_get_enabled_tags` | the graphics tag when `ATrace_isEnabled`, otherwise none |
| `hw_get_module` | `-ENOSYS`, and the module left NULL |
| `sync_wait` | `poll` on the fence, which is what AOSP's does |
| `sync_merge` | `SYNC_IOC_MERGE`, which is what AOSP's does |
| `sync_file_info` | NULL: the caller treats that as "cannot describe this fence" |

`hw_get_module` is the one we cannot honestly implement, because gralloc is
precisely what Android will not let an app reach. Returning `-ENOSYS` is not a
workaround but the true answer, and Mesa handles it: `u_gralloc_fallback_create`
logs "No gralloc hwmodule detected (video buffers won't be supported)" and
returns a working gralloc regardless. Minecraft does not use video buffers.

## Preferring the platform's own

These are loaded only when the device does not offer the library itself - the
bridge tries the bare soname first, and only falls back to the copy in the
pack. `libsync` in particular is public on some devices and not on others, and
where it is public the platform's own is the one that should win. A shipped
copy that shadowed a working library would be the same mistake as shipping
Mesa's stubs.
