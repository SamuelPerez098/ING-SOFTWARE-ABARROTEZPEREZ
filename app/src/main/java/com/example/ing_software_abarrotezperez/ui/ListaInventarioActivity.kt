package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

class ListaInventarioActivity : AppCompatActivity() {

    private lateinit var rvInventario: RecyclerView
    private lateinit var etBuscar: EditText
    private lateinit var adapter: ProductoAdapter
    private lateinit var dbHelper: DatabaseHelper

    // Lista completa para poder filtrar sin perder datos
    private var listaCompleta: MutableList<DatabaseHelper.Producto> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_inventario)

        // 1. Inicializar DB y Vistas
        dbHelper = DatabaseHelper(this)
        rvInventario = findViewById(R.id.rvInventario)
        etBuscar = findViewById(R.id.etBuscar)
        rvInventario.layoutManager = LinearLayoutManager(this)

        // 2. Lógica de Semilla (Cargar los 300 productos una sola vez)
        verificarYFecundarProductos()

        // 3. Configurar el buscador interactivo
        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filtrarLista(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun verificarYFecundarProductos() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val yaCargados = prefs.getBoolean("productos_cargados", false)

        if (!yaCargados) {
            dbHelper.insertar300ProductosDePrueba()
            prefs.edit().putBoolean("productos_cargados", true).apply()
            Toast.makeText(this, " 300 Productos de prueba cargados", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        cargarInventario()
    }

    private fun cargarInventario() {
        // Obtenemos los productos y los ORDENAMOS ALFABÉTICAMENTE ignorando mayúsculas/minúsculas
        listaCompleta = dbHelper.getAllProductos()
            .sortedBy { it.nombre.lowercase() }
            .toMutableList()

        if (!::adapter.isInitialized) {
            adapter = ProductoAdapter(this, listaCompleta, dbHelper)
            rvInventario.adapter = adapter
        } else {
            // Aplicamos el filtro si hay algo escrito en el buscador
            filtrarLista(etBuscar.text.toString())
        }
    }

    private fun filtrarLista(textoBuscado: String) {
        val listaFiltrada = if (textoBuscado.isEmpty()) {
            listaCompleta
        } else {
            listaCompleta.filter { producto ->
                producto.nombre.contains(textoBuscado, ignoreCase = true) ||
                        producto.codigoBarras.contains(textoBuscado, ignoreCase = true)
            }.toMutableList()
        }

        if (::adapter.isInitialized) {
            adapter.actualizarDatos(listaFiltrada)
        }
    }
}