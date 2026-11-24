package com.example.ing_software_abarrotezperez.ai.insights

/** Severidad del indicador; ordena de mayor a menor urgencia. */
enum class Severidad { CRITICA, ATENCION, INFO, POSITIVA }

/** Categoría del indicador (no incluye stock bajo ni caducidades: ya están en la tabla). */
enum class TipoInsight { COBRANZA, VENTAS, GANANCIA, MERMAS, RESPALDO }

/**
 * Indicador del resumen de la tienda.
 *
 * [id] es ESTABLE (slug:periodo:entidad): no depende del texto ni de las cifras. Se usa para
 * decidir "nuevo desde la última vista"; un indicador que no cambió de severidad no vuelve a
 * activar la campanita.
 */
data class Insight(
    val id: String,
    val tipo: TipoInsight,
    val severidad: Severidad,
    val titulo: String,
    val detalle: String,
    val preguntaSugerida: String
)
