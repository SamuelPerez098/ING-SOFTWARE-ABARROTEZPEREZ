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
        // CAMBIO: Se aumenta la versión para que se vuelva a crear la BD con la nueva columna
        const val DATABASE_VERSION = 3

        // --- Tablas ---
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
                nombre      TEXT NOT NULL
            )
        """.trimIndent())

        // CAMBIO: Se agregó precio_compra a la tabla
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
        // Se eliminan las tablas existentes (el orden importa por las llaves foráneas)
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

        // Se vuelve a crear la base de datos con la nueva estructura
        onCreate(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) db.execSQL("PRAGMA foreign_keys = ON;")
    }

    // ─────────────────────────────────────────────
    //  PRODUCTO
    // ─────────────────────────────────────────────

    // CAMBIO: Se agregó precioCompra al Data Class
    data class Producto(
        val idProducto: Int = 0,
        val codigoBarras: String = "",
        val nombre: String = "",
        val descripcion: String = "",
        var precioVenta: Double = 0.0, // <--- CAMBIO AQUÍ (de val a var)
        val precioCompra: Double = 0.0,
        var stock: Int = 0,            // <--- CAMBIO AQUÍ (de val a var)
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
                    precioCompra   = it.getDouble(it.getColumnIndexOrThrow("precio_compra")), // CAMBIO
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
            put("precio_compra",   producto.precioCompra) // CAMBIO
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
            val cursorLote = db.rawQuery(
                "SELECT id_lote FROM lote WHERE id_producto = ? AND fecha_caducidad = ?",
                arrayOf(finalId.toString(), producto.fechaCaducidad)
            )
            if (cursorLote.moveToFirst()) {
                val idLote = cursorLote.getInt(0)
                db.execSQL("UPDATE lote SET cantidad = cantidad + 1 WHERE id_lote = ?", arrayOf(idLote))
            } else {
                val cvLote = ContentValues().apply {
                    put("id_producto", finalId)
                    put("fecha_caducidad", producto.fechaCaducidad)
                    put("cantidad", 1)
                }
                db.insert(TABLE_LOTE, null, cvLote)
            }
            cursorLote.close()
        }
        return finalId
    }

    fun getAllProductos(): List<Producto> {
        val db = readableDatabase
        val lista = mutableListOf<Producto>()

        // Ordena primero por largo del texto y luego por el texto.
        // Esto hace que: "Prueba 2" (8 chars) vaya antes que "Prueba 10" (9 chars)
        val cursor = db.query(
            TABLE_PRODUCTO,
            null,
            null,
            null,
            null,
            null,
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
    //  SPRINT 3: ENTRADAS (Fiado y Merma)
    // ─────────────────────────────────────────────

    fun getSaldoPendienteCliente(idCliente: Int): Double {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT SUM(saldo_pendiente) FROM fiado WHERE id_cliente = ?", arrayOf(idCliente.toString()))
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
    //  SPRINT 3: SALIDAS (Reportes de Negocio)
    // ─────────────────────────────────────────────

    // CAMBIO: Ganancia del Día ahora resta el precio_compra nativo del producto, es mucho más eficiente
    fun getGananciaDelDia(): Double {
        val db = readableDatabase
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val query = """
            SELECT SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad)
            FROM $TABLE_VENTA v
            JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
            WHERE v.fecha LIKE ?
        """.trimIndent()
        return db.rawQuery(query, arrayOf("$today%")).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getVentaDelDia(): Double {
        val db = readableDatabase
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val query = "SELECT SUM(total) FROM $TABLE_VENTA WHERE fecha LIKE ?"
        return db.rawQuery(query, arrayOf("$today%")).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getProductosPorCaducar(): List<Producto> {
        val db = readableDatabase
        val lista = mutableListOf<Producto>()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        val query = """
            SELECT * FROM $TABLE_PRODUCTO 
            WHERE fecha_caducidad IS NOT NULL 
              AND fecha_caducidad != '' 
              AND fecha_caducidad >= ? 
            ORDER BY fecha_caducidad ASC
        """.trimIndent()

        db.rawQuery(query, arrayOf(today)).use {
            while (it.moveToNext()) {
                lista.add(
                    Producto(
                        idProducto     = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                        codigoBarras   = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                        nombre         = it.getString(it.getColumnIndexOrThrow("nombre")),
                        descripcion    = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                        precioVenta    = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                        precioCompra   = it.getDouble(it.getColumnIndexOrThrow("precio_compra")), // CAMBIO
                        stock          = it.getInt(it.getColumnIndexOrThrow("stock")),
                        fechaCaducidad = it.getString(it.getColumnIndexOrThrow("fecha_caducidad"))
                    )
                )
            }
        }
        return lista
    }

    // CAMBIO: Reporte general de ganancias simplificado con la misma lógica
    fun getReporteGanancias(): Double {
        val db = readableDatabase
        val query = """
            SELECT SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad)
            FROM $TABLE_DETALLE_VENTA dv
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
        """.trimIndent()
        return db.rawQuery(query, null).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
    }

    fun getHistorialMovimientos(): List<String> {
        val db = readableDatabase
        val historial = mutableListOf<String>()
        val query = """
            SELECT 'VENTA' as t, fecha, total FROM venta 
            UNION SELECT 'COMPRA' as t, fecha, total FROM compra 
            UNION SELECT 'MERMA' as t, fecha, cantidad FROM merma ORDER BY fecha DESC
        """.trimIndent()
        db.rawQuery(query, null).use {
            while (it.moveToNext()) historial.add("${it.getString(0)} | ${it.getString(1)} | $${it.getDouble(2)}")
        }
        return historial
    }

    fun getTopVendidos(): List<Pair<String, Int>> {
        val db = readableDatabase
        val lista = mutableListOf<Pair<String, Int>>()
        val query = "SELECT p.nombre, SUM(dv.cantidad) as tot FROM detalle_venta dv JOIN producto p ON dv.id_producto = p.id_producto GROUP BY p.id_producto ORDER BY tot DESC LIMIT 5"
        db.rawQuery(query, null).use {
            while (it.moveToNext()) lista.add(it.getString(0) to it.getInt(1))
        }
        return lista
    }

    // CAMBIO: Simplificada la comparativa de márgenes
    fun getComparativaMargen(): List<String> {
        val db = readableDatabase
        val comparativa = mutableListOf<String>()
        val query = "SELECT nombre, precio_venta, precio_compra FROM $TABLE_PRODUCTO"
        db.rawQuery(query, null).use {
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
            "SELECT SUM(saldo_pendiente) FROM fiado WHERE saldo_pendiente > 0", null)
        return cursor.use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
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

    // ─────────────────────────────────────────────
    //  CLIENTES Y FIADO
    // ─────────────────────────────────────────────

    data class Cliente(
        val idCliente: Int = 0,
        val nombre: String = ""
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

    fun getAllClientes(): List<Cliente> {
        val lista = mutableListOf<Cliente>()
        val cursor = readableDatabase.query(
            TABLE_CLIENTE, null, null, null, null, null, "nombre ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                lista.add(Cliente(
                    idCliente = it.getInt(it.getColumnIndexOrThrow("id_cliente")),
                    nombre    = it.getString(it.getColumnIndexOrThrow("nombre"))
                ))
            }
        }
        return lista
    }

    fun getFiadosActivosPorCliente(idCliente: Int): List<Fiado> {
        val lista = mutableListOf<Fiado>()
        val cursor = readableDatabase.query(
            TABLE_FIADO, null,
            "id_cliente = ? AND saldo_pendiente > 0",
            arrayOf(idCliente.toString()),
            null, null, null
        )
        cursor.use {
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
                put("fecha", fecha)
                put("total", monto)
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
        val cursor = readableDatabase.query(
            TABLE_PAGO_FIADO, null,
            "id_fiado = ?", arrayOf(idFiado.toString()),
            null, null, "fecha_pago DESC"
        )
        cursor.use {
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

    fun getTodosLosProductosConCaducidad(): List<Producto> {
        val db = readableDatabase
        val lista = mutableListOf<Producto>()
        // Trae todos los que tengan fecha de caducidad, ordenados de más viejos a más nuevos
        val query = """
            SELECT * FROM $TABLE_PRODUCTO 
            WHERE fecha_caducidad IS NOT NULL 
              AND fecha_caducidad != '' 
            ORDER BY fecha_caducidad ASC
        """.trimIndent()

        db.rawQuery(query, null).use {
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

    // 1. Ganancias por hora (actualizado para recibir fecha)
    fun getGananciasPorHoraDelDia(fechaFiltro: String): Map<Int, Float> {
        val db = readableDatabase
        val mapaGanancias = mutableMapOf<Int, Float>()
        val query = """
            SELECT CAST(strftime('%H', v.fecha) AS INTEGER) as hora, 
                   SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad) as total_ganancia
            FROM $TABLE_VENTA v
            JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
            WHERE v.fecha LIKE ?
            GROUP BY hora
        """.trimIndent()

        // Usamos la fecha seleccionada en lugar de "hoy"
        db.rawQuery(query, arrayOf("$fechaFiltro%")).use { cursor ->
            while (cursor.moveToNext()) {
                val hora = cursor.getInt(cursor.getColumnIndexOrThrow("hora"))
                val ganancia = cursor.getFloat(cursor.getColumnIndexOrThrow("total_ganancia"))
                mapaGanancias[hora] = ganancia
            }
        }
        return mapaGanancias
    }

    // 2. Ganancia Total del Día (asegúrate que la consulta coincida con la tuya)
    fun getGananciaDelDia(fechaFiltro: String): Double {
        val db = readableDatabase
        var ganancia = 0.0
        val query = """
            SELECT SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad) 
            FROM $TABLE_VENTA v
            JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
            JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
            WHERE v.fecha LIKE ?
        """.trimIndent()

        db.rawQuery(query, arrayOf("$fechaFiltro%")).use { cursor ->
            if (cursor.moveToFirst()) { ganancia = cursor.getDouble(0) }
        }
        return ganancia
    }

    // 3. Venta Total del Día
    fun getVentaDelDia(fechaFiltro: String): Double {
        val db = readableDatabase
        var venta = 0.0
        val query = "SELECT SUM(total) FROM $TABLE_VENTA WHERE fecha LIKE ?"

        db.rawQuery(query, arrayOf("$fechaFiltro%")).use { cursor ->
            if (cursor.moveToFirst()) { venta = cursor.getDouble(0) }
        }
        return venta
    }

    fun aplicarPromocion(codigo: String, nuevoPrecio: Double): Int {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("precioVenta", nuevoPrecio)
        }
        return db.update("productos", values, "codigoBarras = ?", arrayOf(codigo))
    }

    fun actualizarPrecioVenta(codigoBarras: String, nuevoPrecio: Double): Boolean {
        val db = this.writableDatabase
        val values = android.content.ContentValues().apply {
            put("precioVenta", nuevoPrecio)
        }

        // Actualiza la fila donde el código de barras coincida
        val filasAfectadas = db.update("productos", values, "codigoBarras = ?", arrayOf(codigoBarras))
        db.close()

        return filasAfectadas > 0
    }
    fun getGananciasPorHoraDelDia(): Map<Int, Float> {
        val db = readableDatabase
        val mapaGanancias = mutableMapOf<Int, Float>()

        // Obtenemos la fecha de hoy en formato yyyy-MM-dd para filtrar
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        // Consulta corregida:
        // 1. Usa la constante TABLE_VENTA.
        // 2. Hace JOIN con detalle y producto para calcular (Venta - Costo).
        // 3. Filtra por el día actual usando LIKE.
        val query = """
        SELECT CAST(strftime('%H', v.fecha) AS INTEGER) as hora, 
               SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad) as total_ganancia
        FROM $TABLE_VENTA v
        JOIN $TABLE_DETALLE_VENTA dv ON v.id_venta = dv.id_venta
        JOIN $TABLE_PRODUCTO p ON dv.id_producto = p.id_producto
        WHERE v.fecha LIKE ?
        GROUP BY hora
    """.trimIndent()

        db.rawQuery(query, arrayOf("$hoy%")).use { cursor ->
            while (cursor.moveToNext()) {
                val hora = cursor.getInt(0)
                val total = cursor.getFloat(1)
                mapaGanancias[hora] = total
            }
        }
        return mapaGanancias
    }

    fun insertar300ProductosDePrueba() {
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (i in 1..300) {
                val producto = Producto(
                    codigoBarras = "PRUEBA-$i",
                    nombre = "Prueba $i",
                    descripcion = "Producto de carga inicial",
                    precioVenta = 50.0,
                    precioCompra = 30.0,
                    stock = 100, // Stock inicial de prueba
                    fechaCaducidad = null
                )

                // Insertamos directamente. Si el código ya existe por algún error,
                // el 'IGNORE' evitará que truene o que resetee el stock.
                val cv = ContentValues().apply {
                    put("codigo_barras",   producto.codigoBarras)
                    put("nombre",          producto.nombre)
                    put("descripcion",     producto.descripcion)
                    put("precio_venta",    producto.precioVenta)
                    put("precio_compra",   producto.precioCompra)
                    put("stock",           producto.stock)
                    put("fecha_caducidad", producto.fechaCaducidad)
                }
                db.insertWithOnConflict("producto", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

}