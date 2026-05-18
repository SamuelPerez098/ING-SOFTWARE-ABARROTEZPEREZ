package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.cardview.widget.CardView
import com.example.ing_software_abarrotezperez.R

class ConfiguracionActivity : AppCompatActivity() {

    // SharedPreferences donde se persiste la preferencia del usuario.
    // Se usa un nombre distinto al de sesión para no mezclar datos.
    private val PREFS_TEMA = "prefs_tema"
    private val KEY_MODO_OSCURO = "modo_oscuro"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        // ── 1. Botón "Mi Perfil" — SIN CAMBIOS ───────────────────
        val cardMiPerfil = findViewById<CardView>(R.id.cardMiPerfil)
        cardMiPerfil.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        // ── 2. Botón "Almacenamiento" — SIN CAMBIOS ───────────────
        val cardAlmacenamiento = findViewById<CardView>(R.id.cardAlmacenamiento)
        cardAlmacenamiento.setOnClickListener {
            startActivity(Intent(this, AlmacenamientoActivity::class.java))
        }

        // ── 3. Botón "Cerrar Sesión" — SIN CAMBIOS ────────────────
        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrarSesion)
        btnCerrarSesion.setOnClickListener {
            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLogged", false).apply()

            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finishAffinity()
        }

        // ── 4. Switch Modo Oscuro — NUEVO ─────────────────────────
        val switchModoOscuro = findViewById<Switch>(R.id.switchModoOscuro)

        // Leer la preferencia guardada y reflejarla en el switch
        // sin disparar el listener (por eso lo asignamos ANTES del listener)
        val modoOscuroActivo = getSharedPreferences(PREFS_TEMA, MODE_PRIVATE)
            .getBoolean(KEY_MODO_OSCURO, false)
        switchModoOscuro.isChecked = modoOscuroActivo

        // Escuchar cambios del usuario en tiempo real
        switchModoOscuro.setOnCheckedChangeListener { _, isChecked ->

            // 1. Guardar la preferencia para que persista entre sesiones
            getSharedPreferences(PREFS_TEMA, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_MODO_OSCURO, isChecked)
                .apply()

            // 2. Aplicar el modo de forma global e inmediata a toda la app
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            // La Activity se recrea automáticamente al cambiar el modo:
            // no hace falta llamar a recreate() manualmente.
        }
    }
}