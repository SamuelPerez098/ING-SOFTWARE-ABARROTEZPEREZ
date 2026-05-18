package com.example.ing_software_abarrotezperez.ui

import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.ing_software_abarrotezperez.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File

class AlmacenamientoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_almacenamiento)

        val btnRespaldo = findViewById<MaterialCardView>(R.id.btnRealizarRespaldo)
        val btnRegresar = findViewById<Button>(R.id.btnRegresarAlmacenamiento)

        // Ejecutamos la magia al abrir la pantalla
        calcularEspacio()

        btnRespaldo.setOnClickListener {
            // Aquí iría la lógica de tu WorkManager de Google Drive si decides implementarla aquí
            Toast.makeText(this, "Iniciando respaldo en Google Drive...", Toast.LENGTH_LONG).show()
        }

        btnRegresar.setOnClickListener {
            finish()
        }
    }

    private fun calcularEspacio() {
        val progressApp = findViewById<CircularProgressIndicator>(R.id.progressAppUsage)
        val tvPorcentaje = findViewById<TextView>(R.id.tvPorcentajeUso)
        val tvEspacioApp = findViewById<TextView>(R.id.tvEspacioApp)
        val tvEspacioLibre = findViewById<TextView>(R.id.tvEspacioLibre)

        // 1. Calcular el peso exacto de la App (APK + Base de datos + Caché + Archivos)
        val dbFile = getDatabasePath("Abarrotes.db") // <- Pon aquí el nombre exacto de tu BD si es distinto
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L

        val apkFile = File(applicationInfo.sourceDir)
        val apkSize = if (apkFile.exists()) apkFile.length() else 0L

        val cacheSize = obtenerTamanoDirectorio(cacheDir)
        val filesSize = obtenerTamanoDirectorio(filesDir)

        val appTotalBytes = dbSize + apkSize + cacheSize + filesSize
        val appMegabytes = appTotalBytes / (1024.0 * 1024.0)

        // 2. Calcular el espacio de almacenamiento del teléfono
        val directorioInterno = Environment.getDataDirectory()
        val estadisticasMemoria = StatFs(directorioInterno.path)

        val tamanoBloque = estadisticasMemoria.blockSizeLong
        val totalBloques = estadisticasMemoria.blockCountLong
        val bloquesDisponibles = estadisticasMemoria.availableBlocksLong

        val totalBytesTelefono = totalBloques * tamanoBloque
        val bytesLibresTelefono = bloquesDisponibles * tamanoBloque
        val bytesUsadosTelefono = totalBytesTelefono - bytesLibresTelefono

        val gigabytesLibres = bytesLibresTelefono / (1024.0 * 1024.0 * 1024.0)

        // 3. Calcular el porcentaje general de uso del teléfono para la gráfica
        val porcentajeUsoTelefono = ((bytesUsadosTelefono.toDouble() / totalBytesTelefono.toDouble()) * 100).toInt()

        // 4. Actualizar la pantalla (UI)
        tvEspacioApp.text = "App y Base de Datos: ${String.format("%.2f", appMegabytes)} MB"
        tvEspacioLibre.text = "Espacio libre en teléfono: ${String.format("%.2f", gigabytesLibres)} GB"

        // Actualizamos el texto gigante y la barra de progreso con animación (si tu versión de Material lo soporta)
        tvPorcentaje.text = "$porcentajeUsoTelefono%"

        // Asignamos el valor a la gráfica. Si quieres que se vea animado al entrar, usamos setProgressCompat
        progressApp.setProgressCompat(porcentajeUsoTelefono, true)
    }

    // Función auxiliar que revisa todas las carpetas internas de la app para sacar el peso real
    private fun obtenerTamanoDirectorio(directorio: File?): Long {
        var tamano: Long = 0
        if (directorio != null && directorio.isDirectory) {
            for (archivo in directorio.listFiles() ?: emptyArray()) {
                if (archivo.isFile) {
                    tamano += archivo.length()
                } else {
                    tamano += obtenerTamanoDirectorio(archivo)
                }
            }
        } else if (directorio != null && directorio.isFile) {
            tamano += directorio.length()
        }
        return tamano
    }
}