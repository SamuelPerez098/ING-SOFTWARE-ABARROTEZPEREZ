package com.example.ing_software_abarrotezperez.ai.engine

import com.example.ing_software_abarrotezperez.ai.interpreter.LlmInterpreter
import com.example.ing_software_abarrotezperez.ai.interpreter.QueryParser
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.InterpretationResult
import com.example.ing_software_abarrotezperez.ai.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Orquesta: texto -> interpretación -> herramienta autorizada -> respuesta.
 *  1. Siempre intenta primero el motor de reglas (instantáneo, sin cargar el modelo).
 *  2. Solo si las reglas no encuentran NINGUNA pista, consulta al modelo local (si está instalado).
 *  3. La respuesta final la arma siempre una herramienta Kotlin con datos reales de SQLite.
 * Todo se ejecuta fuera del hilo principal.
 */
class AssistantEngine(
    private val parser: QueryParser,
    private val tools: ToolRegistry,
    private val llm: LlmInterpreter? = null
) {
    fun llmAvailability(): LlmAvailability = llm?.availability() ?: LlmAvailability.NOT_INSTALLED

    suspend fun respond(userText: String): AssistantResponse = withContext(Dispatchers.IO) {
        var i = parser.parse(userText)
        if (llm != null && noClueFromRules(i)) {
            // Cualquier fallo del modelo (null o excepción) conserva la respuesta de las reglas.
            val fromModel = try {
                llm.interpret(userText, parser)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            i = fromModel ?: i
        }
        when {
            i.directReply != null -> AssistantResponse(i.intent, i.directReply)
            i.clarification != null -> AssistantResponse(i.intent, i.clarification, needsClarification = true)
            else -> tools.execute(i.intent, i.params)
        }
    }

    /** Las reglas no entendieron nada y no hay mensaje específico (fiados, aclaración, "gracias"). */
    private fun noClueFromRules(r: InterpretationResult) =
        r.intent == AssistantIntent.UNKNOWN && r.clarification == null && r.directReply == null
}
