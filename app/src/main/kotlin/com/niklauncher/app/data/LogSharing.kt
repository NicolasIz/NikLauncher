package com.niklauncher.app.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a session log to whatever the player uses to send things.
 *
 * The launcher writes its logs into internal storage, which cannot be reached
 * from a phone without a PC. A launch that fails leaves a diagnosis there that
 * nobody can read, so the file is offered through the share sheet instead - it
 * is the only route off the device that needs no permission and no cable.
 */
object LogSharing {

    /**
     * True when there is something to share. Checked before offering the
     * action, so the button is never there for a file that does not exist.
     */
    fun canShare(log: File?): Boolean = log != null && log.isFile && log.length() > 0

    /**
     * The chooser for one log. Returns null when there is nothing to send,
     * rather than starting an activity that would show an empty share sheet.
     *
     * FLAG_GRANT_READ_URI_PERMISSION is what makes the URI readable by the app
     * the player picks: the provider is not exported, so without the grant the
     * receiving app gets a permission denial instead of the log.
     */
    fun chooserFor(context: Context, log: File, title: String = "Compartir el registro"): Intent? {
        if (!canShare(log)) return null

        val uri = runCatching {
            FileProvider.getUriForFile(context, authority(context), log)
        }.getOrNull() ?: return null

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "NikLauncher · " + log.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** Matches the authority declared in the manifest. */
    private fun authority(context: Context): String = context.packageName + ".logs"
}
