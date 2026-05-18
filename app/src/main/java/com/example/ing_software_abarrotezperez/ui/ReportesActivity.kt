package com.example.ing_software_abarrotezperez.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.components.AxisBase

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

    // Esta variable guardará la fecha que estamos consultando (Por defecto: Hoy)
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

        val btnSeleccionarDia = findViewById<Button>(R.id.btnSeleccionarDia)
        val btnRegresar = findViewById<ImageView>(R.id.btnRegresarReportes)

        rvCaducidad.layoutManager = LinearLayoutManager(this)

        // Definir la fecha de hoy al iniciar
        fechaFiltro = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

        btnRegresar.setOnClickListener { finish() }

        // Abrir el calendario al presionar el botón de DÍA
        btnSeleccionarDia.setOnClickListener {
            mostrarSelectorDeFecha()
        }

        configurarGrafico()
        cargarDatosDeReportes()
    }

    override fun onResume() {
        super.onResume()
        cargarDatosDeReportes()
    }

    private fun mostrarSelectorDeFecha() {
        val calendar = Calendar.getInstance()

        // Sincronizar el calendario con la fecha que ya estaba seleccionada
        val partesFecha = fechaFiltro.split("-")
        if (partesFecha.size == 3) {
            calendar.set(Calendar.YEAR, partesFecha[0].toInt())
            calendar.set(Calendar.MONTH, partesFecha[1].toInt() - 1)
            calendar.set(Calendar.DAY_OF_MONTH, partesFecha[2].toInt())
        }

        val datePickerDialog = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Darle formato a los números (Ejemplo: 9 -> 09)
                val mesFormat = String.format("%02d", month + 1)
                val diaFormat = String.format("%02d", dayOfMonth)

                // Actualizar la variable de búsqueda
                fechaFiltro = "$year-$mesFormat-$diaFormat"

                // Cambiar el título de la pantalla
                tvTituloReportes.text = "REPORTES ($diaFormat/$mesFormat)"

                // Recargar toda la gráfica y los cuadros de ventas
                cargarDatosDeReportes()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    // =========================================================================
    // CONFIGURACIÓN VISUAL DE LA GRÁFICA
    // =========================================================================
    private fun configurarGrafico() {
        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.axisMinimum = 8f
        xAxis.axisMaximum = 18f
        xAxis.granularity = 1f
        xAxis.setDrawGridLines(false)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getAxisLabel(value: Float, axis: AxisBase?): String {
                return "${value.toInt()}:00"
            }
        }

        val yAxisLeft = lineChart.axisLeft
        yAxisLeft.axisMinimum = 0f
        yAxisLeft.axisMaximum = 700f

        lineChart.axisRight.isEnabled = false
    }

    // =========================================================================
    // CARGAR DATOS
    // =========================================================================
    private fun cargarDatosDeReportes() {
        // 1. Cargar textos PASÁNDOLE LA FECHA SELECCIONADA
        val gananciaDia = dbHelper.getGananciaDelDia(fechaFiltro)
        tvGananciaDia.text = "$${String.format("%.2f", gananciaDia)}"

        val ventaDia = dbHelper.getVentaDelDia(fechaFiltro)
        tvVentaDia.text = "$${String.format("%.2f", ventaDia)}"

        // 2. Cargar Gráfica
        cargarDatosGrafico()

        // 3. Caducidades y merma automática (Esto NO cambia con el calendario, siempre muestra actuales)
        val listaCompleta = dbHelper.getTodosLosProductosConCaducidad()
        val listaProcesada = mutableListOf<DatabaseHelper.Producto>()
        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val hoy = Date()

        for (prod in listaCompleta) {
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
        // Obtenemos las ganancias usando LA FECHA SELECCIONADA
        val gananciasPorHora = dbHelper.getGananciasPorHoraDelDia(fechaFiltro)
        val entries = ArrayList<Entry>()

        for (hora in 8..18) {
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

    // =========================================================================
    // ADAPTADOR INTERNO (Sin Cambios)
    // =========================================================================
    inner class CaducidadAdapter(private var productos: MutableList<DatabaseHelper.Producto>) :
        RecyclerView.Adapter<CaducidadAdapter.CaducidadViewHolder>() {

        inner class CaducidadViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvNombre: TextView = itemView.findViewById(android.R.id.text1)
            val tvFecha: TextView = itemView.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CaducidadViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
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

            holder.tvNombre.text = "${producto.nombre} (Stock: ${producto.stock})"
            holder.tvFecha.text = "Caduca el: ${producto.fechaCaducidad}"

            if (estaCaducado) {
                holder.itemView.setBackgroundColor(Color.parseColor("#FFCDD2"))
                holder.tvNombre.setTextColor(Color.parseColor("#B71C1C"))
                holder.tvFecha.setTextColor(Color.parseColor("#B71C1C"))
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT)
                holder.tvNombre.setTextColor(Color.BLACK)
                holder.tvFecha.setTextColor(Color.DKGRAY)
            }

            holder.itemView.setOnClickListener {
                if (estaCaducado) {
                    AlertDialog.Builder(holder.itemView.context)
                        .setTitle("Producto Caducado")
                        .setMessage("Este producto caducó y se registró en Mermas automáticamente. ¿Qué deseas hacer?")
                        .setPositiveButton("Ver Mermas") { _, _ ->
                            val intent = Intent(this@ReportesActivity, MermasActivity::class.java)
                            startActivity(intent)
                        }
                        .setNegativeButton("Borrar de lista") { _, _ ->
                            productos.removeAt(position)
                            notifyItemRemoved(position)
                            notifyItemRangeChanged(position, productos.size)
                        }
                        .show()
                }
            }
        }

        override fun getItemCount(): Int = productos.size
    }
}