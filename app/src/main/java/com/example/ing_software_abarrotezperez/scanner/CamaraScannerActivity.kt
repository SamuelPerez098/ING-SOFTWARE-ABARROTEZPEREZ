package com.example.ing_software_abarrotezperez.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ing_software_abarrotezperez.R
import com.journeyapps.barcodescanner.DecoratedBarcodeView

/**
 * Pantalla completa de escaneo con la cámara (lectura única).
 * Devuelve el código leído en el extra [EXTRA_CODIGO] con RESULT_OK.
 * Se usa desde InventarioActivity.
 */
class CamaraScannerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CODIGO = "CODIGO_ESCANEADO"
    }

    private lateinit var dbv: DecoratedBarcodeView
    private lateinit var camara: CamaraScanner
    private var linternaEncendida = false
    private var yaEntregado = false

    private val pedirPermiso =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
            if (concedido) {
                camara.iniciar()
            } else {
                Toast.makeText(this, "Se necesita permiso de cámara para escanear", Toast.LENGTH_LONG).show()
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camara_scanner)

        dbv = findViewById(R.id.dbvScanner)
        val btnCerrar = findViewById<ImageButton>(R.id.btnCerrarCamara)
        val btnLinterna = findViewById<Button>(R.id.btnLinterna)

        camara = CamaraScanner(this, dbv) { codigo -> entregarCodigo(codigo) }

        btnCerrar.setOnClickListener { finish() }

        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)) {
            btnLinterna.isEnabled = false
        }
        btnLinterna.setOnClickListener {
            linternaEncendida = !linternaEncendida
            camara.linterna(linternaEncendida)
            btnLinterna.text = if (linternaEncendida) "APAGAR" else "LINTERNA"
        }
    }

    private fun tienePermiso() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    override fun onResume() {
        super.onResume()
        if (tienePermiso()) camara.iniciar() else pedirPermiso.launch(Manifest.permission.CAMERA)
    }

    override fun onPause() {
        super.onPause()
        camara.pausar()
    }

    private fun entregarCodigo(codigo: String) {
        if (yaEntregado) return
        yaEntregado = true
        setResult(RESULT_OK, Intent().putExtra(EXTRA_CODIGO, codigo))
        finish()
    }
}
