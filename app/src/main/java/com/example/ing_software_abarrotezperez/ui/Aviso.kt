package com.example.ing_software_abarrotezperez.ui

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import com.example.ing_software_abarrotezperez.R
import com.google.android.material.snackbar.BaseTransientBottomBar
import com.google.android.material.snackbar.Snackbar

/**
 * Avisos transitorios propios, construidos sobre un Snackbar de Material con
 * vista propia ([R.layout.view_aviso]). Sustituyen a los Toast de la app.
 *
 * Tipos y color (regla "Semáforo" + "Blanco Roto" de DESIGN.md):
 *  - EXITO: verde Venta `#00B259`
 *  - ERROR: rojo Merma `#B71C1C`
 *  - ADVERTENCIA: magenta de riesgo `#E42566`
 *  - INFO: azul de mostrador `#3A5BA0`
 *
 * Entra con [Motion.fadeInUp], sale con [Motion.fadeOut], dura 2.2 s, vibra
 * suave en EXITO/ERROR y se puede anclar sobre una vista para no tapar barras
 * fijas (p. ej. la barra de total de Venta).
 */
object Aviso {

    enum class Tipo { EXITO, ERROR, ADVERTENCIA, INFO }

    private const val DURACION_MS = 2200L
    private const val VIBRACION_MS = 35L

    /** Solo un aviso a la vez: el nuevo reemplaza al anterior. */
    private var enPantalla: Snackbar? = null

    fun exito(activity: Activity, titulo: String, detalle: String? = null, anchor: View? = null) =
        mostrar(activity, Tipo.EXITO, titulo, detalle, anchor)

    fun error(activity: Activity, titulo: String, detalle: String? = null, anchor: View? = null) =
        mostrar(activity, Tipo.ERROR, titulo, detalle, anchor)

    fun advertencia(activity: Activity, titulo: String, detalle: String? = null, anchor: View? = null) =
        mostrar(activity, Tipo.ADVERTENCIA, titulo, detalle, anchor)

    fun info(activity: Activity, titulo: String, detalle: String? = null, anchor: View? = null) =
        mostrar(activity, Tipo.INFO, titulo, detalle, anchor)

    fun mostrar(
        activity: Activity,
        tipo: Tipo,
        titulo: String,
        detalle: String? = null,
        anchor: View? = null
    ) {
        val root = LayoutInflater.from(activity).inflate(R.layout.view_aviso, null)
        root.findViewById<ImageView>(R.id.avisoIcono).setImageResource(iconoDe(tipo))
        root.setBackgroundResource(fondoDe(tipo))

        val tvTitulo = root.findViewById<TextView>(R.id.avisoTitulo)
        val tvDetalle = root.findViewById<TextView>(R.id.avisoDetalle)
        tvTitulo.text = titulo
        if (detalle.isNullOrBlank()) {
            tvDetalle.visibility = View.GONE
        } else {
            tvDetalle.text = detalle
            tvDetalle.visibility = View.VISIBLE
        }

        val anuncio = if (detalle.isNullOrBlank()) titulo else "$titulo. $detalle"
        root.contentDescription = anuncio

        val contenedor = activity.findViewById<View>(android.R.id.content)
        val snackbar = Snackbar.make(contenedor, "", Snackbar.LENGTH_INDEFINITE)
        anchor?.let { snackbar.setAnchorView(it) }

        val layout = snackbar.view as Snackbar.SnackbarLayout
        layout.setPadding(0, 0, 0, 0)
        layout.setBackgroundColor(Color.TRANSPARENT)
        layout.removeAllViews()
        val margen = (12 * activity.resources.displayMetrics.density).toInt()
        val lp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginStart = margen
            marginEnd = margen
        }
        layout.addView(root, lp)

        // El Snackbar solo funde su contenedor; el contenido lo anima Motion.
        // Dejamos el contenido transparente hasta que Motion lo haga entrar.
        root.alpha = 0f
        snackbar.setAnimationMode(BaseTransientBottomBar.ANIMATION_MODE_FADE)

        val anterior = enPantalla
        enPantalla = snackbar
        anterior?.dismiss()

        snackbar.addCallback(object : Snackbar.Callback() {
            override fun onShown(sb: Snackbar?) {
                Motion.fadeInUp(root, 8f * activity.resources.displayMetrics.density)
                if (tipo == Tipo.EXITO || tipo == Tipo.ERROR) vibrar(activity)
                root.announceForAccessibility(anuncio)
                root.postDelayed({
                    Motion.fadeOut(root)
                    root.postDelayed({ snackbar.dismiss() }, Motion.DUR_MEDIUM.toLong())
                }, DURACION_MS)
            }

            override fun onDismissed(sb: Snackbar?, event: Int) {
                if (enPantalla === sb) enPantalla = null
            }
        })

        snackbar.show()
    }

    @DrawableRes
    private fun iconoDe(tipo: Tipo): Int = when (tipo) {
        Tipo.EXITO -> R.drawable.ic_aviso_exito
        Tipo.ERROR -> R.drawable.ic_aviso_error
        Tipo.ADVERTENCIA -> R.drawable.ic_aviso_advertencia
        Tipo.INFO -> R.drawable.ic_aviso_info
    }

    @DrawableRes
    private fun fondoDe(tipo: Tipo): Int = when (tipo) {
        Tipo.EXITO -> R.drawable.bg_aviso_exito
        Tipo.ERROR -> R.drawable.bg_aviso_error
        Tipo.ADVERTENCIA -> R.drawable.bg_aviso_advertencia
        Tipo.INFO -> R.drawable.bg_aviso_info
    }

    private fun vibrar(activity: Activity) {
        val vibrator = activity.getSystemService(Vibrator::class.java) ?: return
        if (!vibrator.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(
                VibrationEffect.createOneShot(VIBRACION_MS, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(VIBRACION_MS)
        }
    }
}
