package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.example.ing_software_abarrotezperez.scanner.CamaraScannerActivity
import com.example.ing_software_abarrotezperez.scanner.HidCapturador
import com.example.ing_software_abarrotezperez.viewmodel.ScannerViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import java.util.Locale
import kotlin.math.roundToInt

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
    private lateinit var divisorCaducidad: View
    private lateinit var cardStockCaducidad: CardView
    private lateinit var tvMargen: TextView
    private lateinit var rgTipo: RadioGroup
    private lateinit var rbPerecedero: RadioButton
    private lateinit var rbNoPerecedero: RadioButton
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: ImageView
    private lateinit var ivBarcode: ImageView
    private lateinit var btnVerLista: LinearLayout
    private lateinit var btnCamaraScanner: ImageView

    private var codigoActual: String = ""

    // Resultado del escáner por cámara → misma lógica que el lector físico (suma 1 al stock)
    private val lanzadorCamara =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
            if (resultado.resultCode == RESULT_OK) {
                val codigo = resultado.data
                    ?.getStringExtra(CamaraScannerActivity.EXTRA_CODIGO)
                    ?.trim()
                if (!codigo.isNullOrEmpty()) procesarEscaneoInventario(codigo, false)
            }
        }

    // --- Lector en modo teclado (HID): Bluetooth o cable USB, misma lógica ---
    private val hid = HidCapturador(consumirTodo = false, ignorarTecladoVirtual = true) { codigo ->
        removerBasuraDelFoco(codigo)
        procesarEscaneoInventario(codigo, false)
    }

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
        divisorCaducidad = findViewById(R.id.divisorCaducidad)
        cardStockCaducidad = findViewById(R.id.cardStockCaducidad)
        tvMargen         = findViewById(R.id.tvMargen)
        rgTipo           = findViewById(R.id.rgTipo)
        rbPerecedero     = findViewById(R.id.rbPerecedero)
        rbNoPerecedero   = findViewById(R.id.rbNoPerecedero)
        btnGuardar       = findViewById(R.id.btnGuardarProducto)
        btnLimpiar       = findViewById(R.id.btnLimpiar)
        btnVerLista      = findViewById<LinearLayout>(R.id.btnVerLista)
        btnCamaraScanner = findViewById(R.id.btnCamaraScanner)

        configurarSugerenciasNombres()
        configurarFormatoFecha()
        configurarRadioGroupTipo()
        configurarMargen()

        btnGuardar.setOnClickListener { guardarProducto() }
        btnLimpiar.setOnClickListener { limpiarFormulario() }

        // Modo cámara: abre el escáner con la cámara del teléfono
        btnCamaraScanner.setOnClickListener {
            lanzadorCamara.launch(Intent(this, CamaraScannerActivity::class.java))
        }

        btnVerLista.setOnClickListener {
            val intent = Intent(this, ListaInventarioActivity::class.java)
            startActivity(intent)
        }

        // Recuperar la bandera de solo editar
        val codigoAEditar = intent.getStringExtra("CODIGO_A_EDITAR")
        val soloEditar = intent.getBooleanExtra("SOLO_EDITAR", false)

        if (!codigoAEditar.isNullOrEmpty()) {
            procesarEscaneoInventario(codigoAEditar, soloEditar)
        }
    }

    private fun configurarRadioGroupTipo() {
        // Estado inicial: Perecedero (según el XML, rbPerecedero está checked)
        mostrarCaducidad(true)

        rgTipo.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rbNoPerecedero) {
                // No perecedero: se oculta la caducidad y el stock ocupa todo el ancho
                mostrarCaducidad(false)
                etCaducidad.setText("")
            } else {
                // Perecedero: aparece la caducidad junto al stock
                mostrarCaducidad(true)
            }
            // El stock se puede escribir en ambos tipos
        }
    }

    private fun mostrarCaducidad(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        layoutCaducidad.visibility = v
        divisorCaducidad.visibility = v

        // La tarjeta blanca ocupa todo el ancho con caducidad (weight 2) y se encoge
        // a la mitad sin ella (weight 1), alineada a la izquierda como el stock.
        val lp = cardStockCaducidad.layoutParams as LinearLayout.LayoutParams
        lp.weight = if (visible) 2f else 1f
        cardStockCaducidad.layoutParams = lp
    }

    // ── Precios: lectura segura y ganancia en vivo ──────────────────────────
    /** Lee un precio aceptando punto o coma decimal. Devuelve null si está vacío o es inválido. */
    private fun leerPrecio(campo: EditText): Double? =
        campo.text.toString().trim().replace(',', '.').toDoubleOrNull()

    private fun configurarMargen() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = actualizarMargen()
        }
        etPrecioCompra.addTextChangedListener(watcher)
        etPrecioVenta.addTextChangedListener(watcher)
        actualizarMargen()
    }

    private fun actualizarMargen() {
        val compra = leerPrecio(etPrecioCompra)
        val venta = leerPrecio(etPrecioVenta)
        when {
            compra == null || venta == null || compra <= 0.0 || venta <= 0.0 -> {
                tvMargen.text = "Ganancia: —"
                tvMargen.setTextColor(Color.parseColor("#9E9E9E"))
            }
            compra >= venta -> {
                tvMargen.text = "⚠ La compra debe ser menor que la venta"
                tvMargen.setTextColor(Color.parseColor("#C62828"))
            }
            else -> {
                val ganancia = venta - compra
                val porcentaje = (ganancia / compra * 100).roundToInt()
                tvMargen.text = String.format(Locale.US, "Ganancia: $%.2f  (+%d%%)", ganancia, porcentaje)
                tvMargen.setTextColor(Color.parseColor("#2E7D32"))
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
        etCaducidad.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            private var oldText = ""
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isUpdating) oldText = s.toString()
            }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (isUpdating) return
                isUpdating = true
                val str = s.toString()
                val isDeleting = str.length < oldText.length
                var cleanString = str.replace(Regex("[^\\d]"), "")
                if (!isDeleting) {
                    var newClean = ""
                    for (i in cleanString.indices) {
                        val digit = cleanString[i]
                        if (i == 0 && cleanString.length == 1) {
                            if (digit >= '4') newClean += "0$digit" else newClean += digit
                        } else if (i == 2 && cleanString.length == 3) {
                            if (digit >= '2') newClean += "0$digit" else newClean += digit
                        } else newClean += digit
                    }
                    cleanString = newClean
                }
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
                if (!isDeleting) {
                    if (cleanString.length == 2 && !formattedDate.endsWith("/")) formattedDate += "/"
                    else if (cleanString.length == 4 && !formattedDate.endsWith("/")) formattedDate += "/"
                }
                etCaducidad.setText(formattedDate)
                etCaducidad.setSelection(formattedDate.length)
                if (!isValidDate) {
                    etCaducidad.setTextColor(Color.RED)
                    if (formattedDate.length == 8) vibrarError()
                } else etCaducidad.setTextColor(Color.parseColor("#2B1B14"))
                isUpdating = false
            }
        })
    }

    private fun vibrarError() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE))
        } else vibrator.vibrate(300)
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
        rgTipo.isEnabled = true
        etDescripcion.isEnabled = true
    }

    private fun procesarEscaneoInventario(codigo: String, esSoloEdicion: Boolean) {
        codigoActual = codigo
        tvCodigoProducto.text = codigo
        generarCodigoBarrasImagen(codigo)

        val existente = vm.db.getProductoPorCodigo(codigo)
        if (existente != null) {

            // Si es edición, mantenemos el stock. Si es escaneo, sumamos 1.
            val stockAMostrar = if (esSoloEdicion) existente.stock else existente.stock + 1
            etStock.setText(stockAMostrar.toString())

            actvNombre.setText(existente.nombre)
            etDescripcion.setText(existente.descripcion)
            etPrecioVenta.setText(existente.precioVenta.toString())
            etPrecioCompra.setText(existente.precioCompra.toString())

            if (existente.fechaCaducidad.isNullOrEmpty()) {
                rgTipo.check(R.id.rbNoPerecedero)
            } else {
                rgTipo.check(R.id.rbPerecedero)
                etCaducidad.setText(existente.fechaCaducidad)
            }

            habilitarCamposModoNuevo(false)

            if (esSoloEdicion) {
                Aviso.info(this, "Editando producto", existente.nombre)
            } else {
                Aviso.info(this, "Producto encontrado", "${existente.nombre} · stock $stockAMostrar")
            }
        } else {
            limpiarSoloCampos()
            etStock.setText("1")
            habilitarCamposModoNuevo(true)
            Aviso.info(this, "Producto nuevo", "Registra nombre, precios y stock.")
        }
    }

    private fun guardarProducto() {
        if (codigoActual.isEmpty()) {
            Aviso.advertencia(this, "Falta el código", "Escanea el producto antes de guardar.")
            return
        }
        val nombre = actvNombre.text.toString().trim()
        if (nombre.isEmpty()) { actvNombre.error = "Nombre requerido"; return }

        val precioV = leerPrecio(etPrecioVenta) ?: 0.0
        if (precioV <= 0) { etPrecioVenta.error = "Precio inválido"; return }

        val precioC = leerPrecio(etPrecioCompra) ?: 0.0
        if (precioC <= 0) { etPrecioCompra.error = "Precio de compra requerido"; return }

        if (precioC >= precioV) {
            Aviso.advertencia(this, "Precios inválidos", "Compra ${"$%.2f".format(precioC)} · Venta ${"$%.2f".format(precioV)}")
            etPrecioCompra.error = "Revisa los precios"
            vibrarError()
            return
        }

        val esPerecedero = rgTipo.checkedRadioButtonId == R.id.rbPerecedero
        var caducidad: String? = null
        var stockGuardar = etStock.text.toString().toIntOrNull() ?: 0

        if (esPerecedero) {
            val textoCaducidad = etCaducidad.text.toString().trim()
            if (textoCaducidad.length != 8 || etCaducidad.currentTextColor == Color.RED) {
                Aviso.advertencia(this, "Falta la caducidad", "Completa la fecha DDMMAAAA.")
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
            Aviso.exito(this, "Producto guardado", nombre)
            configurarSugerenciasNombres()
            limpiarFormulario()
        }
    }

    private fun limpiarFormulario() {
        codigoActual = ""
        tvCodigoProducto.text = "ESCANEAR..."
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
        rgTipo.check(R.id.rbPerecedero)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (hid.onKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        hid.detener()
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