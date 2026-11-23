package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import com.example.ing_software_abarrotezperez.ai.repository.CaducidadRow
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class ExpirationTools(
    private val repo: AssistantRepository,
    private val config: AssistantConfig,
    private val clock: Clock = { LocalDate.now() }
) {
    private val excl get() = config.excludeTestProducts

    private fun daysBetween(today: LocalDate, iso: String): Long? =
        try { ChronoUnit.DAYS.between(today, LocalDate.parse(iso)) } catch (e: Exception) { null }

    private fun notes(rows: List<CaducidadRow>): List<String> {
        val n = mutableListOf<String>()
        if (rows.any { it.origen == "producto" })
            n += "Algunos productos no tienen lotes registrados: usé su fecha de caducidad y su stock total."
        if (rows.any { it.origen == "lote" })
            n += "Las cantidades por lote pueden no reflejar mermas ya registradas."
        return n
    }

    fun expiringSoon(p: QueryParameters): AssistantResponse {
        val dias = Params.days(p, 30)
        val hoy = clock()
        val r = repo.caducanEntre(hoy.toString(), hoy.plusDays(dias.toLong()).toString(), Params.limit(p, 20), excl)
        if (r.registros == 0) return AssistantResponse(
            AssistantIntent.EXPIRING_SOON, "No hay productos que caduquen en los próximos $dias días."
        )
        val partial = if (r.registros > r.items.size) " Te muestro los ${r.items.size} más próximos." else ""
        return AssistantResponse(
            AssistantIntent.EXPIRING_SOON,
            "En los próximos $dias días caducan ${r.registros} ${Fmt.plural(r.registros, "lote o producto", "lotes o productos")} " +
                "(${r.unidades} unidades, costo ${Fmt.money(r.costo)}).$partial",
            ResultTable(
                listOf("Producto", "Cant.", "Caduca", "Faltan"),
                r.items.map {
                    listOf(it.nombre, it.cantidad.toString(), it.fechaOriginal,
                        daysBetween(hoy, it.isoFecha)?.let { d -> Fmt.daysText(d) } ?: "—")
                }
            ),
            notes(r.items)
        )
    }

    fun expired(p: QueryParameters): AssistantResponse {
        val hoy = clock()
        val r = repo.caducados(hoy.toString(), Params.limit(p, 20), excl)
        if (r.registros == 0) return AssistantResponse(
            AssistantIntent.EXPIRED, "No tienes productos caducados con existencias."
        )
        val partial = if (r.registros > r.items.size) " Te muestro los ${r.items.size} más antiguos." else ""
        return AssistantResponse(
            AssistantIntent.EXPIRED,
            "Tienes ${r.registros} ${Fmt.plural(r.registros, "lote o producto caducado", "lotes o productos caducados")} " +
                "con existencias (${r.unidades} unidades, costo ${Fmt.money(r.costo)}).$partial",
            ResultTable(
                listOf("Producto", "Cant.", "Caducó", "Hace"),
                r.items.map {
                    listOf(it.nombre, it.cantidad.toString(), it.fechaOriginal,
                        daysBetween(hoy, it.isoFecha)?.let { d -> Fmt.daysText(-d) } ?: "—")
                }
            ),
            notes(r.items)
        )
    }
}
