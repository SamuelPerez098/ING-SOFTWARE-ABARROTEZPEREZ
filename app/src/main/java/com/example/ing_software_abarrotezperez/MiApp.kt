package com.example.ing_software_abarrotezperez

import android.app.Application

class MiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Eliminamos el ActivityLifecycleCallbacks que cerraba la sesión.
        // Ahora Android respetará lo que esté guardado en las SharedPreferences.
    }
}