package com.example.ing_software_abarrotezperez.ui

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
import androidx.cardview.widget.CardView
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

        rvCaducidad.layoutManager = LinearLayoutManager(this)

        fechaFiltro = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        btnRegresar.setOnClickListener { finish() }

        // La campanita abre el diálogo de productos prioritarios
        btnCampanita.setOnClickListener { mostrarProductosPrioritarios() }

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
            AlertDialog.Builder(this)
                .setTitle("Sin alertas")
                .setMessage("No hay productos caducados ni próximos a caducar en 3 días.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        val dialogView = LayoutInflater.from(this)
            .inflate(R.layout.dialog_productos_prioritarios, null)
        val rv = dialogView.findViewById<RecyclerView>(R.id.rvProductosPrioritarios)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = CaducidadAdapter(prioritarios)

        AlertDialog.Builder(this)
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
        yAxisLeft.axisMaximum = 700f
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
        val productosConCaducidad = todosLosProductos.filter { !it.fechaCaducidad.isNullOrEmpty() }

        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val hoy = Date()

        val listaOrdenada = productosConCaducidad.sortedBy { producto ->
            try {
                sdf.parse(producto.fechaCaducidad!!)
            } catch (e: Exception) {
                Date(Long.MAX_VALUE)
            }
        }

        val listaProcesada = mutableListOf<DatabaseHelper.Producto>()

        for (prod in listaOrdenada) {
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
            listaProcesada.add(productoProcesado)
        }

        rvCaducidad.adapter = CaducidadAdapter(listaProcesada)
    }

    private fun cargarDatosGrafico() {
        val gananciasPorHora = dbHelper.getGananciasPorHoraDelDia(fechaFiltro)
        val entries = ArrayList<Entry>()

        for (hora in 7..18) {
            val ganancia = gananciasPorHora[hora] ?: 0f
            entries.add(Entry(hora.toFloat(), ganancia))
        }

        val dataSet = LineDataSet(entries, "Ganancias")
        dataSet.color = Color.parseColor("#3A5BA0")
        dataSet.setCircleColor(Color.parseColor("#3A5BA0"))
        dataSet.lineWidth = 3f
        dataSet.circleRadius = 5f
        dataSet.setDrawCircleHole(true)
        dataSet.valueTextSize = 10f
        dataSet.setDrawFilled(true)
        dataSet.fillColor = Color.parseColor("#803A5BA0")

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

        val dialog = AlertDialog.Builder(this)
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
    inner class CaducidadAdapter(private var productos: MutableList<DatabaseHelper.Producto>) :
        RecyclerView.Adapter<CaducidadAdapter.CaducidadViewHolder>() {

        inner class CaducidadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNombre: TextView = itemView.findViewById(R.id.tvNombreProductoItem)
            val tvFecha: TextView = itemView.findViewById(R.id.tvFechaCaducidadItem)
            val tvStock: TextView = itemView.findViewById(R.id.tvStockItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaducidadViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_producto_caducidad, parent, false)
            return CaducidadViewHolder(view)
        }

        override fun onBindViewHolder(holder: CaducidadViewHolder, position: Int) {
            val producto = productos[position]
            val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
            var estaCaducado = false

            try {
                val fecha = sdf.parse(producto.fechaCaducidad!!)
                if (fecha != null && fecha.before(Date())) {
                    estaCaducado = true
                }
            } catch (e: Exception) { }

            holder.tvNombre.text = producto.nombre
            holder.tvStock.text = "Stock: ${producto.stock}"
            holder.tvFecha.text = "Caduca: ${producto.fechaCaducidad}"

            val card = holder.itemView as CardView
            if (estaCaducado) {
                card.setCardBackgroundColor(Color.parseColor("#FFCDD2"))
                holder.tvNombre.setTextColor(Color.parseColor("#B71C1C"))
                holder.tvFecha.setTextColor(Color.parseColor("#B71C1C"))
                holder.tvStock.setTextColor(Color.parseColor("#B71C1C"))
            } else {
                card.setCardBackgroundColor(Color.WHITE)
                holder.tvNombre.setTextColor(Color.parseColor("#212121"))
                holder.tvFecha.setTextColor(Color.DKGRAY)
                holder.tvStock.setTextColor(Color.parseColor("#2C55A6"))
            }

            // Al tocar cualquier producto -> detalle + promocionar
            holder.itemView.setOnClickListener {
                mostrarDetalleProducto(producto)
            }
        }

        override fun getItemCount(): Int = productos.size
    }
}