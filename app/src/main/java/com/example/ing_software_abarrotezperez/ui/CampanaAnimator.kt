package com.example.ing_software_abarrotezperez.ui

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator

object CampanaAnimator {

    fun iniciar(campana: View): ObjectAnimator? {
        // Respeta la configuración de "Escala de duración de animación" del sistema
        if (!ValueAnimator.areAnimatorsEnabled()) return null

        campana.post {
            campana.pivotX = campana.width / 2f   // centro horizontal
            campana.pivotY = 0f                   // parte superior, como una campana colgando
        }
        return ObjectAnimator.ofFloat(campana, View.ROTATION, 25f, -25f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            repeatMode = ValueAnimator.REVERSE
            repeatCount = 5                       // finita: 6 vaivenes (~3.6 s) y luego reposa
            start()
        }
    }

    fun detener(campana: View, animador: ObjectAnimator?) {
        animador?.cancel()
        campana.rotation = 0f
    }
}
