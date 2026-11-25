package com.example.ing_software_abarrotezperez.ai.interpreter

import com.example.ing_software_abarrotezperez.ai.engine.LlmAvailability
import com.example.ing_software_abarrotezperez.ai.engine.LocalLLMEngine
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.InterpretationResult
import com.example.ing_software_abarrotezperez.ai.models.InterpretationSource
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import org.json.JSONObject
import java.time.LocalDate

/** Lo que el modelo "sugiere": una intención de la lista blanca y parámetros ya validados. */
data class LlmSuggestion(val intent: AssistantIntent, val params: QueryParameters)

/**
 * Convierte la salida del modelo en una [LlmSuggestion] o null. La salida del modelo es NO CONFIABLE:
 * se acepta solo un JSON con una intención conocida y parámetros dentro de rangos estrictos.
 * Cualquier otra cosa (texto libre, SQL, claves desconocidas) se ignora.
 */
object LlmOutputParser {

    private val THINK = Regex("<think>[\\s\\S]*?</think>")
    private val LAST_N_DAYS = Regex("^last_(\\d{1,3})_days$")

    fun parse(raw: String, now: LocalDate): LlmSuggestion? {
        var text = raw.replace(THINK, "").trim()
        if (text.contains("<think>")) return null           // razonamiento sin cerrar: no hay respuesta útil
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        text = text.substring(start, end + 1)

        val o = try { JSONObject(text) } catch (e: Exception) { return null }
        val name = o.optString("intent").trim().uppercase().replace(' ', '_').replace('-', '_')
        val intent = AssistantIntent.values().firstOrNull { it.name == name } ?: return null
        if (intent == AssistantIntent.UNKNOWN) return null

        val params = QueryParameters(
            limit = intInRange(o, "limit", 1, 50),
            days = intInRange(o, "days", 1, 365),
            threshold = intInRange(o, "threshold", 1, 1000),
            period = period(o.optString("period"), now),
            comparePeriod = period(o.optString("compare_period"), now),
            productQuery = product(o.optString("product"))
        )
        return LlmSuggestion(intent, params)
    }

    private fun intInRange(o: JSONObject, key: String, min: Int, max: Int): Int? {
        if (!o.has(key) || o.isNull(key)) return null
        val v = when (val x = o.opt(key)) {
            is Number -> if (x.toDouble() % 1.0 == 0.0) x.toInt() else null
            is String -> x.trim().toIntOrNull()
            else -> null
        } ?: return null
        return v.takeIf { it in min..max }
    }

    /** Vocabulario cerrado de períodos; el modelo nunca escribe fechas. */
    fun period(name: String, now: LocalDate): TimePeriod? = when (val n = name.trim().lowercase()) {
        "today" -> TimePeriod.today(now)
        "yesterday" -> TimePeriod.yesterday(now)
        "this_week" -> TimePeriod.thisWeek(now)
        "last_week" -> TimePeriod.lastWeek(now)
        "this_month" -> TimePeriod.thisMonth(now)
        "last_month" -> TimePeriod.lastMonth(now)
        else -> LAST_N_DAYS.find(n)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..365 }
            ?.let { TimePeriod.lastDays(it, now) }
    }

    private fun product(raw: String): String? {
        val words = TextNormalizer.words(raw).take(6).map { it.take(30) }
        return words.joinToString(" ").ifBlank { null }
    }
}

/**
 * Usa el modelo local SOLO como respaldo cuando las reglas no entienden la pregunta.
 * No ve la base de datos ni genera cifras: devuelve una intención autorizada, que luego ejecuta
 * una herramienta Kotlin con consultas parametrizadas, igual que cualquier otra pregunta.
 */
class LlmInterpreter(
    private val llm: LocalLLMEngine,
    private val clock: () -> LocalDate = { LocalDate.now() }
) {
    fun availability(): LlmAvailability = llm.availability()

    suspend fun interpret(userText: String, parser: QueryParser): InterpretationResult? {
        if (llm.availability() == LlmAvailability.NOT_INSTALLED) return null
        val question = userText.trim().take(MAX_QUESTION_CHARS)
        if (question.isEmpty()) return null
        val raw = llm.complete(SYSTEM_PROMPT, "$question /no_think", MAX_OUTPUT_TOKENS) ?: return null
        val s = LlmOutputParser.parse(raw, clock()) ?: return null
        return parser.interpretAs(s.intent, question, s.params, InterpretationSource.LLM, 0.6f)
    }

    companion object {
        const val MAX_QUESTION_CHARS = 300
        const val MAX_OUTPUT_TOKENS = 80

        // El modelo recomendado (Qwen3-0.6B ekv1280) tiene contexto de 1280 tokens: mantener este texto corto.
        const val SYSTEM_PROMPT = """Clasificas preguntas del dueño de una tienda de abarrotes. Responde SOLO un objeto JSON en una línea, sin explicación.
Intenciones: LOW_STOCK (pocas existencias), OUT_OF_STOCK (agotados), INVENTORY_VALUE (dinero invertido en inventario), RESTOCK_NEEDS (qué comprar o surtir), EXPIRING_SOON (próximos a caducar), EXPIRED (ya caducados), TOP_SELLING (más vendidos), LEAST_SELLING (menos vendidos), SALES_TOTAL (cuánto se vendió), SALES_COMPARE (comparar ventas), NO_MOVEMENT (productos sin ventas), LOW_ROTATION (se venden despacio), SHRINKAGE (mermas y pérdidas), SEARCH_PRODUCT (buscar un producto), STORE_SUMMARY (resumen de la tienda), COBRANZA (fiados: quién te debe), TICKET_PROMEDIO (ticket promedio), HELP (ayuda), UNKNOWN (no es de la tienda).
Campos opcionales: days (número de días), limit (cuántos resultados), period (today, yesterday, this_week, last_week, this_month, last_month, last_7_days, last_30_days), compare_period, product (nombre del producto).
Ejemplos:
¿qué se me va a echar a perder esta semana? -> {"intent":"EXPIRING_SOON","days":7}
¿cómo me fue ayer en la caja? -> {"intent":"SALES_TOTAL","period":"yesterday"}
¿qué mercancía llevo un mes sin sacar? -> {"intent":"NO_MOVEMENT","days":30}
los 3 que más dejan -> {"intent":"TOP_SELLING","limit":3}
¿cuánto cuesta el jabón zote? -> {"intent":"SEARCH_PRODUCT","product":"jabon zote"}
¿quién ganó el partido? -> {"intent":"UNKNOWN"}"""
    }
}
