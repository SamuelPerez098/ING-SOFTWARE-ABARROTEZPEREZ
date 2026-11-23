package com.example.ing_software_abarrotezperez.ai.engine

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.cancellation.CancellationException

/**
 * Adaptador de LiteRT-LM (ÚNICO archivo que usa esa librería).
 *
 * Reglas que cumple (ver guía oficial de Android para LiteRT-LM):
 *  - Un solo hilo dueño del Engine y de cada Conversation (carga, generación, cancelación y cierre).
 *  - `initialize()` nunca en el hilo principal.
 *  - Conversation y Engine se cierran una sola vez; no se cierra el Engine si `initialize()` falló.
 *  - Una Conversation nueva por consulta (sin historial: cada pregunta se clasifica sola).
 *  - Backend CPU (no exige librerías del dispositivo); la GPU se evaluará al medir en el Realme.
 *
 * Cuidado de recursos:
 *  - Carga perezosa: el modelo solo se carga cuando las reglas no entienden una pregunta.
 *  - Se descarga de la RAM tras [idleUnloadMs] sin uso, o cuando el sistema lo pide ([release]).
 *  - No se carga si hay poca memoria libre ([minFreeMemBytes]).
 *  - Tras un fallo de carga espera [retryCooldownMs] antes de reintentar.
 *  - Un perro guardián cancela la generación a los [timeoutMs].
 */
class LiteRtLocalLLM(
    private val context: Context,
    private val locator: ModelLocator = ModelLocator(context),
    private val timeoutMs: Long = 30_000,
    private val idleUnloadMs: Long = 3 * 60_000,
    private val minFreeMemBytes: Long = 800L * 1024 * 1024,
    private val retryCooldownMs: Long = 5 * 60_000
) : LocalLLMEngine {

    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "perez-ai-llm") }
    private val dispatcher = executor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val mutex = Mutex()

    private var engine: Engine? = null
    private var idleJob: Job? = null
    @Volatile private var state: LlmAvailability = LlmAvailability.AVAILABLE
    @Volatile private var failedAt = 0L

    override fun availability(): LlmAvailability = when (state) {
        LlmAvailability.LOADING, LlmAvailability.READY, LlmAvailability.FAILED, LlmAvailability.LOW_MEMORY -> state
        else -> if (locator.find() != null) LlmAvailability.AVAILABLE else LlmAvailability.NOT_INSTALLED
    }

    override suspend fun complete(systemInstruction: String, userMessage: String, maxOutputTokens: Int): String? =
        withContext(dispatcher) {
            idleJob?.cancel()
            mutex.withLock {
                try {
                    if (!ensureLoadedLocked()) null else generateLocked(systemInstruction, userMessage, maxOutputTokens)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {          // incluye errores nativos (UnsatisfiedLinkError, JNI, OOM)
                    Log.e(TAG, "Fallo del modelo local", e)
                    if (e is OutOfMemoryError) releaseLocked()
                    null
                } finally {
                    scheduleIdleUnload()
                }
            }
        }

    private fun ensureLoadedLocked(): Boolean {
        if (engine != null) return true
        val file = locator.find()
        if (file == null) { state = LlmAvailability.NOT_INSTALLED; return false }
        if (failedAt != 0L && System.currentTimeMillis() - failedAt < retryCooldownMs) return false
        if (isLowMemory()) { state = LlmAvailability.LOW_MEMORY; return false }

        state = LlmAvailability.LOADING
        return try {
            val e = Engine(
                EngineConfig(
                    modelPath = file.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.path       // acelera la segunda carga
                )
            )
            e.initialize()                                 // bloquea varios segundos: estamos en el hilo del modelo
            engine = e                                     // solo se guarda si initialize() terminó bien
            failedAt = 0L
            state = LlmAvailability.READY
            true
        } catch (t: Throwable) {
            Log.e(TAG, "No se pudo cargar el modelo", t)
            failedAt = System.currentTimeMillis()
            state = LlmAvailability.FAILED
            false
        }
    }

    private suspend fun generateLocked(system: String, user: String, maxTokens: Int): String? {
        val eng = engine ?: return null
        val conversation = eng.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                // Casi determinista: queremos clasificar, no ser creativos.
                samplerConfig = SamplerConfig(topK = 1, topP = 0.95, temperature = 0.1),
                maxOutputToken = maxTokens
            )
        )
        val out = StringBuilder()
        var timedOut = false
        try {
            coroutineScope {
                // Perro guardián: la generación es asíncrona, así que este temporizador puede cancelarla.
                val watchdog = launch {
                    delay(timeoutMs)
                    timedOut = true
                    conversation.cancelProcess()
                }
                try {
                    conversation.sendMessageAsync(user).collect { out.append(it.toString()) }
                } finally {
                    watchdog.cancel()
                }
            }
        } catch (e: CancellationException) {
            // Pidieron cancelar: se detiene la generación nativa antes de cerrar la conversación.
            try { conversation.cancelProcess() } catch (_: Throwable) {}
            throw e
        } catch (e: Exception) {
            if (!timedOut) Log.e(TAG, "Error al generar", e)
        } finally {
            conversation.close()
        }
        if (timedOut) { Log.w(TAG, "Tiempo agotado en el modelo local"); return null }
        return out.toString().ifBlank { null }
    }

    private fun isLowMemory(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.lowMemory || info.availMem < minFreeMemBytes
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(idleUnloadMs)
            mutex.withLock { releaseLocked() }
        }
    }

    private fun releaseLocked() {
        val e = engine ?: return
        engine = null
        try { e.close() } catch (t: Throwable) { Log.e(TAG, "Error al cerrar el modelo", t) }
        if (state == LlmAvailability.READY || state == LlmAvailability.LOADING) state = LlmAvailability.AVAILABLE
    }

    override fun release() {
        scope.launch { mutex.withLock { releaseLocked() } }
    }

    override fun close() {
        scope.launch { mutex.withLock { releaseLocked() } }
            .invokeOnCompletion { scope.cancel(); executor.shutdown() }
    }

    private companion object { const val TAG = "PerezAI-LLM" }
}
