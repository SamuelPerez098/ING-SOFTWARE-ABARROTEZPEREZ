package com.example.ing_software_abarrotezperez.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.animation.ObjectAnimator
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.AxisBase
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter

import android.content.res.ColorStateList
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ReportesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    private lateinit var tvTituloReportes: TextView
    private lateinit var tvGananciaDia: TextView
    private lateinit var tvVentaDia: TextView
    private lateinit var rvCaducidad: RecyclerView
    private lateinit var lineChart: LineChart

    private lateinit var btnCampanita: ImageButton
    private var animadorCampana: ObjectAnimator? = null

    // Fecha actual (YYYY-MM-DD) para los reportes del día
    private var fechaFiltro: String = ""

    private lateinit var tvFechaReporte: TextView
    private lateinit var btnSeleccionarDia: MaterialButton
    private lateinit var tvTituloTabla: TextView
    private lateinit var tvColumnaDerecha: TextView
    private lateinit var btnModoCaducidad: MaterialButton
    private lateinit var btnModoStock: MaterialButton
    private lateinit var llGraficaVacia: View
    private lateinit var llTablaVacia: View
    private lateinit var tvTextoVacio: TextView

    private enum class ModoTabla { CADUCIDAD, STOCK }
    private var modoTabla = ModoTabla.CADUCIDAD
    private var listaCaducidad: List<DatabaseHelper.Producto> = emptyList()
    private var listaBajoStock: List<DatabaseHelper.Producto> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reportes)

        dbHelper = DatabaseHelper(this)

        tvTituloReportes = findViewById(R.id.tvTituloReportes)
        tvGananciaDia = findViewById(R.id.tvGananciaDia)
        tvVentaDia = findViewById(R.id.tvVentaDia)
        rvCaducidad = findViewById(R.id.rvCaducidad)
        lineChart = findViewById(R.id.lineChartGanancias)

        btnCampanita = findViewById(R.id.btnCampanita)
        val btnRegresar = findViewById<ImageView>(R.id.btnRegresarReportes)

        tvFechaReporte = findViewById(R.id.tvFechaReporte)
        btnSeleccionarDia = findViewById(R.id.btnSeleccionarDia)
        tvTituloTabla = findViewById(R.id.tvTituloTabla)
        tvColumnaDerecha = findViewById(R.id.tvColumnaDerecha)
        btnModoCaducidad = findViewById(R.id.btnModoCaducidad)
        btnModoStock = findViewById(R.id.btnModoStock)
        llGraficaVacia = findViewById(R.id.llGraficaVacia)
        llTablaVacia = findViewById(R.id.llTablaVacia)
        tvTextoVacio = findViewById(R.id.tvTextoVacio)

        rvCaducidad.layoutManager = LinearLayoutManager(this)

        fechaFiltro = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        btnRegresar.setOnClickListener { finish() }

        // La campanita abre el diálogo de productos prioritarios
        btnCampanita.setOnClickListener { mostrarProductosPrioritarios() }

        btnSeleccionarDia.setOnClickListener { mostrarSelectorFecha() }
        btnModoCaducidad.setOnClickListener { cambiarModoTabla(ModoTabla.CADUCIDAD) }
        btnModoStock.setOnClickListener { cambiarModoTabla(ModoTabla.STOCK) }
        actualizarTextoFecha()

        configurarGrafico()
        cargarDatosDeReportes()
    }

    override fun onResume() {
        super.onResume()
        cargarDatosDeReportes()
        actualizarCampanita()
    }

    override fun onPause() {
        super.onPause()
        detenerCampanita()
    }

    override fun onDestroy() {
        super.onDestroy()
        detenerCampanita()
    }

    // ======================================================================
    // CAMPANITA: productos caducados o por caducar en <= 3 días
    // ======================================================================
    private fun mostrarProductosPrioritarios() {
        val prioritarios = productosPrioritarios()

        if (prioritarios.isEmpty()) {
            Dialogos.informacion(
                this,
                "Sin alertas",
                "No hay productos caducados ni próximos a caducar en 3 días.",
                "OK"
            )
            return
        }

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_productos_prioritarios, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rvProductosPrioritarios)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CaducidadAdapter(prioritarios)

        MaterialAlertDialogBuilder(this)
            .setTitle("⚠ Productos prioritarios")
            .setView(dialogView)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    /** Productos caducados o por caducar en ≤3 días (solo lectura). */
    private fun productosPrioritarios(): MutableList<DatabaseHelper.Producto> {
        val todos = dbHelper.getAllProductos()
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val limite = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 3) }.time

        return todos
            .filter { prod ->
                if (prod.fechaCaducidad.isNullOrEmpty()) return@filter false
                try {
                    val f = sdf.parse(prod.fechaCaducidad!!) ?: return@filter false
                    // Caducados o dentro de los próximos 3 días
                    f.before(limite)
                } catch (e: Exception) {
                    false
                }
            }
            .sortedBy { prod ->
                try {
                    sdf.parse(prod.fechaCaducidad!!)
                } catch (e: Exception) {
                    Date(Long.MAX_VALUE)
                }
            }
            .toMutableList()
    }

    /** La campanita solo se mueve si hay productos prioritarios. */
    private fun actualizarCampanita() {
        if (productosPrioritarios().isEmpty()) {
            detenerCampanita()
            return
        }
        if (animadorCampana?.isRunning != true) {
            animadorCampana = CampanaAnimator.iniciar(btnCampanita)
        }
    }

    private fun detenerCampanita() {
        if (::btnCampanita.isInitialized) {
            CampanaAnimator.detener(btnCampanita, animadorCampana)
        }
        animadorCampana = null
    }

    // ======================================================================
    // Gráfica
    // ======================================================================
    private fun configurarGrafico() {
        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.axisMinimum = 7f
        xAxis.axisMaximum = 18f
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true
        xAxis.setLabelCount(12, true)
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                if (value < 7f || value > 18f) return ""
                return "${value.toInt()}:00"
            }
        }

        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.axisMinimum = 0f
        lineChart.axisRight.isEnabled = false
    }

    // ======================================================================
    // Carga de datos (resumen + lista + gráfico)
    // ======================================================================
    private fun cargarDatosDeReportes() {
        val gananciaDia = dbHelper.getGananciaDelDia(fechaFiltro)
        tvGananciaDia.text = "$${String.format("%.2f", gananciaDia)}"

        val ventaDia = dbHelper.getVentaDelDia(fechaFiltro)
        tvVentaDia.text = "$${String.format("%.2f", ventaDia)}"

        cargarDatosGrafico()

        // Lista de caducidades
        val todosLosProductos = dbHelper.getAllProductos()
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val hoy = Date()

        val listaProcesada = todosLosProductos
            .filter { !it.fechaCaducidad.isNullOrEmpty() }
            .sortedBy { producto ->
                try { sdf.parse(producto.fechaCaducidad!!) } catch (e: Exception) { Date(Long.MAX_VALUE) }
            }
            .map { prod ->
                var productoProcesado = prod
                try {
                    val fechaCad = sdf.parse(prod.fechaCaducidad!!)
                    if (fechaCad != null && fechaCad.before(hoy)) {
                        if (prod.stock > 0) {
                            dbHelper.registrarMerma(prod.idProducto, prod.stock, "Caducado")
                            productoProcesado = prod.copy(stock = 0)
                        }
                    }
                } catch (e: Exception) { }
                productoProcesado
            }
            .toMutableList()
        listaCaducidad = listaProcesada

        val umbral = AssistantConfig(this).lowStockThreshold
        listaBajoStock = todosLosProductos.filter { it.stock <= umbral }.sortedBy { it.stock }

        cambiarModoTabla(modoTabla)
    }

    private fun cargarDatosGrafico() {
        val gananciasPorHora = dbHelper.getGananciasPorHoraDelDia(fechaFiltro)
        val entries = ArrayList<Entry>()

        for (hora in 7..18) {
            val ganancia = gananciasPorHora[hora] ?: 0f
            entries.add(Entry(hora.toFloat(), ganancia))
        }

        val sinDatos = entries.all { it.y == 0f }
        llGraficaVacia.visibility = if (sinDatos) View.VISIBLE else View.GONE
        lineChart.visibility = if (sinDatos) View.GONE else View.VISIBLE

        val dataSet = LineDataSet(entries, "Ganancias")
        dataSet.color = Color.parseColor("#2563EB")
        dataSet.setCircleColor(Color.parseColor("#2563EB"))
        dataSet.lineWidth = 3f
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(true)
        dataSet.valueTextSize = 10f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#802563EB")

        val lineData = LineData(dataSet)
        lineChart.data = lineData
        lineChart.invalidate()
    }

    // ======================================================================
    // Diálogo de detalle + promoción
    // ======================================================================
    private fun mostrarDetalleProducto(producto: DatabaseHelper.Producto) {
        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_detalle_producto, null)

        val tvNombre = dialogView.findViewById<TextView>(R.id.tvNombreDetalle)
        val tvStock = dialogView.findViewById<TextView>(R.id.tvStockDetalle)
        val tvFecha = dialogView.findViewById<TextView>(R.id.tvFechaDetalle)
        val tvPrecioOriginal = dialogView.findViewById<TextView>(R.id.tvPrecioOriginal)
        val tvPrecioDescuento = dialogView.findViewById<TextView>(R.id.tvPrecioConDescuento)
        val btnPromocionar = dialogView.findViewById<Button>(R.id.btnPromocionar)
        val llPromocion = dialogView.findViewById<LinearLayout>(R.id.llPromocion)
        val etPorcentaje = dialogView.findViewById<EditText>(R.id.etPorcentaje)
        val btnAceptar = dialogView.findViewById<Button>(R.id.btnAceptarPromocion)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarPromocion)

        // ⚠ Ajusta 'producto.precio' al nombre real del campo en tu entidad Producto
        val precioOriginal = producto.precioVenta

        tvNombre.text = producto.nombre
        tvStock.text = "Stock: ${producto.stock}"
        tvFecha.text = "Caducidad: ${producto.fechaCaducidad}"
        tvPrecioOriginal.text = "Precio actual: $${String.format("%.2f", precioOriginal)}"
        llPromocion.visibility = View.GONE

        btnPromocionar.setOnClickListener {
            btnPromocionar.visibility = View.GONE
            Motion.fadeInUp(llPromocion, 6f * llPromocion.resources.displayMetrics.density)
            etPorcentaje.requestFocus()
        }

        // Mostrar el precio con descuento en tiempo real
        etPorcentaje.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val pct = s?.toString()?.toDoubleOrNull() ?: 0.0
                val nuevo = precioOriginal * (1 - pct / 100.0)
                tvPrecioDescuento.text =
                    "Precio con descuento: $${String.format("%.2f", nuevo)}"
            }
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .create()

        btnAceptar.setOnClickListener {
            val pct = etPorcentaje.text.toString().toDoubleOrNull()
            if (pct == null || pct < 0 || pct > 100) {
                Aviso.advertencia(this, "Porcentaje inválido", "Debe estar entre 0 y 100.")
                return@setOnClickListener
            }
            val nuevoPrecio = precioOriginal * (1 - pct / 100.0)

            // ⚠ Necesitas este método en DatabaseHelper (mira la nota al final)
            dbHelper.actualizarPrecioProducto(producto.idProducto, nuevoPrecio)

            Aviso.exito(
                this,
                "Precio actualizado",
                "${producto.nombre} · ${"$%.2f".format(nuevoPrecio)}"
            )

            dialog.dismiss()
            cargarDatosDeReportes()
        }

        btnCancelar.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    // ======================================================================
    // Adapter reutilizado en la lista y en el diálogo de la campanita
    // ======================================================================
    private object ProductoDiff : DiffUtil.ItemCallback<DatabaseHelper.Producto>() {
        override fun areItemsTheSame(a: DatabaseHelper.Producto, b: DatabaseHelper.Producto) =
            a.idProducto == b.idProducto
        override fun areContentsTheSame(a: DatabaseHelper.Producto, b: DatabaseHelper.Producto) = a == b
    }

    private fun parseFechaCaducidad(s: String?): Date? {
        if (s.isNullOrEmpty()) return null
        for (fmt in listOf("dd/MM/yyyy", "dd/MM/yy")) {
            try { return SimpleDateFormat(fmt, Locale.getDefault()).parse(s) } catch (e: Exception) { }
        }
        return null
    }

    private fun pintarInsignia(tv: TextView, ambar: Boolean) {
        if (ambar) {
            tv.setBackgroundResource(R.drawable.bg_insignia_ambar)
            tv.setTextColor(ContextCompat.getColor(tv.context, R.color.insignia_ambar_texto))
        } else {
            tv.setBackgroundResource(R.drawable.bg_insignia_roja)
            tv.setTextColor(ContextCompat.getColor(tv.context, R.color.insignia_roja_texto))
        }
    }

    private fun mostrarSelectorFecha() {
        val hoy = MaterialDatePicker.todayInUtcMilliseconds()
        val constraints = CalendarConstraints.Builder().setEnd(hoy).build()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setCalendarConstraints(constraints)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            fechaFiltro = String.format(
                Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            actualizarTextoFecha()
            cargarDatosDeReportes()
        }
        picker.show(supportFragmentManager, "fechaReporte")
    }

    private fun actualizarTextoFecha() {
        val hoy = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        if (fechaFiltro == hoy) {
            tvFechaReporte.text = "Hoy"
        } else {
            try {
                val d = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(fechaFiltro)
                tvFechaReporte.text = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(d ?: Date())
            } catch (e: Exception) {
                tvFechaReporte.text = fechaFiltro
            }
        }
    }

    private fun cambiarModoTabla(modo: ModoTabla) {
        modoTabla = modo
        if (modo == ModoTabla.CADUCIDAD) {
            tvTituloTabla.text = "Caducidad próxima"
            tvColumnaDerecha.text = "Caducidad"
            rvCaducidad.adapter = CaducidadAdapter(listaCaducidad)
            llTablaVacia.visibility = if (listaCaducidad.isEmpty()) View.VISIBLE else View.GONE
            tvTextoVacio.text = "Nada por caducar"
        } else {
            tvTituloTabla.text = "Bajo stock"
            tvColumnaDerecha.text = "Stock"
            rvCaducidad.adapter = StockAdapter(listaBajoStock)
            llTablaVacia.visibility = if (listaBajoStock.isEmpty()) View.VISIBLE else View.GONE
            tvTextoVacio.text = "Todo el stock en orden"
        }
        marcarModoActivo()
    }

    private fun marcarModoActivo() {
        val activo = if (modoTabla == ModoTabla.CADUCIDAD) btnModoCaducidad else btnModoStock
        val inactivo = if (modoTabla == ModoTabla.CADUCIDAD) btnModoStock else btnModoCaducidad
        val gris = MaterialColors.getColor(btnModoCaducidad, com.google.android.material.R.attr.colorOnSurfaceVariant)
        activo.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1A2563EB"))
        activo.iconTint = ColorStateList.valueOf(Color.parseColor("#2563EB"))
        inactivo.backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        inactivo.iconTint = ColorStateList.valueOf(gris)
    }

    inner class CaducidadAdapter(list: List<DatabaseHelper.Producto>) :
        ListAdapter<DatabaseHelper.Producto, CaducidadAdapter.VH>(ProductoDiff) {

        init { submitList(list) }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNombre: TextView = itemView.findViewById(R.id.tvNombreCaducidad)
            val tvStock: TextView = itemView.findViewById(R.id.tvStockCaducidad)
            val tvInsignia: TextView = itemView.findViewById(R.id.tvInsigniaCaducidad)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_caducidad, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val producto = getItem(position)
            holder.tvNombre.text = producto.nombre
            holder.tvStock.text = "Stock: ${producto.stock}"

            val fecha = parseFechaCaducidad(producto.fechaCaducidad)
            if (fecha == null) {
                holder.tvInsignia.text = producto.fechaCaducidad ?: "—"
                pintarInsignia(holder.tvInsignia, ambar = true)
            } else {
                val hoy = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.time
                val dias = ((fecha.time - hoy.time) / (1000 * 60 * 60 * 24)).toInt()
                when {
                    dias < 0 -> { holder.tvInsignia.text = "Vencido"; pintarInsignia(holder.tvInsignia, ambar = false) }
                    dias == 0 -> { holder.tvInsignia.text = "Vence hoy"; pintarInsignia(holder.tvInsignia, ambar = false) }
                    else -> { holder.tvInsignia.text = "$dias ${if (dias == 1) "día" else "días"}"; pintarInsignia(holder.tvInsignia, ambar = true) }
                }
            }
            holder.itemView.setOnClickListener { mostrarDetalleProducto(producto) }
        }
    }

    inner class StockAdapter(list: List<DatabaseHelper.Producto>) :
        ListAdapter<DatabaseHelper.Producto, StockAdapter.VH>(ProductoDiff) {

        init { submitList(list) }

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNombre: TextView = itemView.findViewById(R.id.tvNombreStock)
            val tvCodigo: TextView = itemView.findViewById(R.id.tvCodigoStock)
            val tvInsignia: TextView = itemView.findViewById(R.id.tvInsigniaStock)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stock, parent, false))

        override fun onBindViewHolder(holder: VH, position: Int) {
            val producto = getItem(position)
            holder.tvNombre.text = producto.nombre
            holder.tvCodigo.text = "Código: ${producto.codigoBarras}"
            val u = producto.stock
            holder.tvInsignia.text = if (u == 1) "1 ud" else "$u uds"
            pintarInsignia(holder.tvInsignia, ambar = u > 0)
            holder.itemView.setOnClickListener { mostrarDetalleProducto(producto) }
        }
    }
}