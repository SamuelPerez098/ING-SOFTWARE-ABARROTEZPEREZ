package com.example.ing_software_abarrotezperez.ui

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogos unificados: misma forma (esquinas de 24dp vía
 * `ThemeOverlay.Abarrotes.Dialogo`), tipografía y botones del tema, válidos en
 * claro y oscuro. Cubre los dos casos comunes; los diálogos con vista propia
 * siguen usando [MaterialAlertDialogBuilder] con el mismo tema.
 */
object Dialogos {

    /** Aviso informativo con un solo botón de cierre. */
    fun informacion(
        activity: Activity,
        titulo: String,
        mensaje: String,
        textoBoton: String = "Entendido"
    ): AlertDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(titulo)
        .setMessage(mensaje)
        .setPositiveButton(textoBoton, null)
        .show()

    /** Confirmación de dos botones; [onConfirmar] corre solo si se acepta. */
    fun confirmar(
        activity: Activity,
        titulo: String,
        mensaje: String,
        textoConfirmar: String = "Aceptar",
        textoCancelar: String = "Cancelar",
        onConfirmar: () -> Unit
    ): AlertDialog = MaterialAlertDialogBuilder(activity)
        .setTitle(titulo)
        .setMessage(mensaje)
        .setPositiveButton(textoConfirmar) { _, _ -> onConfirmar() }
        .setNegativeButton(textoCancelar, null)
        .show()
}
