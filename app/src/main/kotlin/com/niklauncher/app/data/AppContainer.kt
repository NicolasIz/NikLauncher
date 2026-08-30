package com.niklauncher.app.data

import android.content.Context
import kotlinx.coroutines.flow.first
import com.niklauncher.core.download.Downloader
import com.niklauncher.core.install.GameInstaller
import com.niklauncher.core.install.InstallPlanner
import com.niklauncher.core.install.MetadataClient
import com.niklauncher.core.install.VersionCatalog
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.runtime.NativeRuntimeProvider
import com.niklauncher.core.runtime.RuntimePackCatalog
import com.niklauncher.core.runtime.RuntimePackInstaller

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

    val controlLayouts: ControlLayoutRepository = ControlLayoutRepository(paths)

    val transport: OkHttpTransport = OkHttpTransport()

    val downloader: Downloader = Downloader(transport)

    val metadata: MetadataClient = MetadataClient(transport)

    val catalog: VersionCatalog = VersionCatalog(metadata, paths)

    val installer: GameInstaller = GameInstaller(catalog, downloader, paths, InstallPlanner(paths))

    val packCatalog: RuntimePackCatalog = RuntimePackCatalog(
        metadata = metadata,
        paths = paths,
        indexUrlProvider = { settings.settings.first().runtimePackIndexUrl },
    )

    /**
     * The real provider. With no pack source configured the catalogue is empty,
     * so this reports nothing installed - which is the truth, rather than a
     * stub pretending to be one.
     */
    val runtimeProvider: NativeRuntimeProvider = RuntimePackInstaller(
        paths = paths,
        downloader = downloader,
        indexProvider = { packCatalog.index() },
        devicePageSizeBytes = device.pageSizeBytes,
    )
}
