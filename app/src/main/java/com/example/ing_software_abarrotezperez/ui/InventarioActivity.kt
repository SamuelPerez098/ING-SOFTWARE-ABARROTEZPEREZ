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
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.lifecycle.ViewModelProvider
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.example.ing_software_abarrotezperez.viewmodel.ScannerViewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter

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
    private lateinit var spTipo: Spinner
    private lateinit var btnGuardar: Button
    private lateinit var btnLimpiar: Button
    private lateinit var ivBarcode: ImageView
    private lateinit var btnVerLista: CardView // NUEVA VARIABLE PARA EL BOTÓN

    private var codigoActual: String = ""

    // Variables para el interceptor HID
    private val barcodeBuffer = StringBuilder()

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
        spTipo           = findViewById(R.id.spTipo)
        btnGuardar       = findViewById(R.id.btnGuardarProducto)
        btnLimpiar       = findViewById(R.id.btnLimpiar)
        btnVerLista      = findViewById(R.id.btnVerLista)

        configurarSugerenciasNombres()
        configurarFormatoFecha()
        configurarSpinnerTipo()

        btnGuardar.setOnClickListener { guardarProducto() }
        btnLimpiar.setOnClickListener { limpiarFormulario() }

        // EVENTO DEL NUEVO BOTÓN
        btnVerLista.setOnClickListener {
            val intent = Intent(this, ListaInventarioActivity::class.java)
            startActivity(intent)
        }

        // Bloquear stock por defecto hasta que se elija "No Perecedero"
        etStock.isEnabled = false

        // --- AQUÍ ES EL LUGAR CORRECTO ---
        // Revisamos si venimos de la lista con la intención de editar
        val codigoAEditar = intent.getStringExtra("CODIGO_A_EDITAR")
        if (!codigoAEditar.isNullOrEmpty()) {
            // Simulamos que el usuario escaneó este código para que se llene el formulario
            procesarEscaneoInventario(codigoAEditar)
        }
    }

    private fun configurarSpinnerTipo() {
        val opciones = arrayOf("Perecedero", "No Perecedero")

        val adapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, opciones) {
            override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.BLACK)
                view.setBackgroundColor(Color.WHITE)
                return view
            }
        }

        spTipo.adapter = adapter

        spTipo.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 1) {
                    etCaducidad.isEnabled = false
                    etCaducidad.setText("")
                    etStock.isEnabled = true
                    Toast.makeText(this@InventarioActivity, "Stock manual habilitado", Toast.LENGTH_SHORT).show()
                } else {
                    etCaducidad.isEnabled = true
                    etStock.isEnabled = false
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
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
                            if (digit >= '4') newClean += "0$digit"
                            else newClean += digit
                        } else if (i == 2 && cleanString.length == 3) {
                            if (digit >= '2') newClean += "0$digit"
                            else newClean += digit
                        } else {
                            newClean += digit
                        }
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
                    if (cleanString.length == 2 && !formattedDate.endsWith("/")) {
                        formattedDate += "/"
                    } else if (cleanString.length == 4 && !formattedDate.endsWith("/")) {
                        formattedDate += "/"
                    }
                }

                etCaducidad.setText(formattedDate)
                etCaducidad.setSelection(formattedDate.length)

                if (!isValidDate) {
                    etCaducidad.setTextColor(Color.RED)
                    if(formattedDate.length == 8) vibrarError()
                } else {
                    etCaducidad.setTextColor(Color.WHITE)
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

    private fun habilitarCamposModoNuevo(esNuevo: Boolean) {
        actvNombre.isEnabled = esNuevo
        etPrecioVenta.isEnabled = esNuevo
        etPrecioCompra.isEnabled = esNuevo
        spTipo.isEnabled = esNuevo
        etDescripcion.isEnabled = true
    }

    private fun procesarEscaneoInventario(codigo: String) {
        codigoActual = codigo
        tvCodigoProducto.text = codigo
        generarCodigoBarrasImagen(codigo)

        val existente = vm.db.getProductoPorCodigo(codigo)
        if (existente != null) {
            val stockNuevo = existente.stock + 1
            etStock.setText(stockNuevo.toString())

            actvNombre.setText(existente.nombre)
            etDescripcion.setText(existente.descripcion)
            etPrecioVenta.setText(existente.precioVenta.toString())
            etPrecioCompra.setText(existente.precioCompra.toString())

            if (existente.fechaCaducidad.isNullOrEmpty()) {
                spTipo.setSelection(1) // No Perecedero
            } else {
                spTipo.setSelection(0) // Perecedero
                etCaducidad.setText(existente.fechaCaducidad)
            }

            habilitarCamposModoNuevo(false)
            Toast.makeText(this, "Producto conocido. Agregando lote (Stock: $stockNuevo)", Toast.LENGTH_SHORT).show()
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
        if (nombre.isEmpty()) {
            actvNombre.error = "Nombre requerido"
            return
        }

        val precioV = etPrecioVenta.text.toString().toDoubleOrNull() ?: 0.0
        if (precioV <= 0) {
            etPrecioVenta.error = "Precio inválido"
            return
        }

        val precioC = etPrecioCompra.text.toString().toDoubleOrNull() ?: 0.0
        if (precioC <= 0) {
            etPrecioCompra.error = "Precio de compra requerido"
            return
        }
        if (precioC >= precioV) {
            Toast.makeText(this, "Advertencia: El precio de compra es mayor o igual al de venta", Toast.LENGTH_LONG).show()
        }

        val esPerecedero = spTipo.selectedItemPosition == 0
        var caducidad: String? = null
        var stockGuardar = 0

        if (esPerecedero) {
            val textoCaducidad = etCaducidad.text.toString().trim()
            if (textoCaducidad.length != 8 || etCaducidad.currentTextColor == Color.RED) {
                Toast.makeText(this, "Para Perecederos, caducidad obligatoria", Toast.LENGTH_SHORT).show()
                vibrarError()
                return
            }
            caducidad = textoCaducidad
            stockGuardar = etStock.text.toString().toIntOrNull() ?: 0
        } else {
            stockGuardar = etStock.text.toString().toIntOrNull() ?: 0
            if (stockGuardar <= 0) {
                etStock.error = "Stock inicial requerido"
                return
            }
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
        spTipo.setSelection(0)
    }

    // SOLUCIÓN: Separar lógicamente teclado en pantalla vs Scanner Físico
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 1. Si la entrada viene del Teclado Virtual (en pantalla), dejamos que funcione normal.
        if (event.deviceId == -1 || event.device?.isVirtual == true) {
            return super.dispatchKeyEvent(event)
        }

        // 2. Si la entrada viene de Hardware (Escáner Bluetooth), la atrapamos.
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Cuando el lector manda el 'Enter' (fin del código de barras)
            if (event.keyCode == KeyEvent.KEYCODE_ENTER) {
                val codigoLeido = barcodeBuffer.toString().trim()
                if (codigoLeido.isNotEmpty()) {
                    procesarEscaneoInventario(codigoLeido)
                }
                barcodeBuffer.setLength(0)
                return true // Evita que el ENTER haga cosas extrañas en la pantalla
            }

            // Atrapamos cualquier letra o número del código
            if (event.unicodeChar > 31) {
                barcodeBuffer.append(event.unicodeChar.toChar())
                return true // Esto EVITA que el número se escriba en los EditText
            }
        }
        // Dejar pasar botones físicos nativos (Volumen, Retroceso, etc.)
        return super.dispatchKeyEvent(event)
    }

    // --- PEGAR ESTO AL FINAL DEL onCreate EN InventarioActivity ---
    // Revisamos si venimos de la lista con la intención de editar

}