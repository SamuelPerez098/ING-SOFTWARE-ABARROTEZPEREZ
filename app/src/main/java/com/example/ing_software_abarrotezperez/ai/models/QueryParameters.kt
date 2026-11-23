package com.example.ing_software_abarrotezperez.ai.models

/**
 * Parámetros ya interpretados. Todos son opcionales; cada herramienta aplica sus
 * valores por defecto y VALIDA rangos antes de tocar la base de datos.
 */
data class QueryParameters(
    val limit: Int? = null,
    val days: Int? = null,
    val period: TimePeriod? = null,
    val comparePeriod: TimePeriod? = null,
    val productQuery: String? = null,
    val threshold: Int? = null
)
