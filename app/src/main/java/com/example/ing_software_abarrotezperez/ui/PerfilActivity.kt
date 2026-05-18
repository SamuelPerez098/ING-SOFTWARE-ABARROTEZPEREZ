package com.example.ing_software_abarrotezperez.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ing_software_abarrotezperez.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import java.io.File
import java.io.FileOutputStream

class PerfilActivity : AppCompatActivity() {

    private lateinit var ivFotoPerfil: ImageView
    // Se cambia a TextInputEditText para que coincida exactamente con el XML
    private lateinit var etNombrePerfil: TextInputEditText
    private lateinit var btnCambiarFoto: FloatingActionButton
    private lateinit var btnGuardarPerfil: Button

    private var uriCamara: Uri? = null
    private var imagenCambiada: Boolean = false

    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) { abrirCamara() }
        else { Toast.makeText(this, "Permiso denegado", Toast.LENGTH_SHORT).show() }
    }

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ivFotoPerfil.setImageURI(uri)
            uriCamara = uri
            imagenCambiada = true
        }
    }

    private val tomarFoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        if (exito) {
            ivFotoPerfil.setImageURI(uriCamara)
            imagenCambiada = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_perfil)

        ivFotoPerfil = findViewById(R.id.ivFotoPerfil)
        etNombrePerfil = findViewById(R.id.etNombrePerfil)
        btnCambiarFoto = findViewById(R.id.btnCambiarFoto)
        btnGuardarPerfil = findViewById(R.id.btnGuardarPerfil)

        // Funcionalidad para el botón de Regresar del XML
        val btnRegresar = findViewById<Button>(R.id.btnRegresarConfig)
        btnRegresar.setOnClickListener {
            finish() // Cierra la pantalla y vuelve a configuración
        }

        cargarDatosGuardados() // Carga foto y nombre

        btnCambiarFoto.setOnClickListener { mostrarOpcionesImagen() }

        btnGuardarPerfil.setOnClickListener {
            val nombre = etNombrePerfil.text.toString().trim()

            if (nombre.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa un nombre", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 1. Guardar el nombre en SharedPreferences
            val prefs = getSharedPreferences("PerfilUsuario", Context.MODE_PRIVATE)
            prefs.edit().putString("nombre_usuario", nombre).apply()

            // 2. Guardar la foto si cambió
            var fotoGuardadaCorrectamente = true
            if (imagenCambiada && uriCamara != null) {
                fotoGuardadaCorrectamente = guardarFotoInternamente(uriCamara!!)
            }

            mostrarAlertaExito(fotoGuardadaCorrectamente)
        }
    }

    private fun mostrarOpcionesImagen() {
        val opciones = arrayOf("Cámara", "Galería", "Cancelar")
        AlertDialog.Builder(this)
            .setTitle("Seleccionar foto")
            .setItems(opciones) { _, i ->
                when (i) {
                    0 -> verificarPermisoCamara()
                    1 -> pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }.show()
    }

    private fun verificarPermisoCamara() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            abrirCamara()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun abrirCamara() {
        val file = File(filesDir, "temp_camara.jpg")
        uriCamara = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        tomarFoto.launch(uriCamara)
    }

    private fun guardarFotoInternamente(uri: Uri): Boolean {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val file = File(filesDir, "foto_perfil.jpg")
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            imagenCambiada = false
            true
        } catch (e: Exception) { false }
    }

    private fun mostrarAlertaExito(esExitoso: Boolean) {
        AlertDialog.Builder(this)
            .setTitle(if (esExitoso) "¡Éxito!" else "Error")
            .setMessage(if (esExitoso) "Tus datos se guardaron correctamente." else "Error al guardar la imagen.")
            .setPositiveButton("Aceptar") { _, _ -> if (esExitoso) finish() }
            .show()
    }

    private fun cargarDatosGuardados() {
        // Cargar Nombre
        val prefs = getSharedPreferences("PerfilUsuario", Context.MODE_PRIVATE)
        etNombrePerfil.setText(prefs.getString("nombre_usuario", ""))

        // Cargar Foto
        val file = File(filesDir, "foto_perfil.jpg")
        if (file.exists()) {
            ivFotoPerfil.setImageURI(Uri.fromFile(file))
        }
    }
}