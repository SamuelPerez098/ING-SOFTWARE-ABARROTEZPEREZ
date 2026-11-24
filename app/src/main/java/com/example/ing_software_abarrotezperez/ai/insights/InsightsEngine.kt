package com.example.ing_software_abarrotezperez.ai.insights

import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Motor determinista del resumen de la tienda. No depende del modelo de lenguaje.
 * Ids estables `slug[:entidad]` (estado) o `slug:periodo[:entidad]` (día/mes/semana).
 * Cálculo en segundo plano con caché de 60 s.
 */
class InsightsEngine(
    private val repo: AssistantRepository,
    private val cfg: InsightConfig,
    private val ahora: () -> LocalDateTime = { LocalDateTime.now() },
    private val ultimoRespaldoMillis: () -> Long? = { null }
) {

    private val mutex = Mutex()
    private var cache: Pair<Long, List<Insight>>? = null

    suspend fun calcular(): List<Insight> {
        val ts = System.currentTimeMillis()
        mutex.withLock { cache?.let { if (ts - it.first < 60_000L) return it.second } }
        val resultado = withContext(Dispatchers.IO) { calcularSync(ahora()) }
        mutex.withLock { cache = ts to resultado }
        return resultado
    }

    /** Versión síncrona para herramientas (corre en el hilo del motor; sin caché ni hilos propios). */
    fun calcularSync(): List<Insight> = calcularSync(ahora())

    private fun calcularSync(now: LocalDateTime): List<Insight> {
        val out = mutableListOf<Insight>()
        val hoy = now.toLocalDate()

        val historial = repo.resumenVentas(
            hoy.minusDays(cfg.diasHistorialMinimo.toLong()).toString(),
            hoy.plusDays(1).toString()
        )
        val hayHistorial = historial.tickets >= cfg.ventasMinimasHistorico

        cobranza(out, hoy)
        if (hayHistorial) {
            ventas(out, now, hoy)
            ganancia(out, hoy)
            mermas(out, hoy)
        }
        respaldo(out)
        return out
    }

    private fun id(slug: String, periodo: String = "", entidad: String = "") = when {
        periodo.isNotEmpty() && entidad.isNotEmpty() -> "$slug:$periodo:$entidad"
        periodo.isNotEmpty() -> "$slug:$periodo"
        entidad.isNotEmpty() -> "$slug:$entidad"
        else -> slug
    }

    private fun semanaDe(d: LocalDate): String =
        "${d.get(WeekFields.ISO.weekBasedYear())}-W${d.get(WeekFields.ISO.weekOfWeekBasedYear())}"

    private fun money(v: Double) = "$%.2f".format(Locale.US, v)

    private fun horaFmt(h: Int) = String.format(Locale.US, "%02d:00:00", h)

    // ── Cobranza ──
    private fun cobranza(out: MutableList<Insight>, hoy: LocalDate) {
        val (total, cuentas) = repo.fiadoPendiente()
        if (total > 0) {
            val sev = when {
                total >= cfg.montoDeudaTotalCritica.toDouble() -> Severidad.CRITICA
                total >= cfg.montoDeudaAlerta.toDouble() -> Severidad.ATENCION
                else -> Severidad.INFO
            }
            out += Insight(
                id("fiado_pendiente"), TipoInsight.COBRANZA, sev,
                "Cobranza pendiente",
                "$cuentas ${if (cuentas == 1) "fiado" else "fiados"} por cobrar · ${money(total)} en total.",
                "¿Quién me debe dinero?"
            )
        }

        val limiteCritica = hoy.minusDays(cfg.diasDeudaCritica.toLong()).toString()
        val criticos = repo.fiadoAntiguo(limiteCritica).filter { it.monto >= cfg.montoDeudaCritica.toDouble() }
        val criticosIds = criticos.map { it.idCliente }.toSet()
        criticos.forEach { d ->
            out += Insight(
                id("fiado_antiguo", entidad = "cliente${d.idCliente}"), TipoInsight.COBRANZA, Severidad.CRITICA,
                "Deuda crítica: ${d.nombre}",
                "Debe ${money(d.monto)} desde hace más de ${cfg.diasDeudaCritica} días.",
                "¿Cuánto me debe ${d.nombre}?"
            )
        }

        val limiteAtencion = hoy.minusDays(cfg.diasDeudaAntigua.toLong()).toString()
        repo.fiadoAntiguo(limiteAtencion).filter { it.idCliente !in criticosIds }.forEach { d ->
            out += Insight(
                id("fiado_antiguo", entidad = "cliente${d.idCliente}"), TipoInsight.COBRANZA, Severidad.ATENCION,
                "Deuda antigua: ${d.nombre}",
                "Debe ${money(d.monto)} desde hace más de ${cfg.diasDeudaAntigua} días.",
                "¿Cuánto me debe ${d.nombre}?"
            )
        }

        repo.mayorDeudor()?.let { d ->
            out += Insight(
                id("mayor_deudor", entidad = "cliente${d.idCliente}"), TipoInsight.COBRANZA,
                if (d.monto >= cfg.montoDeudaAlerta.toDouble()) Severidad.ATENCION else Severidad.INFO,
                "Mayor deudor: ${d.nombre}",
                "Concentra ${money(d.monto)} de la deuda total.",
                "¿Qué me debe ${d.nombre} y desde cuándo?"
            )
        }
    }

    // ── Ventas ──
    private fun ventas(out: MutableList<Insight>, now: LocalDateTime, hoy: LocalDate) {
        val hoyIso = hoy.toString()
        val hora = now.hour
        val hoyHasta = "$hoyIso ${horaFmt(hora)}"
        val semanaPasada = hoy.minusDays(7)

        val hoyResumen = repo.resumenVentas(hoyIso, hoyHasta)
        val semanaPasadaResumen = repo.resumenVentas(semanaPasada.toString(), "$semanaPasada ${horaFmt(hora)}")

        // hoy vs mismo día semana pasada (INFO/ATENCION, nunca CRITICA)
        if (semanaPasadaResumen.total > 0 && hoyResumen.total < semanaPasadaResumen.total * 0.5) {
            out += Insight(
                id("ventas_hoy_vs_semana", hoyIso), TipoInsight.VENTAS, Severidad.ATENCION,
                "Ventas de hoy por debajo de la semana pasada",
                "Hoy ${money(hoyResumen.total)} vs ${money(semanaPasadaResumen.total)} el mismo día de la semana pasada (a la misma hora).",
                "¿Cómo van las ventas de hoy?"
            )
        }

        // hoy vs promedio de las últimas 4 semanas (mismo día y hora) -> CRITICA
        val (promedio, semanasConVentas) = promedioMismoDia(hoy, hora)
        if (semanasConVentas >= 3 && promedio > 0 && hoyResumen.total < promedio * cfg.caidaVentasCritica.toDouble()) {
            out += Insight(
                id("ventas_hoy_vs_promedio", hoyIso), TipoInsight.VENTAS, Severidad.CRITICA,
                "Ventas de hoy muy por debajo del promedio",
                "Hoy ${money(hoyResumen.total)} vs ${money(promedio)} de promedio en las últimas 4 semanas (mismo día y hora).",
                "¿Cuánto he vendido hoy?"
            )
        }

        // sin ventas: ATENCION tras horaSinVentas, CRITICA tras horaCierre si el día suele vender
        if (hoyResumen.tickets == 0) {
            if (hora >= cfg.horaCierre && promedioTicketsDiaSemana(hoy) >= cfg.ticketsMinimosDiaActivo) {
                out += Insight(
                    id("sin_ventas", hoyIso), TipoInsight.VENTAS, Severidad.CRITICA,
                    "Sin ventas registradas hoy",
                    "Pasó la hora de cierre (${cfg.horaCierre}:00) y este día suele vender.",
                    "¿Cuánto he vendido hoy?"
                )
            } else if (hora >= cfg.horaSinVentas && mismoDiaSemanaTuvoVentas(hoy)) {
                out += Insight(
                    id("sin_ventas", hoyIso), TipoInsight.VENTAS, Severidad.ATENCION,
                    "Sin ventas registradas hoy",
                    "Pasó la hora de cierre (${cfg.horaSinVentas}:00) y aún no hay ventas.",
                    "¿Cuánto he vendido hoy?"
                )
            }
        }

        val treinta = repo.resumenVentas(hoy.minusDays(30).toString(), hoy.plusDays(1).toString())
        if (treinta.tickets > 0) {
            out += Insight(
                id("ticket_promedio"), TipoInsight.VENTAS, Severidad.INFO,
                "Ticket promedio (30 días)",
                "${money(treinta.total / treinta.tickets)} por venta (${treinta.tickets} ventas).",
                "¿Cuánto he vendido en los últimos 30 días?"
            )
        }
    }

    private fun promedioMismoDia(hoy: LocalDate, hora: Int): Pair<Double, Int> {
        var suma = 0.0
        var conVentas = 0
        for (i in 1..4) {
            val d = hoy.minusWeeks(i.toLong())
            val r = repo.resumenVentas(d.toString(), "$d ${horaFmt(hora)}")
            if (r.tickets > 0) { suma += r.total; conVentas++ }
        }
        return (if (conVentas > 0) suma / conVentas else 0.0) to conVentas
    }

    private fun promedioTicketsDiaSemana(hoy: LocalDate, semanas: Int = 4): Double {
        var total = 0
        for (i in 1..semanas) {
            val d = hoy.minusWeeks(i.toLong())
            total += repo.resumenVentas(d.toString(), d.plusDays(1).toString()).tickets
        }
        return total.toDouble() / semanas
    }

    private fun mismoDiaSemanaTuvoVentas(hoy: LocalDate, semanas: Int = 4): Boolean {
        for (i in 1..semanas) {
            val d = hoy.minusWeeks(i.toLong())
            if (repo.resumenVentas(d.toString(), d.plusDays(1).toString()).tickets > 0) return true
        }
        return false
    }

    // ── Ganancia ──
    private fun ganancia(out: MutableList<Insight>, hoy: LocalDate) {
        val iniSemana = hoy.minusDays((hoy.dayOfWeek.value - 1).toLong())
        val actual = repo.resumenVentas(iniSemana.toString(), hoy.plusDays(1).toString())
        val anterior = repo.resumenVentas(iniSemana.minusWeeks(1).toString(), iniSemana.toString())
        val semana = semanaDe(iniSemana)

        when {
            anterior.ganancia >= cfg.pisoGananciaSemanal.toDouble() &&
                actual.ganancia < anterior.ganancia * cfg.caidaVentasCritica.toDouble() ->
                out += Insight(
                    id("ganancia_semana", semana), TipoInsight.GANANCIA, Severidad.CRITICA,
                    "Ganancia de la semana desplomada",
                    "Esta semana ${money(actual.ganancia)} vs ${money(anterior.ganancia)} la semana pasada.",
                    "¿Cuánto he ganado esta semana?"
                )
            anterior.ganancia > 0 && actual.ganancia < anterior.ganancia * 0.5 ->
                out += Insight(
                    id("ganancia_semana", semana), TipoInsight.GANANCIA, Severidad.ATENCION,
                    "Ganancia de la semana por debajo de la anterior",
                    "Esta semana ${money(actual.ganancia)} vs ${money(anterior.ganancia)} la semana pasada.",
                    "¿Cómo va mi ganancia esta semana?"
                )
        }

        val perdida7d = repo.perdidaMargenNegativo(hoy.minusDays(7).toString(), hoy.plusDays(1).toString())
        repo.listarProductos(cfg.excludeTestProducts)
            .filter { it.stock > 0 && it.precioVenta <= it.precioCompra }
            .forEach { p ->
                val negativo = p.precioVenta < p.precioCompra
                out += Insight(
                    id("margen_bajo", entidad = p.codigo), TipoInsight.GANANCIA, Severidad.ATENCION,
                    if (negativo) "Vendido bajo el costo: ${p.nombre}" else "Sin margen: ${p.nombre}",
                    if (negativo) "Venta ${money(p.precioVenta)} < compra ${money(p.precioCompra)} (¿promoción?)."
                    else "Venta ${money(p.precioVenta)} = compra ${money(p.precioCompra)}.",
                    "¿Qué productos me dejan pérdida?"
                )
            }

        if (perdida7d >= cfg.perdidaMargenCritica.toDouble()) {
            out += Insight(
                id("perdida_margen", hoy.plusDays(0).toString()), TipoInsight.GANANCIA, Severidad.CRITICA,
                "Pérdida por vender bajo el costo",
                "En los últimos 7 días perdiste ${money(perdida7d)} vendiendo bajo el costo.",
                "¿Qué productos vendí bajo el costo?"
            )
        }
    }

    // ── Mermas ──
    private fun mermas(out: MutableList<Insight>, hoy: LocalDate) {
        val iniMes = hoy.withDayOfMonth(1)
        val mes = "${hoy.year}-${String.format(Locale.US, "%02d", hoy.monthValue)}"
        val iniMesAnterior = iniMes.minusMonths(1)
        val actual = repo.mermas(iniMes.toString(), hoy.plusDays(1).toString(), 50, cfg.excludeTestProducts)
        val anterior = repo.mermas(iniMesAnterior.toString(), iniMes.toString(), 50, cfg.excludeTestProducts)

        when {
            anterior.perdida > 0 && actual.perdida >= cfg.mermasPisoCritico.toDouble() &&
                actual.perdida >= anterior.perdida * cfg.factorMermaCritica.toDouble() ->
                out += Insight(
                    id("mermas_mes", mes), TipoInsight.MERMAS, Severidad.CRITICA,
                    "Mermas críticas este mes",
                    "Este mes ${money(actual.perdida)} vs ${money(anterior.perdida)} el mes pasado.",
                    "¿Cuánto he perdido en mermas este mes?"
                )
            anterior.perdida > 0 && actual.perdida > anterior.perdida &&
                actual.perdida >= cfg.mermasPisoPesos.toDouble() ->
                out += Insight(
                    id("mermas_mes", mes), TipoInsight.MERMAS, Severidad.ATENCION,
                    "Mermas al alza este mes",
                    "Este mes ${money(actual.perdida)} vs ${money(anterior.perdida)} el mes pasado.",
                    "¿Cuánto he perdido en mermas este mes?"
                )
        }

        actual.items.firstOrNull()?.let { m ->
            out += Insight(
                id("merma_producto", mes, m.nombre), TipoInsight.MERMAS, Severidad.INFO,
                "Mayor pérdida: ${m.nombre}",
                "${m.unidades} ${if (m.unidades == 1) "unidad" else "unidades"} · ${money(m.perdida)} (${m.motivos}).",
                "¿Cuánto he perdido en mermas?"
            )
        }
    }

    // ── Respaldo ──
    private fun respaldo(out: MutableList<Insight>) {
        val ultimo = ultimoRespaldoMillis()
        if (ultimo == null) {
            out += Insight(
                id("respaldo"), TipoInsight.RESPALDO, Severidad.INFO,
                "Respaldo",
                "Aún no hay registro de respaldos.",
                "¿Cómo hago un respaldo?"
            )
        } else {
            val dias = (System.currentTimeMillis() - ultimo) / (1000L * 60 * 60 * 24)
            if (dias >= cfg.diasRespaldoAlerta) {
                out += Insight(
                    id("respaldo"), TipoInsight.RESPALDO,
                    if (dias >= cfg.diasRespaldoAlerta * 2) Severidad.CRITICA else Severidad.ATENCION,
                    "Respaldo desactualizado",
                    "El último respaldo fue hace $dias días.",
                    "¿Cuándo fue mi último respaldo?"
                )
            }
        }
    }
}
