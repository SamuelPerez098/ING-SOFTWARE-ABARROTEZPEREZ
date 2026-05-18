package com.example.ing_software_abarrotezperez.ui

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper
import com.google.android.material.textfield.TextInputEditText

class FiadoActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: ClienteAdapter
    private var todosLosClientes = listOf<DatabaseHelper.Cliente>()
    private var saldos = mapOf<Int, Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fiado)

        db = DatabaseHelper(this)

        val rvClientes     = findViewById<RecyclerView>(R.id.rvClientes)
        val etBuscar       = findViewById<TextInputEditText>(R.id.etBuscar)
        val btnNuevoCliente = findViewById<Button>(R.id.btnNuevoCliente)
        val tvVacio        = findViewById<TextView>(R.id.tvVacio)

        adapter = ClienteAdapter(emptyList(), emptyMap()) { cliente ->
            val intent = Intent(this, DetalleFiadoActivity::class.java)
            intent.putExtra("id_cliente", cliente.idCliente)
            intent.putExtra("nombre_cliente", cliente.nombre)
            startActivity(intent)
        }
        rvClientes.layoutManager = LinearLayoutManager(this)
        rvClientes.adapter = adapter

        cargarClientes(rvClientes, tvVacio)

        etBuscar.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().lowercase().trim()
                // Si el buscador está vacío, mostramos todos
                if (query.isEmpty()) {
                    cargarClientes(rvClientes, tvVacio)
                    return
                }
                // Si hay texto, filtramos de la lista completa
                val filtrados = todosLosClientes.filter {
                    it.nombre.lowercase().contains(query)
                }
                adapter.actualizar(filtrados, saldos)

                actualizarVisibilidad(filtrados.isEmpty(), rvClientes, tvVacio)
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        btnNuevoCliente.setOnClickListener {
            mostrarDialogoNuevoCliente(rvClientes, tvVacio)
        }
    }

    override fun onResume() {
        super.onResume()
        val rvClientes = findViewById<RecyclerView>(R.id.rvClientes)
        val tvVacio    = findViewById<TextView>(R.id.tvVacio)
        cargarClientes(rvClientes, tvVacio)
    }

    private fun cargarClientes(rvClientes: RecyclerView, tvVacio: TextView) {
        todosLosClientes = db.getAllClientes()

        saldos = todosLosClientes.associate { it.idCliente to db.getSaldoPendienteCliente(it.idCliente) }

        adapter.actualizar(todosLosClientes, saldos)

        actualizarVisibilidad(todosLosClientes.isEmpty(), rvClientes, tvVacio)
    }

    private fun actualizarVisibilidad(isEmpty: Boolean, rv: RecyclerView, tv: TextView) {
        if (isEmpty) {
            tv.visibility = View.VISIBLE
            rv.visibility = View.GONE
        } else {
            tv.visibility = View.GONE
            rv.visibility = View.VISIBLE
        }
    }

    private fun mostrarDialogoNuevoCliente(rvClientes: RecyclerView, tvVacio: TextView) {
        // AQUÍ ESTÁ LA MAGIA: Llamamos al NUEVO archivo XML (dialog_nuevo_cliente)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nuevo_cliente, null)

        // Usamos los IDs del nuevo diseño
        val etNombre    = dialogView.findViewById<TextInputEditText>(R.id.etNombreCliente)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarCliente)
        val btnGuardar  = dialogView.findViewById<Button>(R.id.btnGuardarCliente)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val nombre = etNombre.text.toString().trim()

            if (nombre.isEmpty()) {
                // Mostramos el error directamente en el campo de texto (estilo Material Design)
                etNombre.error = "El nombre es obligatorio"
                return@setOnClickListener
            }

            val resultado = db.registrarCliente(nombre)
            if (resultado != -1L) {
                Toast.makeText(this, "Cliente '$nombre' listo", Toast.LENGTH_SHORT).show()
                cargarClientes(rvClientes, tvVacio) // Recarga la lista para que aparezca luego luego
                dialog.dismiss()
            } else {
                Toast.makeText(this, "Este nombre ya existe", Toast.LENGTH_LONG).show()
            }
        }
        dialog.show()
    }
}