package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.insights.InsightsEngine
import com.example.ing_software_abarrotezperez.ai.insights.Severidad
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Herramientas de "negocio" (resumen de la tienda y consultas de fiado / ticket). Solo lectura.
 */
class BusinessTools(
    private val repo: AssistantRepository,
    private val config: AssistantConfig,
    private val clock: Clock = { java.time.LocalDate.now() },
    private val ultimoRespaldoMillis: () -> Long? = { null }
) {
    private val fecha = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    private fun etiqueta(s: Severidad) = when (s) {
        Severidad.CRITICA -> "Crítico"
        Severidad.ATENCION -> "Atención"
        Severidad.INFO -> "Info"
        Severidad.POSITIVA -> "Bien"
    }

    // ── Resumen de la tienda ──
    fun storeSummary(@Suppress("UNUSED_PARAMETER") p: QueryParameters): AssistantResponse {
        val engine = InsightsEngine(repo, config, { LocalDateTime.now() }, ultimoRespaldoMillis)
        val insights = engine.calcularSync().sortedBy { it.severidad.ordinal }
        val hoy = clock().format(fecha)
        if (insights.isEmpty()) {
            return AssistantResponse(
                AssistantIntent.STORE_SUMMARY,
                "Resumen de tu tienda al $hoy: no hay alertas. Todo en orden."
            )
        }
        val criticas = insights.count { it.severidad == Severidad.CRITICA }
        val atencion = insights.count { it.severidad == Severidad.ATENCION }
        val resumen = when {
            criticas > 0 -> "$criticas ${Fmt.plural(criticas, "alerta crítica", "alertas críticas")}"
            atencion > 0 -> "$atencion ${Fmt.plural(atencion, "aviso", "avisos")}"
            else -> "sin alertas"
        }
        return AssistantResponse(
            AssistantIntent.STORE_SUMMARY,
            "Resumen de tu tienda al $hoy: $resumen. Te detallo cada indicador abajo.",
            ResultTable(
                listOf("Nivel", "Indicador", "Detalle"),
                insights.map { listOf(etiqueta(it.severidad), it.titulo, it.detalle) }
            )
        )
    }
}
