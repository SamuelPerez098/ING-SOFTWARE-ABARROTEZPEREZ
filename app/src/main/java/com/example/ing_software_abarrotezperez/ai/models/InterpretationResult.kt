package com.example.ing_software_abarrotezperez.ai.models

enum class InterpretationSource { RULES, LLM }

/** Resultado del motor de interpretación. Nunca contiene SQL: solo una intención de la lista blanca. */
data class InterpretationResult(
    val intent: AssistantIntent,
    val params: QueryParameters = QueryParameters(),
    val confidence: Float = 0f,
    val source: InterpretationSource = InterpretationSource.RULES,
    /** Si no es null, el asistente pregunta esto en vez de ejecutar una herramienta. */
    val clarification: String? = null,
    /** Mensaje directo que no requiere consultar datos (p. ej. "gracias"). */
    val directReply: String? = null,
    /** Intenciones plausibles cuando hay duda (la Fase 5 permite que el modelo local elija entre ellas). */
    val candidates: List<AssistantIntent> = emptyList()
)
