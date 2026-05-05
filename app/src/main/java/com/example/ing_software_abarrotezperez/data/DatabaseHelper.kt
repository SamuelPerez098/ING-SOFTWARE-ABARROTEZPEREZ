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
        // Cambiamos a versión 2. Esto obligará al teléfono a borrar las tablas viejas
        // y crear las nuevas correctamente, arreglando el bug del id_producto.
        const val DATABASE_VERSION = 2

        // --- Tablas ---
        const val TABLE_PRODUCTO       = "producto"
        const val TABLE_LOTE           = "lote" // <-- NUEVA TABLA
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

        // NUEVA TABLA DE LOTES
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
        db.execSQL("DROP TABLE IF EXISTS lote") // Borrar Lotes también
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

    /** Inserta o actualiza un producto y administra los LOTES */
    fun upsertProducto(producto: Producto): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put("codigo_barras",   producto.codigoBarras)
            put("nombre",          producto.nombre)
            put("descripcion",     producto.descripcion)
            put("precio_venta",    producto.precioVenta)
            put("stock",           producto.stock) // Actualizamos el stock global
            put("fecha_caducidad", producto.fechaCaducidad) // Guardamos la última registrada como referencia visual
        }

        val existing = getProductoPorCodigo(producto.codigoBarras)
        val finalId: Long

        if (existing == null) {
            finalId = db.insert(TABLE_PRODUCTO, null, cv)
        } else {
            db.update(TABLE_PRODUCTO, cv, "codigo_barras = ?", arrayOf(producto.codigoBarras))
            finalId = existing.idProducto.toLong()
        }

        // ── LÓGICA DE LOTES (Si el producto es perecedero) ──
        if (producto.fechaCaducidad != null) {
            val cursorLote = db.rawQuery(
                "SELECT id_lote FROM lote WHERE id_producto = ? AND fecha_caducidad = ?",
                arrayOf(finalId.toString(), producto.fechaCaducidad)
            )
            if (cursorLote.moveToFirst()) {
                // Si el lote ya existe, le sumamos 1 al stock de ese lote
                val idLote = cursorLote.getInt(0)
                db.execSQL("UPDATE lote SET cantidad = cantidad + 1 WHERE id_lote = ?", arrayOf(idLote))
            } else {
                // Si no existe, creamos el lote con cantidad 1
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
    //  VENTA
    // ─────────────────────────────────────────────

    data class ItemVenta(
        val producto: Producto,
        var cantidad: Int = 1
    ) {
        val subtotal get() = producto.precioVenta * cantidad
    }

    /**
     * Registra la venta: Inserta venta, detalle_venta, reduce stock global y reduce LOTES en modo FIFO.
     */
    fun registrarVenta(items: List<ItemVenta>): Long {
        val db = writableDatabase
        db.beginTransaction()
        return try {
            val total = items.sumOf { it.subtotal }

            // AHORA GUARDA FECHA Y HORA (Ej: 2026-05-04 15:30:00)
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

                // 1. Reducir stock global en tabla producto
                val stmt = db.compileStatement("UPDATE producto SET stock = stock - ? WHERE id_producto = ?")
                stmt.bindLong(1, item.cantidad.toLong())
                stmt.bindLong(2, item.producto.idProducto.toLong())
                stmt.executeUpdateDelete()

                // 2. Reducir stock de LOTES por fecha de caducidad (FIFO: El que caduca primero se va primero)
                var restante = item.cantidad
                val cursorLote = db.rawQuery(
                    "SELECT id_lote, cantidad FROM lote WHERE id_producto = ? AND cantidad > 0 ORDER BY fecha_caducidad ASC",
                    arrayOf(item.producto.idProducto.toString())
                )

                while (cursorLote.moveToNext() && restante > 0) {
                    val idLote = cursorLote.getInt(0)
                    val cantLote = cursorLote.getInt(1)

                    if (cantLote <= restante) {
                        // Consumimos todo este lote
                        db.execSQL("UPDATE lote SET cantidad = 0 WHERE id_lote = ?", arrayOf(idLote))
                        restante -= cantLote
                    } else {
                        // Consumimos solo una parte de este lote
                        db.execSQL("UPDATE lote SET cantidad = cantidad - ? WHERE id_lote = ?", arrayOf(restante, idLote))
                        restante = 0
                    }
                }
                cursorLote.close()
            }

            db.setTransactionSuccessful()
            idVenta
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        } finally {
            db.endTransaction()
        }
    }
}