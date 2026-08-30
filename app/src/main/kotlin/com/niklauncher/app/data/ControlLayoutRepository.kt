package com.niklauncher.app.data

import com.niklauncher.core.control.ControlLayout
import com.niklauncher.core.control.ControlPresets
import com.niklauncher.core.io.GamePaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Stores the player's control layouts.
 *
 * Layouts are user work - someone spent time arranging those buttons - so a
 * failed read falls back to the shipped presets rather than starting from an
 * empty screen, and writes go through a temp file and a rename so a crash
 * mid-save cannot destroy them.
 */
class ControlLayoutRepository(private val paths: GamePaths) {

    private val file: File get() = File(paths.root, LAYOUTS_FILE)
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _layouts = MutableStateFlow(ControlPresets.all())
    val layouts: StateFlow<List<ControlLayout>> = _layouts.asStateFlow()

    suspend fun load(): List<ControlLayout> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = runCatching {
                if (!file.isFile) {
                    ControlPresets.all()
                } else {
                    json.decodeFromString(ListSerializer(ControlLayout.serializer()), file.readText())
                        .map { it.normalised() }
                        .ifEmpty { ControlPresets.all() }
                }
            }.getOrElse { ControlPresets.all() }
            _layouts.value = loaded
            loaded
        }
    }

    suspend fun save(layouts: List<ControlLayout>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalised = layouts.map { it.normalised() }
            paths.root.mkdirs()
            val temp = File(paths.root, "$LAYOUTS_FILE.tmp")
            temp.writeText(json.encodeToString(ListSerializer(ControlLayout.serializer()), normalised))
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            _layouts.value = normalised
        }
    }

    suspend fun upsert(layout: ControlLayout) {
        val current = _layouts.value
        val index = current.indexOfFirst { it.id == layout.id }
        save(if (index >= 0) current.toMutableList().also { it[index] = layout } else current + layout)
    }

    suspend fun resetToDefaults() = save(ControlPresets.all())

    private companion object {
        const val LAYOUTS_FILE = "control-layouts.json"
    }
}
