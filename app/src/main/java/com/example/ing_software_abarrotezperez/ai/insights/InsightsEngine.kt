package com.example.ing_software_abarrotezperez.ai.insights

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
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
 *
 * - Ids estables `slug:periodo:entidad` (no dependen de texto ni cifras): la campanita decide
 *   "nuevo desde la última vista" por ese id + severidad, no por el contenido.
 * - Comparativos justos (hoy vs mismo día de la semana pasada solo hasta la misma hora).
 * - "Sin ventas hoy" se suprime si ese día de la semana no vendió en las últimas 4 semanas
 *   (tienda cerrada) o antes de la hora configurada.
 * - Mínimo de historial por indicador: sin datos, no se inventa nada.
 * - Cálculo en segundo plano con caché de 60 s.
 */
class InsightsEngine(
    private val repo: AssistantRepository,
    private val cfg: AssistantConfig,
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

    private fun calcularSync(now: LocalDateTime): List<Insight> {
        val out = mutableListOf<Insight>()
        val hoy = now.toLocalDate()
        val hoyIso = hoy.toString()

        val historial = repo.resumenVentas(
            hoy.minusDays(cfg.diasHistorialMinimo.toLong()).toString(),
            hoy.plusDays(1).toString()
        )
        val hayHistorial = historial.tickets >= cfg.ventasMinimasHistorico

        cobranza(out, hoy, hoyIso)
        if (hayHistorial) {
            ventas(out, now, hoy)
            ganancia(out, hoy)
            mermas(out, hoy)
        }
        respaldo(out, hoyIso)
        return out
    }

    private fun id(slug: String, periodo: String, entidad: String = "") =
        if (entidad.isEmpty()) "$slug:$periodo" else "$slug:$periodo:$entidad"

    private fun semanaDe(d: LocalDate): String =
        "${d.get(WeekFields.ISO.weekBasedYear())}-W${d.get(WeekFields.ISO.weekOfWeekBasedYear())}"

    private fun money(v: Double) = "$%.2f".format(Locale.US, v)

    // ── Cobranza ──
    private fun cobranza(out: MutableList<Insight>, hoy: LocalDate, hoyIso: String) {
        val (total, cuentas) = repo.fiadoPendiente()
        if (total > 0) {
            out += Insight(
                id("fiado_pendiente", hoyIso), TipoInsight.COBRANZA, Severidad.ATENCION,
                "Cobranza pendiente",
                "$cuentas ${if (cuentas == 1) "fiado" else "fiados"} por cobrar · ${money(total)} en total.",
                "¿Quién me debe dinero?"
            )
        }

        val limite = hoy.minusDays(cfg.diasDeudaAntigua.toLong()).toString()
        repo.fiadoAntiguo(limite).forEach { d ->
            out += Insight(
                id("fiado_antiguo", hoyIso, "cliente${d.idCliente}"), TipoInsight.COBRANZA,
                if (d.monto >= 100) Severidad.CRITICA else Severidad.ATENCION,
                "Deuda antigua: ${d.nombre}",
                "Debe ${money(d.monto)} desde hace más de ${cfg.diasDeudaAntigua} días.",
                "¿Cuánto me debe ${d.nombre}?"
            )
        }

        repo.mayorDeudor()?.let { d ->
            out += Insight(
                id("mayor_deudor", hoyIso, "cliente${d.idCliente}"), TipoInsight.COBRANZA, Severidad.INFO,
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
        val hoyHasta = "$hoyIso ${String.format(Locale.US, "%02d:00:00", hora)}"
        val semanaPasada = hoy.minusDays(7)
        val semanaPasadaHasta = "$semanaPasada ${String.format(Locale.US, "%02d:00:00", hora)}"

        val hoyResumen = repo.resumenVentas(hoyIso, hoyHasta)
        val semanaPasadaResumen = repo.resumenVentas(semanaPasada.toString(), semanaPasadaHasta)

        if (semanaPasadaResumen.total > 0 && hoyResumen.total < semanaPasadaResumen.total * 0.5) {
            out += Insight(
                id("ventas_hoy_vs_semana", hoyIso), TipoInsight.VENTAS, Severidad.ATENCION,
                "Ventas de hoy por debajo de la semana pasada",
                "Hoy ${money(hoyResumen.total)} vs ${money(semanaPasadaResumen.total)} el mismo día de la semana pasada (a la misma hora).",
                "¿Cómo van las ventas de hoy?"
            )
        }

        if (hora >= cfg.horaSinVentas && hoyResumen.tickets == 0 && mismoDiaSemanaTuvoVentas(hoy)) {
            out += Insight(
                id("sin_ventas", hoyIso), TipoInsight.VENTAS, Severidad.CRITICA,
                "Sin ventas registradas hoy",
                "Pasó la hora de cierre (${cfg.horaSinVentas}:00) y aún no hay ventas.",
                "¿Por qué no hay ventas hoy?"
            )
        }

        val treinta = repo.resumenVentas(hoy.minusDays(30).toString(), hoy.plusDays(1).toString())
        if (treinta.tickets > 0) {
            out += Insight(
                id("ticket_promedio", "30d"), TipoInsight.VENTAS, Severidad.INFO,
                "Ticket promedio (30 días)",
                "${money(treinta.total / treinta.tickets)} por venta (${treinta.tickets} ventas).",
                "¿Cuál es mi ticket promedio?"
            )
        }
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
        if (anterior.ganancia > 0 && actual.ganancia < anterior.ganancia * 0.5) {
            out += Insight(
                id("ganancia_semana", semanaDe(iniSemana)), TipoInsight.GANANCIA, Severidad.ATENCION,
                "Ganancia de la semana por debajo de la anterior",
                "Esta semana ${money(actual.ganancia)} vs ${money(anterior.ganancia)} la semana pasada.",
                "¿Cómo va mi ganancia esta semana?"
            )
        }

        repo.listarProductos(cfg.excludeTestProducts)
            .filter { it.stock > 0 && it.precioVenta <= it.precioCompra }
            .forEach { p ->
                val negativo = p.precioVenta < p.precioCompra
                out += Insight(
                    id("margen_bajo", p.codigo), TipoInsight.GANANCIA,
                    if (negativo) Severidad.CRITICA else Severidad.ATENCION,
                    if (negativo) "Margen negativo: ${p.nombre}" else "Sin margen: ${p.nombre}",
                    "Venta ${money(p.precioVenta)} ≤ compra ${money(p.precioCompra)}.",
                    "¿Qué productos me dejan pérdida?"
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
        if (anterior.perdida > 0 && actual.perdida > anterior.perdida) {
            out += Insight(
                id("mermas_mes", mes), TipoInsight.MERMAS, Severidad.ATENCION,
                "Mermas al alza este mes",
                "Este mes ${money(actual.perdida)} vs ${money(anterior.perdida)} el mes pasado.",
                "¿Por qué subieron las mermas?"
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
    private fun respaldo(out: MutableList<Insight>, hoyIso: String) {
        val ultimo = ultimoRespaldoMillis()
        if (ultimo == null) {
            out += Insight(
                id("respaldo", hoyIso), TipoInsight.RESPALDO, Severidad.INFO,
                "Sin respaldo en la nube",
                "Aún no se ha subido la base de datos a Drive.",
                "¿Cómo hago un respaldo?"
            )
        } else {
            val dias = (System.currentTimeMillis() - ultimo) / (1000L * 60 * 60 * 24)
            if (dias >= cfg.diasRespaldoAlerta) {
                out += Insight(
                    id("respaldo", hoyIso), TipoInsight.RESPALDO,
                    if (dias >= cfg.diasRespaldoAlerta * 2) Severidad.CRITICA else Severidad.ATENCION,
                    "Respaldo desactualizado",
                    "El último respaldo fue hace $dias días.",
                    "¿Cuándo fue mi último respaldo?"
                )
            }
        }
    }
}
