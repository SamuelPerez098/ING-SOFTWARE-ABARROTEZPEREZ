package com.example.ing_software_abarrotezperez.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.content.ComponentCallbacks2
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.ai.engine.LlmAvailability
import com.example.ing_software_abarrotezperez.viewmodel.ChatViewModel
import com.google.android.material.chip.Chip
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

/** Pantalla de conversación con Pérez AI. Solo consulta datos; no modifica nada del punto de venta. */
class ChatActivity : AppCompatActivity() {

    private lateinit var vm: ChatViewModel
    private lateinit var adapter: ChatAdapter
    private lateinit var rvChat: RecyclerView
    private lateinit var etPregunta: TextInputEditText
    private lateinit var btnEnviar: ImageButton
    private lateinit var layoutPensando: View
    private lateinit var tvEstado: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Misma regla que el resto de la app: sin sesión no se muestran datos del negocio.
        val sesion = getSharedPreferences("SesionApp", MODE_PRIVATE)
        if (!sesion.getBoolean("isLogged", false)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_chat)
        vm = ViewModelProvider(this)[ChatViewModel::class.java]

        rvChat = findViewById(R.id.rvChat)
        etPregunta = findViewById(R.id.etPregunta)
        btnEnviar = findViewById(R.id.btnEnviar)
        layoutPensando = findViewById(R.id.layoutPensando)
        tvEstado = findViewById(R.id.tvEstadoOffline)
        val btnBorrar = findViewById<ImageButton>(R.id.btnBorrarHistorial)
        val llSugerencias = findViewById<LinearLayout>(R.id.llSugerencias)

        adapter = ChatAdapter()
        rvChat.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        rvChat.adapter = adapter

        btnEnviar.setOnClickListener { enviar() }
        btnBorrar.setOnClickListener { confirmarBorrado() }
        crearSugerencias(llSugerencias)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.messages.collect { lista ->
                        adapter.submitList(lista) {
                            if (lista.isNotEmpty()) rvChat.scrollToPosition(lista.size - 1)
                        }
                    }
                }
                launch {
                    vm.modelStatus.collect { tvEstado.text = textoEstado(it) }
                }
                launch {
                    vm.thinking.collect { pensando ->
                        if (pensando) {
                            Motion.fadeInUp(
                                layoutPensando,
                                8f * resources.displayMetrics.density
                            )
                        } else {
                            Motion.fadeOut(layoutPensando)
                        }
                        btnEnviar.isEnabled = !pensando
                        if (pensando && adapter.itemCount > 0) rvChat.scrollToPosition(adapter.itemCount - 1)
                    }
                }
            }
        }
    }

    /** Siempre indica que funciona sin Internet; añade si la IA local está disponible. */
    private fun textoEstado(s: LlmAvailability) = when (s) {
        LlmAvailability.READY -> "● Sin Internet · IA local activa"
        LlmAvailability.AVAILABLE -> "● Sin Internet · IA local lista"
        LlmAvailability.LOADING -> "● Sin Internet · cargando IA local…"
        LlmAvailability.LOW_MEMORY -> "● Sin Internet · IA local en pausa (poca memoria)"
        LlmAvailability.NOT_INSTALLED, LlmAvailability.FAILED -> "● Sin Internet · modo básico"
    }

    // Si el sistema pide memoria, se descarga el modelo; se vuelve a cargar solo cuando haga falta.
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (::vm.isInitialized && level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) vm.releaseModel()
    }

    private fun enviar() {
        val texto = etPregunta.text?.toString().orEmpty().trim()
        if (texto.isEmpty()) return
        etPregunta.setText("")
        vm.send(texto)
    }

    private fun crearSugerencias(contenedor: LinearLayout) {
        val sugerencias = listOf(
            "Caducan en 3 semanas",
            "Más vendidos",
            "Pocas existencias",
            "Ventas de esta semana",
            "Valor del inventario",
            "¿Qué necesito comprar?"
        )
        sugerencias.forEach { texto ->
            val chip = Chip(this).apply {
                text = texto
                isClickable = true
                setOnClickListener { if (btnEnviar.isEnabled) vm.send(texto) }
            }
            contenedor.addView(chip, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * resources.displayMetrics.density).toInt() })
        }
    }

    private fun confirmarBorrado() {
        Dialogos.confirmar(
            this,
            "Borrar historial",
            "Se borrará la conversación guardada en este teléfono. Tus ventas e inventario no se tocan.",
            "Borrar"
        ) { vm.clearHistory() }
    }
}
