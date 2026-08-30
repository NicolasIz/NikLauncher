package com.niklauncher.app.data

import android.content.Context
import com.niklauncher.core.download.Downloader
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.runtime.NativeRuntimeProvider
import com.niklauncher.core.runtime.UnavailableRuntimeProvider

/**
 * Manual dependency wiring.
 *
 * A DI framework would earn its keep once the graph is deep; at this size it
 * would only add build time and indirection.
 */
class AppContainer(context: Context) {

    private val applicationContext = context.applicationContext

    /**
     * Game data lives in internal storage. Scoped storage makes the external
     * directories awkward to use for thousands of small asset files, and
     * internal storage is what the runtime packs must load from anyway.
     */
    val paths: GamePaths = GamePaths(applicationContext.filesDir).also { it.createDirectories() }

    val device: DeviceCapabilities = DeviceCapabilities.detect(applicationContext)

    val settings: SettingsRepository = SettingsRepository(applicationContext)

    val instances: InstanceRepository = InstanceRepository(paths)

    val transport: OkHttpTransport = OkHttpTransport()

    val downloader: Downloader = Downloader(transport)

    /** Replaced by the real pack installer in Phase 2. */
    val runtimeProvider: NativeRuntimeProvider = UnavailableRuntimeProvider()
}
