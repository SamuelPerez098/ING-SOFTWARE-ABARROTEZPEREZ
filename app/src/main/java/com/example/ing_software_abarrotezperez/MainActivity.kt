package com.example.ing_software_abarrotezperez

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.ing_software_abarrotezperez.ui.InventarioActivity
import com.example.ing_software_abarrotezperez.ui.VentaActivity
import com.example.ing_software_abarrotezperez.ui.ReportesActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Referencias a los botones del XML
        val btnInventario = findViewById<Button>(R.id.btnIrInventario)
        val btnVentas = findViewById<Button>(R.id.btnIrVentas)
        val btnReportes = findViewById<Button>(R.id.btnVerReportes)

        // Acción: Abrir Inventario
        btnInventario.setOnClickListener {
            val intent = Intent(this, InventarioActivity::class.java)
            startActivity(intent)
        }

        // Acción: Abrir Punto de Venta
        btnVentas.setOnClickListener {
            val intent = Intent(this, VentaActivity::class.java)
            startActivity(intent)
        }

        // Acción: Abrir Reportes de Negocio
        btnReportes.setOnClickListener {
            val intent = Intent(this, ReportesActivity::class.java)
            startActivity(intent)
        }
    }
}