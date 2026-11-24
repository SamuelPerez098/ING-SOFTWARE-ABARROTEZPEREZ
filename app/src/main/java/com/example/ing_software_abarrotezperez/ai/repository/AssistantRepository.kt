package com.example.ing_software_abarrotezperez.ai.repository

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

// ─── Filas de resultado ──────────────────────────────────────────────────────

data class ProductoStockRow(val nombre: String, val codigo: String, val stock: Int, val precioVenta: Double)
data class ListaConTotal<T>(val items: List<T>, val total: Int)
data class InventoryValue(
    val productos: Int, val unidades: Int, val costo: Double, val ventaPotencial: Double,
    val sinCosto: Int, val productosPrueba: Int
)
data class ProductoBusqueda(
    val nombre: String, val codigo: String, val stock: Int, val precioVenta: Double, val precioCompra: Double
)
data class CaducidadRow(
    val nombre: String, val codigo: String, val cantidad: Int,
    val isoFecha: String, val fechaOriginal: String, val origen: String
)
data class CaducidadResultado(val items: List<CaducidadRow>, val registros: Int, val unidades: Int, val costo: Double)
data class VentasResumen(
    val total: Double, val tickets: Int, val unidades: Int, val ganancia: Double, val fiado: Double
)
data class VentaProductoRow(val nombre: String, val unidades: Int, val monto: Double)
data class SinMovimientoRow(val nombre: String, val codigo: String, val stock: Int, val capital: Double)
data class SinMovimientoResultado(val items: List<SinMovimientoRow>, val total: Int, val capital: Double)
data class RotacionRow(val nombre: String, val codigo: String, val stock: Int, val vendidas: Int, val cobertura: Double?)
data class MermaRow(val nombre: String, val unidades: Int, val perdida: Double, val motivos: String)
data class MotivoRow(val motivo: String, val unidades: Int, val perdida: Double)
data class MermasResultado(
    val items: List<MermaRow>, val registros: Int, val unidades: Int, val perdida: Double,
    val porMotivo: List<MotivoRow>
)
data class DeudaRow(val idCliente: Int, val nombre: String, val monto: Double)

/**
 * Acceso de SOLO LECTURA a los datos del punto de venta.
 * Fechas de entrada: texto "yyyy-MM-dd".
 */
interface AssistantRepository {
    fun productosPocoStock(umbral: Int, limite: Int, excluirPrueba: Boolean): ListaConTotal<ProductoStockRow>
    fun productosAgotados(limite: Int, excluirPrueba: Boolean): ListaConTotal<ProductoStockRow>
    fun valorInventario(excluirPrueba: Boolean): InventoryValue
    fun listarProductos(excluirPrueba: Boolean): List<ProductoBusqueda>
    fun reabastecer(
        desde: String, hasta: String, dias: Int, umbral: Int, coberturaObjetivo: Int,
        limite: Int, excluirPrueba: Boolean
    ): ListaConTotal<RotacionRow>

    fun caducanEntre(desdeIso: String, hastaIso: String, limite: Int, excluirPrueba: Boolean): CaducidadResultado
    fun caducados(hoyIso: String, limite: Int, excluirPrueba: Boolean): CaducidadResultado

    fun resumenVentas(desde: String, hastaExclusivo: String): VentasResumen
    fun ventasPorProducto(
        desde: String, hastaExclusivo: String, limite: Int, masVendidos: Boolean, excluirPrueba: Boolean
    ): List<VentaProductoRow>

    fun sinMovimiento(desde: String, hastaExclusivo: String, limite: Int, excluirPrueba: Boolean): SinMovimientoResultado
    fun bajaRotacion(
        desde: String, hastaExclusivo: String, dias: Int, coberturaMin: Int, limite: Int, excluirPrueba: Boolean
    ): List<RotacionRow>

    fun mermas(desde: String, hastaExclusivo: String, limite: Int, excluirPrueba: Boolean): MermasResultado

    // ── Resumen de tienda (fiado) ──
    fun fiadoPendiente(): Pair<Double, Int>
    fun fiadoAntiguo(limiteIso: String): List<DeudaRow>
    fun mayorDeudor(): DeudaRow?
}

