package com.niklauncher.app.ui

import com.niklauncher.core.install.InstallProgress
import com.niklauncher.core.manifest.VersionSummary

sealed interface CatalogState {
    data object Loading : CatalogState

    data class Ready(
        val versions: List<VersionSummary>,
        val installed: Set<String>,
        val latestRelease: String,
    ) : CatalogState

    data class Error(val message: String) : CatalogState
}

sealed interface InstallState {
    data object Idle : InstallState

    data class Running(val versionId: String, val progress: InstallProgress) : InstallState

    data class Done(val versionId: String) : InstallState

    /** [detail] lists the files that could not be fetched, when that is why it failed. */
    data class Failed(
        val versionId: String,
        val message: String,
        val detail: List<String> = emptyList(),
    ) : InstallState
}
