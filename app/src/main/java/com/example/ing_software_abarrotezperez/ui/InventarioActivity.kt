package com.example.ing_software_abarrotezperez.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.KeyEvent
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.example.ing_software_abarrotezperez.scanner.CamaraScannerActivity
import com.example.ing_software_abarrotezperez.viewmodel.ScannerViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.Calendar
import java.util.Locale

class InventarioActivity : AppCompatActivity() {
    private lateinit var vm: ScannerViewModel

    // Vistas
    private lateinit var tvEstadoBt: TextView
    private lateinit var tvCodigoProducto: TextView
    private lateinit var actvNombre: AutoCompleteTextView
    private lateinit var etDescripcion: EditText
    private lateinit var etPrecioVenta: EditText
    private lateinit var etPrecioCompra: EditText
    private lateinit var etStock: EditText
    private lateinit var etCaducidad: EditText
    private lateinit var layoutCaducidad: LinearLayout
    private lateinit var rgTipo: RadioGroup
    private lateinit var rbPerecedero: RadioButton
    private lateinit var rbNoPerecedero: RadioButton
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: ImageButton
    private lateinit var ivBarcode: ImageView
    private lateinit var btnVerLista: LinearLayout
    private lateinit var btnCamaraScanner: ImageView

    private var codigoActual: String = ""

