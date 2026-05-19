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
import java.text.Normalizer

class FiadoActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private lateinit var adapter: ClienteAdapter
    private var todosLosClientes = listOf<DatabaseHelper.Cliente>()
    private var saldos = mapOf<Int, Double>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fiado)

        db = DatabaseHelper(this)

        val rvClientes      = findViewById<RecyclerView>(R.id.rvClientes)
        val etBuscar        = findViewById<TextInputEditText>(R.id.etBuscar)
        val btnNuevoCliente = findViewById<Button>(R.id.btnNuevoCliente)
        val tvVacio         = findViewById<TextView>(R.id.tvVacio)

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
                if (query.isEmpty()) {
                    cargarClientes(rvClientes, tvVacio)
                    return
                }
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

    // ─────────────────────────────────────────────
    //  VALIDACIONES HELPER
    // ─────────────────────────────────────────────

    // Quita acentos y convierte a minúsculas para comparar nombres
    private fun normalizarNombre(nombre: String): String {
        val sinAcentos = Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return sinAcentos.lowercase().trim()
    }

    // Verifica si el nombre ya existe ignorando mayúsculas, minúsculas y acentos
    private fun nombreYaExiste(nombre: String): Boolean {
        val nombreNorm = normalizarNombre(nombre)
        return todosLosClientes.any { normalizarNombre(it.nombre) == nombreNorm }
    }
    //Verifica que solo acepte letras en el nombre de el deudor
    private fun nombreTieneLetras(nombre: String): Boolean {
        return nombre.all { it.isLetter() || it.isWhitespace() }
    }

    // ─────────────────────────────────────────────
    //  DIÁLOGO NUEVO CLIENTE
    // ─────────────────────────────────────────────
    private fun mostrarDialogoNuevoCliente(rvClientes: RecyclerView, tvVacio: TextView) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_nuevo_cliente, null)

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

            // Validación 1: campo vacío
            if (nombre.isEmpty()) {
                etNombre.error = "El nombre es obligatorio"
                return@setOnClickListener
            }

            // Validación 2: solo números o sin letras
            if (!nombreTieneLetras(nombre)) {
                etNombre.error = "El nombre debe contener letras"
                return@setOnClickListener
            }

            // Validación 3: nombre duplicado (ignora mayúsculas, minúsculas y acentos)
            if (nombreYaExiste(nombre)) {
                etNombre.error = "Ya existe un cliente con ese nombre"
                return@setOnClickListener
            }

            val resultado = db.registrarCliente(nombre)
            if (resultado != -1L) {
                Toast.makeText(this, "Cliente '$nombre' registrado ✅", Toast.LENGTH_SHORT).show()
                cargarClientes(rvClientes, tvVacio)
                dialog.dismiss()
            } else {
                etNombre.error = "Error al guardar, intenta de nuevo"
            }
        }
        dialog.show()
    }
}