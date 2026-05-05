package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider

import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.example.ing_software_abarrotezperez.viewmodel.ScannerViewModel

class InventarioActivity : AppCompatActivity() {

    private lateinit var vm: ScannerViewModel

    // Vistas
    private lateinit var tvEstadoBt: TextView
    private lateinit var etCodigoProducto: EditText
    private lateinit var actvNombre: AutoCompleteTextView
    private lateinit var etDescripcion: EditText
    private lateinit var etPrecioVenta: EditText
    private lateinit var tvStock: TextView
    private lateinit var etCaducidad: EditText
    private lateinit var cbNoPerecedero: CheckBox
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var layoutCaducidad: View

    private var codigoActual: String = ""
    private var stockActual: Int = 0

    // Variables para el interceptor HID
    private val barcodeBuffer = StringBuilder()
    private var lastKeyTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.inventario)

        vm = ViewModelProvider(this)[ScannerViewModel::class.java]

        // Bind vistas
        tvEstadoBt       = findViewById(R.id.tvEstadoBtInv)
        etCodigoProducto = findViewById(R.id.etCodigoProducto)
        actvNombre       = findViewById(R.id.actvNombre)
        etDescripcion    = findViewById(R.id.etDescripcion)
        etPrecioVenta    = findViewById(R.id.etPrecioVenta)
        tvStock          = findViewById(R.id.tvStock)
        etCaducidad      = findViewById(R.id.etCaducidad)
        cbNoPerecedero   = findViewById(R.id.cbNoPerecedero)
        layoutCaducidad  = findViewById(R.id.layoutCaducidad)
        btnGuardar       = findViewById(R.id.btnGuardarProducto)
        btnLimpiar       = findViewById(R.id.btnLimpiar)

        configurarSugerenciasNombres()
        configurarFormatoFecha()

        cbNoPerecedero.setOnCheckedChangeListener { _, checked ->
            layoutCaducidad.visibility = if (checked) View.GONE else View.VISIBLE
            if (checked) etCaducidad.setText("")
        }

        btnGuardar.setOnClickListener { guardarProducto() }
        btnLimpiar.setOnClickListener { limpiarFormulario() }
    }

    private fun configurarFormatoFecha() {
        etCaducidad.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true

                val cleanString = s.toString().replace(Regex("[^\\d]"), "")
                var formattedDate = ""
                var isValidDate = true

                if (cleanString.isNotEmpty()) {
                    val dayStr = cleanString.take(2)
                    formattedDate += dayStr
                    if (dayStr.length == 2) {
                        val day = dayStr.toInt()
                        if (day < 1 || day > 31) isValidDate = false
                    }

                    if (cleanString.length > 2) {
                        formattedDate += "/"
                        val monthStr = cleanString.substring(2, minOf(4, cleanString.length))
                        formattedDate += monthStr
                        if (monthStr.length == 2) {
                            val month = monthStr.toInt()
                            if (month < 1 || month > 12) isValidDate = false
                        }

                        if (cleanString.length > 4) {
                            formattedDate += "/"
                            formattedDate += cleanString.substring(4, minOf(6, cleanString.length))
                        }
                    }
                }

                etCaducidad.setText(formattedDate)
                etCaducidad.setSelection(formattedDate.length)

                if (!isValidDate) {
                    etCaducidad.setTextColor(Color.RED)
                    vibrarError()
                } else {
                    etCaducidad.setTextColor(Color.BLACK)
                }

                isUpdating = false
            }
        })
    }

    private fun vibrarError() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(300)
        }
    }

    private fun configurarSugerenciasNombres() {
        val productos = vm.db.getAllProductos()
        val nombresExistentes = productos.map { it.nombre }.distinct()

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            nombresExistentes
        )
        actvNombre.setAdapter(adapter)
    }

    // ── LÓGICA DE BLOQUEO DE CAMPOS ──────────────────────────────────────────
    private fun habilitarCamposModoNuevo(esNuevo: Boolean) {
        actvNombre.isEnabled = esNuevo
        etPrecioVenta.isEnabled = esNuevo
        cbNoPerecedero.isEnabled = esNuevo

        // La descripción y la caducidad SIEMPRE se pueden editar
        etDescripcion.isEnabled = true
        etCaducidad.isEnabled = true
    }

    private fun procesarEscaneoInventario(codigo: String) {
        codigoActual = codigo
        etCodigoProducto.setText(codigo)

        val existente = vm.db.getProductoPorCodigo(codigo)
        if (existente != null) {
            // EL PRODUCTO YA EXISTE
            stockActual = existente.stock + 1

            actvNombre.setText(existente.nombre)
            etDescripcion.setText(existente.descripcion)
            etPrecioVenta.setText(existente.precioVenta.toString())
            tvStock.text = "Cantidad en Stock: $stockActual"

            if (existente.fechaCaducidad.isNullOrEmpty()) {
                cbNoPerecedero.isChecked = true
            } else {
                cbNoPerecedero.isChecked = false
                etCaducidad.setText(existente.fechaCaducidad)
            }

            // Bloqueamos los datos maestros para no modificarlos por error
            habilitarCamposModoNuevo(false)

            Toast.makeText(this, "Producto conocido. Agregando lote (Stock: $stockActual)", Toast.LENGTH_SHORT).show()
        } else {
            // ES UN PRODUCTO NUEVO
            limpiarSoloCampos()
            stockActual = 1
            tvStock.text = "Cantidad en Stock: $stockActual"

            // Habilitamos todo para que pueda registrarlo
            habilitarCamposModoNuevo(true)
            Toast.makeText(this, "Producto nuevo. Registre los datos.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun guardarProducto() {
        if (codigoActual.isEmpty()) {
            Toast.makeText(this, "Escanea un código primero", Toast.LENGTH_SHORT).show()
            return
        }

        val nombre = actvNombre.text.toString().trim()
        if (nombre.isEmpty()) {
            actvNombre.error = "Nombre requerido"
            return
        }

        if (etCaducidad.currentTextColor == Color.RED) {
            Toast.makeText(this, "Por favor, ingresa una fecha válida", Toast.LENGTH_SHORT).show()
            vibrarError()
            return
        }

        val precio = etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
        val caducidad = if (cbNoPerecedero.isChecked) null else etCaducidad.text.toString().trim().ifEmpty { null }

        val producto = DatabaseHelper.Producto(
            codigoBarras   = codigoActual,
            nombre         = nombre,
            descripcion    = etDescripcion.text.toString().trim(),
            precioVenta    = precio,
            stock          = stockActual,
            fechaCaducidad = caducidad
        )

        val result = vm.db.upsertProducto(producto)
        if (result >= 0) {
            Toast.makeText(this, "✓ Guardado exitosamente", Toast.LENGTH_SHORT).show()
            configurarSugerenciasNombres()
            limpiarFormulario()
        } else {
            Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show()
        }
    }

    private fun limpiarFormulario() {
        codigoActual = ""
        etCodigoProducto.setText("")
        limpiarSoloCampos()
        habilitarCamposModoNuevo(true) // Al limpiar, preparamos para un posible producto nuevo
    }

    private fun limpiarSoloCampos() {
        stockActual = 0
        tvStock.text = "Cantidad en Stock: 0"
        actvNombre.setText("")
        etDescripcion.setText("")
        etPrecioVenta.setText("")
        etCaducidad.setText("")
        cbNoPerecedero.isChecked = false
        layoutCaducidad.visibility = View.VISIBLE
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  INTERCEPTOR MODO HID (Teclado)
    // ─────────────────────────────────────────────────────────────────────────
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val isHardwareDevice = event.device != null && !event.device.isVirtual

        if (event.action == KeyEvent.ACTION_DOWN) {
            val timeNow = System.currentTimeMillis()

            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val codigoLeido = barcodeBuffer.toString().trim()
                if (codigoLeido.isNotEmpty()) {
                    procesarEscaneoInventario(codigoLeido)
                    barcodeBuffer.clear()
                    return true
                }
            }

            val pressedChar = event.unicodeChar.toChar()
            if (pressedChar.isDefined() && event.unicodeChar > 31) {
                if (timeNow - lastKeyTime > 200) {
                    barcodeBuffer.clear()
                }
                barcodeBuffer.append(pressedChar)
                lastKeyTime = timeNow

                if (isHardwareDevice) {
                    return true
                }
            }
        } else if (event.action == KeyEvent.ACTION_UP) {
            if (isHardwareDevice && (event.unicodeChar > 31 || event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }
}