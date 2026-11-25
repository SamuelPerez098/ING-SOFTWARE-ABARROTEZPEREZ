package com.example.ing_software_abarrotezperez.ui

import android.view.LayoutInflater
import android.view.View
import android.graphics.Color
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

class ClienteAdapter(
    private var clientes: List<DatabaseHelper.Cliente>,
    private var saldos: Map<Int, Double>,
    private val onVerClick: (DatabaseHelper.Cliente) -> Unit,
    private val onEditarClick: (DatabaseHelper.Cliente) -> Unit
) : RecyclerView.Adapter<ClienteAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvNombreCliente)
        val tvSaldo: TextView = view.findViewById(R.id.tvSaldoPendiente)
        val btnVer: Button = view.findViewById(R.id.btnVerDetalle)
        val btnEditar: Button = view.findViewById(R.id.btnEditarCliente)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_cliente_fiado, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cliente = clientes[position]
        val saldo = saldos[cliente.idCliente] ?: 0.0

        holder.tvNombre.text = cliente.nombre
        holder.tvSaldo.text = "Saldo: $${"%.2f".format(saldo)}"
        holder.tvSaldo.setTextColor(
            if (saldo > 0) Color.parseColor("#B71C1C")
            else MaterialColors.getColor(holder.tvSaldo, com.google.android.material.R.attr.colorOnSurfaceVariant)
        )

        holder.btnVer.setOnClickListener { onVerClick(cliente) }
        holder.btnEditar.setOnClickListener { onEditarClick(cliente) }
    }

    override fun getItemCount() = clientes.size

    // Actualizar lista al buscar
    fun actualizar(nuevaLista: List<DatabaseHelper.Cliente>, nuevosSaldos: Map<Int, Double>) {
        clientes = nuevaLista
        saldos = nuevosSaldos
        notifyDataSetChanged()
    }
}