package com.example.ing_software_abarrotezperez.data

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.FileContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import java.io.File

/**
 * DriveHelper
 *
 * Clase utilitaria (singleton) que centraliza toda la lógica de
 * autenticación y subida a Google Drive.
 *
 * Es usada tanto por AlmacenamientoActivity (respaldo manual)
 * como por DriveBackupWorker (respaldo automático en segundo plano).
 *
 * ──────────────────────────────────────────────────────────────────
 *  FLUJO GENERAL
 * ──────────────────────────────────────────────────────────────────
 *  1.  El usuario inicia sesión en AlmacenamientoActivity.
 *  2.  Se guarda su correo en SharedPreferences (KEY_ACCOUNT_EMAIL).
 *  3.  Cualquier llamada a uploadDatabase() usa ese correo para
 *      construir GoogleAccountCredential, que recupera el token
 *      OAuth automáticamente (sin UI) siempre que el usuario
 *      haya otorgado el permiso DriveScopes.DRIVE_FILE.
 * ──────────────────────────────────────────────────────────────────
 */
object DriveHelper {

    // ── Constantes ────────────────────────────────────────────────

    /** Nombre del SharedPreferences donde se persiste el correo */
    const val PREFS_NAME = "drive_prefs"

    /** Clave del correo seleccionado por el usuario en el Sign-In */
    const val KEY_ACCOUNT_EMAIL = "account_email"

    /** Nombre real de la base de datos SQLite del proyecto */
    const val DB_NAME = "tienda.db"

    /** Nombre de la aplicación para la cabecera del cliente Drive */
    private const val APP_NAME = "ING-SOFTWARE-ABARROTEZPEREZ"

    // ── Construcción del servicio Drive ───────────────────────────

    /**
     * Construye e inicializa el servicio Drive usando las credenciales
     * OAuth del [accountEmail] proporcionado.
     *
     * Este objeto es thread-safe para lecturas concurrentes pero
     * se debe construir desde Dispatchers.IO (es una operación de red).
     *
     * @param context  Contexto de la aplicación (no Activity).
     * @param accountEmail Correo de la cuenta Google ya autenticada.
     * @return Servicio [Drive] listo para operar.
     */
    fun buildDriveService(context: Context, accountEmail: String): Drive {
        // GoogleAccountCredential gestiona el ciclo de vida del token OAuth
        // de forma transparente: refresca tokens expirados sin mostrar UI.
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(DriveScopes.DRIVE_FILE))
            .apply {
                selectedAccountName = accountEmail
            }

        return Drive.Builder(
            NetHttpTransport(),          // transporte HTTP estándar de Android
            GsonFactory.getDefaultInstance(), // parser JSON ligero
            credential
        )
            .setApplicationName(APP_NAME)
            .build()
    }

    // ── Subida de la base de datos ────────────────────────────────

    /**
     * Sube (o actualiza) el archivo [DB_NAME] en Google Drive.
     *
     * ─ Si ya existe un archivo con ese nombre en el Drive del usuario
     *   (y no está en la papelera), lo reemplaza con el contenido actual.
     * ─ Si no existe ninguno, crea un archivo nuevo en la raíz de Drive.
     *
     * ⚠  Este método es BLOQUEANTE: ejecútalo siempre en Dispatchers.IO.
     *
     * @param context     Contexto de la aplicación.
     * @param driveService Instancia ya construida con [buildDriveService].
     * @return            El ID del archivo en Google Drive tras la operación.
     * @throws IllegalStateException  Si tienda.db no existe en el dispositivo.
     */
    fun uploadDatabase(context: Context, driveService: Drive): String {
        // Ruta correcta y canónica de la base de datos SQLite
        val dbFile: File = context.getDatabasePath(DB_NAME)

        check(dbFile.exists()) {
            "No se encontró la base de datos en: ${dbFile.absolutePath}"
        }

        // El MIME type correcto para archivos SQLite
        val mediaContent = FileContent("application/x-sqlite3", dbFile)

        // Verificar si ya existe un respaldo anterior en Drive
        val existingId = findExistingBackup(driveService)

        return if (existingId != null) {
            // ── UPDATE: reemplazar el archivo existente ────────────
            // Solo se actualiza el contenido binario; los metadatos se
            // pasan vacíos para no alterar el nombre ni la carpeta.
            driveService.files()
                .update(existingId, DriveFile().apply { name = DB_NAME }, mediaContent)
                .execute()
                .id
        } else {
            // ── CREATE: subir el archivo por primera vez ───────────
            val metadata = DriveFile().apply {
                name    = DB_NAME
                parents = listOf("root") // carpeta raíz del Drive del usuario
            }
            driveService.files()
                .create(metadata, mediaContent)
                .setFields("id")
                .execute()
                .id
        }
    }

    // ── Búsqueda de respaldo existente ────────────────────────────

    /**
     * Busca en Google Drive si ya existe un archivo llamado [DB_NAME]
     * que no esté en la papelera.
     *
     * @return El ID del primer resultado encontrado, o `null` si no existe.
     */
    private fun findExistingBackup(driveService: Drive): String? {
        val result = driveService.files().list()
            .setQ("name = '$DB_NAME' and trashed = false")
            .setSpaces("drive")
            .setFields("files(id, name)")
            .setPageSize(1) // sólo necesitamos el primero
            .execute()
        return result.files?.firstOrNull()?.id
    }

    // ── Helpers de SharedPreferences ─────────────────────────────

    /**
     * Persiste el correo de la cuenta Google en SharedPreferences para
     * que el Worker pueda recuperarlo sin interacción del usuario.
     */
    fun saveAccountEmail(context: Context, email: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCOUNT_EMAIL, email)
            .apply()
    }

    /**
     * Recupera el correo guardado previamente, o `null` si el usuario
     * nunca ha iniciado sesión desde esta app.
     */
    fun getSavedAccountEmail(context: Context): String? =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACCOUNT_EMAIL, null)
}