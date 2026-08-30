package com.niklauncher.app.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.niklauncher.core.instance.Instance

/**
 * What this device can actually do, read once at startup.
 *
 * Phase 4 builds its thermal and performance management on top of this; for now
 * it is what lets the launcher propose a sane default heap and graphics backend
 * instead of a fixed guess.
 */
data class DeviceCapabilities(
    val model: String,
    val socName: String,
    val totalMemoryMegabytes: Int,
    val availableMemoryMegabytes: Int,
    val cpuCores: Int,
    val supportsVulkan: Boolean,
    val vulkanVersion: Int,
    val supports64Bit: Boolean,
    /**
     * Kernel page size. A runtime pack whose shared objects are aligned for
     * smaller pages will not map, so this decides which packs are offered.
     */
    val pageSizeBytes: Int,
    val supportedAbis: List<String>,
) {
    /**
     * A heap that leaves room for the launcher, the graphics translation layer
     * and Android itself. Minecraft runs inside our own process, so an
     * over-sized heap does not fail gracefully - the whole app gets killed.
     */
    fun recommendedMemoryMegabytes(): Int {
        val quarter = totalMemoryMegabytes / 4
        return quarter.coerceIn(Instance.MIN_MEMORY_MB, Instance.MAX_MEMORY_MB)
    }

    companion object {
        fun detect(context: Context): DeviceCapabilities {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo().also { activityManager.getMemoryInfo(it) }
            val packageManager = context.packageManager

            val vulkanVersion = packageManager
                .systemAvailableFeatures
                .firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
                ?.version
                ?: 0

            return DeviceCapabilities(
                model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                socName = listOfNotNull(
                    Build.SOC_MANUFACTURER.takeIf { it.isNotBlank() && it != Build.UNKNOWN },
                    Build.SOC_MODEL.takeIf { it.isNotBlank() && it != Build.UNKNOWN },
                ).joinToString(" ").ifBlank { Build.HARDWARE },
                totalMemoryMegabytes = (memoryInfo.totalMem / BYTES_PER_MB).toInt(),
                availableMemoryMegabytes = (memoryInfo.availMem / BYTES_PER_MB).toInt(),
                cpuCores = Runtime.getRuntime().availableProcessors(),
                supportsVulkan = vulkanVersion > 0,
                vulkanVersion = vulkanVersion,
                supports64Bit = Build.SUPPORTED_64_BIT_ABIS.isNotEmpty(),
                pageSizeBytes = readPageSize(),
                supportedAbis = Build.SUPPORTED_ABIS.toList(),
            )
        }

        /**
         * Reads the kernel page size, falling back to 4 KB - the value every
         * Android device used before 16 KB pages appeared - if the query
         * fails, since guessing larger would rule out usable packs.
         */
        private fun readPageSize(): Int = runCatching {
            android.system.Os.sysconf(android.system.OsConstants._SC_PAGESIZE).toInt()
        }.getOrDefault(DEFAULT_PAGE_SIZE).takeIf { it > 0 } ?: DEFAULT_PAGE_SIZE

        private const val DEFAULT_PAGE_SIZE = 4096
        private const val BYTES_PER_MB = 1024L * 1024L
    }
}
