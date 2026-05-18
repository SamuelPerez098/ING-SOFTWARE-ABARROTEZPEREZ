package com.example.ing_software_abarrotezperez.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DriveBackupWorker
 *
 * Worker que ejecuta el respaldo automático de "tienda.db" a Google Drive
 * en segundo plano cuando WorkManager lo invoca (p. ej., todos los días
 * a las 4:00 AM, como estaba configurado en la versión anterior).
 *
 * ── PRE-REQUISITO ─────────────────────────────────────────────────
 *  El usuario DEBE haber iniciado sesión al menos UNA VEZ desde
 *  AlmacenamientoActivity. Esa sesión guarda el correo en
 *  SharedPreferences bajo la clave DriveHelper.KEY_ACCOUNT_EMAIL.
 *  Sin ese correo, el Worker no tiene credenciales y falla con gracia.
 *
 * ── MANEJO DE ERRORES ─────────────────────────────────────────────
 *  • UserRecoverableAuthIOException → el token fue revocado por el
 *    usuario en su cuenta Google. Se devuelve Result.failure() porque
 *    el Worker no puede mostrar UI. El usuario debe volver a la app
 *    y presionar "Crear Respaldo" para re-autorizarse.
 *
 *  • Cualquier otra excepción → Result.retry() para que WorkManager
 *    vuelva a intentarlo según su política de reintentos.
 *
 * ── SIN CAMBIOS ───────────────────────────────────────────────────
 *  La firma de la clase, su herencia de CoroutineWorker y el uso de
 *  inputData para leer KEY_CORREO_DRIVE permanecen intactos.
 */
class DriveBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "BackupWorker"

        /**
         * Clave opcional que puede pasarse desde WorkManager al encolar
         * la tarea. Si se provee, sobreescribe el correo guardado en prefs.
         * (Mantiene compatibilidad con el código anterior.)
         */
        const val KEY_CORREO_DRIVE = "KEY_CORREO_DRIVE"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Iniciando respaldo automático de '${DriveHelper.DB_NAME}'…")

        // ── 1. Obtener el correo de la cuenta ─────────────────────
        // Prioridad: inputData > SharedPreferences
        val accountEmail: String? =
            inputData.getString(KEY_CORREO_DRIVE)?.takeIf { it.isNotBlank() }
                ?: DriveHelper.getSavedAccountEmail(applicationContext)

        if (accountEmail.isNullOrBlank()) {
            Log.e(
                TAG,
                "No hay cuenta Google guardada. El usuario debe iniciar sesión " +
                        "desde AlmacenamientoActivity al menos una vez."
            )
            // Failure (no retry): sin credenciales no tiene sentido reintentar
            return Result.failure()
        }

        Log.d(TAG, "Usando cuenta: $accountEmail")

        // ── 2. Verificar que el archivo exista ────────────────────
        val dbFile = applicationContext.getDatabasePath(DriveHelper.DB_NAME)
        if (!dbFile.exists()) {
            Log.e(TAG, "No se encontró '${DriveHelper.DB_NAME}' en: ${dbFile.absolutePath}")
            return Result.failure()
        }

        // ── 3. Ejecutar la subida en Dispatchers.IO ───────────────
        // La biblioteca google-api-services-drive hace llamadas de red
        // bloqueantes; SIEMPRE debe correr fuera del hilo principal.
        return withContext(Dispatchers.IO) {
            try {
                val driveService = DriveHelper.buildDriveService(
                    applicationContext,
                    accountEmail
                )

                val fileId = DriveHelper.uploadDatabase(applicationContext, driveService)

                Log.d(TAG, "Respaldo completado. ID en Drive: $fileId")
                Result.success()

            } catch (e: UserRecoverableAuthIOException) {
                // El usuario revocó el permiso desde su cuenta Google.
                // No se puede recuperar sin interacción del usuario.
                Log.e(
                    TAG,
                    "Permiso de Drive revocado. El usuario debe volver a autorizar la app.",
                    e
                )
                // Limpiar el correo guardado para forzar un nuevo Sign-In
                DriveHelper.saveAccountEmail(applicationContext, "")
                Result.failure()

            } catch (e: Exception) {
                // Error genérico (sin red, cuota excedida, etc.)
                // WorkManager lo reintentará según la política configurada.
                Log.e(TAG, "Error al subir el respaldo: ${e.message}", e)
                Result.retry()
            }
        }
    }
}