package com.example.ing_software_abarrotezperez.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.example.ing_software_abarrotezperez.scanner.CamaraScanner
import com.example.ing_software_abarrotezperez.scanner.HidCapturador
import com.example.ing_software_abarrotezperez.scanner.LectoresConectados
import com.example.ing_software_abarrotezperez.scanner.VigilanteLectores
import com.example.ing_software_abarrotezperez.viewmodel.ScannerViewModel
import com.google.android.material.button.MaterialButton
import com.journeyapps.barcodescanner.DecoratedBarcodeView

class VentaActivity : AppCompatActivity() {

    private companion object {
        const val PREFS_SCANNER = "scanner_prefs"
        const val CLAVE_MODO_VENTA = "modo_venta"
    }

    private lateinit var vm: ScannerViewModel
    private lateinit var adapter: VentaAdapter

    private lateinit var tvEstadoBt: TextView
    private lateinit var btnConectarBt: Button
    private lateinit var rvItems: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var btnCobrar: Button
    private lateinit var btnCancelar: Button

    private val itemsVenta = mutableListOf<DatabaseHelper.ItemVenta>()

    // ── Lector en modo teclado (HID) ────────────────────────────────────────
    // Sirve igual para el lector por Bluetooth (HID) y por cable USB: en ambos casos
    // Android lo ve como un teclado externo y entrega las teclas como KeyEvent.
    private val hid = HidCapturador(consumirTodo = true, ignorarTecladoVirtual = false) { codigo ->
        procesarEscaneoVenta(codigo)
    }
    private lateinit var vigilante: VigilanteLectores
    private var ultimoCodigo = ""

    // ── Modo de escaneo: Bluetooth / Cable USB / Cámara ─────────────────────
    private enum class Modo { BLUETOOTH, CABLE, CAMARA }

    private lateinit var btnModoBluetooth: MaterialButton
    private lateinit var btnModoCable: MaterialButton
    private lateinit var btnModoCamara: MaterialButton
    private lateinit var panelCamara: FrameLayout
    private lateinit var dbvVenta: DecoratedBarcodeView
    private lateinit var camara: CamaraScanner
    private var modo = Modo.BLUETOOTH
    private var enPrimerPlano = false
    private var dialogoNoEncontradoVisible = false

