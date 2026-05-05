package com.example.ing_software_abarrotezperez.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

// 1 y 2. IMPORTS CORREGIDOS:
// Nota: Tu paquete tiene un pequeño error de tipeo ("bluethooth" en vez de "bluetooth").
// Lo he dejado exactamente como lo tienes en tu archivo para que funcione.
import com.example.ing_software_abarrotezperez.bluethooth.BluetoothScannerManager
import com.example.ing_software_abarrotezperez.data.DatabaseHelper

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScannerViewModel(app: Application) : AndroidViewModel(app) {

    val db = DatabaseHelper(app)
    val scanner = BluetoothScannerManager(app)

    // Último código escaneado (lo consumen las Activities según su contexto)
    private val _lastBarcode = MutableStateFlow<String?>(null)
    val lastBarcode: StateFlow<String?> = _lastBarcode

    // Estado de conexión Bluetooth
    val connectionState = scanner.stateFlow

    init {
        // Reenviar barcodeFlow → lastBarcode
        viewModelScope.launch {
            // Al resolver el import del scanner, Kotlin ya deduce automáticamente que 'code' es un String
            scanner.barcodeFlow.collect { code ->
                _lastBarcode.value = code
            }
        }
    }

    fun clearBarcode() { _lastBarcode.value = null }

    override fun onCleared() {
        super.onCleared()
        scanner.destroy()
        db.close()
    }
}