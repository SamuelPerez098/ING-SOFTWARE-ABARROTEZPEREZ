package com.example.ing_software_abarrotezperez.ai.engine

import android.content.Context
import java.io.File

/**
 * Busca el archivo del modelo (.litertlm) FUERA del APK: en el almacenamiento interno de la app
 * (donde lo deja `adb shell run-as ... cp`) o en la carpeta externa propia de la app.
 */
class ModelLocator(private val context: Context) {

    fun find(): File? {
        val dirs = listOfNotNull(context.filesDir, context.getExternalFilesDir(null))
        return dirs.asSequence()
            .flatMap { d -> (d.listFiles() ?: emptyArray()).asSequence() }
            .filter { it.isFile && it.extension.equals("litertlm", ignoreCase = true) && it.length() >= MIN_BYTES }
            .sortedBy { it.name }
            .firstOrNull()
    }

    companion object {
        /** Menos que esto se considera una descarga incompleta o dañada. */
        const val MIN_BYTES = 50L * 1024 * 1024
    }
}
