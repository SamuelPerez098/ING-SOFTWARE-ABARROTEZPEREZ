package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.ArrayAdapter
import android.widget.Button
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import java.text.Collator
import java.util.Locale

class ListaInventarioActivity : AppCompatActivity() {

    private lateinit var rvInventario: RecyclerView
    private lateinit var etBuscar: EditText
    private lateinit var adapter: ProductoAdapter
    private lateinit var dbHelper: DatabaseHelper

    // Lista completa para poder filtrar sin perder datos
    private var listaCompleta: MutableList<DatabaseHelper.Producto> = mutableListOf()

    private enum class CriterioOrden { RECIENTES, NOMBRE, PRECIO, STOCK }

    private var criterioOrden = CriterioOrden.RECIENTES
    private var descendente = true

    private val collator = Collator.getInstance(Locale("es", "MX")).apply {
        strength = Collator.PRIMARY
    }

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

        configurarOrdenamiento()
    }

    private fun verificarYFecundarProductos() {
        val prefs = getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
        val yaCargados = prefs.getBoolean("productos_cargados", false)

        if (!yaCargados) {
            dbHelper.insertar300ProductosDePrueba()
            prefs.edit().putBoolean("productos_cargados", true).apply()
            Aviso.info(this, "Productos de prueba cargados", "Se agregaron 300 productos de ejemplo.")
        }
    }

    override fun onResume() {
        super.onResume()
        cargarInventario()
    }

    private fun cargarInventario() {
        listaCompleta = dbHelper.getAllProductos().toMutableList()
        aplicarOrden()

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

    private fun configurarOrdenamiento() {
        val autoOrden = findViewById<MaterialAutoCompleteTextView>(R.id.autoOrden)
        val btnInvertir = findViewById<Button>(R.id.btnInvertirOrden)

        val criterios = listOf(
            CriterioOrden.RECIENTES to "Más recientes",
            CriterioOrden.NOMBRE to "Nombre (A-Z)",
            CriterioOrden.PRECIO to "Precio",
            CriterioOrden.STOCK to "Stock"
        )
        val etiquetas = criterios.map { it.second }.toTypedArray()
        autoOrden.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, etiquetas))
        autoOrden.setText("Más recientes", false)

        autoOrden.setOnItemClickListener { _, _, position, _ ->
            val criterio = criterios[position].first
            if (criterio == criterioOrden) return@setOnItemClickListener
            criterioOrden = criterio
            descendente = when (criterio) {
                CriterioOrden.RECIENTES, CriterioOrden.PRECIO, CriterioOrden.STOCK -> true
                CriterioOrden.NOMBRE -> false
            }
            aplicarOrden()
            filtrarLista(etBuscar.text.toString())
        }

        btnInvertir.setOnClickListener {
            descendente = !descendente
            aplicarOrden()
            filtrarLista(etBuscar.text.toString())
        }
    }

    private fun aplicarOrden() {
        val comparador: Comparator<DatabaseHelper.Producto> = when (criterioOrden) {
            CriterioOrden.RECIENTES -> compareBy { it.idProducto }
            CriterioOrden.NOMBRE -> Comparator { a, b -> collator.compare(a.nombre, b.nombre) }
            CriterioOrden.PRECIO -> compareBy { it.precioVenta }
            CriterioOrden.STOCK -> compareBy { it.stock }
        }
        listaCompleta.sortWith(if (descendente) comparador.reversed() else comparador)
    }
}