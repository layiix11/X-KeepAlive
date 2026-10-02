package app.xkeepalive.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File

data class EventEntry(
    val timeMs: Long,
    val kind: String,
    val message: String,
)

class EventLogRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "event-log.jsonl")
    private val mutex = Mutex()
    private val _events = MutableStateFlow<List<EventEntry>>(emptyList())
    val events: StateFlow<List<EventEntry>> = _events.asStateFlow()

    suspend fun load() = mutex.withLock {
        if (!file.exists()) return@withLock
        val parsed = file.readLines()
            .mapNotNull { line -> parse(line) }
            .takeLast(MAX)
        _events.value = parsed
    }

    suspend fun append(kind: String, message: String) = mutex.withLock {
        val entry = EventEntry(System.currentTimeMillis(), kind, message.take(900))
        val next = (_events.value + entry).takeLast(MAX)
        _events.value = next
        file.parentFile?.mkdirs()
        file.writeText(next.joinToString("\n") { encode(it) })
    }

    suspend fun clear() = mutex.withLock {
        _events.value = emptyList()
        if (file.exists()) file.delete()
    }

    private fun encode(entry: EventEntry): String = JSONObject()
        .put("t", entry.timeMs)
        .put("k", entry.kind)
        .put("m", entry.message)
        .toString()

    private fun parse(line: String): EventEntry? = try {
        val json = JSONObject(line)
        EventEntry(
            timeMs = json.getLong("t"),
            kind = json.getString("k"),
            message = json.getString("m"),
        )
    } catch (_: Throwable) {
        null
    }

    private companion object {
        const val MAX = 400
    }
}
