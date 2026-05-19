package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
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

        rvInventario = findViewById(R.id.rvInventario)
        rvInventario.layoutManager = LinearLayoutManager(this)

        dbHelper = DatabaseHelper(this) // Instancia de tu base de datos
    }

    // Usamos onResume para que la lista se refresque automáticamente si editamos un producto y regresamos
    override fun onResume() {
        super.onResume()
        cargarInventario()
    }

    private fun cargarInventario() {
        val listaProductos = dbHelper.getAllProductos().toMutableList()

        if (!::adapter.isInitialized) {
            adapter = ProductoAdapter(this, listaProductos, dbHelper)
            rvInventario.adapter = adapter
        } else {
            adapter.actualizarDatos(listaProductos)
        }
    }
}