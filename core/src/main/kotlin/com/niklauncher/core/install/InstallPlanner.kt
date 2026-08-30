package com.niklauncher.core.install

import com.niklauncher.core.assets.AssetIndex
import com.niklauncher.core.download.DownloadRequest
import com.niklauncher.core.io.GamePaths
import com.niklauncher.core.library.LibraryResolver
import com.niklauncher.core.manifest.VersionJson
import com.niklauncher.core.rules.LaunchEnvironment
import com.niklauncher.core.runtime.JavaRuntime

/**
 * Turns a resolved version descriptor plus its asset index into a concrete
 * [InstallPlan].
 */
class InstallPlanner(
    private val paths: GamePaths,
    environment: LaunchEnvironment = LaunchEnvironment.ANDROID_ARM64,
    private val libraryResolver: LibraryResolver = LibraryResolver(environment),
) {

    fun plan(version: VersionJson, assetIndex: AssetIndex?): InstallPlan {
        val resolution = libraryResolver.resolve(version.libraries)

        // For an inherited version the jar belongs to the root parent, so the
        // client is stored under that id and shared by every loader built on it.
        val jarVersionId = version.jar?.takeIf { it.isNotBlank() } ?: version.id
        val clientJar = paths.versionJar(jarVersionId)

        val client = version.downloads?.client?.let { entry ->
            DownloadRequest(
                url = entry.url,
                destination = clientJar,
                sha1 = entry.sha1,
                size = entry.size,
                label = "$jarVersionId.jar",
            )
        }

        val libraries = resolution.classpath.mapNotNull { library ->
            val url = library.url ?: return@mapNotNull null
            DownloadRequest(
                url = url,
                destination = paths.library(library.path),
                sha1 = library.sha1,
                size = library.size,
                label = library.coordinate.toString(),
            )
        }

        val assets = assetIndex?.uniqueObjects()?.map { asset ->
            DownloadRequest(
                url = asset.downloadUrl,
                destination = paths.assetObject(asset.hash),
                sha1 = asset.hash,
                size = asset.size,
                label = asset.hash.take(8),
            )
        }.orEmpty()

        // Loader libraries come first, then the client jar: a mod loader that
        // replaces a vanilla class has to be found before the original.
        val classpath = resolution.classpath.map { paths.library(it.path) } + clientJar

        return InstallPlan(
            versionId = version.id,
            version = version,
            javaRuntime = JavaRuntime.forMajorVersion(version.javaVersion?.majorVersion ?: 8),
            client = client,
            libraries = libraries,
            assets = assets,
            classpath = classpath,
            skippedLibraries = resolution.skipped,
            runtimeProvidedLibraries = resolution.runtimeProvided,
            assetIndexId = version.assetIndex?.id ?: version.assets,
            requiresNamedAssetCopies = assetIndex?.requiresNamedCopies() ?: false,
        )
    }
}
