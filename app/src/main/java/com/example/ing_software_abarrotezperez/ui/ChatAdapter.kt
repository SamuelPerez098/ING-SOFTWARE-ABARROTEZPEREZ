package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.ai.models.ChatMessage
import com.example.ing_software_abarrotezperez.ai.models.ChatRole
import com.example.ing_software_abarrotezperez.ai.models.ResultTable
import com.google.android.material.color.MaterialColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ChatAdapter : ListAdapter<ChatMessage, RecyclerView.ViewHolder>(Diff) {

    private val horaFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun getItemViewType(position: Int) =
        if (getItem(position).role == ChatRole.USER) TIPO_USUARIO else TIPO_ASISTENTE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TIPO_USUARIO)
            UsuarioHolder(inflater.inflate(R.layout.item_chat_usuario, parent, false))
        else
            AsistenteHolder(inflater.inflate(R.layout.item_chat_asistente, parent, false))
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val m = getItem(position)
        val hora = if (m.timestamp > 0) horaFmt.format(Date(m.timestamp)) else ""
        when (holder) {
            is UsuarioHolder -> {
                holder.tvMensaje.text = m.text
                holder.tvHora.text = hora
            }
            is AsistenteHolder -> holder.bind(m, hora)
        }
    }

    private class UsuarioHolder(v: View) : RecyclerView.ViewHolder(v) {
        val tvMensaje: TextView = v.findViewById(R.id.tvMensaje)
        val tvHora: TextView = v.findViewById(R.id.tvHora)
    }

    private class AsistenteHolder(v: View) : RecyclerView.ViewHolder(v) {
        private val tvMensaje: TextView = v.findViewById(R.id.tvMensaje)
        private val llTabla: LinearLayout = v.findViewById(R.id.llTabla)
        private val tvNotas: TextView = v.findViewById(R.id.tvNotas)
        private val tvHora: TextView = v.findViewById(R.id.tvHora)

        fun bind(m: ChatMessage, hora: String) {
            tvMensaje.text = m.text
            tvMensaje.setTextColor(
                MaterialColors.getColor(tvMensaje, if (m.isError) com.google.android.material.R.attr.colorError
                else com.google.android.material.R.attr.colorOnSurface)
            )
            tvHora.text = hora

            llTabla.removeAllViews()
            val tabla = m.table
            if (tabla != null && tabla.rows.isNotEmpty()) {
                llTabla.addView(construirTabla(llTabla.context, tabla))
                llTabla.visibility = View.VISIBLE
            } else {
                llTabla.visibility = View.GONE
            }

            if (m.notes.isNotEmpty()) {
                tvNotas.text = m.notes.joinToString("\n") { "• $it" }
                tvNotas.visibility = View.VISIBLE
            } else {
                tvNotas.visibility = View.GONE
            }
        }

        private fun construirTabla(ctx: Context, t: ResultTable): TableLayout {
            val dp = ctx.resources.displayMetrics.density
            fun px(v: Int) = (v * dp).toInt()
            val colorTexto = MaterialColors.getColor(llTabla, com.google.android.material.R.attr.colorOnSurface)
            val colorSuave = MaterialColors.getColor(llTabla, com.google.android.material.R.attr.colorOnSurfaceVariant)
            val colorLinea = MaterialColors.getColor(llTabla, com.google.android.material.R.attr.colorOutlineVariant)

            // Columnas numéricas (cantidades, dinero, fechas) se alinean a la derecha.
            val numerica = t.headers.indices.map { c ->
                t.rows.all { r -> r.getOrNull(c)?.let { it.isNotEmpty() && (it[0].isDigit() || it[0] in "$+-≈—") } == true }
            }

            fun celda(texto: String, col: Int, encabezado: Boolean) = TextView(ctx).apply {
                text = texto
                textSize = if (encabezado) 12f else 13f
                setTextColor(if (encabezado) colorSuave else colorTexto)
                if (encabezado) setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = if (numerica.getOrElse(col) { false }) Gravity.END else Gravity.START
                setPadding(px(if (col == 0) 0 else 10), px(5), 0, px(5))
            }

            return TableLayout(ctx).apply {
                isShrinkAllColumns = false
                setColumnShrinkable(0, true)
                setColumnStretchable(0, true)
                addView(TableRow(ctx).also { row -> t.headers.forEachIndexed { c, h -> row.addView(celda(h, c, true)) } })
                addView(View(ctx).apply { setBackgroundColor(colorLinea) }, TableLayout.LayoutParams(-1, px(1)))
                t.rows.forEach { fila ->
                    addView(TableRow(ctx).also { row ->
                        t.headers.indices.forEach { c -> row.addView(celda(fila.getOrElse(c) { "" }, c, false)) }
                    })
                }
            }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(a: ChatMessage, b: ChatMessage) = a.id == b.id
        override fun areContentsTheSame(a: ChatMessage, b: ChatMessage) = a == b
    }

    private companion object {
        const val TIPO_USUARIO = 0
        const val TIPO_ASISTENTE = 1
    }
}
