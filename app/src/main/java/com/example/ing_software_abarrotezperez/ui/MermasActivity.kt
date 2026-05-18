package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

data class MermaItem(
    val nombre: String,
    val cantidad: Int,
    val motivo: String,
    val fecha: String,
    val perdida: Double
)

class MermasActivity : AppCompatActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var rvMermas: RecyclerView
    private lateinit var tvTotalMermas: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mermas)

        dbHelper = DatabaseHelper(this)
        rvMermas = findViewById(R.id.rvMermas)
        tvTotalMermas = findViewById(R.id.tvTotalMermasDinero) // Asegúrate de agregarlo al XML
        rvMermas.layoutManager = LinearLayoutManager(this)

        cargarMermas()
    }

    private fun cargarMermas() {
        val db = dbHelper.readableDatabase
        val listaMermas = mutableListOf<MermaItem>()
        var totalPerdida = 0.0

        val query = """
            SELECT p.nombre, m.cantidad, m.motivo, m.fecha, p.precio_compra 
            FROM merma m 
            JOIN producto p ON m.id_producto = p.id_producto 
            ORDER BY m.fecha DESC
        """.trimIndent()

        db.rawQuery(query, null).use { cursor ->
            while (cursor.moveToNext()) {
                val item = MermaItem(
                    nombre = cursor.getString(0),
                    cantidad = cursor.getInt(1),
                    motivo = cursor.getString(2),
                    fecha = cursor.getString(3),
                    perdida = cursor.getInt(1) * cursor.getDouble(4)
                )
                listaMermas.add(item)
                totalPerdida += item.perdida
            }
        }

        tvTotalMermas.text = "PÉRDIDA TOTAL: $${String.format("%.2f", totalPerdida)}"
        rvMermas.adapter = MermasAdapter(listaMermas)
    }

    inner class MermasAdapter(private val mermas: List<MermaItem>) :
        RecyclerView.Adapter<MermasAdapter.MermasViewHolder>() {

        inner class MermasViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val tvNombre: TextView = v.findViewById(R.id.tvNombreProducto)
            val tvDetalle: TextView = v.findViewById(R.id.tvDetalleMerma)
            val tvFecha: TextView = v.findViewById(R.id.tvFechaMerma)
            val tvPerdida: TextView = v.findViewById(R.id.tvPerdidaDinero)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MermasViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_merma, parent, false)
            return MermasViewHolder(view)
        }

        override fun onBindViewHolder(holder: MermasViewHolder, position: Int) {
            val item = mermas[position]
            holder.tvNombre.text = item.nombre
            holder.tvDetalle.text = "${item.cantidad} unidades (${item.motivo})"
            holder.tvFecha.text = item.fecha
            holder.tvPerdida.text = "-$${String.format("%.2f", item.perdida)}"
        }

        override fun getItemCount() = mermas.size
    }
}