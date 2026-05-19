package com.example.ing_software_abarrotezperez

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.example.ing_software_abarrotezperez.ui.LoginActivity

class MiApp : Application() {

    private var actividadesActivas = 0
    private val handler = Handler(Looper.getMainLooper())

    private val cerrarSesion = Runnable {
        if (actividadesActivas == 0) {
            val prefs = getSharedPreferences("SesionApp", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("isLogged", false).apply()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                actividadesActivas++
                handler.removeCallbacks(cerrarSesion)
            }
            override fun onActivityStopped(activity: Activity) {
                actividadesActivas--
                if (actividadesActivas == 0) {
                    // No hay ninguna pantalla visible — posiblemente salió de la app
                    handler.postDelayed(cerrarSesion, 700)
                }
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}