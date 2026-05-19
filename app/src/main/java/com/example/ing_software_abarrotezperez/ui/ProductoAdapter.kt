package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

class ProductoAdapter(
    private val context: Context,
    private var listaProductos: MutableList<DatabaseHelper.Producto>,
    private val dbHelper: DatabaseHelper
) : RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder>() {

    class ProductoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvItemNombre)
        val tvCodigo: TextView = view.findViewById(R.id.tvItemCodigo)
        val tvPrecio: TextView = view.findViewById(R.id.tvItemPrecio)
        val tvStock: TextView = view.findViewById(R.id.tvItemStock)
        val btnEditar: Button = view.findViewById(R.id.btnItemEditar)
        val btnPromo: Button = view.findViewById(R.id.btnItemPromo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
        // Fíjate muy bien aquí: es R.layout (no R.id)
        val view = LayoutInflater.from(context).inflate(R.layout.item_producto, parent, false)
        return ProductoViewHolder(view)
    }

    override fun getItemCount(): Int = listaProductos.size

    override fun onBindViewHolder(holder: ProductoViewHolder, position: Int) {
        val producto = listaProductos[position]

        holder.tvNombre.text = producto.nombre
        holder.tvCodigo.text = "Código: ${producto.codigoBarras}"
        holder.tvPrecio.text = "Precio: $${producto.precioVenta}"
        holder.tvStock.text = "Stock: ${producto.stock}"

        // LÓGICA DEL BOTÓN EDITAR
        holder.btnEditar.setOnClickListener {
            val intent = Intent(context, InventarioActivity::class.java).apply {
                putExtra("CODIGO_A_EDITAR", producto.codigoBarras)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP // Limpia la pila para no abrir mil ventanas
            }
            context.startActivity(intent)
        }

        // LÓGICA DEL BOTÓN PROMOCIÓN
        // LÓGICA DEL BOTÓN PROMOCIÓN / PRECIO EXACTO
        holder.btnPromo.setOnClickListener {
            val builder = AlertDialog.Builder(context)
            builder.setTitle("Modificar Precio / Promoción")
            builder.setMessage("Ingresa el NUEVO PRECIO exacto para ${producto.nombre}.\n\n(Para quitar una promoción, simplemente ingresa el precio normal).")

            // Campo de texto para el nuevo precio
            val input = EditText(context)
            input.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            // Autocompletamos con el precio actual para que sea más fácil de editar
            input.setText(producto.precioVenta.toString())
            builder.setView(input)

            builder.setPositiveButton("Guardar Precio") { _, _ ->
                val nuevoPrecioStr = input.text.toString()
                if (nuevoPrecioStr.isNotEmpty()) {
                    val nuevoPrecio = nuevoPrecioStr.toDouble()

                    // Actualizar en Base de Datos
                    val exito = dbHelper.actualizarPrecioVenta(producto.codigoBarras, nuevoPrecio)

                    if (exito) {
                        // Actualizar la lista visualmente
                        producto.precioVenta = nuevoPrecio
                        notifyItemChanged(position)
                        Toast.makeText(context, "Precio actualizado a $${nuevoPrecio}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Error al actualizar precio", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
            builder.show()
        }
    }

    // Función para refrescar la lista completa si es necesario
    fun actualizarDatos(nuevaLista: List<DatabaseHelper.Producto>) {
        listaProductos.clear()
        listaProductos.addAll(nuevaLista)
        notifyDataSetChanged()
    }
}