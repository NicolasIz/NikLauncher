package com.niklauncher.app.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Verifies, on the real device, that NikLauncher's runtime design is possible.
 *
 * The launcher intends to download a Java runtime pack and load `libjvm.so`
 * from its own data directory. Android forbids `exec()`ing a binary from there,
 * so `dlopen()` plus the JNI Invocation API is the only route - and a JVM also
 * needs executable memory for its JIT. Both assumptions are checked here rather
 * than discovered after the rest of Phase 2 is built on top of them.
 */
object NativeProbe {

    private const val LIBRARY_NAME = "nikprobe"

    private val loadError: Throwable? = runCatching { System.loadLibrary(LIBRARY_NAME) }.exceptionOrNull()

    private external fun nativePageSize(): Int
    private external fun nativeDlopen(path: String): String
    private external fun nativeExecutableMemory(): String

    suspend fun run(context: Context): ProbeReport = withContext(Dispatchers.IO) {
        loadError?.let {
            return@withContext ProbeReport(
                libraryLoaded = false,
                failureReason = it.message ?: it.toString(),
            )
        }

        val pageSize = runCatching { nativePageSize() }.getOrDefault(0)
        val executableMemory = runCatching { nativeExecutableMemory() }.getOrElse { "error: ${it.message}" }
        val dlopen = probeDlopenFromDataDirectory(context)

        ProbeReport(
            libraryLoaded = true,
            pageSizeBytes = pageSize,
            executableMemory = executableMemory,
            dlopenFromDataDir = dlopen.first,
            dlopenDetail = dlopen.second,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
        )
    }

    /**
     * Copies our own shared object into the app data directory under a new name
     * and re-opens it from there. That copy is the same situation a downloaded
     * runtime pack will be in, so success here means the pack design holds.
     */
    private fun probeDlopenFromDataDirectory(context: Context): Pair<Boolean, String> {
        val source = File(context.applicationInfo.nativeLibraryDir, "lib$LIBRARY_NAME.so")
        if (!source.isFile) {
            return false to "packaged library not found at ${source.path}"
        }

        val target = File(File(context.filesDir, "probe"), "lib${LIBRARY_NAME}_copy.so")
        return try {
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
            val result = nativeDlopen(target.absolutePath)
            (result == "OK") to result
        } catch (error: Throwable) {
            false to (error.message ?: error.toString())
        } finally {
            target.delete()
        }
    }
}

data class ProbeReport(
    val libraryLoaded: Boolean,
    val pageSizeBytes: Int = 0,
    val executableMemory: String = "",
    val dlopenFromDataDir: Boolean = false,
    val dlopenDetail: String = "",
    val supportedAbis: List<String> = emptyList(),
    val failureReason: String? = null,
) {
    /** True when everything the runtime-pack design depends on is available. */
    val runtimeDesignViable: Boolean
        get() = libraryLoaded && dlopenFromDataDir && executableMemory.contains("=yes")

    val pageSizeLabel: String
        get() = when (pageSizeBytes) {
            0 -> "desconocido"
            else -> "${pageSizeBytes / 1024} KB"
        }
}
