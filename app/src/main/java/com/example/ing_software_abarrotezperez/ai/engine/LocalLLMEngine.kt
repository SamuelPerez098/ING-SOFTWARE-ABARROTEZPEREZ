package com.example.ing_software_abarrotezperez.ai.engine

enum class LlmAvailability {
    /** No hay archivo de modelo en el teléfono: el asistente funciona solo con reglas. */
    NOT_INSTALLED,
    /** Hay modelo en disco pero aún no está cargado en memoria. */
    AVAILABLE,
    /** Cargando el modelo (la primera vez puede tardar varios segundos). */
    LOADING,
    /** Modelo cargado y listo. */
    READY,
    /** Poca memoria libre: no se carga para no afectar el punto de venta. */
    LOW_MEMORY,
    /** Falló la carga; se reintenta tras un tiempo de espera. */
    FAILED
}

/**
 * Abstracción del modelo de lenguaje local. Solo devuelve TEXTO; no conoce SQLite ni las herramientas.
 * Cualquier fallo (modelo ausente, poca memoria, tiempo agotado, error nativo) se traduce en `null`
 * para que el motor de reglas siga respondiendo.
 */
interface LocalLLMEngine {
    fun availability(): LlmAvailability

    /** Genera una respuesta corta. Devuelve null si el modelo no está disponible o falla. */
    suspend fun complete(systemInstruction: String, userMessage: String, maxOutputTokens: Int): String?

    /** Libera la memoria del modelo (se vuelve a cargar al usarse de nuevo). */
    fun release()

    /** Libera todo de forma definitiva (al destruir la pantalla). */
    fun close()
}

/** Implementación sin modelo: el asistente funciona solo con el motor de reglas. */
object NoLocalLLM : LocalLLMEngine {
    override fun availability() = LlmAvailability.NOT_INSTALLED
    override suspend fun complete(systemInstruction: String, userMessage: String, maxOutputTokens: Int): String? = null
    override fun release() {}
    override fun close() {}
}
