package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import java.time.LocalDate

class StatisticsTools(
    private val repo: AssistantRepository,
    private val config: AssistantConfig,
    private val clock: Clock = { LocalDate.now() }
) {
    private val excl get() = config.excludeTestProducts

    fun noMovement(p: QueryParameters): AssistantResponse {
        val dias = Params.days(p, 30)
        val per = TimePeriod.lastDays(dias, clock())
        val r = repo.sinMovimiento(per.from.toString(), per.toExclusive.toString(), Params.limit(p, 15), excl)
        if (r.total == 0) return AssistantResponse(
            AssistantIntent.NO_MOVEMENT, "Todos los productos con existencias tuvieron ventas ${per.inLabel}."
        )
        val partial = if (r.total > r.items.size) " Te muestro los ${r.items.size} con más dinero detenido." else ""
        return AssistantResponse(
            AssistantIntent.NO_MOVEMENT,
            "${r.total} ${Fmt.plural(r.total, "producto con existencias no se vendió", "productos con existencias no se vendieron")} " +
                "${per.inLabel}; hay ${Fmt.money(r.capital)} de costo detenido.$partial",
            ResultTable(
                listOf("Producto", "Stock", "Costo detenido"),
                r.items.map { listOf(it.nombre, it.stock.toString(), Fmt.money(it.capital)) }
            ),
            listOf("Incluye productos agregados recientemente, porque la app no guarda su fecha de alta.")
        )
    }

    fun lowRotation(p: QueryParameters): AssistantResponse {
        val dias = Params.days(p, 30)
        val minCover = 90
        val per = TimePeriod.lastDays(dias, clock())
        val rows = repo.bajaRotacion(per.from.toString(), per.toExclusive.toString(), dias, minCover, Params.limit(p, 15), excl)
        if (rows.isEmpty()) return AssistantResponse(
            AssistantIntent.LOW_ROTATION,
            "No veo productos con exceso de existencias respecto a sus ventas de ${per.label}."
        )
        return AssistantResponse(
            AssistantIntent.LOW_ROTATION,
            (if (rows.size == 1) "Este producto se vende despacio" else "Estos ${rows.size} productos se venden despacio") +
                ": su stock alcanzaría para más de $minCover días al ritmo de ${per.label}.",
            ResultTable(
                listOf("Producto", "Stock", "Vendidos", "Alcanza para"),
                rows.map { listOf(it.nombre, it.stock.toString(), it.vendidas.toString(),
                    it.cobertura?.let { c -> "≈ ${Math.round(c)} d" } ?: "—") }
            ),
            listOf("Los productos que no se vendieron nada aparecen en \"productos sin movimiento\".")
        )
    }

    fun shrinkage(p: QueryParameters): AssistantResponse {
        val per = Params.period(p.period, TimePeriod.thisMonth(clock()))
        val r = repo.mermas(per.from.toString(), per.toExclusive.toString(), Params.limit(p, 10), excl)
        if (r.registros == 0) return AssistantResponse(
            AssistantIntent.SHRINKAGE, "No hay mermas registradas ${per.inLabel}."
        )
        val motivo = r.porMotivo.firstOrNull()?.let { " El motivo con más pérdida: ${it.motivo} (${Fmt.money(it.perdida)})." } ?: ""
        return AssistantResponse(
            AssistantIntent.SHRINKAGE,
            "${per.inLabelCap} se registraron ${r.registros} ${Fmt.plural(r.registros, "merma", "mermas")} " +
                "(${r.unidades} unidades) con una pérdida de ${Fmt.money(r.perdida)} al costo.$motivo",
            ResultTable(
                listOf("Producto", "Unid.", "Pérdida", "Motivo"),
                r.items.map { listOf(it.nombre, it.unidades.toString(), Fmt.money(it.perdida), it.motivos) }
            ),
            listOf("Reportes registra como merma los productos caducados cuando se abre esa pantalla.")
        )
    }
}