    private val pedirPermisoCamara =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                seleccionarModo(Modo.CAMARA)
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara para escanear", Toast.LENGTH_LONG).show()
                seleccionarModo(Modo.BLUETOOTH)
            }
        }

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

        // ── Selector de modo: Bluetooth (HID) / Cable USB / Cámara ──
        btnModoBluetooth = findViewById(R.id.btnModoBluetooth)
        btnModoCable     = findViewById(R.id.btnModoCable)
        btnModoCamara    = findViewById(R.id.btnModoCamara)
        panelCamara      = findViewById(R.id.panelCamara)
        dbvVenta         = findViewById(R.id.dbvVenta)
        camara = CamaraScanner(this, dbvVenta) { codigo -> procesarEscaneoVenta(codigo) }
        vigilante = VigilanteLectores(this) { actualizarEstado() }

        btnModoBluetooth.setOnClickListener { seleccionarModo(Modo.BLUETOOTH) }
        btnModoCable.setOnClickListener { seleccionarModo(Modo.CABLE) }
        btnModoCamara.setOnClickListener {
            if (tienePermisoCamara()) seleccionarModo(Modo.CAMARA)
            else pedirPermisoCamara.launch(Manifest.permission.CAMERA)
        }

        // Mantener presionado el texto de estado abre el diagnóstico del lector
        tvEstadoBt.setOnLongClickListener { mostrarDiagnostico(); true }

        // Recuerda el último modo usado (si era cámara y ya no hay permiso, vuelve a Bluetooth)
        val guardado = runCatching {
            Modo.valueOf(
                getSharedPreferences(PREFS_SCANNER, MODE_PRIVATE)
                    .getString(CLAVE_MODO_VENTA, Modo.BLUETOOTH.name) ?: Modo.BLUETOOTH.name
            )
        }.getOrDefault(Modo.BLUETOOTH)
        seleccionarModo(if (guardado == Modo.CAMARA && !tienePermisoCamara()) Modo.BLUETOOTH else guardado)

        btnCobrar.setOnClickListener {
            if (itemsVenta.isEmpty()) {
                Toast.makeText(this, "No hay productos escaneados", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            confirmarVenta()
        }

        btnCancelar.setOnClickListener { finish() }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  MODO DE ESCANEO (Bluetooth / Cable USB / Cámara)
    // ─────────────────────────────────────────────────────────────────────────
    private fun tienePermisoCamara() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun seleccionarModo(nuevo: Modo) {
        modo = nuevo
        if (nuevo == Modo.CAMARA) {
            panelCamara.visibility = View.VISIBLE
            if (enPrimerPlano) camara.iniciar()      // si no, lo inicia onResume
        } else {
            camara.pausar()
            panelCamara.visibility = View.GONE
        }
        pintarBotonModo(btnModoBluetooth, nuevo == Modo.BLUETOOTH)
        pintarBotonModo(btnModoCable, nuevo == Modo.CABLE)
        pintarBotonModo(btnModoCamara, nuevo == Modo.CAMARA)

        getSharedPreferences(PREFS_SCANNER, MODE_PRIVATE)
            .edit().putString(CLAVE_MODO_VENTA, nuevo.name).apply()
        actualizarEstado()
    }

    private fun pintarBotonModo(boton: MaterialButton, activo: Boolean) {
        val verde = Color.parseColor("#00B259")
        boton.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (activo) verde else Color.TRANSPARENT
        )
        boton.setTextColor(if (activo) Color.WHITE else verde)
    }

    /** Texto de estado: modo activo, si Android ve el lector y el último código leído. */
    private fun actualizarEstado() {
        val lectores = LectoresConectados.listar(this)
        val base = when (modo) {
            Modo.BLUETOOTH -> {
                val bt = lectores.firstOrNull { !it.porUsb }
                if (bt != null) "Modo: Bluetooth ✓ ${bt.nombre}"
                else "Modo: Bluetooth ⚠ lector no detectado"
            }
            Modo.CABLE -> {
                val usb = lectores.firstOrNull { it.porUsb }
                when {
                    !packageManager.hasSystemFeature(PackageManager.FEATURE_USB_HOST) ->
                        "Modo: Cable ⚠ este teléfono no admite USB-OTG"
                    usb != null -> "Modo: Cable USB ✓ ${usb.nombre}"
                    else -> "Modo: Cable USB ⚠ conecta el lector (adaptador OTG)"
                }
            }
            Modo.CAMARA -> "Modo: Cámara del teléfono"
        }
        tvEstadoBt.text = if (ultimoCodigo.isEmpty()) base else "$base\nÚltimo: $ultimoCodigo"
    }

    /** Se abre manteniendo presionado el texto de estado. Muestra las teclas que llegan del lector. */
    private fun mostrarDiagnostico() {
        val tv = TextView(this).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            textSize = 11f
            setPadding(32, 16, 32, 16)
        }
        fun refrescar() {
            tv.text = "LECTORES DETECTADOS\n" + LectoresConectados.descripcion(this) +
                    "\n\nTECLAS RECIBIDAS (la más reciente arriba)\n" + hid.textoDiagnostico() +
                    "\n\nEscanea un código con el lector: si no aparece ninguna línea, Android no está " +
                    "entregando las teclas a la app (revisa el modo HID / la conexión del lector)."
        }
        refrescar()
        AlertDialog.Builder(this)
            .setTitle("Diagnóstico del lector")
            .setView(ScrollView(this).apply { addView(tv) })
            .setNeutralButton("Cerrar", null)
            .setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    false
                } else {
                    // El diálogo recibe las teclas en lugar de la pantalla: se anotan y se consumen
                    hid.observar(event)
                    refrescar()
                    true
                }
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        enPrimerPlano = true
        vigilante.iniciar()
        actualizarEstado()
        if (modo == Modo.CAMARA && tienePermisoCamara()) camara.iniciar()
    }

    override fun onPause() {
        super.onPause()
        enPrimerPlano = false
        camara.pausar()
        vigilante.detener()
        hid.detener()
    }

    private fun procesarEscaneoVenta(codigo: String) {
        ultimoCodigo = codigo
        actualizarEstado()
        val producto = vm.db.getProductoPorCodigo(codigo)
        if (producto == null) {
            // Evita apilar varios diálogos si la cámara sigue viendo el mismo código
            if (dialogoNoEncontradoVisible) return
            dialogoNoEncontradoVisible = true
            AlertDialog.Builder(this)
                .setTitle("Producto no encontrado")
                .setMessage("Código: $codigo\n¿Deseas registrarlo primero en inventario?")
                .setPositiveButton("Ir a Inventario") { _, _ ->
                    startActivity(android.content.Intent(this, InventarioActivity::class.java))
                }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { dialogoNoEncontradoVisible = false }
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
        if (hid.onKeyEvent(event)) return true
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
            // LÍNEA CORREGIDA ABAJO:
            val btnMenos: Button     = view.findViewById(R.id.btnMenos)
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