package com.example.ing_software_abarrotezperez.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "tienda.db"
        const val DATABASE_VERSION = 4

        const val TABLE_PRODUCTO       = "producto"
        const val TABLE_LOTE           = "lote"
        const val TABLE_VENTA          = "venta"
        const val TABLE_DETALLE_VENTA  = "detalle_venta"
        const val TABLE_CLIENTE        = "cliente"
        const val TABLE_FIADO          = "fiado"
        const val TABLE_PAGO_FIADO     = "pago_fiado"
        const val TABLE_MERMA          = "merma"
        const val TABLE_COMPRA         = "compra"
        const val TABLE_DETALLE_COMPRA = "detalle_compra"
        const val TABLE_PROV_FISICO    = "proveedor_fisico"
        const val TABLE_PROV_DIGITAL   = "proveedor_digital"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA foreign_keys = ON;")

        db.execSQL("""
            CREATE TABLE cliente (
                id_cliente  INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre      TEXT NOT NULL,
                telefono    TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE producto (
                id_producto     INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo_barras   TEXT UNIQUE NOT NULL,
                nombre          TEXT NOT NULL,
                descripcion     TEXT,
                precio_venta    REAL,
                precio_compra   REAL NOT NULL DEFAULT 0.0,
                stock           INTEGER DEFAULT 0,
                fecha_caducidad TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE lote (
                id_lote         INTEGER PRIMARY KEY AUTOINCREMENT,
                id_producto     INTEGER,
                fecha_caducidad TEXT,
                cantidad        INTEGER DEFAULT 0,
                FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE proveedor_fisico (
                id_proveedor_fisico INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre              TEXT,
                direccion           TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE proveedor_digital (
                id_proveedor_digital INTEGER PRIMARY KEY AUTOINCREMENT,
                nombre               TEXT
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE compra (
                id_compra           INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha               TEXT,
                total               REAL,
                id_proveedor_fisico INTEGER,
                id_proveedor_digital INTEGER,
                FOREIGN KEY (id_proveedor_fisico)  REFERENCES proveedor_fisico(id_proveedor_fisico),
                FOREIGN KEY (id_proveedor_digital) REFERENCES proveedor_digital(id_proveedor_digital)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE detalle_compra (
                id_detalle_compra INTEGER PRIMARY KEY AUTOINCREMENT,
                id_compra         INTEGER,
                id_producto       INTEGER,
                cantidad          INTEGER,
                precio_compra     REAL,
                FOREIGN KEY (id_compra)   REFERENCES compra(id_compra),
                FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE venta (
                id_venta INTEGER PRIMARY KEY AUTOINCREMENT,
                fecha    TEXT,
                total    REAL
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE detalle_venta (
                id_detalle_venta INTEGER PRIMARY KEY AUTOINCREMENT,
                id_venta         INTEGER,
                id_producto      INTEGER,
                cantidad         INTEGER,
                precio_unitario  REAL,
                FOREIGN KEY (id_venta)    REFERENCES venta(id_venta),
                FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE fiado (
                id_fiado         INTEGER PRIMARY KEY AUTOINCREMENT,
                id_cliente       INTEGER,
                id_venta         INTEGER,
                saldo_pendiente  REAL,
                FOREIGN KEY (id_cliente) REFERENCES cliente(id_cliente),
                FOREIGN KEY (id_venta)   REFERENCES venta(id_venta)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE pago_fiado (
                id_pago    INTEGER PRIMARY KEY AUTOINCREMENT,
                id_fiado   INTEGER,
                fecha_pago TEXT,
                monto      REAL,
                FOREIGN KEY (id_fiado) REFERENCES fiado(id_fiado)
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE merma (
                id_merma    INTEGER PRIMARY KEY AUTOINCREMENT,
                id_producto INTEGER,
                cantidad    INTEGER,
                motivo      TEXT,
                fecha       TEXT,
                FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS pago_fiado")
        db.execSQL("DROP TABLE IF EXISTS fiado")
        db.execSQL("DROP TABLE IF EXISTS detalle_venta")
        db.execSQL("DROP TABLE IF EXISTS venta")
        db.execSQL("DROP TABLE IF EXISTS detalle_compra")
        db.execSQL("DROP TABLE IF EXISTS compra")
        db.execSQL("DROP TABLE IF EXISTS proveedor_digital")
        db.execSQL("DROP TABLE IF EXISTS proveedor_fisico")
        db.execSQL("DROP TABLE IF EXISTS lote")
        db.execSQL("DROP TABLE IF EXISTS merma")
        db.execSQL("DROP TABLE IF EXISTS producto")
        db.execSQL("DROP TABLE IF EXISTS cliente")
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) db.execSQL("PRAGMA foreign_keys = ON;")
    }

    // ─────────────────────────────────────────────
    //  PRODUCTO
    // ─────────────────────────────────────────────

    data class Producto(
        val idProducto: Int = 0,
        val codigoBarras: String = "",
        val nombre: String = "",
        val descripcion: String = "",
        var precioVenta: Double = 0.0,
        val precioCompra: Double = 0.0,
        var stock: Int = 0,
        val fechaCaducidad: String? = null
    )

    fun getProductoPorCodigo(codigo: String): Producto? {
        val db = readableDatabase
        val cursor = db.query(
            TABLE_PRODUCTO, null,
            "codigo_barras = ?", arrayOf(codigo),
            null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Producto(
                    idProducto     = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                    codigoBarras   = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                    nombre         = it.getString(it.getColumnIndexOrThrow("nombre")),
                    descripcion    = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                    precioVenta    = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                    precioCompra   = it.getDouble(it.getColumnIndexOrThrow("precio_compra")),
                    stock          = it.getInt(it.getColumnIndexOrThrow("stock")),
                    fechaCaducidad = it.getString(it.getColumnIndexOrThrow("fecha_caducidad"))
                )
            } else null
        }
    }

    fun upsertProducto(producto: Producto): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("codigo_barras",   producto.codigoBarras)
            put("nombre",          producto.nombre)
            put("descripcion",     producto.descripcion)
            put("precio_venta",    producto.precioVenta)
            put("precio_compra",   producto.precioCompra)
            put("stock",           producto.stock)
            put("fecha_caducidad", producto.fechaCaducidad)
        }

        val existing = getProductoPorCodigo(producto.codigoBarras)
        val finalId: Long

        if (existing == null) {
            finalId = db.insert(TABLE_PRODUCTO, null, cv)
        } else {
            db.update(TABLE_PRODUCTO, cv, "id_producto = ?", arrayOf(existing.idProducto.toString()))
            finalId = existing.idProducto.toLong()
        }

        if (producto.fechaCaducidad != null) {
            val unidadesNuevas = producto.stock - (existing?.stock ?: 0)
            if (unidadesNuevas > 0) {
                val cursorLote = db.rawQuery(
                    "SELECT id_lote FROM lote WHERE id_producto = ? AND fecha_caducidad = ?",
                    arrayOf(finalId.toString(), producto.fechaCaducidad)
                )
                if (cursorLote.moveToFirst()) {
                    val idLote = cursorLote.getInt(0)
                    db.execSQL(
                        "UPDATE lote SET cantidad = cantidad + ? WHERE id_lote = ?",
                        arrayOf(unidadesNuevas, idLote)
                    )
                } else {
                    val cvLote = ContentValues().apply {
                        put("id_producto", finalId)
                        put("fecha_caducidad", producto.fechaCaducidad)
                        put("cantidad", unidadesNuevas)
                    }
                    db.insert(TABLE_LOTE, null, cvLote)
                }
                cursorLote.close()
            }
        }

        return finalId
    }

    fun getAllProductos(): List<Producto> {
        val db = readableDatabase
        val lista = mutableListOf<Producto>()
        val cursor = db.query(
            TABLE_PRODUCTO, null, null, null, null, null,
            "LENGTH(nombre) ASC, nombre ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Producto(
                        idProducto     = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                        codigoBarras   = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                        nombre         = it.getString(it.getColumnIndexOrThrow("nombre")),
                        descripcion    = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                        precioVenta    = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                        precioCompra   = it.getDouble(it.getColumnIndexOrThrow("precio_compra")),
                        stock          = it.getInt(it.getColumnIndexOrThrow("stock")),
                        fechaCaducidad = it.getString(it.getColumnIndexOrThrow("fecha_caducidad"))
                    )
                )
            }
        }
        return lista
    }

    fun eliminarProducto(codigoBarras: String): Boolean {
        val db = writableDatabase
        val filasBorradas = db.delete(TABLE_PRODUCTO, "codigo_barras = ?", arrayOf(codigoBarras))
        return filasBorradas > 0
    }

    // ─────────────────────────────────────────────
    //  PROMOCIONES / ACTUALIZAR PRECIO
    // ─────────────────────────────────────────────

    /** Actualiza el precio de venta usando el ID interno del producto. */
    fun actualizarPrecioProducto(idProducto: Int, nuevoPrecio: Double): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("precio_venta", nuevoPrecio)
        }
        return db.update(TABLE_PRODUCTO, values, "id_producto = ?", arrayOf(idProducto.toString()))
    }

    /** Atajo: actualiza el precio por código de barras. */
    fun actualizarPrecioVenta(codigoBarras: String, nuevoPrecio: Double): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply { put("precio_venta", nuevoPrecio) }
        return db.update(TABLE_PRODUCTO, values, "codigo_barras = ?", arrayOf(codigoBarras)) > 0
    }

    /** Alias de compatibilidad por si lo llamas desde otro lado. */
    fun aplicarPromocion(codigo: String, nuevoPrecio: Double): Int {
        val values = ContentValues().apply { put("precio_venta", nuevoPrecio) }
        return writableDatabase.update(TABLE_PRODUCTO, values, "codigo_barras = ?", arrayOf(codigo))
    }

    // ─────────────────────────────────────────────
    //  VENTAS Y FIFO
    // ─────────────────────────────────────────────

    data class ItemVenta(val producto: Producto, var cantidad: Int = 1) {
        val subtotal get() = producto.precioVenta * cantidad
    }

    fun registrarVenta(items: List<ItemVenta>): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val total = items.sumOf { it.subtotal }
            val fechaHora = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            val cvVenta = ContentValues().apply {
                put("fecha", fechaHora)
                put("total", total)
            }
            val idVenta = db.insert(TABLE_VENTA, null, cvVenta)
            if (idVenta == -1L) error("Error insertando venta")

            for (item in items) {
                val cvDetalle = ContentValues().apply {
                    put("id_venta",        idVenta)
                    put("id_producto",     item.producto.idProducto)
                    put("cantidad",        item.cantidad)
                    put("precio_unitario", item.producto.precioVenta)
                }
                db.insert(TABLE_DETALLE_VENTA, null, cvDetalle)

                db.execSQL("UPDATE producto SET stock = stock - ? WHERE id_producto = ?",
                    arrayOf(item.cantidad, item.producto.idProducto))

                var restante = item.cantidad
                val cursorLote = db.rawQuery(
                    "SELECT id_lote, cantidad FROM lote WHERE id_producto = ? AND cantidad > 0 ORDER BY fecha_caducidad ASC",
                    arrayOf(item.producto.idProducto.toString())
                )
                while (cursorLote.moveToNext() && restante > 0) {
                    val idLote   = cursorLote.getInt(0)
                    val cantLote = cursorLote.getInt(1)
                    if (cantLote <= restante) {
                        db.execSQL("UPDATE lote SET cantidad = 0 WHERE id_lote = ?", arrayOf(idLote))
                        restante -= cantLote
                    } else {
                        db.execSQL("UPDATE lote SET cantidad = cantidad - ? WHERE id_lote = ?", arrayOf(restante, idLote))
                        restante = 0
                    }
                }
                cursorLote.close()
            }
            db.setTransactionSuccessful()
            idVenta
        } catch (e: Exception) { -1L } finally { db.endTransaction() }
    }

    // ─────────────────────────────────────────────
    //  FIADO Y MERMA
    // ─────────────────────────────────────────────

    fun getSaldoPendienteCliente(idCliente: Int): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM(saldo_pendiente) FROM fiado WHERE id_cliente = ?",
            arrayOf(idCliente.toString())
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun registrarPagoFiado(idFiado: Int, monto: Double): Boolean {
        val db = writableDatabase
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        db.beginTransaction()
        return try {
            val cvPago = ContentValues().apply {
                put("id_fiado", idFiado); put("fecha_pago", fecha); put("monto", monto)
            }
            db.insert(TABLE_PAGO_FIADO, null, cvPago)
            db.execSQL("UPDATE fiado SET saldo_pendiente = saldo_pendiente - ? WHERE id_fiado = ?", arrayOf(monto, idFiado))
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) { false } finally { db.endTransaction() }
    }

    fun registrarMerma(idProducto: Int, cantidad: Int, motivo: String): Boolean {
        val db = writableDatabase
        val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        db.beginTransaction()
        return try {
            val cv = ContentValues().apply {
                put("id_producto", idProducto); put("cantidad", cantidad); put("motivo", motivo); put("fecha", fecha)
            }
            db.insert(TABLE_MERMA, null, cv)
            db.execSQL("UPDATE producto SET stock = stock - ? WHERE id_producto = ?", arrayOf(cantidad, idProducto))
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) { false } finally { db.endTransaction() }
    }

    // ─────────────────────────────────────────────
    //  REPORTES
    // ─────────────────────────────────────────────

    fun getGananciaDelDia(): Double {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val query = """
            SELECT SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad)
            FROM $TABLE_VENTA v
            JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
            WHERE v.fecha LIKE ?
        """.trimIndent()
        return readableDatabase.rawQuery(query, arrayOf("$today%"))
            .use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getVentaDelDia(): Double {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val query = "SELECT SUM(total) FROM $TABLE_VENTA WHERE fecha LIKE ?"
        return readableDatabase.rawQuery(query, arrayOf("$today%"))
            .use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getGananciaDelDia(fechaFiltro: String): Double {
        val query = """
            SELECT SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad)
            FROM $TABLE_VENTA v
            JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
            WHERE v.fecha LIKE ?
        """.trimIndent()
        return readableDatabase.rawQuery(query, arrayOf("$fechaFiltro%"))
            .use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getVentaDelDia(fechaFiltro: String): Double {
        val query = "SELECT SUM(total) FROM $TABLE_VENTA WHERE fecha LIKE ?"
        return readableDatabase.rawQuery(query, arrayOf("$fechaFiltro%"))
            .use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getGananciasPorHoraDelDia(fechaFiltro: String): Map<Int, Float> {
        val mapa = mutableMapOf<Int, Float>()
        val query = """
            SELECT CAST(strftime('%H', v.fecha) AS INTEGER) as hora,
                   SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad) as total_ganancia
            FROM $TABLE_VENTA v
            JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
            WHERE v.fecha LIKE ?
            GROUP BY hora
        """.trimIndent()
        readableDatabase.rawQuery(query, arrayOf("$fechaFiltro%")).use { c ->
            while (c.moveToNext()) {
                mapa[c.getInt(c.getColumnIndexOrThrow("hora"))] =
                    c.getFloat(c.getColumnIndexOrThrow("total_ganancia"))
            }
        }
        return mapa
    }

    fun getGananciasPorHoraDelDia(): Map<Int, Float> {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return getGananciasPorHoraDelDia(hoy)
    }

    fun getProductosPorCaducar(): List<Producto> {
        val lista = mutableListOf<Producto>()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val query = """
            SELECT * FROM $TABLE_PRODUCTO
            WHERE fecha_caducidad IS NOT NULL
              AND fecha_caducidad != ''
              AND fecha_caducidad >= ?
            ORDER BY fecha_caducidad ASC
        """.trimIndent()
        readableDatabase.rawQuery(query, arrayOf(today)).use {
            while (it.moveToNext()) {
                lista.add(
                    Producto(
                        idProducto     = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                        codigoBarras   = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                        nombre         = it.getString(it.getColumnIndexOrThrow("nombre")),
                        descripcion    = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                        precioVenta    = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                        precioCompra   = it.getDouble(it.getColumnIndexOrThrow("precio_compra")),
                        stock          = it.getInt(it.getColumnIndexOrThrow("stock")),
                        fechaCaducidad = it.getString(it.getColumnIndexOrThrow("fecha_caducidad"))
                    )
                )
            }
        }
        return lista
    }

    fun getReporteGanancias(): Double {
        val query = """
            SELECT SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad)
            FROM $TABLE_DETALLE_VENTA dv
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
        """.trimIndent()
        return readableDatabase.rawQuery(query, null)
            .use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getHistorialMovimientos(): List<String> {
        val historial = mutableListOf<String>()
        val query = """
            SELECT 'VENTA' as t, fecha, total FROM venta
            UNION SELECT 'COMPRA' as t, fecha, total FROM compra
            UNION SELECT 'MERMA' as t, fecha, cantidad FROM merma ORDER BY fecha DESC
        """.trimIndent()
        readableDatabase.rawQuery(query, null).use {
            while (it.moveToNext())
                historial.add("${it.getString(0)} | ${it.getString(1)} | $${it.getDouble(2)}")
        }
        return historial
    }

    fun getComparativaMargen(): List<String> {
        val comparativa = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT nombre, precio_venta, precio_compra FROM $TABLE_PRODUCTO", null
        ).use {
            while (it.moveToNext()) {
                val margen = it.getDouble(1) - it.getDouble(2)
                comparativa.add("${it.getString(0)} | Venta: $${it.getDouble(1)} | Margen: $${String.format("%.2f", margen)}")
            }
        }
        return comparativa
    }

    fun getTotalMermaUnidades(): Int {
        val cursor = readableDatabase.rawQuery("SELECT SUM(cantidad) FROM merma", null)
        return cursor.use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun getTopProductosMermados(): List<Pair<String, Int>> {
        val lista = mutableListOf<Pair<String, Int>>()
        val query = """
            SELECT p.nombre, SUM(m.cantidad) as total
            FROM merma m
            JOIN producto p ON m.id_producto = p.id_producto
            GROUP BY m.id_producto
            ORDER BY total DESC
            LIMIT 5
        """.trimIndent()
        readableDatabase.rawQuery(query, null).use {
            while (it.moveToNext()) lista.add(it.getString(0) to it.getInt(1))
        }
        return lista
    }

    fun getTotalDeudaPendiente(): Double {
        val cursor = readableDatabase.rawQuery(
            "SELECT SUM(saldo_pendiente) FROM fiado WHERE saldo_pendiente > 0", null
        )
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    // ─────────────────────────────────────────────
    //  CLIENTES Y FIADO
    // ─────────────────────────────────────────────

    data class Cliente(
        val idCliente: Int = 0,
        val nombre: String = "",
        val telefono: String = ""
    )

    data class Fiado(
        val idFiado: Int = 0,
        val idCliente: Int = 0,
        val idVenta: Int = 0,
        val saldoPendiente: Double = 0.0
    )

    data class PagoFiado(
        val idPago: Int = 0,
        val idFiado: Int = 0,
        val fechaPago: String = "",
        val monto: Double = 0.0
    )

    fun registrarCliente(nombre: String): Long {
        val cv = ContentValues().apply { put("nombre", nombre) }
        return writableDatabase.insert(TABLE_CLIENTE, null, cv)
    }

    fun registrarClienteConTelefono(nombre: String, telefono: String): Long {
        val cv = ContentValues().apply {
            put("nombre", nombre)
            put("telefono", telefono)
        }
        return writableDatabase.insert(TABLE_CLIENTE, null, cv)
    }

    fun getAllClientes(): List<Cliente> {
        val lista = mutableListOf<Cliente>()
        readableDatabase.query(
            TABLE_CLIENTE, null, null, null, null, null, "nombre ASC"
        ).use {
            while (it.moveToNext()) {
                lista.add(Cliente(
                    idCliente = it.getInt(it.getColumnIndexOrThrow("id_cliente")),
                    nombre    = it.getString(it.getColumnIndexOrThrow("nombre")),
                    telefono  = it.getString(it.getColumnIndexOrThrow("telefono")) ?: ""
                ))
            }
        }
        return lista
    }

    fun getClientePorTelefono(telefono: String): Cliente? {
        val cursor = readableDatabase.query(
            TABLE_CLIENTE, null, "telefono = ?", arrayOf(telefono), null, null, null
        )
        return cursor.use {
            if (it.moveToFirst()) {
                Cliente(
                    idCliente = it.getInt(it.getColumnIndexOrThrow("id_cliente")),
                    nombre    = it.getString(it.getColumnIndexOrThrow("nombre")),
                    telefono  = it.getString(it.getColumnIndexOrThrow("telefono"))
                )
            } else null
        }
    }

    fun actualizarCliente(idCliente: Int, nuevoNombre: String, nuevoTelefono: String): Int {
        val values = ContentValues().apply {
            put("nombre", nuevoNombre)
            put("telefono", nuevoTelefono)
        }
        return writableDatabase.update(TABLE_CLIENTE, values, "id_cliente = ?", arrayOf(idCliente.toString()))
    }

    fun getFiadosActivosPorCliente(idCliente: Int): List<Fiado> {
        val lista = mutableListOf<Fiado>()
        readableDatabase.query(
            TABLE_FIADO, null,
            "id_cliente = ? AND saldo_pendiente > 0",
            arrayOf(idCliente.toString()),
            null, null, null
        ).use {
            while (it.moveToNext()) {
                lista.add(Fiado(
                    idFiado        = it.getInt(it.getColumnIndexOrThrow("id_fiado")),
                    idCliente      = it.getInt(it.getColumnIndexOrThrow("id_cliente")),
                    idVenta        = it.getInt(it.getColumnIndexOrThrow("id_venta")),
                    saldoPendiente = it.getDouble(it.getColumnIndexOrThrow("saldo_pendiente"))
                ))
            }
        }
        return lista
    }

    fun registrarFiado(idCliente: Int, monto: Double): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val fecha = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val cvVenta = ContentValues().apply {
                put("fecha", fecha); put("total", monto)
            }
            val idVenta = db.insert(TABLE_VENTA, null, cvVenta)
            val cvFiado = ContentValues().apply {
                put("id_cliente",      idCliente)
                put("id_venta",        idVenta)
                put("saldo_pendiente", monto)
            }
            db.insert(TABLE_FIADO, null, cvFiado)
            db.setTransactionSuccessful()
            true
        } catch (e: Exception) { false } finally { db.endTransaction() }
    }

    fun getPagosPorFiado(idFiado: Int): List<PagoFiado> {
        val lista = mutableListOf<PagoFiado>()
        readableDatabase.query(
            TABLE_PAGO_FIADO, null, "id_fiado = ?", arrayOf(idFiado.toString()),
            null, null, "fecha_pago DESC"
        ).use {
            while (it.moveToNext()) {
                lista.add(PagoFiado(
                    idPago    = it.getInt(it.getColumnIndexOrThrow("id_pago")),
                    idFiado   = it.getInt(it.getColumnIndexOrThrow("id_fiado")),
                    fechaPago = it.getString(it.getColumnIndexOrThrow("fecha_pago")),
                    monto     = it.getDouble(it.getColumnIndexOrThrow("monto"))
                ))
            }
        }
        return lista
    }

    fun getClientesConDeuda(): List<Triple<Int, String, Double>> {
        val lista = mutableListOf<Triple<Int, String, Double>>()
        val query = """
            SELECT c.id_cliente, c.nombre, SUM(f.saldo_pendiente) as deuda
            FROM fiado f
            JOIN cliente c ON f.id_cliente = c.id_cliente
            WHERE f.saldo_pendiente > 0
            GROUP BY c.id_cliente
            ORDER BY deuda DESC
        """.trimIndent()
        readableDatabase.rawQuery(query, null).use {
            while (it.moveToNext())
                lista.add(Triple(it.getInt(0), it.getString(1), it.getDouble(2)))
        }
        return lista
    }

    fun getTodosLosProductosConCaducidad(): List<Producto> {
        val lista = mutableListOf<Producto>()
        val query = """
            SELECT * FROM $TABLE_PRODUCTO
            WHERE fecha_caducidad IS NOT NULL
              AND fecha_caducidad != ''
            ORDER BY fecha_caducidad ASC
        """.trimIndent()
        readableDatabase.rawQuery(query, null).use {
            while (it.moveToNext()) {
                lista.add(
                    Producto(
                        idProducto     = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                        codigoBarras   = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                        nombre         = it.getString(it.getColumnIndexOrThrow("nombre")),
                        descripcion    = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                        precioVenta    = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                        precioCompra   = it.getDouble(it.getColumnIndexOrThrow("precio_compra")),
                        stock          = it.getInt(it.getColumnIndexOrThrow("stock")),
                        fechaCaducidad = it.getString(it.getColumnIndexOrThrow("fecha_caducidad"))
                    )
                )
            }
        }
        return lista
    }

    fun insertar300ProductosDePrueba() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 1..300) {
                val cv = ContentValues().apply {
                    put("codigo_barras",   "PRUEBA-$i")
                    put("nombre",          "Prueba $i")
                    put("descripcion",     "Producto de carga inicial")
                    put("precio_venta",    50.0)
                    put("precio_compra",   30.0)
                    put("stock",           100)
                    put("fecha_caducidad", null as String?)
                }
                db.insertWithOnConflict(TABLE_PRODUCTO, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
}