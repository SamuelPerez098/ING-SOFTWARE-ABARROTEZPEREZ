package com.example.ing_software_abarrotezperez.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
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

        val rvFiados         = findViewById<RecyclerView>(R.id.rvDeudasCliente)
        val tvSinDeudas      = findViewById<TextView>(R.id.tvSinDeudas)

        // Se asigna únicamente el nombre sin el emoji de la persona
        //tvTitulo.text = nombreCliente

        val layoutManager = LinearLayoutManager(this)
        rvFiados.layoutManager = layoutManager

        cargarDetalle(tvSaldoTotal, rvFiados, tvSinDeudas)

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
            rvFiados.visibility = View.GONE
            if (tvSinDeudas.visibility != View.VISIBLE) {
                Motion.fadeInUp(tvSinDeudas, 6f * tvSinDeudas.resources.displayMetrics.density)
            }
        } else {
            rvFiados.visibility = View.VISIBLE
            if (tvSinDeudas.visibility == View.VISIBLE) Motion.fadeOut(tvSinDeudas)

            rvFiados.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                    val view = LayoutInflater.from(parent.context)
                        .inflate(R.layout.item_deuda, parent, false)
                    return object : RecyclerView.ViewHolder(view) {}
                }
                override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                    val fiado = fiados[position]
                    holder.itemView.findViewById<TextView>(R.id.tvDeudaTitulo).text = "Deuda #${position + 1}"
                    holder.itemView.findViewById<TextView>(R.id.tvDeudaMonto).text = "$${"%.2f".format(fiado.saldoPendiente)}"
                }
                override fun getItemCount() = fiados.size
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

        val tilDesc = etDesc.parent.parent as? TextInputLayout
        tilDesc?.hint = "Descripción (ej: leche, pan...)"
        etDesc.hint = ""

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val desc  = etDesc.text.toString().trim()
            val monto = etMonto.text.toString().toDoubleOrNull()

            if (desc.isEmpty() || monto == null || monto <= 0) {
                tvError.text = "Por favor completa todos los campos"
                Motion.fadeErrorIn(tvError)
                return@setOnClickListener
            }

            val ok = db.registrarFiado(idCliente, monto)
            if (ok) {
                Aviso.exito(this, "Deuda registrada", "$desc · ${"$%.2f".format(monto)}")
                cargarDetalle(tvSaldoTotal, rvFiados, tvSinDeudas)
                dialog.dismiss()
            } else {
                tvError.text = "Error al guardar, intenta de nuevo"
                Motion.fadeErrorIn(tvError)
            }
        }

        dialog.show()
    }

    private fun mostrarDialogoPago(tvSaldoTotal: TextView, rvFiados: RecyclerView, tvSinDeudas: TextView) {
        val fiados = db.getFiadosActivosPorCliente(idCliente)
        if (fiados.isEmpty()) {
            Aviso.info(this, "Sin deudas", "Este cliente no tiene deudas activas.")
            return
        }

        val dialogView  = LayoutInflater.from(this).inflate(R.layout.dialog_nuevo_fiado, null)
        val etDesc      = dialogView.findViewById<TextInputEditText>(R.id.etDescripcion)
        val etMonto     = dialogView.findViewById<TextInputEditText>(R.id.etMonto)
        val tvError     = dialogView.findViewById<TextView>(R.id.tvErrorFiado)
        val btnCancelar = dialogView.findViewById<Button>(R.id.btnCancelarFiado)
        val btnGuardar  = dialogView.findViewById<Button>(R.id.btnGuardarFiado)

        val tilDesc = etDesc.parent.parent as? TextInputLayout
        tilDesc?.visibility = View.GONE
        etDesc.visibility = View.GONE

        val saldoTotalMaximo = fiados.sumOf { it.saldoPendiente }

        val tilMonto = etMonto.parent.parent as? TextInputLayout
        tilMonto?.hint = "Monto a pagar (máx: $${"%.2f".format(saldoTotalMaximo)})"
        etMonto.hint = ""

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        btnCancelar.setOnClickListener { dialog.dismiss() }

        btnGuardar.setOnClickListener {
            val monto = etMonto.text.toString().toDoubleOrNull()

            if (monto == null || monto <= 0) {
                tvError.text = "Ingresa un monto válido"
                Motion.fadeErrorIn(tvError)
                return@setOnClickListener
            }

            var montoPago = "%.2f".format(monto).toDouble()
            val saldoMaximo = "%.2f".format(saldoTotalMaximo).toDouble()

            if (montoPago > saldoMaximo) {
                tvError.text = "El monto no puede ser mayor al saldo total ($${"%.2f".format(saldoMaximo)})"
                Motion.fadeErrorIn(tvError)
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
                Aviso.exito(this, "Pago registrado", "${"$%.2f".format(monto)}")
                cargarDetalle(tvSaldoTotal, rvFiados, tvSinDeudas)
                dialog.dismiss()
            } else {
                tvError.text = "Error al procesar parte del pago, intenta de nuevo"
                Motion.fadeErrorIn(tvError)
            }
        }

        dialog.show()
    }
}