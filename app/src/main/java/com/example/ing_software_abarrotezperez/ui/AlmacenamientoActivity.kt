package com.example.ing_software_abarrotezperez.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
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

/**
 * AlmacenamientoActivity
 *
 * Pantalla de gestión de almacenamiento y respaldo.
 *
 * ── CAMBIOS APLICADOS (sólo los elementos indicados) ─────────────
 *
 *  • btnRealizarRespaldo: ahora inicia el flujo Google Sign-In
 *    con el scope DRIVE_FILE y, tras autenticarse, sube físicamente
 *    "tienda.db" a Google Drive mediante DriveHelper.
 *
 *  • Todo lo demás (btnRegresar, calcularEspacio, UI de almacenamiento)
 *    permanece SIN CAMBIOS respecto a la versión original.
 *
 * ──────────────────────────────────────────────────────────────────
 *  FLUJO DEL BOTÓN btnRealizarRespaldo
 * ──────────────────────────────────────────────────────────────────
 *  1.  Si el usuario ya inició sesión → sube directamente.
 *  2.  Si no → lanza el selector de cuenta Google (Sign-In).
 *  3.  Al obtener la cuenta: guarda el correo y sube la BD.
 * ──────────────────────────────────────────────────────────────────
 */
class AlmacenamientoActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AlmacenamientoActivity"
    }

    // ── Cliente de Google Sign-In ─────────────────────────────────

    private lateinit var googleSignInClient: GoogleSignInClient

    // ── Launcher moderno para el resultado de Sign-In ─────────────
    // Reemplaza el onActivityResult deprecado usando ActivityResultContracts.
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        // Este bloque se ejecuta cuando el usuario termina de elegir su cuenta
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val email   = account?.email ?: return@registerForActivityResult

            // Persistir el correo para que el Worker lo use en background
            DriveHelper.saveAccountEmail(applicationContext, email)
            Log.d(TAG, "Sign-In exitoso: $email")

            // Iniciar subida real a Drive
            performDriveUpload(email)

        } catch (e: ApiException) {
            Log.e(TAG, "Sign-In fallido. Código de error: ${e.statusCode}", e)
            Toast.makeText(
                this,
                "Error al iniciar sesión con Google (código: ${e.statusCode}). " +
                        "Verifica tu configuración en Google Cloud Console.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  LIFECYCLE
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_almacenamiento)

        // Configurar Google Sign-In
        // Se solicita el correo del usuario Y el permiso para crear/leer
        // archivos en su Google Drive (DriveScopes.DRIVE_FILE).
        val signInOptions = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, signInOptions)

        // ── Botones ───────────────────────────────────────────────

        val btnRespaldo = findViewById<MaterialCardView>(R.id.btnRealizarRespaldo)
        val btnRegresar = findViewById<Button>(R.id.btnRegresarAlmacenamiento)

        // Calcular y mostrar el espacio de almacenamiento al abrir la pantalla
        calcularEspacio()

        // Botón de respaldo: flujo completo Google Sign-In → subida Drive
        btnRespaldo.setOnClickListener {
            iniciarRespaldoDrive()
        }

        // Botón regresar: sin cambios
        btnRegresar.setOnClickListener {
            finish()
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  LÓGICA DE RESPALDO A DRIVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Punto de entrada del respaldo.
     *
     * Si el usuario ya autorizó la app en esta sesión o en una anterior
     * (y el scope Drive fue concedido), sube directamente.
     * En caso contrario, lanza el selector de cuenta de Google.
     */
    private fun iniciarRespaldoDrive() {
        // Verificar si ya hay una sesión activa con el permiso de Drive
        val lastAccount = GoogleSignIn.getLastSignedInAccount(this)
        val driveScope  = Scope(DriveScopes.DRIVE_FILE)

        if (lastAccount != null && GoogleSignIn.hasPermissions(lastAccount, driveScope)) {
            // ── Ya tiene sesión y permiso → subir directamente ────
            val email = lastAccount.email ?: run {
                Toast.makeText(this, "No se pudo obtener el correo de la cuenta.", Toast.LENGTH_SHORT).show()
                return
            }
            DriveHelper.saveAccountEmail(applicationContext, email)
            performDriveUpload(email)
        } else {
            // ── No tiene sesión o le falta el scope Drive → Sign-In ─
            // Se desconecta cualquier sesión previa sin el scope correcto
            // para forzar al usuario a conceder el permiso de Drive.
            googleSignInClient.signOut().addOnCompleteListener {
                val signInIntent = googleSignInClient.signInIntent
                signInLauncher.launch(signInIntent)
            }
        }
    }

    /**
     * Ejecuta la subida de "tienda.db" a Google Drive en un hilo de I/O.
     * Muestra un diálogo de progreso mientras opera y notifica el resultado.
     *
     * @param accountEmail Correo de la cuenta Google ya autenticada.
     */
    private fun performDriveUpload(accountEmail: String) {
        // Verificar que la base de datos existe antes de abrir cualquier diálogo
        val dbFile = applicationContext.getDatabasePath(DriveHelper.DB_NAME)
        if (!dbFile.exists()) {
            Toast.makeText(
                this,
                "No se encontró la base de datos '${DriveHelper.DB_NAME}'.\n" +
                        "Ruta esperada: ${dbFile.absolutePath}",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        // Diálogo de progreso (no cancela la operación una vez iniciada)
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("Subiendo respaldo…")
            .setMessage("Conectando con Google Drive.\nPor favor espera.")
            .setCancelable(false)
            .create()
        progressDialog.show()

        // Lanzar la subida en el hilo de I/O (nunca en el hilo principal)
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

            // Volver al hilo principal para actualizar la UI
            progressDialog.dismiss()

            uploadResult.fold(
                onSuccess = { fileId ->
                    Log.d(TAG, "Respaldo exitoso. ID en Drive: $fileId")
                    Toast.makeText(
                        this@AlmacenamientoActivity,
                        "✅ Respaldo subido correctamente a Google Drive.\nCuenta: $accountEmail",
                        Toast.LENGTH_LONG
                    ).show()
                },
                onFailure = { error ->
                    Log.e(TAG, "Error al subir respaldo: ${error.message}", error)
                    Toast.makeText(
                        this@AlmacenamientoActivity,
                        "❌ Error al subir el respaldo:\n${error.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  LÓGICA DE ALMACENAMIENTO — SIN MODIFICACIONES
    // ─────────────────────────────────────────────────────────────

    private fun calcularEspacio() {
        val progressApp  = findViewById<CircularProgressIndicator>(R.id.progressAppUsage)
        val tvPorcentaje = findViewById<TextView>(R.id.tvPorcentajeUso)
        val tvEspacioApp = findViewById<TextView>(R.id.tvEspacioApp)
        val tvEspacioLibre = findViewById<TextView>(R.id.tvEspacioLibre)

        // 1. Calcular el peso exacto de la App (APK + Base de datos + Caché + Archivos)
        val dbFile   = getDatabasePath("Abarrotes.db")
        val dbSize   = if (dbFile.exists()) dbFile.length() else 0L

        val apkFile  = File(applicationInfo.sourceDir)
        val apkSize  = if (apkFile.exists()) apkFile.length() else 0L

        val cacheSize = obtenerTamanoDirectorio(cacheDir)
        val filesSize = obtenerTamanoDirectorio(filesDir)

        val appTotalBytes  = dbSize + apkSize + cacheSize + filesSize
        val appMegabytes   = appTotalBytes / (1024.0 * 1024.0)

        // 2. Calcular el espacio de almacenamiento del teléfono
        val directorioInterno    = Environment.getDataDirectory()
        val estadisticasMemoria  = StatFs(directorioInterno.path)

        val tamanoBloque         = estadisticasMemoria.blockSizeLong
        val totalBloques         = estadisticasMemoria.blockCountLong
        val bloquesDisponibles   = estadisticasMemoria.availableBlocksLong

        val totalBytesTelefono   = totalBloques * tamanoBloque
        val bytesLibresTelefono  = bloquesDisponibles * tamanoBloque
        val bytesUsadosTelefono  = totalBytesTelefono - bytesLibresTelefono

        val gigabytesLibres      = bytesLibresTelefono / (1024.0 * 1024.0 * 1024.0)

        // 3. Calcular el porcentaje general de uso del teléfono para la gráfica
        val porcentajeUsoTelefono =
            ((bytesUsadosTelefono.toDouble() / totalBytesTelefono.toDouble()) * 100).toInt()

        // 4. Actualizar la pantalla (UI)
        tvEspacioApp.text   = "App y Base de Datos: ${String.format("%.2f", appMegabytes)} MB"
        tvEspacioLibre.text = "Espacio libre en teléfono: ${String.format("%.2f", gigabytesLibres)} GB"
        tvPorcentaje.text   = "$porcentajeUsoTelefono%"
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