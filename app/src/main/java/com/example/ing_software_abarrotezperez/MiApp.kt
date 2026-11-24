package com.example.ing_software_abarrotezperez

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Modo oscuro: se lee UNA sola vez al iniciar el proceso, desde cualquier
        // pantalla. Evita que, al restaurar el proceso, la app siga el tema del
        // sistema e ignore la preferencia del usuario.
        val modoOscuro = getSharedPreferences("prefs_tema", MODE_PRIVATE)
            .getBoolean("modo_oscuro", false)
        AppCompatDelegate.setDefaultNightMode(
            if (modoOscuro) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        )
    }
}
