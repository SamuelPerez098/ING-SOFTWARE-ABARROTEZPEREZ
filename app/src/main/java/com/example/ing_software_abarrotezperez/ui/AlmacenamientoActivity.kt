package com.example.ing_software_abarrotezperez.ui

import com.google.android.material.dialog.MaterialAlertDialogBuilder

import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ing_software_abarrotezperez.R
import com.example.ing_software_abarrotezperez.data.DriveHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AlmacenamientoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlmacenamientoActivity"
    }

    private lateinit var googleSignInClient: GoogleSignInClient

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email   = account?.email ?: return@registerForActivityResult

            DriveHelper.saveAccountEmail(applicationContext, email)
            Log.d(TAG, "Sign-In exitoso: $email")

            performDriveUpload(email)

        } catch (e: ApiException) {
            Log.e(TAG, "Sign-In fallido. Código de error: ${e.statusCode}", e)
            Aviso.error(
                this,
                "No se inició sesión",
                "Código ${e.statusCode}. Revisa tu configuración de Google."
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_almacenamiento)

        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("354895927491-fajslal55aav68sdup0ehvjle47lqkov.apps.googleusercontent.com")
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, signInOptions)

        val btnRespaldo = findViewById<MaterialCardView>(R.id.btnRealizarRespaldo)
        val btnRegresar = findViewById<Button>(R.id.btnRegresarAlmacenamiento)

        calcularEspacio()

        btnRespaldo.setOnClickListener {
            iniciarRespaldoDrive()
        }

        btnRegresar.setOnClickListener {
            finish()
        }
    }

    private fun iniciarRespaldoDrive() {
        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        val driveScope  = Scope(DriveScopes.DRIVE_FILE)

        if (lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, driveScope)) {
            val email = lastAccount.email ?: run {
                Aviso.error(this, "Cuenta sin correo", "No se pudo obtener el correo de la cuenta.")
                return
            }
            DriveHelper.saveAccountEmail(applicationContext, email)
            performDriveUpload(email)
        } else {
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                signInLauncher.launch(signInIntent)
            }
        }
    }

    private fun performDriveUpload(accountEmail: String) {
        val dbFile = applicationContext.getDatabasePath(DriveHelper.DB_NAME)
        if (!dbFile.exists()) {
            Aviso.error(
                this,
                "No se encontró la base de datos",
                "Archivo: ${DriveHelper.DB_NAME}"
            )
            return
        }

        val progressDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Subiendo respaldo…")
            .setMessage("Conectando con Google Drive.\nPor favor espera.")
            .setCancelable(false)
            .create()
        progressDialog.show()

        lifecycleScope.launch {
            val uploadResult = withContext(Dispatchers.IO) {
                runCatching {
                    val driveService = DriveHelper.buildDriveService(
                        applicationContext,
                        accountEmail
                    )
                    DriveHelper.uploadDatabase(applicationContext, driveService)
                }
            }

            progressDialog.dismiss()

            uploadResult.fold(
                onSuccess = { fileId ->
                    Log.d(TAG, "Respaldo exitoso. ID en Drive: $fileId")
                    Aviso.exito(
                        this@AlmacenamientoActivity,
                        "Respaldo subido",
                        accountEmail
                    )
                },
                onFailure = { error ->
                    Log.e(TAG, "Error al subir respaldo: ${error.message}", error)
                    Aviso.error(
                        this@AlmacenamientoActivity,
                        "No se subió el respaldo",
                        error.message ?: "Intenta de nuevo."
                    )
                }
            )
        }
    }

    private fun calcularEspacio() {
        val progressApp    = findViewById<CircularProgressIndicator>(R.id.progressAppUsage)
        val tvPorcentaje   = findViewById<TextView>(R.id.tvPorcentajeUso)
        val tvEspacioApp   = findViewById<TextView>(R.id.tvEspacioApp)
        val tvEspacioLibre = findViewById<TextView>(R.id.tvEspacioLibre)

        val dbFile = getDatabasePath(DriveHelper.DB_NAME)
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L

        val apkFile = File(applicationInfo.sourceDir)
        val apkSize = if (apkFile.exists()) apkFile.length() else 0L

        val cacheSize = obtenerTamanoDirectorio(cacheDir)
        val filesSize = obtenerTamanoDirectorio(filesDir)

        val appTotalBytes = dbSize + apkSize + cacheSize + filesSize
        val appMegabytes  = appTotalBytes / (1024.0 * 1024.0)

        val directorioInterno   = Environment.getDataDirectory()
        val estadisticasMemoria = StatFs(directorioInterno.path)

        val tamanoBloque       = estadisticasMemoria.blockSizeLong
        val totalBloques       = estadisticasMemoria.blockCountLong
        val bloquesDisponibles = estadisticasMemoria.availableBlocksLong

        val totalBytesTelefono  = totalBloques * tamanoBloque
        val bytesLibresTelefono = bloquesDisponibles * tamanoBloque
        val bytesUsadosTelefono = totalBytesTelefono - bytesLibresTelefono

        val gigabytesLibres = bytesLibresTelefono / (1024.0 * 1024.0 * 1024.0)

        val porcentajeUsoTelefono =
            ((bytesUsadosTelefono.toDouble() / totalBytesTelefono.toDouble()) * 100).toInt()

        tvEspacioApp.text   = "App y Base de Datos: ${String.format("%.2f", appMegabytes)} MB"
        tvEspacioLibre.text = "Espacio libre en teléfono: ${String.format("%.2f", gigabytesLibres)} GB"
        tvPorcentaje.text   = "$porcentajeUsoTelefono%"
        progressApp.setProgressCompat(porcentajeUsoTelefono, true)
    }

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