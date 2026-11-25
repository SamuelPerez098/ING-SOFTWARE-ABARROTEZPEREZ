package com.example.ing_software_abarrotezperez.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

class ProductoAdapter(
    private val context: Context,
    private var listaProductos: MutableList<DatabaseHelper.Producto>,
    private val dbHelper: DatabaseHelper
) : RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder>() {

    private fun avisar(accion: (Activity) -> Unit) {
        (context as? Activity)?.let(accion)
    }

    private val umbralBajo = AssistantConfig(context).lowStockThreshold

    private fun aplicarEstadoStock(tv: TextView, stock: Int) {
        val bg: Int
        val estilo: Int
        when {
            stock <= 0 -> {
                bg = R.drawable.bg_estado_peligro
                estilo = R.style.Widget_Abarrotes_Estado_Peligro
            }
            stock <= umbralBajo -> {
                bg = R.drawable.bg_estado_advertencia
                estilo = R.style.Widget_Abarrotes_Estado_Advertencia
            }
            else -> {
                bg = R.drawable.bg_estado_neutro
                estilo = R.style.Widget_Abarrotes_Estado_Neutro
            }
        }
        tv.setBackgroundResource(bg)
        tv.setTextAppearance(estilo)
    }

    class ProductoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvNombre: TextView = view.findViewById(R.id.tvItemNombre)
        val tvCodigo: TextView = view.findViewById(R.id.tvItemCodigo)
        val tvPrecio: TextView = view.findViewById(R.id.tvItemPrecio)
        val tvStock: TextView = view.findViewById(R.id.tvItemStock)
        val btnEditar: Button = view.findViewById(R.id.btnItemEditar)
        val btnPromo: Button = view.findViewById(R.id.btnItemPromo)
        val btnEliminar: Button = view.findViewById(R.id.btnItemEliminar)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductoViewHolder {
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
        aplicarEstadoStock(holder.tvStock, producto.stock)

        // LÓGICA DEL BOTÓN EDITAR
        holder.btnEditar.setOnClickListener {

            val intent = Intent(context, InventarioActivity::class.java).apply {

                // Código del producto a editar
                putExtra("CODIGO_A_EDITAR", producto.codigoBarras)

                // IMPORTANTE:
                // Esto evita que se sume +1 al stock automáticamente
                putExtra("SOLO_EDITAR", true)

                // Opcional
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            context.startActivity(intent)
        }

        // LÓGICA DEL BOTÓN PROMOCIÓN / PRECIO EXACTO
        holder.btnPromo.setOnClickListener {
            val builder = MaterialAlertDialogBuilder(context)
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

                    // --- NUEVA VALIDACIÓN: PRECIO DE COMPRA ---
                    if (nuevoPrecio < producto.precioCompra) {
                        avisar { Aviso.error(it, "Precio inválido", "No puede ser menor a la compra: ${"$%.2f".format(producto.precioCompra)}") }
                        return@setPositiveButton // Detiene la ejecución aquí, no guarda en BD
                    }
                    // ------------------------------------------

                    // Actualizar en Base de Datos
                    val exito = dbHelper.actualizarPrecioVenta(producto.codigoBarras, nuevoPrecio)

                    if (exito) {
                        // Actualizar la lista visualmente
                        producto.precioVenta = nuevoPrecio
                        notifyItemChanged(position)
                        avisar { Aviso.exito(it, "Precio actualizado", "${producto.nombre} · ${"$%.2f".format(nuevoPrecio)}") }
                    } else {
                        avisar { Aviso.error(it, "No se actualizó el precio", producto.nombre) }
                    }
                }
            }
            builder.setNegativeButton("Cancelar") { dialog, _ -> dialog.cancel() }
            builder.show()
        }

        // LÓGICA DEL BOTÓN ELIMINAR (BASURERO)
        holder.btnEliminar.setOnClickListener {
            avisar { activity ->
                Dialogos.confirmar(
                    activity,
                    "Eliminar producto",
                    "¿Eliminar '${producto.nombre}' definitivamente del inventario?",
                    "Sí, eliminar"
                ) {
                    val exito = dbHelper.eliminarProducto(producto.codigoBarras)
                    if (exito) {
                        listaProductos.removeAt(position)
                        notifyItemRemoved(position)
                        notifyItemRangeChanged(position, listaProductos.size)
                        Aviso.exito(activity, "Producto eliminado", producto.nombre)
                    } else {
                        Aviso.error(activity, "No se eliminó el producto", producto.nombre)
                    }
                }
            }
        }
    }

    // Función para refrescar la lista completa si es necesario
    fun actualizarDatos(nuevaLista: List<DatabaseHelper.Producto>) {
        listaProductos.clear()
        listaProductos.addAll(nuevaLista)
        notifyDataSetChanged()
    }
}