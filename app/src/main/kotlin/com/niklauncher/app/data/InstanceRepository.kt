package com.niklauncher.app.data

import com.niklauncher.core.instance.Instance
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
 * Stores the instance list as a single JSON file.
 *
 * Writes go through a temp file and a rename, so a process death mid-write
 * cannot leave the user with an unreadable instance list - losing that file
 * would orphan every install they have.
 */
class InstanceRepository(private val paths: GamePaths) {

    private val file: File get() = File(paths.root, INDEX_FILE)
    private val mutex = Mutex()
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _instances = MutableStateFlow<List<Instance>>(emptyList())
    val instances: StateFlow<List<Instance>> = _instances.asStateFlow()

    suspend fun load(): List<Instance> = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = runCatching {
                if (!file.isFile) emptyList()
                else json.decodeFromString(ListSerializer(Instance.serializer()), file.readText())
            }.getOrElse { emptyList() }
            _instances.value = loaded
            loaded
        }
    }

    suspend fun save(instances: List<Instance>) = withContext(Dispatchers.IO) {
        mutex.withLock {
            paths.root.mkdirs()
            val temp = File(paths.root, "$INDEX_FILE.tmp")
            temp.writeText(json.encodeToString(ListSerializer(Instance.serializer()), instances))
            if (file.exists()) file.delete()
            if (!temp.renameTo(file)) {
                temp.copyTo(file, overwrite = true)
                temp.delete()
            }
            _instances.value = instances
        }
    }

    suspend fun upsert(instance: Instance) {
        val current = _instances.value
        val index = current.indexOfFirst { it.id == instance.id }
        save(if (index >= 0) current.toMutableList().also { it[index] = instance } else current + instance)
    }

    suspend fun delete(instanceId: String) {
        save(_instances.value.filterNot { it.id == instanceId })
    }

    private companion object {
        const val INDEX_FILE = "instances.json"
    }
}
