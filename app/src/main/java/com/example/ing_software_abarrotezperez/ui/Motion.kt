package com.example.ing_software_abarrotezperez.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import androidx.recyclerview.widget.RecyclerView

/**
 * Punto único de animación de la app.
 *
 * Reglas (ver AGENTS.md y DESIGN.md):
 *  - Toda animación respeta [ValueAnimator.areAnimatorsEnabled]; si el sistema
 *    las desactiva, se aplica el estado final sin animar.
 *  - Entradas: Decelerate. Salidas: Accelerate.
 *  - Alta frecuencia (escaneo, carrito, cobro): 100 ms o nada.
 */
object Motion {

    /** 100 ms: feedback rápido (presión, habilitar/deshabilitar). */
    const val DUR_FAST = 100

    /** 150 ms: fundidos de salida y errores. */
    const val DUR_MEDIUM = 150

    /** 180 ms: entradas de contenido. */
    const val DUR_ENTER = 180

    /** 300 ms: tope para transiciones de UI. */
    const val DUR_SLOW = 300

    private const val PRESS_DOWN_MS = 110L
    private const val PRESS_UP_MS = 140L

    /** Entrada: desacelera al llegar. */
    val entrada: Interpolator = DecelerateInterpolator()

    /** Salida: acelera al irse. */
    val salida: Interpolator = AccelerateInterpolator()

    /**
     * Feedback de presión: escala a 0.98 al tocar y vuelve a 1.0 al soltar.
     * El listener devuelve false para no consumir el toque: el click y el
     * ripple del tema siguen funcionando.
     */
    fun pressFeedback(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        v.animate()
                            .scaleX(0.98f)
                            .scaleY(0.98f)
                            .setDuration(PRESS_DOWN_MS)
                            .setInterpolator(entrada)
                            .start()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (ValueAnimator.areAnimatorsEnabled()) {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(PRESS_UP_MS)
                            .setInterpolator(salida)
                            .start()
                    } else {
                        v.scaleX = 1f
                        v.scaleY = 1f
                    }
                }
            }
            false
        }
    }

    /**
     * Entrada de contenido: aparece con fundido y sube hasta su sitio.
     *
     * @param dy desplazamiento inicial en píxeles (p. ej. `8f * density`).
     */
    fun fadeInUp(view: View, dy: Float) {
        view.visibility = View.VISIBLE
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = dy
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DUR_ENTER.toLong())
            .setInterpolator(entrada)
            .start()
    }

    /**
     * Salida: funde a opacidad 0 y oculta la vista (GONE) al terminar.
     */
    fun fadeOut(view: View) {
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 0f
            view.visibility = View.GONE
            return
        }
        view.animate()
            .alpha(0f)
            .setDuration(DUR_MEDIUM.toLong())
            .setInterpolator(salida)
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
            }
            .start()
    }

    /** Error de formulario: fundido de entrada con un leve ascenso de 4 dp. */
    fun fadeErrorIn(view: View) {
        view.visibility = View.VISIBLE
        val dy = 4f * view.resources.displayMetrics.density
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = 1f
            view.translationY = 0f
            return
        }
        view.alpha = 0f
        view.translationY = dy
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(DUR_MEDIUM.toLong())
            .setInterpolator(entrada)
            .start()
    }

    /**
     * Habilita o deshabilita un control animando su opacidad
     * (activo 1.0, inactivo 0.5) y aplicando [View.isEnabled] de inmediato.
     */
    fun setEnabledAnimated(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        val target = if (enabled) 1f else 0.5f
        if (!ValueAnimator.areAnimatorsEnabled()) {
            view.alpha = target
            return
        }
        view.animate()
            .alpha(target)
            .setDuration(DUR_FAST.toLong())
            .setInterpolator(if (enabled) entrada else salida)
            .start()
    }

    /**
     * Para listas de alta frecuencia (carrito de Venta): el ítem entra sin
     * animación; el escaneo ya confirma por vibración y estado.
     */
    fun sinAnimacionDeInsercion(recyclerView: RecyclerView) {
        recyclerView.itemAnimator = null
    }
}

/**
 * Animación de atención de la campanita de caducidades.
 * Finita: 5 ciclos (≈3.6 s) y reposa; nunca en bucle infinito.
 */
object CampanaAnimator {

    fun iniciar(campana: View): ObjectAnimator? {
        if (!ValueAnimator.areAnimatorsEnabled()) return null

        campana.post {
            campana.pivotX = campana.width / 2f
            campana.pivotY = 0f
        }
        return ObjectAnimator.ofFloat(campana, View.ROTATION, 25f, -25f).apply {
            duration = 600L
            interpolator = AccelerateDecelerateInterpolator()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 5
            start()
        }
    }

    fun detener(campana: View, animador: ObjectAnimator?) {
        animador?.cancel()
        campana.rotation = 0f
    }
}
