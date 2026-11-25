package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.insights.InsightsEngine
import com.example.ing_software_abarrotezperez.ai.insights.Severidad
import com.example.ing_software_abarrotezperez.ai.interpreter.TextNormalizer
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
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

    // ── Cobranza / fiados ──
    fun cobranza(p: QueryParameters): AssistantResponse {
        val hoy = clock().format(fecha)
        val deudores = repo.deudores(50)
        val nombre = limpiaNombre(p.productQuery)

        // "¿cuánto me debe X?" -> busca por nombre de cliente.
        if (nombre.isNotBlank()) {
            val tokens = TextNormalizer.words(nombre).map { TextNormalizer.stem(it) }
            val hits = deudores.filter { d ->
                val n = TextNormalizer.normalize(d.nombre)
                tokens.all { n.contains(it) }
            }
            return when {
                hits.isEmpty() ->
                    AssistantResponse(
                        AssistantIntent.COBRANZA,
                        "No encontré a ningún cliente con saldo pendiente que coincida con \"$nombre\"."
                    )
                hits.size == 1 -> {
                    val d = hits.first()
                    AssistantResponse(
                        AssistantIntent.COBRANZA,
                        "${d.nombre} te debe ${Fmt.money(d.monto)} a hoy ($hoy)."
                    )
                }
                else -> AssistantResponse(
                    AssistantIntent.COBRANZA,
                    "Encontré ${hits.size} clientes con ese nombre:",
                    ResultTable(
                        listOf("Cliente", "Debe"),
                        hits.map { listOf(it.nombre, Fmt.money(it.monto)) }
                    )
                )
            }
        }

        // Sin nombre: total pendiente + mayores deudores.
        val (total, cuentas) = repo.fiadoPendiente()
        if (total <= 0) {
            return AssistantResponse(AssistantIntent.COBRANZA, "No tienes fiados pendientes por cobrar.")
        }
        val mayor = deudores.firstOrNull()
        val texto = buildString {
            append("Tienes ${Fmt.plural(cuentas, "un fiado", "$cuentas fiados")} por cobrar, ")
            append("${Fmt.money(total)} en total a hoy ($hoy).")
            mayor?.let { append(" El mayor deudor es ${it.nombre} (${Fmt.money(it.monto)}).") }
        }
        return AssistantResponse(
            AssistantIntent.COBRANZA,
            texto,
            ResultTable(
                listOf("Cliente", "Debe"),
                deudores.take(5).map { listOf(it.nombre, Fmt.money(it.monto)) }
            )
        )
    }

    // ── Ticket promedio ──
    // Solo lectura: total vendido / número de ventas del periodo (por defecto, los últimos 30 días,
    // igual que el indicador "Ticket promedio" de la campanita) y comparación con el periodo anterior.
    fun ticketPromedio(p: QueryParameters): AssistantResponse {
        val per = Params.period(p.period, TimePeriod.lastDays(30, clock()))
        val r = repo.resumenVentas(per.from.toString(), per.toExclusive.toString())
        if (r.tickets == 0) {
            return AssistantResponse(
                AssistantIntent.TICKET_PROMEDIO,
                "No hay ventas registradas ${per.inLabel}, así que no hay ticket promedio que calcular."
            )
        }
        val promedio = r.total / r.tickets

        val ant = per.previous()
        val ra = repo.resumenVentas(ant.from.toString(), ant.toExclusive.toString())
        val promedioAnterior = if (ra.tickets > 0) ra.total / ra.tickets else null
        val cambio = if (promedioAnterior != null && promedioAnterior > 0.0)
            (promedio - promedioAnterior) / promedioAnterior * 100.0 else null

        val texto = buildString {
            append("${per.inLabelCap} tu ticket promedio fue ${Fmt.money(promedio)} ")
            append("(${r.tickets} ${Fmt.plural(r.tickets, "venta", "ventas")}, ${Fmt.money(r.total)} en total).")
            if (promedioAnterior != null && cambio != null)
                append(" Frente a ${Fmt.money(promedioAnterior)} ${ant.inLabel} (${Fmt.pct(cambio)}).")
        }

        val filas = mutableListOf<List<String>>()
        filas.add(listOf("Ticket promedio", Fmt.money(promedio)))
        filas.add(listOf("Número de ventas", r.tickets.toString()))
        filas.add(listOf("Total vendido", Fmt.money(r.total)))
        if (promedioAnterior != null) filas.add(listOf("Periodo anterior", Fmt.money(promedioAnterior)))

        val notas = mutableListOf<String>()
        if (r.fiado > 0) notas.add("Incluye ${Fmt.money(r.fiado)} vendidos a fiado (todavía sin cobrar).")
        if (r.tickets < config.ventasMinimasHistorico)
            notas.add("Con pocas ventas el promedio puede variar mucho.")

        return AssistantResponse(
            AssistantIntent.TICKET_PROMEDIO,
            texto,
            ResultTable(listOf("Concepto", "Valor"), filas),
            notas
        )
    }

    private fun limpiaNombre(q: String?): String {
        if (q.isNullOrBlank()) return ""
        val ruido = Regex("\\b(?:debe|deben|debo|me|te|le|les|quien|quienes|fiado\\w*|adeudo\\w*|deuda\\w*|dinero|plata|lana|cuanto|cuanta|cuantos|cuantas|mas|desde|cuando|y)\\b")
        return ruido.replace(q, " ").trim().replace(Regex("\\s+"), " ")
    }
}
