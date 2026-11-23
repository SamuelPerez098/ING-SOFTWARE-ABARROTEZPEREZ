package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import java.time.LocalDate

class SalesTools(
    private val repo: AssistantRepository,
    private val config: AssistantConfig,
    private val clock: Clock = { LocalDate.now() }
) {
    private val excl get() = config.excludeTestProducts

    fun salesTotal(p: QueryParameters): AssistantResponse {
        val per = Params.period(p.period, TimePeriod.today(clock()))
        val r = repo.resumenVentas(per.from.toString(), per.toExclusive.toString())
        if (r.tickets == 0) return AssistantResponse(
            AssistantIntent.SALES_TOTAL, "No hay ventas registradas ${per.inLabel}."
        )
        val notes = mutableListOf<String>()
        if (r.fiado > 0) notes += "De ese total, ${Fmt.money(r.fiado)} quedó a fiado (todavía sin cobrar)."
        notes += "La ganancia es estimada con el precio de compra actual de cada producto."
        return AssistantResponse(
            AssistantIntent.SALES_TOTAL,
            "${per.inLabelCap} vendiste ${Fmt.money(r.total)} en ${r.tickets} ${Fmt.plural(r.tickets, "venta", "ventas")}.",
            ResultTable(
                listOf("Concepto", "Valor"),
                listOf(
                    listOf("Total vendido", Fmt.money(r.total)),
                    listOf("Número de ventas", r.tickets.toString()),
                    listOf("Unidades vendidas", r.unidades.toString()),
                    listOf("Ganancia estimada", Fmt.money(r.ganancia))
                )
            ),
            notes
        )
    }

    private fun ranking(p: QueryParameters, top: Boolean): AssistantResponse {
        val intent = if (top) AssistantIntent.TOP_SELLING else AssistantIntent.LEAST_SELLING
        val per = Params.period(p.period, TimePeriod.lastDays(30, clock()))
        val n = Params.limit(p, 5)
        val rows = repo.ventasPorProducto(per.from.toString(), per.toExclusive.toString(), n, top, excl)
        if (rows.isEmpty()) return AssistantResponse(intent, "No hay ventas de productos registradas ${per.inLabel}.")
        val encabezado = when {
            rows.size == 1 -> "Este es tu producto ${if (top) "más vendido" else "menos vendido"} ${per.inLabel}."
            else -> "Estos son tus ${rows.size} productos ${if (top) "más vendidos" else "menos vendidos"} ${per.inLabel}."
        }
        return AssistantResponse(
            intent,
            encabezado,
            ResultTable(
                listOf("#", "Producto", "Unid.", "Monto"),
                rows.mapIndexed { i, r -> listOf("${i + 1}", r.nombre, r.unidades.toString(), Fmt.money(r.monto)) }
            ),
            if (top) emptyList() else listOf(
                "Solo incluye productos que sí se vendieron; los que no tuvieron ventas aparecen en \"productos sin movimiento\"."
            )
        )
    }

    fun topSelling(p: QueryParameters) = ranking(p, true)
    fun leastSelling(p: QueryParameters) = ranking(p, false)

    fun compare(p: QueryParameters): AssistantResponse {
        val a = Params.period(p.period, TimePeriod.thisMonth(clock()))
        val b = Params.period(p.comparePeriod, a.previous())
        val ra = repo.resumenVentas(a.from.toString(), a.toExclusive.toString())
        val rb = repo.resumenVentas(b.from.toString(), b.toExclusive.toString())
        val change = if (rb.total > 0) (ra.total - rb.total) / rb.total * 100.0 else null
        val resumen = when {
            ra.tickets == 0 && rb.tickets == 0 -> "No hay ventas registradas en ninguno de los dos períodos."
            change == null -> "${a.inLabelCap} vendiste ${Fmt.money(ra.total)}; ${b.inLabel} no hubo ventas registradas."
            else -> "${a.inLabelCap} vendiste ${Fmt.money(ra.total)} frente a ${Fmt.money(rb.total)} ${b.inLabel} (${Fmt.pct(change)})."
        }
        return AssistantResponse(
            AssistantIntent.SALES_COMPARE, resumen,
            ResultTable(
                listOf("Concepto", a.label, b.label),
                listOf(
                    listOf("Total vendido", Fmt.money(ra.total), Fmt.money(rb.total)),
                    listOf("Ventas", ra.tickets.toString(), rb.tickets.toString()),
                    listOf("Unidades", ra.unidades.toString(), rb.unidades.toString()),
                    listOf("Ganancia estimada", Fmt.money(ra.ganancia), Fmt.money(rb.ganancia))
                )
            ),
            if (a.days != b.days) listOf("Los dos períodos no duran lo mismo (${a.days} vs ${b.days} días).") else emptyList()
        )
    }
}
