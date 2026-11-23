package com.example.ing_software_abarrotezperez.ai.context

import com.example.ing_software_abarrotezperez.ai.models.ChatMessage
import com.example.ing_software_abarrotezperez.ai.models.ChatRole
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Historial local en un archivo JSON Lines (una línea = un mensaje). No usa SQLite ni sale del teléfono.
 * Pensado para vivir en noBackupFilesDir (se excluye de los respaldos automáticos de Android).
 * Líneas dañadas se ignoran; el archivo se recorta a los últimos [maxMessages] mensajes.
 */
class ChatHistoryStore(private val file: File, private val maxMessages: Int = 300) {

    private var lineCount = -1

    @Synchronized
    fun load(): List<ChatMessage> {
        if (!file.exists()) { lineCount = 0; return emptyList() }
        val out = ArrayList<ChatMessage>()
        file.forEachLine(Charsets.UTF_8) { line ->
            if (line.isNotBlank()) fromJson(line)?.let { out.add(it) }
        }
        lineCount = out.size
        return out.takeLast(maxMessages)
    }

    @Synchronized
    fun append(m: ChatMessage) {
        if (!m.persist) return
        if (lineCount < 0) load()
        file.parentFile?.mkdirs()
        file.appendText(toJson(m) + "\n", Charsets.UTF_8)
        lineCount++
        if (lineCount > maxMessages + 100) compact()
    }

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
        lineCount = 0
    }

    private fun compact() {
        val keep = load().takeLast(maxMessages)
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(keep.joinToString("") { toJson(it) + "\n" }, Charsets.UTF_8)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        lineCount = keep.size
    }

    private fun toJson(m: ChatMessage): String = JSONObject().apply {
        put("r", if (m.role == ChatRole.USER) "U" else "A")
        put("t", m.text)
        put("ts", m.timestamp)
        if (m.isError) put("e", true)
        if (m.notes.isNotEmpty()) put("n", JSONArray(m.notes))
        m.table?.let { t ->
            put("h", JSONArray(t.headers))
            put("rw", JSONArray().also { rows -> t.rows.forEach { rows.put(JSONArray(it)) } })
        }
    }.toString()

    private fun fromJson(line: String): ChatMessage? = try {
        val o = JSONObject(line)
        val table = if (o.has("h")) {
            val h = o.getJSONArray("h").let { a -> List(a.length()) { a.getString(it) } }
            val rw = o.getJSONArray("rw").let { a ->
                List(a.length()) { i -> a.getJSONArray(i).let { r -> List(r.length()) { r.getString(it) } } }
            }
            ResultTable(h, rw)
        } else null
        ChatMessage(
            id = 0L,
            role = if (o.getString("r") == "U") ChatRole.USER else ChatRole.ASSISTANT,
            text = o.getString("t"),
            table = table,
            notes = if (o.has("n")) o.getJSONArray("n").let { a -> List(a.length()) { a.getString(it) } } else emptyList(),
            timestamp = o.optLong("ts", 0L),
            isError = o.optBoolean("e", false)
        )
    } catch (e: Exception) { null }
}
