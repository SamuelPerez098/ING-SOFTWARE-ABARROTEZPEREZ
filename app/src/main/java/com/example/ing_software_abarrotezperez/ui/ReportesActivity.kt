package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

class ReportesActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reportes)

        dbHelper = DatabaseHelper(this)

        val tvGanancia = findViewById<TextView>(R.id.tvGananciaTotal)
        val tvTop = findViewById<TextView>(R.id.tvTopVendidos)
        val tvMargen = findViewById<TextView>(R.id.tvComparativaMargen)

        // 1. Mostrar Ganancia Real
        val ganancia = dbHelper.getReporteGanancias()
        tvGanancia.text = "$${String.format("%.2f", ganancia)}"

        // 2. Mostrar Top Vendidos
        val topList = dbHelper.getTopVendidos()
        if (topList.isEmpty()) {
            tvTop.text = "Aún no hay ventas registradas."
        } else {
            tvTop.text = topList.joinToString("\n") { "${it.first}: ${it.second} unidades" }
        }

        // 3. Mostrar Comparativa de Márgenes
        val margenList = dbHelper.getComparativaMargen()
        if (margenList.isEmpty()) {
            tvMargen.text = "No hay datos de costos disponibles."
        } else {
            tvMargen.text = margenList.joinToString("\n")
        }
    }
}