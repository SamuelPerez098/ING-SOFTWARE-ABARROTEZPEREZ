package com.example.ing_software_abarrotezperez.ai.models

/** Tabla simple para mostrar resultados como tarjeta/tabla en el chat. */
data class ResultTable(val headers: List<String>, val rows: List<List<String>>)

data class AssistantResponse(
    val intent: AssistantIntent,
    val text: String,
    val table: ResultTable? = null,
    /** Advertencias sobre calidad de datos (p. ej. productos de prueba excluidos). */
    val notes: List<String> = emptyList(),
    val isError: Boolean = false,
    val needsClarification: Boolean = false
)
