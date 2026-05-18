package com.example.ing_software_abarrotezperez

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.work.*
import com.example.ing_software_abarrotezperez.ui.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ──────────────────────────────────────────
        // 1. BOTÓN DE CLIENTES (Manda a FiadoActivity)
        // ──────────────────────────────────────────
        val btnClientesExtra = findViewById<CardView>(R.id.btnClientesExtra)
        btnClientesExtra.setOnClickListener {
            try {
                val intent = Intent(this, FiadoActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Error al abrir Clientes: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }

        // Los demás botones que ya servían...
        findViewById<CardView>(R.id.btnIrVentas).setOnClickListener {
            startActivity(Intent(this, VentaActivity::class.java))
        }
        findViewById<CardView>(R.id.btnVerReportes).setOnClickListener {
            startActivity(Intent(this, ReportesActivity::class.java))
        }
        findViewById<CardView>(R.id.btnIrInventario).setOnClickListener {
            startActivity(Intent(this, InventarioActivity::class.java))
        }
        findViewById<CardView>(R.id.btnConfiguracionExtra).setOnClickListener {
            startActivity(Intent(this, ConfiguracionActivity::class.java))
        }

        // ──────────────────────────────────────────
        // 2. BOTÓN COMPRAS (Usando el ID btnIrFiados)
        // ──────────────────────────────────────────
        val btnOnline = findViewById<CardView>(R.id.btnIrFiados)
        btnOnline.setOnClickListener {
            // Se quitó el popup de respaldo y ahora lanza directamente CompraActivity
            val intent = Intent(this, CompraActivity::class.java)
            startActivity(intent)
        }

        // ──────────────────────────────────────────
        // 3. ACTIVAR RESPALDO AUTOMÁTICO SILENCIOSO
        // ──────────────────────────────────────────
        programarRespaldoMensual(this)
    }

    private fun programarRespaldoMensual(context: Context) {
        val currentDate = Calendar.getInstance()
        val dueDate = Calendar.getInstance()

        dueDate.set(Calendar.HOUR_OF_DAY, 4)
        dueDate.set(Calendar.MINUTE, 0)
        dueDate.set(Calendar.SECOND, 0)

        if (dueDate.before(currentDate)) {
            dueDate.add(Calendar.HOUR_OF_DAY, 24)
        }

        val timeDiff = dueDate.timeInMillis - currentDate.timeInMillis

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresCharging(true)
            .build()

        val backupWorkRequest = PeriodicWorkRequestBuilder<com.example.ing_software_abarrotezperez.data.DriveBackupWorker>(30, TimeUnit.DAYS)
            .setInitialDelay(timeDiff, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "DriveBackupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            backupWorkRequest
        )
    }
}