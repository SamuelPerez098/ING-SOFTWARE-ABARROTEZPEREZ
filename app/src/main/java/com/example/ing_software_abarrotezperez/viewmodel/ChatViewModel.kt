package com.example.ing_software_abarrotezperez.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.context.ChatHistoryStore
import com.example.ing_software_abarrotezperez.ai.engine.AssistantEngine
import com.example.ing_software_abarrotezperez.ai.engine.LiteRtLocalLLM
import com.example.ing_software_abarrotezperez.ai.engine.LlmAvailability
import com.example.ing_software_abarrotezperez.ai.engine.LocalLLMEngine
import com.example.ing_software_abarrotezperez.ai.interpreter.LlmInterpreter
import com.example.ing_software_abarrotezperez.ai.interpreter.ProductCatalog
import com.example.ing_software_abarrotezperez.ai.interpreter.ProductNameSource
import com.example.ing_software_abarrotezperez.ai.interpreter.QueryParser
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.ChatMessage
import com.example.ing_software_abarrotezperez.ai.models.ChatRole
import com.example.ing_software_abarrotezperez.ai.repository.SqliteAssistantRepository
import com.example.ing_software_abarrotezperez.ai.tools.ToolRegistry
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Estado del chat de Pérez AI. Usa su propia instancia de DatabaseHelper (como ScannerViewModel)
 * y SOLO la lee: el asistente no tiene ninguna herramienta de escritura.
 */
class ChatViewModel(app: Application) : AndroidViewModel(app) {

    private val db = DatabaseHelper(app)
    private val store = ChatHistoryStore(File(app.noBackupFilesDir, "perez_ai_historial.jsonl"))
    private val config = AssistantConfig(app)
    private val repo = SqliteAssistantRepository { db.readableDatabase }
    // Modelo de lenguaje local: se carga solo si las reglas no entienden la pregunta (ver AssistantEngine).
    private val llm: LocalLLMEngine = LiteRtLocalLLM(app)
    private val engine = AssistantEngine(
        // El catálogo permite reconocer nombres de productos en las preguntas (se recarga cada minuto).
        QueryParser(ProductCatalog(ProductNameSource { repo.listarProductos(config.excludeTestProducts).map { it.nombre } })),
        ToolRegistry(repo, config, logError = { msg, e -> Log.e(TAG, msg, e) }),
        LlmInterpreter(llm)
    )

    private val _modelStatus = MutableStateFlow(LlmAvailability.NOT_INSTALLED)
    val modelStatus: StateFlow<LlmAvailability> = _modelStatus

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _thinking = MutableStateFlow(false)
    val thinking: StateFlow<Boolean> = _thinking

    private var nextId = 1L
    private val historyLoaded = CompletableDeferred<Unit>()

    init {
        viewModelScope.launch {
            val saved = try {
                withContext(Dispatchers.IO) { store.load() }
            } catch (e: Exception) {
                Log.e(TAG, "No se pudo leer el historial", e); emptyList()
            }
            refreshModelStatus()
            val numbered = saved.map { it.copy(id = nextId++) }
            _messages.value = if (numbered.isEmpty()) listOf(welcome()) else numbered
            historyLoaded.complete(Unit)
        }
    }

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _thinking.value) return
        _thinking.value = true
        viewModelScope.launch {
            try {
                historyLoaded.await()
                add(ChatMessage(nextId++, ChatRole.USER, text))
                val r: AssistantResponse = try {
                    engine.respond(text)
                } catch (e: Exception) {
                    Log.e(TAG, "Fallo al responder", e)
                    AssistantResponse(
                        AssistantIntent.UNKNOWN,
                        "Ocurrió un problema al procesar tu pregunta. Intenta de nuevo.", isError = true
                    )
                }
                add(ChatMessage(nextId++, ChatRole.ASSISTANT, r.text, r.table, r.notes, isError = r.isError))
                refreshModelStatus()
            } finally {
                _thinking.value = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { store.clear() } } catch (e: Exception) { Log.e(TAG, "No se pudo borrar", e) }
            _messages.value = listOf(welcome())
        }
    }

    /** Libera la RAM del modelo (p. ej. cuando el sistema avisa de poca memoria). */
    fun releaseModel() = llm.release()

    private suspend fun refreshModelStatus() {
        try { _modelStatus.value = withContext(Dispatchers.IO) { llm.availability() } }
        catch (e: Exception) { Log.e(TAG, "No se pudo leer el estado del modelo", e) }
    }

    private suspend fun add(m: ChatMessage) {
        _messages.value = _messages.value + m
        try { withContext(Dispatchers.IO) { store.append(m) } } catch (e: Exception) { Log.e(TAG, "No se pudo guardar", e) }
    }

    private fun welcome() = ChatMessage(
        nextId++, ChatRole.ASSISTANT,
        "¡Hola! Soy Pérez AI. Respondo con los datos reales de tu tienda y no necesito Internet.\n" +
            "Pregúntame, por ejemplo, qué productos caducan pronto, cuáles se venden más o cuánto vendiste esta semana.",
        persist = false
    )

    override fun onCleared() {
        llm.close()
        db.close()
        super.onCleared()
    }

    private companion object { const val TAG = "PerezAI" }
}
