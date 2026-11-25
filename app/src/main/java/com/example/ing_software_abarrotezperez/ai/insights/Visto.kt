package com.example.ing_software_abarrotezperez.ai.insights

/**
 * Lógica pura de "visto" para la campanita. Sin Android: opera sobre un mapa `id → severidad`.
 * La campanita suena si hay un CRITICA/ATENCION cuyo id no está visto o cuya severidad empeoró.
 */
object Visto {

    /** True si hay algo nuevo o que empeoró de severidad (CRITICA/ATENCION). */
    fun haySinVer(visto: Map<String, Severidad>, insights: List<Insight>): Boolean =
        insights.any { i ->
            (i.severidad == Severidad.CRITICA || i.severidad == Severidad.ATENCION) &&
                (visto[i.id] == null || i.severidad.ordinal < visto.getValue(i.id).ordinal)
        }

    /**
     * Marca como visto lo mostrado y limpia los ids que ya no existen (si un problema se
     * resuelve y regresa, vuelve a avisar).
     */
    fun marcarVisto(visto: Map<String, Severidad>, insights: List<Insight>): Map<String, Severidad> {
        val ids = insights.map { it.id }.toSet()
        return visto.filterKeys { it in ids }.toMutableMap().apply {
            insights.forEach { put(it.id, it.severidad) }
        }
    }
}
