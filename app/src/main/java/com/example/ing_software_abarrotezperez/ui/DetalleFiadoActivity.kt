package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
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
import com.google.android.material.textfield.TextInputLayout

class DetalleFiadoActivity : AppCompatActivity() {

    private lateinit var db: DatabaseHelper
    private var idCliente: Int = -1
    private var nombreCliente: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_fiado)

        db            = DatabaseHelper(this)
        idCliente     = intent.getIntExtra("id_cliente", -1)
        nombreCliente = intent.getStringExtra("nombre_cliente") ?: "Cliente"

        val tvTitulo         = findViewById<TextView>(R.id.tvTituloCliente)
        val tvSaldoTotal     = findViewById<TextView>(R.id.tvSaldoTotal)
        val btnNuevoFiado    = findViewById<Button>(R.id.btnNuevoFiado)
        val btnRegistrarPago = findViewById<Button>(R.id.btnRegistrarPago)
        val btnRegresar      = findViewById<Button>(R.id.btnRegresar)
        val rvFiados         = findViewById<RecyclerView>(R.id.rvDeudasCliente)
        val tvSinDeudas      = findViewById<TextView>(R.id.tvSinDeudas)

        tvTitulo.text = "👤 $nombreCliente"

        val layoutManager = LinearLayoutManager(this)
        rvFiados.layoutManager = layoutManager

        cargarDetalle(tvSaldoTotal, rvFiados, tvSinDeudas)

        btnRegresar.setOnClickListener { finish() }

        btnNuevoFiado.setOnClickListener {
            mostrarDialogoNuevoFiado(tvSaldoTotal, rvFiados, tvSinDeudas)
        }

        btnRegistrarPago.setOnClickListener {
            mostrarDialogoPago(tvSaldoTotal, rvFiados, tvSinDeudas)
        }
    }

    private fun cargarDetalle(tvSaldoTotal: TextView, rvFiados: RecyclerView, tvSinDeudas: TextView) {
        val saldoTotal = db.getSaldoPendienteCliente(idCliente)
        tvSaldoTotal.text = "$${"%.2f".format(saldoTotal)}"

        val fiados = db.getFiadosActivosPorCliente(idCliente)

        if (fiados.isEmpty()) {
            rvFiados.visibility    = View.GONE
            tvSinDeudas.visibility = View.VISIBLE
        } else {
            rvFiados.visibility    = View.VISIBLE
            tvSinDeudas.visibility = View.GONE

            val textos = fiados.mapIndexed { index, fiado ->
                "Deuda #${index + 1} — Saldo: $${"%.2f".format(fiado.saldoPendiente)}"
            }

            rvFiados.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val tv = TextView(parent.context).apply {
                        setPadding(32, 24, 32, 24)
                        textSize = 15f
                        setTextColor(android.graphics.Color.parseColor("#212121"))
                    }
                    return object : RecyclerView.ViewHolder(tv) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    (holder.itemView as TextView).text = textos[position]
                }
                override fun getItemCount() = textos.size
            }
        }
    }

    private fun mostrarDialogoNuevoFiado(tvSaldoTotal: TextView, rvFiados: RecyclerView, tvSinDeudas: TextView) {
        val dialogView  = LayoutInflater.from(this).inflate(R.layout.dialog_nuevo_fiado, null)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.etDescripcion)
        val etMonto     = dialogView.findViewById<TextInputEditText>(R.id.etMonto)
        val tvError     = dialogView.findViewById<TextView>(R.id.tvErrorFiado)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarFiado)
        val btnGuardar  = dialogView.findViewById<Button>(R.id.btnGuardarFiado)

        // FIX VISUAL: Asignamos el hint al contenedor padre (TextInputLayout) y limpiamos el EditText
        val tilDesc = etDesc.parent.parent as? TextInputLayout
        tilDesc?.hint = "Descripción (ej: leche, pan...)"
        etDesc.hint = ""

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val desc  = etDesc.text.toString().trim()
            val monto = etMonto.text.toString().toDoubleOrNull()

            if (desc.isEmpty() || monto == null || monto <= 0) {
                tvError.visibility = View.VISIBLE
                tvError.text = "Por favor completa todos los campos"
                return@setOnClickListener
            }

            val ok = db.registrarFiado(idCliente, monto)
            if (ok) {
                Toast.makeText(this, "Deuda registrada ✅", Toast.LENGTH_SHORT).show()
                cargarDetalle(tvSaldoTotal, rvFiados, tvSinDeudas)
                dialog.dismiss()
            } else {
                tvError.visibility = View.VISIBLE
                tvError.text = "Error al guardar, intenta de nuevo"
            }
        }

        dialog.show()
    }

    private fun mostrarDialogoPago(tvSaldoTotal: TextView, rvFiados: RecyclerView, tvSinDeudas: TextView) {
        val fiados = db.getFiadosActivosPorCliente(idCliente)
        if (fiados.isEmpty()) {
            Toast.makeText(this, "Este cliente no tiene deudas activas", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView  = LayoutInflater.from(this).inflate(R.layout.dialog_nuevo_fiado, null)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.etDescripcion)
        val etMonto     = dialogView.findViewById<TextInputEditText>(R.id.etMonto)
        val tvError     = dialogView.findViewById<TextView>(R.id.tvErrorFiado)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarFiado)
        val btnGuardar  = dialogView.findViewById<Button>(R.id.btnGuardarFiado)

        // Ocultamos el contenedor completo de la descripción para que no ocupe espacio extra
        val tilDesc = etDesc.parent.parent as? TextInputLayout
        tilDesc?.visibility = View.GONE
        etDesc.visibility = View.GONE

        val saldoTotalMaximo = fiados.sumOf { it.saldoPendiente }

        // FIX VISUAL: Asignamos el límite al contenedor padre (TextInputLayout) y limpiamos el EditText
        val tilMonto = etMonto.parent.parent as? TextInputLayout
        tilMonto?.hint = "Monto a pagar (máx: $${"%.2f".format(saldoTotalMaximo)})"
        etMonto.hint = ""

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val monto = etMonto.text.toString().toDoubleOrNull()

            if (monto == null || monto <= 0) {
                tvError.visibility = View.VISIBLE
                tvError.text = "Ingresa un monto válido"
                return@setOnClickListener
            }

            var montoPago = "%.2f".format(monto).toDouble()
            val saldoMaximo = "%.2f".format(saldoTotalMaximo).toDouble()

            if (montoPago > saldoMaximo) {
                tvError.visibility = View.VISIBLE
                tvError.text = "El monto no puede ser mayor al saldo total ($${"%.2f".format(saldoMaximo)})"
                return@setOnClickListener
            }

            var exitoEnPagos = true
            for (fiado in fiados) {
                if (montoPago <= 0.0) break

                val aPagarEnEsteFiado = minOf(montoPago, fiado.saldoPendiente)

                val ok = db.registrarPagoFiado(fiado.idFiado, aPagarEnEsteFiado)
                if (ok) {
                    montoPago = "%.2f".format(montoPago - aPagarEnEsteFiado).toDouble()
                } else {
                    exitoEnPagos = false
                    break
                }
            }

            if (exitoEnPagos) {
                Toast.makeText(this, "Pago registrado ✅", Toast.LENGTH_SHORT).show()
                cargarDetalle(tvSaldoTotal, rvFiados, tvSinDeudas)
                dialog.dismiss()
            } else {
                tvError.visibility = View.VISIBLE
                tvError.text = "Error al procesar parte del pago, intenta de nuevo"
            }
        }

        dialog.show()
    }
}