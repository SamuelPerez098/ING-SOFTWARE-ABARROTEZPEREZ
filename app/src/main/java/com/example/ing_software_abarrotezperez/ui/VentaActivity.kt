package com.example.ing_software_abarrotezperez.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
import androidx.core.content.FileProvider
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
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    private lateinit var etRecibido: EditText

    private val itemsVenta = mutableListOf<DatabaseHelper.ItemVenta>()

    // ── Lector en modo teclado (HID) ────────────────────────────────────────
    // consumirTodo = false e ignorarTecladoVirtual = true → deja pasar las teclas del
    // teclado en pantalla al EditText, pero sigue capturando los escaneos rápidos del lector.
    private val hid = HidCapturador(consumirTodo = false, ignorarTecladoVirtual = true) { codigo ->
        removerBasuraDelFoco(codigo)
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
            if (concedido) seleccionarModo(Modo.CAMARA)
            else {
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
        etRecibido    = findViewById(R.id.etRecibido)

        // HID: ocultamos el botón CONECTAR
        btnConectarBt.visibility = View.GONE

        adapter = VentaAdapter(itemsVenta) { actualizarTotal() }
        rvItems.layoutManager = LinearLayoutManager(this)
        rvItems.adapter = adapter

        // Selector de modo
        btnModoBluetooth = findViewById(R.id.btnModoBluetooth)
        btnModoCable     = findViewById(R.id.btnModoCable)
        btnModoCamara    = findViewById(R.id.btnModoCamara)
        panelCamara      = findViewById(R.id.panelCamara)
        dbvVenta         = findViewById(R.id.dbvVenta)
        camara    = CamaraScanner(this, dbvVenta) { codigo -> procesarEscaneoVenta(codigo) }
        vigilante = VigilanteLectores(this) { actualizarEstado() }

        btnModoBluetooth.setOnClickListener { seleccionarModo(Modo.BLUETOOTH) }
        btnModoCable.setOnClickListener     { seleccionarModo(Modo.CABLE) }
        btnModoCamara.setOnClickListener {
            if (tienePermisoCamara()) seleccionarModo(Modo.CAMARA)
            else pedirPermisoCamara.launch(Manifest.permission.CAMERA)
        }

        // Mantener presionado el estado → diagnóstico
        tvEstadoBt.setOnLongClickListener { mostrarDiagnostico(); true }

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
    //  MODOS DE ESCANEO
    // ─────────────────────────────────────────────────────────────────────────
    private fun tienePermisoCamara() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun seleccionarModo(nuevo: Modo) {
        modo = nuevo
        if (nuevo == Modo.CAMARA) {
            panelCamara.visibility = View.VISIBLE
            if (enPrimerPlano) camara.iniciar()
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
                if (keyCode == KeyEvent.KEYCODE_BACK) false
                else { hid.observar(event); refrescar(); true }
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

    // ─────────────────────────────────────────────────────────────────────────
    //  ESCANEO
    // ─────────────────────────────────────────────────────────────────────────
    private fun procesarEscaneoVenta(codigo: String) {
        ultimoCodigo = codigo
        actualizarEstado()
        val producto = vm.db.getProductoPorCodigo(codigo)
        if (producto == null) {
            if (dialogoNoEncontradoVisible) return
            dialogoNoEncontradoVisible = true
            AlertDialog.Builder(this)
                .setTitle("Producto no encontrado")
                .setMessage("Código: $codigo\n¿Deseas registrarlo primero en inventario?")
                .setPositiveButton("Ir a Inventario") { _, _ ->
                    startActivity(Intent(this, InventarioActivity::class.java))
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

    // ─────────────────────────────────────────────────────────────────────────
    //  COBRO / TICKET
    // ─────────────────────────────────────────────────────────────────────────
    private fun confirmarVenta() {
        val total = itemsVenta.sumOf { it.subtotal }
        val recibidoStr = etRecibido.text?.toString()?.trim() ?: ""

        val recibido = if (recibidoStr.isEmpty()) 0.0 else (recibidoStr.toDoubleOrNull() ?: 0.0)

        if (recibidoStr.isNotEmpty() && recibido < total) {
            Toast.makeText(this, "El monto recibido es menor al TOTAL", Toast.LENGTH_SHORT).show()
            return
        }

        val cambio = if (recibido >= total) recibido - total else 0.0
        val mensajeTicketListo = construirTextoTicket(total, recibido, cambio)

        val ticketView = layoutInflater.inflate(R.layout.dialog_ticket, null)
        val tvFechaHora      = ticketView.findViewById<TextView>(R.id.tvTicketFechaHora)
        val llProductos      = ticketView.findViewById<LinearLayout>(R.id.llTicketProductos)
        val tvTicketTotal    = ticketView.findViewById<TextView>(R.id.tvTicketTotal)
        val tvTicketRecibido = ticketView.findViewById<TextView>(R.id.tvTicketRecibido)
        val tvTicketCambio   = ticketView.findViewById<TextView>(R.id.tvTicketCambio)
        val btnWhatsApp      = ticketView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGenerarPdfWhatsApp)
        val spinnerClientes  = ticketView.findViewById<Spinner>(R.id.spinnerClientesTicket)

        val sdf = SimpleDateFormat("dd/MM/yyyy   HH:mm", Locale.getDefault())
        tvFechaHora.text = "Fecha: ${sdf.format(Date())}"

        llProductos.removeAllViews()
        for (item in itemsVenta) {
            val tv = TextView(this)
            val cant = item.cantidad.toString().padStart(3)
            val desc = (item.producto?.nombre ?: "Producto").padEnd(12).take(12)
            val precio = String.format(Locale.US, "%.2f", item.producto?.precioVenta ?: 0.0).padStart(6)
            val importe = String.format(Locale.US, "%.2f", item.subtotal).padStart(6)

            tv.text = "$cant| $desc |$precio|$importe"
            tv.typeface = android.graphics.Typeface.MONOSPACE
            tv.textSize = 12f
            tv.setTextColor(android.graphics.Color.BLACK)
            llProductos.addView(tv)
        }

        tvTicketTotal.text    = "TOTAL: ${String.format(Locale.US, "$%.2f", total)}"
        tvTicketRecibido.text = "RECIBIDO: ${String.format(Locale.US, "$%.2f", recibido)}"
        tvTicketCambio.text   = "CAMBIO: ${String.format(Locale.US, "$%.2f", cambio)}"

        // --- CONFIGURACIÓN DEL SPINNER DE CLIENTES ---
        // Cargamos los clientes con la función que modificaste en el paso anterior
        val listaClientes = vm.db.getAllClientes()

        // Creamos la lista visual para el Spinner. La primera opción será manual.
        val opcionesSpinner = mutableListOf("Ingresar número manualmente...")
        opcionesSpinner.addAll(listaClientes.map {
            "${it.nombre} ${if(it.telefono.isNotEmpty()) "(Tel: ${it.telefono})" else "(Sin tel)"}"
        })

        val adapterSpinner = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, opcionesSpinner)
        spinnerClientes.adapter = adapterSpinner

        btnWhatsApp.isEnabled = false
        btnWhatsApp.text = "COBRA PARA ENVIAR TICKET"

        btnWhatsApp.setOnClickListener {
            val posicion = spinnerClientes.selectedItemPosition
            if (posicion == 0) {
                // Eligieron "Ingresar número manualmente..." -> Abre el cuadro de texto
                solicitarNumeroParaTicket(mensajeTicketListo)
            } else {
                // Eligieron un cliente de la lista. Restamos 1 por la opción manual inicial.
                val clienteSeleccionado = listaClientes[posicion - 1]
                if (clienteSeleccionado.telefono.isNotEmpty()) {
                    // Envia directo porque ya tiene teléfono guardado
                    enviarTicketPorWhatsAppTexto(clienteSeleccionado.telefono, mensajeTicketListo)
                } else {
                    Toast.makeText(this, "Este cliente no tiene teléfono. Ingresa uno.", Toast.LENGTH_SHORT).show()
                    solicitarNumeroParaTicket(mensajeTicketListo)
                }
            }
        }

        val dialog = AlertDialog.Builder(this)
            .setView(ticketView)
            .setPositiveButton("Confirmar", null)
            .setNeutralButton("Fiado") { _, _ -> mostrarDialogoFiado() }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        val btnConfirmar = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val btnFiado     = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        val btnCancelar  = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

        btnConfirmar.setOnClickListener {
            ejecutarVenta()

            btnWhatsApp.isEnabled = true
            btnWhatsApp.text = "ENVIAR POR WHATSAPP"

            btnConfirmar.isEnabled = false
            btnFiado.visibility = View.GONE
            btnCancelar.text = "Cerrar"
        }
    }

    private fun ejecutarVenta() {
        val idVenta = vm.db.registrarVenta(itemsVenta)
        if (idVenta > 0) {
            Toast.makeText(this, "✓ Venta registrada #$idVenta", Toast.LENGTH_LONG).show()
            itemsVenta.clear()
            etRecibido.text.clear()
            adapter.notifyDataSetChanged()
            actualizarTotal()
        } else {
            Toast.makeText(this, "Error al registrar la venta", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDialogoFiado() {
        val db = vm.db.readableDatabase
        val cursor = db.rawQuery("SELECT nombre FROM cliente ORDER BY nombre ASC", null)
        val clientes = mutableListOf<String>()

        while (cursor.moveToNext()) {
            clientes.add(cursor.getString(0))
        }
        cursor.close()

        // Si aún no hay clientes en la base de datos, abrimos directamente la ventana para crear uno
        if (clientes.isEmpty()) {
            mostrarDialogoNuevoCliente()
            return
        }

        val arregloClientes = clientes.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Seleccionar Cliente (Fiado)")
            .setItems(arregloClientes) { _, which ->
                // Si el usuario selecciona un cliente de la lista, registramos el fiado
                val nombreSeleccionado = arregloClientes[which]
                registrarFiado(nombreSeleccionado)
            }
            .setPositiveButton("Nuevo Cliente") { _, _ ->
                // Botón extra por si el cliente al que se le fía no está en la lista
                mostrarDialogoNuevoCliente()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun mostrarDialogoNuevoCliente() {
        val input = EditText(this).apply { hint = "Nombre del cliente" }
        AlertDialog.Builder(this)
            .setTitle("Nuevo cliente a fiado")
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
        val idCliente: Long
        val cursorCliente = db.rawQuery(
            "SELECT id_cliente FROM cliente WHERE nombre = ? LIMIT 1", arrayOf(nombreCliente)
        )
        idCliente = if (cursorCliente.moveToFirst()) cursorCliente.getLong(0)
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
        etRecibido.text.clear()
        adapter.notifyDataSetChanged()
        actualizarTotal()
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  PDF / WHATSAPP
    // ─────────────────────────────────────────────────────────────────────────

    // Prueba de si funciona con wasap


    //-----------------------------------------------------------------------------------------------------------------------------------------------------------
    private fun solicitarNumeroParaTicket(mensajeTicket: String) {
        val campoTelefono = EditText(this).apply {
            hint = "Ej. 9511234567"
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val contenedorDialogo = FrameLayout(this)
        val margen = (16 * resources.displayMetrics.density).toInt()
        contenedorDialogo.setPadding(margen, margen, margen, margen)
        contenedorDialogo.addView(campoTelefono)

        AlertDialog.Builder(this)
            .setTitle("Enviar Ticket por WhatsApp")
            .setMessage("Ingresa el número celular de 10 dígitos:")
            .setView(contenedorDialogo)
            .setPositiveButton("Enviar") { _, _ ->
                val telefonoIngresado = campoTelefono.text.toString().trim()
                if (telefonoIngresado.isEmpty()) {
                    Toast.makeText(this, "Por favor ingresa un número", Toast.LENGTH_SHORT).show()
                } else {
                    enviarTicketPorWhatsAppTexto(telefonoIngresado, mensajeTicket)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }



    private fun construirTextoTicket(totalVenta: Double, recibidoVenta: Double, cambioVenta: Double): String {
        val fechaHoraActual = SimpleDateFormat("dd/MM/yyyy   'Hora:' HH:mm", Locale.getDefault()).format(Date())

        val sbProductos = StringBuilder()
        for (item in itemsVenta) {
            val nombreSeguro = item.producto?.nombre ?: "Producto"
            val desc = nombreSeguro.padEnd(20).take(20)
            val precioSeguro = item.producto?.precioVenta ?: 0.0
            val pu = String.format(Locale.US, "$%.2f", precioSeguro).padStart(6)
            val imp = String.format(Locale.US, "$%.2f", item.subtotal).padStart(7)
            sbProductos.append(" ${item.cantidad.toString().padStart(2)}    | $desc | $pu | $imp\n")
        }

        val strTotal = String.format(Locale.US, "$%.2f", totalVenta)
        val strRecibido = String.format(Locale.US, "$%.2f", recibidoVenta)
        val strCambio = String.format(Locale.US, "$%.2f", cambioVenta)

        return """
==================================================
                 ABARROTES PÉREZ
==================================================
Propietario: Saúl Pérez Martínez
Mercado Venustiano Carranza, Local 23
Colonia Miguel Alemán Valdéz
Oaxaca de Juárez, Oax., C.P. 68120

Fecha: $fechaHoraActual
Folio de Venta: #VENTA
Atendido por: Saúl Pérez

--------------------------------------------------
CANT. | DESCRIPCIÓN          | P.U.   | IMPORTE
--------------------------------------------------
$sbProductos--------------------------------------------------
SUBTOTAL:                             $strTotal
TOTAL A PAGAR:                        $strTotal

==================================================
               DETALLE DE PAGO
==================================================
MÉTODO DE PAGO: EFECTIVO

Total Recibido:                       $strRecibido
Cambio Entregado:                     $strCambio
==================================================

¡Gracias por su preferencia!
Vuelva pronto a Abarrotes Pérez.
==================================================
""".trimIndent()
    }


    private fun enviarTicketPorWhatsAppTexto(telefono10Digitos: String, textoTicket: String) {
        val telefonoLimpio = telefono10Digitos.replace(Regex("[^\\d]"), "")

        if (telefonoLimpio.length != 10) {
            Toast.makeText(this, "Ingresa un número celular válido de 10 dígitos", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val numeroConCodigo = "52$telefonoLimpio"
            val textoCodificado = java.net.URLEncoder.encode(textoTicket, "UTF-8")
            val uri = android.net.Uri.parse("https://api.whatsapp.com/send?phone=$numeroConCodigo&text=$textoCodificado")
            val intent = Intent(Intent.ACTION_VIEW, uri)

            try {
                intent.setPackage("com.whatsapp")
                startActivity(intent)
            } catch (e1: android.content.ActivityNotFoundException) {
                try {
                    intent.setPackage("com.whatsapp.w4b")
                    startActivity(intent)
                } catch (e2: android.content.ActivityNotFoundException) {
                    intent.setPackage(null)
                    startActivity(intent)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al abrir WhatsApp: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }


    //-----------------------------------------------------------------------------------------------------------------------------------------------------------


    // ─────────────────────────────────────────────────────────────────────────
    //  HID (dispatch a HidCapturador)
    // ─────────────────────────────────────────────────────────────────────────
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hid.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    /** Si el lector escanea mientras hay un EditText enfocado, borra los caracteres que
     *  alcanzaron a colarse en el campo (mismo comportamiento que Inventario). */
    private fun removerBasuraDelFoco(codigoLeido: String) {
        val view = currentFocus
        if (view is EditText) {
            var textoActual = view.text.toString()
            for (i in minOf(codigoLeido.length, 5) downTo 1) {
                val pedazoFiltrado = codigoLeido.substring(0, i)
                if (textoActual.endsWith(pedazoFiltrado)) {
                    textoActual = textoActual.substring(0, textoActual.length - i)
                    view.setText(textoActual)
                    view.setSelection(textoActual.length)
                    break
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Adapter
    // ─────────────────────────────────────────────────────────────────────────

        inner class VentaAdapter(
        private val items: MutableList<DatabaseHelper.ItemVenta>,
        private val onChange: () -> Unit
    ) : RecyclerView.Adapter<VentaAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvNombre: TextView    = view.findViewById(R.id.tvNombreProducto)
            val tvPrecio: TextView    = view.findViewById(R.id.tvPrecioUnitario)
            val tvCantidad: TextView  = view.findViewById(R.id.tvCantidad)
            val tvSubtotal: TextView  = view.findViewById(R.id.tvSubtotal)
            val btnMas: ImageButton   = view.findViewById(R.id.btnMas)
            val btnMenos: Button      = view.findViewById(R.id.btnMenos)
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