/**
 * Implementación sobre SQLite. Recibe un proveedor de la base (p. ej. { helper.readableDatabase })
 * y NUNCA escribe: [query] rechaza cualquier sentencia que no sea SELECT/WITH.
 */
class SqliteAssistantRepository(private val dbProvider: () -> SQLiteDatabase) : AssistantRepository {

    private fun <T> query(sql: String, excl: Boolean, args: List<Any>, map: (Cursor) -> T): List<T> {
        val finalSql = AssistantSql.expand(sql, excl)
        val head = finalSql.trimStart().uppercase()
        require(head.startsWith("SELECT") || head.startsWith("WITH")) { "Solo se permiten consultas de lectura" }
        val out = mutableListOf<T>()
        dbProvider().rawQuery(finalSql, Array(args.size) { args[it].toString() }).use { c ->
            while (c.moveToNext()) out.add(map(c))
        }
        return out
    }

    private fun <T> one(sql: String, excl: Boolean, args: List<Any>, map: (Cursor) -> T): T =
        query(sql, excl, args, map).first()

    private fun Cursor.str(i: Int) = getString(i) ?: ""

    // ── Inventario ──
    override fun productosPocoStock(umbral: Int, limite: Int, excluirPrueba: Boolean) = ListaConTotal(
        query(AssistantSql.LOW_STOCK, excluirPrueba, listOf(umbral, limite)) {
            ProductoStockRow(it.str(0), it.str(1), it.getInt(2), it.getDouble(3))
        },
        one(AssistantSql.LOW_STOCK_TOTAL, excluirPrueba, listOf(umbral)) { it.getInt(0) }
    )

    override fun productosAgotados(limite: Int, excluirPrueba: Boolean) = ListaConTotal(
        query(AssistantSql.OUT_OF_STOCK, excluirPrueba, listOf(limite)) {
            ProductoStockRow(it.str(0), it.str(1), it.getInt(2), it.getDouble(3))
        },
        one(AssistantSql.OUT_OF_STOCK_TOTAL, excluirPrueba, emptyList()) { it.getInt(0) }
    )

    override fun valorInventario(excluirPrueba: Boolean): InventoryValue {
        val prueba = one(AssistantSql.TEST_PRODUCT_COUNT, false, emptyList()) { it.getInt(0) }
        return one(AssistantSql.INVENTORY_VALUE, excluirPrueba, emptyList()) {
            InventoryValue(it.getInt(0), it.getInt(1), it.getDouble(2), it.getDouble(3), it.getInt(4), prueba)
        }
    }

    override fun listarProductos(excluirPrueba: Boolean) =
        query(AssistantSql.ALL_PRODUCTS, excluirPrueba, emptyList()) {
            ProductoBusqueda(it.str(0), it.str(1), it.getInt(2), it.getDouble(3), it.getDouble(4))
        }

    override fun reabastecer(
        desde: String, hasta: String, dias: Int, umbral: Int, coberturaObjetivo: Int,
        limite: Int, excluirPrueba: Boolean
    ) = ListaConTotal(
        query(AssistantSql.RESTOCK, excluirPrueba, listOf(desde, hasta, dias, umbral, coberturaObjetivo, limite)) {
            RotacionRow(it.str(0), it.str(1), it.getInt(2), it.getInt(3),
                if (it.isNull(4)) null else it.getDouble(4))
        },
        one(AssistantSql.RESTOCK_TOTAL, excluirPrueba, listOf(desde, hasta, dias, umbral, coberturaObjetivo)) {
            it.getInt(0)
        }
    )

    // ── Caducidades ──
    private fun mapCad(c: Cursor) =
        CaducidadRow(c.str(0), c.str(1), c.getInt(2), c.str(3), c.str(4), c.str(5))

    override fun caducanEntre(desdeIso: String, hastaIso: String, limite: Int, excluirPrueba: Boolean):
            CaducidadResultado {
        val items = query(AssistantSql.EXPIRING, excluirPrueba, listOf(desdeIso, hastaIso, limite), ::mapCad)
        return one(AssistantSql.EXPIRING_TOTAL, excluirPrueba, listOf(desdeIso, hastaIso)) {
            CaducidadResultado(items, it.getInt(0), it.getInt(1), it.getDouble(2))
        }
    }

