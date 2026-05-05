package com.example.ing_software_abarrotezperez

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.example.ing_software_abarrotezperez.ui.InventarioActivity
import com.example.ing_software_abarrotezperez.ui.VentaActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnInventario = findViewById<Button>(R.id.btnIrInventario)
        val btnVentas = findViewById<Button>(R.id.btnIrVentas)

        // Al hacer clic, abrimos la pantalla de Inventario
        btnInventario.setOnClickListener {
            val intent = Intent(this, InventarioActivity::class.java)
            startActivity(intent)
        }

        // Al hacer clic, abrimos la pantalla de Ventas
        btnVentas.setOnClickListener {
            val intent = Intent(this, VentaActivity::class.java)
            startActivity(intent)
        }
    }
}