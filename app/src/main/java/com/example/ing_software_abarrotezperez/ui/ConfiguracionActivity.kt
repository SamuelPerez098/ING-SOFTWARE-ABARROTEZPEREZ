package com.example.ing_software_abarrotezperez.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import com.example.ing_software_abarrotezperez.R

class ConfiguracionActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_configuracion)

        // 1. Botón "Mi Perfil" (Abre la pantalla de la foto de perfil)
        val cardMiPerfil = findViewById<CardView>(R.id.cardMiPerfil)
        cardMiPerfil.setOnClickListener {
            startActivity(Intent(this, PerfilActivity::class.java))
        }

        // 2. Botón "Almacenamiento" (Abre la pantalla que calcula el peso de la BD y el APK)
        val cardAlmacenamiento = findViewById<CardView>(R.id.cardAlmacenamiento)
        cardAlmacenamiento.setOnClickListener {
            startActivity(Intent(this, AlmacenamientoActivity::class.java))
        }

        // 3. Botón "Cerrar Sesión" (Corregido para borrar la sesión antes de ir al Login)
        val btnCerrarSesion = findViewById<Button>(R.id.btnCerrarSesion)
        btnCerrarSesion.setOnClickListener {

            // --- NUEVO: Cambiamos el estado del login a false ---
            val sharedPref = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            sharedPref.edit().putBoolean("isLogged", false).apply()

            // Ahora sí, abrimos el Login de forma limpia
            val intent = Intent(this, LoginActivity::class.java)

            // Estas banderas limpian el historial de pantallas para que no puedas regresar con el botón de atrás
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)

            // Cierra por completo todas las actividades anteriores que queden en segundo plano
            finishAffinity()
        }
    }
}