    override fun caducados(hoyIso: String, limite: Int, excluirPrueba: Boolean): CaducidadResultado {
        val items = query(AssistantSql.EXPIRED, excluirPrueba, listOf(hoyIso, limite), ::mapCad)
        return one(AssistantSql.EXPIRED_TOTAL, excluirPrueba, listOf(hoyIso)) {
            CaducidadResultado(items, it.getInt(0), it.getInt(1), it.getDouble(2))
        }
    }

    // ── Ventas ──
    override fun resumenVentas(desde: String, hastaExclusivo: String): VentasResumen {
        val a = listOf(desde, hastaExclusivo)
        val (total, tickets) = one(AssistantSql.SALES_TOTALS, false, a) { it.getDouble(0) to it.getInt(1) }
        val (unidades, ganancia) = one(AssistantSql.SALES_UNITS_PROFIT, false, a) { it.getInt(0) to it.getDouble(1) }
        val fiado = one(AssistantSql.SALES_FIADO, false, a) { it.getDouble(0) }
        return VentasResumen(total, tickets, unidades, ganancia, fiado)
    }

    override fun ventasPorProducto(
        desde: String, hastaExclusivo: String, limite: Int, masVendidos: Boolean, excluirPrueba: Boolean
    ) = query(
        if (masVendidos) AssistantSql.TOP_SELLING else AssistantSql.LEAST_SELLING,
        excluirPrueba, listOf(desde, hastaExclusivo, limite)
    ) { VentaProductoRow(it.str(0), it.getInt(1), it.getDouble(2)) }

    // ── Análisis comercial ──
    override fun sinMovimiento(desde: String, hastaExclusivo: String, limite: Int, excluirPrueba: Boolean):
            SinMovimientoResultado {
        val items = query(AssistantSql.NO_MOVEMENT, excluirPrueba, listOf(desde, hastaExclusivo, limite)) {
            SinMovimientoRow(it.str(0), it.str(1), it.getInt(2), it.getDouble(3))
        }
        return one(AssistantSql.NO_MOVEMENT_TOTAL, excluirPrueba, listOf(desde, hastaExclusivo)) {
            SinMovimientoResultado(items, it.getInt(0), it.getDouble(1))
        }
    }

    override fun bajaRotacion(
        desde: String, hastaExclusivo: String, dias: Int, coberturaMin: Int, limite: Int, excluirPrueba: Boolean
    ) = query(AssistantSql.LOW_ROTATION, excluirPrueba, listOf(desde, hastaExclusivo, dias, coberturaMin, limite)) {
        RotacionRow(it.str(0), it.str(1), it.getInt(2), it.getInt(3), it.getDouble(4))
    }

    override fun mermas(desde: String, hastaExclusivo: String, limite: Int, excluirPrueba: Boolean):
            MermasResultado {
        val a = listOf(desde, hastaExclusivo)
        val items = query(AssistantSql.SHRINK_BY_PRODUCT, excluirPrueba, a + limite) {
            MermaRow(it.str(0), it.getInt(1), it.getDouble(2), it.str(3))
        }
        val motivos = query(AssistantSql.SHRINK_BY_REASON, excluirPrueba, a) {
            MotivoRow(it.str(0), it.getInt(1), it.getDouble(2))
        }
        return one(AssistantSql.SHRINK_TOTAL, excluirPrueba, a) {
            MermasResultado(items, it.getInt(0), it.getInt(1), it.getDouble(2), motivos)
        }
    }

    // ── Resumen de tienda (fiado) ──
    override fun fiadoPendiente(): Pair<Double, Int> =
        one(AssistantSql.FIADO_TOTAL, false, emptyList()) { it.getDouble(0) to it.getInt(1) }

    override fun fiadoAntiguo(limiteIso: String): List<DeudaRow> =
        query(AssistantSql.FIADO_ANTIGUO, false, listOf(limiteIso)) {
            DeudaRow(it.getInt(0), it.str(1), it.getDouble(2))
        }

    override fun mayorDeudor(): DeudaRow? =
        query(AssistantSql.MAYOR_DEUDOR, false, emptyList()) {
            DeudaRow(it.getInt(0), it.str(1), it.getDouble(2))
        }.firstOrNull()
}
