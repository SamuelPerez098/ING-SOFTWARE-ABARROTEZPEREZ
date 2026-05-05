package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.example.ing_software_abarrotezperez.viewmodel.ScannerViewModel

class VentaActivity : AppCompatActivity() {

    private lateinit var vm: ScannerViewModel
    private lateinit var adapter: VentaAdapter

    private lateinit var tvEstadoBt: TextView
    private lateinit var btnConectarBt: Button
    private lateinit var rvItems: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var btnCobrar: Button
    private lateinit var btnCancelar: Button

    private val itemsVenta = mutableListOf<DatabaseHelper.ItemVenta>()

    // Variables para el interceptor HID
    private val barcodeBuffer = StringBuilder()
    private var lastKeyTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_venta)

        vm = ViewModelProvider(this)[ScannerViewModel::class.java]

        tvEstadoBt    = findViewById(R.id.tvEstadoBt)
        btnConectarBt = findViewById(R.id.btnConectarBt)
        rvItems       = findViewById(R.id.rvItems)
        tvTotal       = findViewById(R.id.tvTotal)
        btnCobrar     = findViewById(R.id.btnCobrar)
        btnCancelar   = findViewById(R.id.btnCancelar)

        // Ocultar botón y ajustar estado para HID
        btnConectarBt.visibility = View.GONE
        tvEstadoBt.text = "Modo: Lector (HID)"

        adapter = VentaAdapter(itemsVenta) { actualizarTotal() }
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter

        btnCobrar.setOnClickListener {
            if (itemsVenta.isEmpty()) {
                Toast.makeText(this, "No hay productos escaneados", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmarVenta()
        }

        btnCancelar.setOnClickListener { finish() }
    }

    private fun procesarEscaneoVenta(codigo: String) {
        val producto = vm.db.getProductoPorCodigo(codigo)
        if (producto == null) {
            AlertDialog.Builder(this)
                .setTitle("Producto no encontrado")
                .setMessage("Código: $codigo\n¿Deseas registrarlo primero en inventario?")
                .setPositiveButton("Ir a Inventario") { _, _ ->
                    startActivity(android.content.Intent(this, InventarioActivity::class.java))
                }
                .setNegativeButton("Cancelar", null)
                .show()
            return
        }

        if (producto.stock <= 0) {
            Toast.makeText(this, "⚠ Sin stock: ${producto.nombre}", Toast.LENGTH_SHORT).show()
            return
        }

        val existente = itemsVenta.find { it.producto.codigoBarras == codigo }
        if (existente != null) {
            if (existente.cantidad >= existente.producto.stock) {
                Toast.makeText(this, "Stock máximo alcanzado (${existente.producto.stock})", Toast.LENGTH_SHORT).show()
                return
            }
            existente.cantidad++
            adapter.notifyDataSetChanged()
        } else {
            itemsVenta.add(DatabaseHelper.ItemVenta(producto, 1))
            adapter.notifyItemInserted(itemsVenta.size - 1)
        }
        actualizarTotal()
    }

    private fun actualizarTotal() {
        val total = itemsVenta.sumOf { it.subtotal }
        tvTotal.text = "Total: \$%.2f".format(total)
        btnCobrar.isEnabled = itemsVenta.isNotEmpty()
    }

    private fun confirmarVenta() {
        val total = itemsVenta.sumOf { it.subtotal }
        AlertDialog.Builder(this)
            .setTitle("Confirmar venta")
            .setMessage("Total: \$%.2f\n\n¿Completar venta?".format(total))
            .setPositiveButton("Cobrar") { _, _ -> ejecutarVenta() }
            .setNeutralButton("Fiado") { _, _ -> mostrarDialogoFiado() }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun ejecutarVenta() {
        val idVenta = vm.db.registrarVenta(itemsVenta)
        if (idVenta > 0) {
            Toast.makeText(this, "✓ Venta registrada #$idVenta", Toast.LENGTH_LONG).show()
            itemsVenta.clear()
            adapter.notifyDataSetChanged()
            actualizarTotal()
        } else {
            Toast.makeText(this, "Error al registrar la venta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDialogoFiado() {
        val input = EditText(this).apply { hint = "Nombre del cliente" }
        AlertDialog.Builder(this)
            .setTitle("Venta a fiado")
            .setView(input)
            .setPositiveButton("Guardar") { _, _ ->
                val nombre = input.text.toString().trim()
                if (nombre.isEmpty()) {
                    Toast.makeText(this, "Escribe el nombre del cliente", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                registrarFiado(nombre)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun registrarFiado(nombreCliente: String) {
        val db = vm.db.writableDatabase
        var idCliente: Long
        val cursorCliente = db.rawQuery(
            "SELECT id_cliente FROM cliente WHERE nombre = ? LIMIT 1", arrayOf(nombreCliente)
        )
        idCliente = if (cursorCliente.moveToFirst())
            cursorCliente.getLong(0)
        else {
            val cv = android.content.ContentValues().apply { put("nombre", nombreCliente) }
            db.insert("cliente", null, cv)
        }
        cursorCliente.close()

        val idVenta = vm.db.registrarVenta(itemsVenta)
        if (idVenta < 0) {
            Toast.makeText(this, "Error en la venta", Toast.LENGTH_SHORT).show()
            return
        }

        val total = itemsVenta.sumOf { it.subtotal }
        val cvFiado = android.content.ContentValues().apply {
            put("id_cliente", idCliente)
            put("id_venta", idVenta)
            put("saldo_pendiente", total)
        }
        db.insert("fiado", null, cvFiado)

        Toast.makeText(this, "✓ Fiado guardado para $nombreCliente", Toast.LENGTH_LONG).show()
        itemsVenta.clear()
        adapter.notifyDataSetChanged()
        actualizarTotal()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERCEPTOR MODO HID (Teclado)
    // ─────────────────────────────────────────────────────────────────────────
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            val timeNow = System.currentTimeMillis()

            if (timeNow - lastKeyTime > 200) {
                barcodeBuffer.clear()
            }
            lastKeyTime = timeNow

            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val codigoLeido = barcodeBuffer.toString().trim()
                if (codigoLeido.isNotEmpty()) {
                    procesarEscaneoVenta(codigoLeido)
                    barcodeBuffer.clear()
                    return true
                }
            }

            val pressedChar = event.unicodeChar.toChar()
            if (pressedChar.isDefined() && event.unicodeChar > 31) {
                barcodeBuffer.append(pressedChar)
            }
        }
        return super.dispatchKeyEvent(event)
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  RecyclerView Adapter
    // ─────────────────────────────────────────────────────────────────────────

    inner class VentaAdapter(
        private val items: MutableList<DatabaseHelper.ItemVenta>,
        private val onChange: () -> Unit
    ) : RecyclerView.Adapter<VentaAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvNombre: TextView   = view.findViewById(R.id.tvNombreProducto)
            val tvPrecio: TextView   = view.findViewById(R.id.tvPrecioUnitario)
            val tvCantidad: TextView = view.findViewById(R.id.tvCantidad)
            val tvSubtotal: TextView = view.findViewById(R.id.tvSubtotal)
            val btnMas: ImageButton  = view.findViewById(R.id.btnMas)
            val btnMenos: ImageButton = view.findViewById(R.id.btnMenos)
            val btnEliminar: ImageButton = view.findViewById(R.id.btnEliminar)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_venta, parent, false))

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.tvNombre.text   = item.producto.nombre
            holder.tvPrecio.text   = "\$%.2f c/u".format(item.producto.precioVenta)
            holder.tvCantidad.text = item.cantidad.toString()
            holder.tvSubtotal.text = "\$%.2f".format(item.subtotal)

            holder.btnMas.setOnClickListener {
                if (item.cantidad < item.producto.stock) {
                    item.cantidad++
                    notifyItemChanged(position)
                    onChange()
                } else {
                    Toast.makeText(holder.itemView.context, "Stock máximo", Toast.LENGTH_SHORT).show()
                }
            }

            holder.btnMenos.setOnClickListener {
                if (item.cantidad > 1) {
                    item.cantidad--
                    notifyItemChanged(position)
                    onChange()
                }
            }

            holder.btnEliminar.setOnClickListener {
                items.removeAt(position)
                notifyItemRemoved(position)
                notifyItemRangeChanged(position, items.size)
                onChange()
            }
        }
    }
}