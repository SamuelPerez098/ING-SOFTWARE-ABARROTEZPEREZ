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

        // Definir la fecha de hoy al iniciar (Formato: YYYY-MM-DD)
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

    private fun configurarGrafico() {
        lineChart.description.isEnabled = false
        lineChart.setDrawGridBackground(false)
        lineChart.legend.isEnabled = false

        val xAxis = lineChart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM

        // 1. Definir los límites estrictos
        xAxis.axisMinimum = 7f
        xAxis.axisMaximum = 18f

        // 2. Obligar a que salte de 1 en 1
        xAxis.granularity = 1f
        xAxis.isGranularityEnabled = true

        // 3. LA CLAVE: Forzar exactamente 12 etiquetas (las 12 horas de 7 a 18)
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

    private fun cargarDatosDeReportes() {
        val gananciaDia = dbHelper.getGananciaDelDia(fechaFiltro)
        tvGananciaDia.text = "$${String.format("%.2f", gananciaDia)}"

        val ventaDia = dbHelper.getVentaDelDia(fechaFiltro)
        tvVentaDia.text = "$${String.format("%.2f", ventaDia)}"

        cargarDatosGrafico()

        // =========================================================================
        // NUEVA LÓGICA DE ORDENAMIENTO DE CADUCIDADES
        // =========================================================================

        // 1. Obtenemos TODOS los productos y filtramos solo los que tienen fecha de caducidad
        val todosLosProductos = dbHelper.getAllProductos()
        val productosConCaducidad = todosLosProductos.filter { !it.fechaCaducidad.isNullOrEmpty() }

        val sdf = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val hoy = Date()

        // 2. Los ordenamos cronológicamente (del pasado al futuro)
        val listaOrdenada = productosConCaducidad.sortedBy { producto ->
            try {
                sdf.parse(producto.fechaCaducidad!!)
            } catch (e: Exception) {
                Date(Long.MAX_VALUE) // Si alguna fecha está corrupta, la manda hasta el final
            }
        }

        val listaProcesada = mutableListOf<DatabaseHelper.Producto>()

        // 3. Aplicamos la lógica de merma (Mandar a 0 el stock si ya caducó)
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