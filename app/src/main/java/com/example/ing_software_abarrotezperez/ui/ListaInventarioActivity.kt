package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

class ListaInventarioActivity : AppCompatActivity() {

    private lateinit var rvInventario: RecyclerView
    private lateinit var adapter: ProductoAdapter
    private lateinit var dbHelper: DatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_inventario)

        // 1. Inicializar DB y Vistas
        dbHelper = DatabaseHelper(this)
        rvInventario = findViewById(R.id.rvInventario)
        rvInventario.layoutManager = LinearLayoutManager(this)

        // 2. Lógica de Semilla (Cargar los 300 productos una sola vez)
        verificarYFecundarProductos()
    }

    private fun verificarYFecundarProductos() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val yaCargados = prefs.getBoolean("productos_cargados", false)

        if (!yaCargados) {
            // Llamamos al método que pusimos en el DatabaseHelper
            dbHelper.insertar300ProductosDePrueba()

            // Guardamos que ya se hizo para que no se repita nunca más
            prefs.edit().putBoolean("productos_cargados", true).apply()

            Toast.makeText(this, " 300 Productos de prueba cargados", Toast.LENGTH_LONG).show()
        }
    }

    // Usamos onResume para que la lista se refresque automáticamente si editamos un producto y regresamos
    override fun onResume() {
        super.onResume()
        cargarInventario()
    }

    private fun cargarInventario() {
        // Obtenemos todos los productos (incluyendo los 300 si es la primera vez)
        val listaProductos = dbHelper.getAllProductos().toMutableList()

        if (!::adapter.isInitialized) {
            // Asegúrate de que tu ProductoAdapter acepte estos 3 parámetros
            adapter = ProductoAdapter(this, listaProductos, dbHelper)
            rvInventario.adapter = adapter
        } else {
            adapter.actualizarDatos(listaProductos)
        }
    }
}