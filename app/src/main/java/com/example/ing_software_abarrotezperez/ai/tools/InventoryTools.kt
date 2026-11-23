package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.interpreter.TextNormalizer
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository

class InventoryTools(
    private val repo: AssistantRepository,
    private val config: AssistantConfig,
    private val clock: Clock = { java.time.LocalDate.now() }
) {
    private val excl get() = config.excludeTestProducts

    fun lowStock(p: QueryParameters): AssistantResponse {
        val t = Params.threshold(p, config.lowStockThreshold)
        val r = repo.productosPocoStock(t, Params.limit(p, 15), excl)
        if (r.total == 0) return AssistantResponse(
            AssistantIntent.LOW_STOCK, "No tienes productos con pocas existencias (entre 1 y $t unidades)."
        )
        val partial = if (r.total > r.items.size) " Te muestro los ${r.items.size} con menos stock." else ""
        return AssistantResponse(
            AssistantIntent.LOW_STOCK,
            "Tienes ${r.total} ${Fmt.plural(r.total, "producto", "productos")} con pocas existencias " +
                "(de 1 a $t unidades).$partial",
            ResultTable(listOf("Producto", "Stock"), r.items.map { listOf(it.nombre, it.stock.toString()) })
        )
    }

    fun outOfStock(p: QueryParameters): AssistantResponse {
        val r = repo.productosAgotados(Params.limit(p, 20), excl)
        if (r.total == 0) return AssistantResponse(AssistantIntent.OUT_OF_STOCK, "No tienes productos agotados.")
        val partial = if (r.total > r.items.size) " Te muestro los primeros ${r.items.size}." else ""
        return AssistantResponse(
            AssistantIntent.OUT_OF_STOCK,
            "Hay ${r.total} ${Fmt.plural(r.total, "producto agotado", "productos agotados")}.$partial",
            ResultTable(listOf("Producto", "Precio venta"), r.items.map { listOf(it.nombre, Fmt.money(it.precioVenta)) })
        )
    }

    fun inventoryValue(@Suppress("UNUSED_PARAMETER") p: QueryParameters): AssistantResponse {
        val v = repo.valorInventario(excl)
        val notes = mutableListOf<String>()
        if (v.sinCosto > 0) notes += "${v.sinCosto} ${Fmt.plural(v.sinCosto, "producto no tiene", "productos no tienen")} " +
            "precio de compra registrado, así que el valor real podría ser mayor."
        if (v.productosPrueba > 0) notes += if (excl)
            "Excluí ${v.productosPrueba} productos de prueba (código PRUEBA-…)."
        else "Incluye ${v.productosPrueba} productos de prueba (código PRUEBA-…)."
        return AssistantResponse(
            AssistantIntent.INVENTORY_VALUE,
            "Tu inventario tiene un costo de ${Fmt.money(v.costo)} en ${v.productos} " +
                "${Fmt.plural(v.productos, "producto", "productos")} (${v.unidades} unidades). " +
                "A precio de venta valdría ${Fmt.money(v.ventaPotencial)}.",
            ResultTable(
                listOf("Concepto", "Valor"),
                listOf(
                    listOf("Costo del inventario", Fmt.money(v.costo)),
                    listOf("Valor a precio de venta", Fmt.money(v.ventaPotencial)),
                    listOf("Ganancia potencial", Fmt.money(v.ventaPotencial - v.costo)),
                    listOf("Productos con existencias", v.productos.toString()),
                    listOf("Unidades en total", v.unidades.toString())
                )
            ),
            notes
        )
    }

    fun searchProduct(p: QueryParameters): AssistantResponse {
        val q = p.productQuery?.trim().orEmpty()
        if (q.length < 2) return AssistantResponse(
            AssistantIntent.SEARCH_PRODUCT, "¿Qué producto quieres buscar?", needsClarification = true
        )
        val tokens = TextNormalizer.words(q).map { TextNormalizer.stem(it) }
        val all = repo.listarProductos(excl).map { it to TextNormalizer.normalize(it.nombre) }
        var hits = all.filter { (prod, name) ->
            tokens.all { name.contains(it) } || prod.codigo == q
        }
        if (hits.isEmpty()) hits = all.filter { (_, name) -> tokens.any { it.length >= 3 && name.contains(it) } }
        val ordered = hits.sortedWith(
            compareBy({ !it.second.startsWith(tokens.first()) }, { it.second.length })
        ).map { it.first }
        if (ordered.isEmpty()) return AssistantResponse(
            AssistantIntent.SEARCH_PRODUCT, "No encontré productos que coincidan con \"$q\"."
        )
        val shown = ordered.take(Params.limit(p, 10))
        val partial = if (ordered.size > shown.size) " Te muestro los primeros ${shown.size}." else ""
        return AssistantResponse(
            AssistantIntent.SEARCH_PRODUCT,
            "Encontré ${ordered.size} ${Fmt.plural(ordered.size, "producto", "productos")} para \"$q\".$partial",
            ResultTable(
                listOf("Producto", "Stock", "Precio"),
                shown.map { listOf(it.nombre, it.stock.toString(), Fmt.money(it.precioVenta)) }
            )
        )
    }

    fun restockNeeds(p: QueryParameters): AssistantResponse {
        val dias = Params.days(p, 30)
        val umbral = Params.threshold(p, config.lowStockThreshold)
        val periodo = TimePeriod.lastDays(dias, clock())
        val r = repo.reabastecer(
            periodo.from.toString(), periodo.toExclusive.toString(), dias, umbral, TARGET_COVER_DAYS,
            Params.limit(p, 15), excl
        )
        if (r.total == 0) return AssistantResponse(
            AssistantIntent.RESTOCK_NEEDS,
            "Por ahora no veo productos que necesiten reabastecerse, con base en las ventas de los últimos $dias días."
        )
        val partial = if (r.total > r.items.size) " Te muestro los ${r.items.size} más urgentes." else ""
        return AssistantResponse(
            AssistantIntent.RESTOCK_NEEDS,
            "Conviene reabastecer ${r.total} ${Fmt.plural(r.total, "producto", "productos")}: tienen pocas " +
                "existencias o les alcanza para menos de $TARGET_COVER_DAYS días al ritmo de venta de los últimos $dias días.$partial",
            ResultTable(
                listOf("Producto", "Stock", "Vendidos", "Alcanza para"),
                r.items.map {
                    listOf(
                        it.nombre, it.stock.toString(), it.vendidas.toString(),
                        it.cobertura?.let { c -> "≈ ${Math.round(c)} d" } ?: "—"
                    )
                }
            ),
            listOf("Es una estimación con ventas recientes; aún no hay registro de pedidos o compras en la app.")
        )
    }

    private companion object {
        const val TARGET_COVER_DAYS = 14
    }
}