    private val lanzadorCamara =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            if (resultado.resultCode == RESULT_OK) {
                val codigo = resultado.data
                    ?.getStringExtra(CamaraScannerActivity.EXTRA_CODIGO)
                    ?.trim()
                if (!codigo.isNullOrEmpty()) procesarEscaneoInventario(codigo, false)
            }
        }

    // Variables para el escáner físico por velocidad
    private val barcodeBuffer = StringBuilder()
    private var lastKeyTime: Long = 0
    private val MAX_DELAY_SCANNER = 150L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.inventario)

        vm = ViewModelProvider(this)[ScannerViewModel::class.java]

        // Bind vistas
        tvEstadoBt       = findViewById(R.id.tvEstadoBtInv)
        tvCodigoProducto = findViewById(R.id.tvCodigoProducto)
        ivBarcode        = findViewById(R.id.ivBarcode)
        actvNombre       = findViewById(R.id.actvNombre)
        etDescripcion    = findViewById(R.id.etDescripcion)
        etPrecioVenta    = findViewById(R.id.etPrecioVenta)
        etPrecioCompra   = findViewById(R.id.etPrecioCompra)
        etStock          = findViewById(R.id.etStock)
        etCaducidad      = findViewById(R.id.etCaducidad)
        layoutCaducidad  = findViewById(R.id.layoutCaducidad)
        rgTipo           = findViewById(R.id.rgTipo)
        rbPerecedero     = findViewById(R.id.rbPerecedero)
        rbNoPerecedero   = findViewById(R.id.rbNoPerecedero)
        btnGuardar       = findViewById(R.id.btnGuardarProducto)
        btnLimpiar       = findViewById(R.id.btnLimpiar)
        btnVerLista      = findViewById(R.id.btnVerLista)
        btnCamaraScanner = findViewById(R.id.btnCamaraScanner)

        configurarSugerenciasNombres()
        configurarFormatoFecha()
        configurarRadioTipo()

        btnGuardar.setOnClickListener { guardarProducto() }
        btnLimpiar.setOnClickListener { limpiarFormulario() }

        btnCamaraScanner.setOnClickListener {
            lanzadorCamara.launch(Intent(this, CamaraScannerActivity::class.java))
        }

        btnVerLista.setOnClickListener {
            val intent = Intent(this, ListaInventarioActivity::class.java)
            startActivity(intent)
        }

        val codigoAEditar = intent.getStringExtra("CODIGO_A_EDITAR")
        val soloEditar = intent.getBooleanExtra("SOLO_EDITAR", false)

        if (!codigoAEditar.isNullOrEmpty()) {
            procesarEscaneoInventario(codigoAEditar, soloEditar)
        }
    }

    private fun configurarRadioTipo() {
        rgTipo.setOnCheckedChangeListener { _, checkedId ->
            etStock.isEnabled = true

            if (checkedId == R.id.rbNoPerecedero) {
                layoutCaducidad.visibility = View.GONE
                etCaducidad.setText("")
            } else {
                layoutCaducidad.visibility = View.VISIBLE
                etCaducidad.isEnabled = true
            }
        }
    }

    private fun generarCodigoBarrasImagen(codigo: String) {
        try {
            val bitMatrix = MultiFormatWriter().encode(codigo, BarcodeFormat.CODE_128, 600, 200)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            ivBarcode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            ivBarcode.setImageDrawable(null)
        }
    }

    private fun configurarFormatoFecha() {
        etCaducidad.setOnClickListener {
            val calendario = Calendar.getInstance()
            val anioActual = calendario.get(Calendar.YEAR)
            val mesActual = calendario.get(Calendar.MONTH)
            val diaActual = calendario.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    val diaFormateado = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                    val mesFormateado = String.format(Locale.getDefault(), "%02d", month + 1)
                    val anioCorto = String.format(Locale.getDefault(), "%02d", year % 100)

                    etCaducidad.setText("$diaFormateado/$mesFormateado/$anioCorto")
                    etCaducidad.setTextColor(Color.WHITE)
                },
                anioActual,
                mesActual,
                diaActual
            )

            datePicker.datePicker.minDate = calendario.timeInMillis
            datePicker.show()
        }
    }

    private fun vibrarError() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(300)
        }
    }

    private fun configurarSugerenciasNombres() {
        val productos = vm.db.getAllProductos()
        val nombresExistentes = productos.map { it.nombre }.distinct()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, nombresExistentes)
        actvNombre.setAdapter(adapter)
    }

    private fun habilitarCamposModoNuevo(esNuevo: Boolean) {
        actvNombre.isEnabled = true
        etPrecioVenta.isEnabled = true
        etPrecioCompra.isEnabled = true
        rbPerecedero.isEnabled = true
        rbNoPerecedero.isEnabled = true
        etDescripcion.isEnabled = true
        etStock.isEnabled = true
    }

    private fun procesarEscaneoInventario(codigo: String, esSoloEdicion: Boolean) {
        codigoActual = codigo
        tvCodigoProducto.text = codigo
        generarCodigoBarrasImagen(codigo)

        val existente = vm.db.getProductoPorCodigo(codigo)
        if (existente != null) {
            val stockAMostrar = if (esSoloEdicion) existente.stock else existente.stock + 1
            etStock.setText(stockAMostrar.toString())

            actvNombre.setText(existente.nombre)
            etDescripcion.setText(existente.descripcion)
            etPrecioVenta.setText(existente.precioVenta.toString())
            etPrecioCompra.setText(existente.precioCompra.toString())

            if (existente.fechaCaducidad.isNullOrEmpty()) {
                rbNoPerecedero.isChecked = true
                layoutCaducidad.visibility = View.GONE
            } else {
                rbPerecedero.isChecked = true
                layoutCaducidad.visibility = View.VISIBLE
                etCaducidad.setText(existente.fechaCaducidad)
            }

            habilitarCamposModoNuevo(false)

            if (esSoloEdicion) {
                Toast.makeText(this, "Editando: ${existente.nombre}", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Producto conocido. Stock: $stockAMostrar", Toast.LENGTH_SHORT).show()
            }
        } else {
            limpiarSoloCampos()
            etStock.setText("1")
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
        if (nombre.isEmpty()) { actvNombre.error = "Nombre requerido"; return }

        val precioV = etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
        if (precioV <= 0) { etPrecioVenta.error = "Precio inválido"; return }

        val precioC = etPrecioCompra.text.toString().toDoubleOrNull() ?: 0.0
        if (precioC <= 0) { etPrecioCompra.error = "Precio de compra requerido"; return }

        if (precioC >= precioV) {
            Toast.makeText(this, "Error: Compra no puede ser >= Venta", Toast.LENGTH_LONG).show()
            etPrecioCompra.error = "Revisa los precios"
            vibrarError()
            return
        }

        val esPerecedero = rbPerecedero.isChecked
        var caducidad: String? = null
        val stockGuardar = etStock.text.toString().toIntOrNull() ?: 0

        if (esPerecedero) {
            val textoCaducidad = etCaducidad.text.toString().trim()
            if (textoCaducidad.length != 8 || etCaducidad.currentTextColor == Color.RED) {
                Toast.makeText(this, "Caducidad obligatoria", Toast.LENGTH_SHORT).show()
                vibrarError()
                return
            }
            caducidad = textoCaducidad
        } else {
            if (stockGuardar < 0) { etStock.error = "Stock inválido"; return }
            caducidad = null
        }

        val producto = DatabaseHelper.Producto(
            codigoBarras   = codigoActual,
            nombre         = nombre,
            descripcion    = etDescripcion.text.toString().trim(),
            precioVenta    = precioV,
            precioCompra   = precioC,
            stock          = stockGuardar,
            fechaCaducidad = caducidad
        )

        if (vm.db.upsertProducto(producto) >= 0) {
            Toast.makeText(this, "✓ Guardado", Toast.LENGTH_SHORT).show()
            configurarSugerenciasNombres()
            limpiarFormulario()
        }
    }

    private fun limpiarFormulario() {
        codigoActual = ""
        tvCodigoProducto.text = ""
        ivBarcode.setImageDrawable(null)
        limpiarSoloCampos()
        habilitarCamposModoNuevo(true)
    }

    private fun limpiarSoloCampos() {
        etStock.setText("")
        actvNombre.setText("")
        etDescripcion.setText("")
        etPrecioVenta.setText("")
        etPrecioCompra.setText("")
        etCaducidad.setText("")
        rbPerecedero.isChecked = true
        layoutCaducidad.visibility = View.VISIBLE
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.deviceId == -1 || event.device?.isVirtual == true) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val currentTime = System.currentTimeMillis()
            val timeDiff = currentTime - lastKeyTime
            if (timeDiff > MAX_DELAY_SCANNER) barcodeBuffer.setLength(0)
            lastKeyTime = currentTime

            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val codigoLeido = barcodeBuffer.toString().trim()
                if (codigoLeido.isNotEmpty()) {
                    removerBasuraDelFoco(codigoLeido)
                    procesarEscaneoInventario(codigoLeido, false)
                    barcodeBuffer.setLength(0)
                    return true
                }
            }
            if (event.unicodeChar > 31) {
                barcodeBuffer.append(event.unicodeChar.toChar())
                if (barcodeBuffer.length >= 3 && timeDiff <= MAX_DELAY_SCANNER) return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

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
}