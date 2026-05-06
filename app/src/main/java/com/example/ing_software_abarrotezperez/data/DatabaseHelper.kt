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
        // Versión 2: Arregla el bug del id_producto y habilita nuevas tablas.
        const val DATABASE_VERSION = 2

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

        db.execSQL("""
            CREATE TABLE producto (
                id_producto     INTEGER PRIMARY KEY AUTOINCREMENT,
                codigo_barras   TEXT UNIQUE NOT NULL,
                nombre          TEXT NOT NULL,
                descripcion     TEXT,
                precio_venta    REAL,
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
        db.execSQL("DROP TABLE IF EXISTS merma")
        db.execSQL("DROP TABLE IF EXISTS lote")
        db.execSQL("DROP TABLE IF EXISTS producto")
        db.execSQL("DROP TABLE IF EXISTS cliente")
        db.execSQL("DROP TABLE IF EXISTS proveedor_fisico")
        db.execSQL("DROP TABLE IF EXISTS proveedor_digital")
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
        val precioVenta: Double = 0.0,
        val stock: Int = 0,
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
                    idProducto    = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                    codigoBarras  = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                    nombre        = it.getString(it.getColumnIndexOrThrow("nombre")),
                    descripcion   = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                    precioVenta   = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                    stock         = it.getInt(it.getColumnIndexOrThrow("stock")),
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
        val cursor = db.query(TABLE_PRODUCTO, null, null, null, null, null, "nombre ASC")
        cursor.use {
            while (it.moveToNext()) {
                lista.add(
                    Producto(
                        idProducto    = it.getInt(it.getColumnIndexOrThrow("id_producto")),
                        codigoBarras  = it.getString(it.getColumnIndexOrThrow("codigo_barras")),
                        nombre        = it.getString(it.getColumnIndexOrThrow("nombre")),
                        descripcion   = it.getString(it.getColumnIndexOrThrow("descripcion")) ?: "",
                        precioVenta   = it.getDouble(it.getColumnIndexOrThrow("precio_venta")),
                        stock         = it.getInt(it.getColumnIndexOrThrow("stock")),
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
                    put("id_venta", idVenta)
                    put("id_producto", item.producto.idProducto)
                    put("cantidad", item.cantidad)
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
                    val idLote = cursorLote.getInt(0)
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

    fun getReporteGanancias(): Double {
        val db = readableDatabase
        val query = """
            SELECT SUM((dv.precio_unitario - IFNULL(dc.precio_compra, 0)) * dv.cantidad)
            FROM $TABLE_DETALLE_VENTA dv
            LEFT JOIN $TABLE_DETALLE_COMPRA dc ON dv.id_producto = dc.id_producto
        """.trimIndent()
        return db.rawQuery(query, null).use { if (it.moveToFirst()) it.getDouble(0) else 0.0 }
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

    fun getComparativaMargen(): List<String> {
        val db = readableDatabase
        val comparativa = mutableListOf<String>()
        val query = "SELECT p.nombre, p.precio_venta, IFNULL(dc.precio_compra, 0) FROM producto p LEFT JOIN detalle_compra dc ON p.id_producto = dc.id_producto GROUP BY p.id_producto"
        db.rawQuery(query, null).use {
            while (it.moveToNext()) {
                val margen = it.getDouble(1) - it.getDouble(2)
                comparativa.add("${it.getString(0)} | Venta: $${it.getDouble(1)} | Margen: $${String.format("%.2f", margen)}")
            }
        }
        return comparativa
    }
}