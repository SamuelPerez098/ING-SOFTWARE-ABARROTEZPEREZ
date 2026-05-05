package com.example.ing_software_abarrotezperez.bluethooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * BluetoothScannerManager
 *
 * Soporta tres modos del escáner Eyoyo:
 *  - SPP  (Serial Port Profile) — PRIMARIO: comunicación serial bidireccional
 *  - HID  (Human Interface Device) — PASIVO: el SO lo trata como teclado, no hay socket
 *  - BLE  (Bluetooth Low Energy) — Notificaciones GATT
 *
 * Uso típico:
 *   val mgr = BluetoothScannerManager(context)
 *   mgr.barcodeFlow.collect { codigo -> /* manejar */ }
 *   mgr.connectSpp(device)   // o connectBle(device)
 *
 * Modo HID: el escáner ya emparejado como teclado envía el texto al campo de
 * texto que tenga foco.  Para capturarlo, usa [hidInputBuffer] en el Activity.
 */
class BluetoothScannerManager(private val context: Context) {

    companion object {
        private const val TAG = "EyoyoBT"

        // UUID estándar SPP
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // UUID estándar BLE Nordic UART (muchos escáneres Eyoyo BLE lo usan)
        val BLE_SERVICE_UUID: UUID  = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        val BLE_NOTIFY_UUID:  UUID  = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")
    }

    // ── Estado de conexión ──────────────────────────────────────────────────
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED_SPP, CONNECTED_BLE, HID_MODE }

    private val _barcodeFlow = MutableSharedFlow<String>(extraBufferCapacity = 32)
    /** Emite el código de barras cada vez que el escáner lee uno. */
    val barcodeFlow: SharedFlow<String> = _barcodeFlow

    private val _stateFlow = MutableSharedFlow<ConnectionState>(
        replay = 1, extraBufferCapacity = 8
    )
    val stateFlow: SharedFlow<ConnectionState> = _stateFlow

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bm?.adapter
    }

    private var sppSocket: BluetoothSocket? = null
    private var sppJob: Job? = null

    // ─────────────────────────────────────────────────────────────────────────
    //  PERMISOS — helper
    // ─────────────────────────────────────────────────────────────────────────

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  SPP — MODO PRIMARIO
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Devuelve los dispositivos ya emparejados.
     * Filtra los que tengan el UUID SPP (escáneres Eyoyo ya configurados en SPP).
     */
    @SuppressLint("MissingPermission")
    fun getPairedSppDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return adapter.bondedDevices?.filter { device ->
            device.uuids?.any { it.uuid == SPP_UUID } == true
        } ?: emptyList()
    }

    /** Devuelve TODOS los dispositivos emparejados (útil para selección manual) */
    @SuppressLint("MissingPermission")
    fun getAllPairedDevices(): List<BluetoothDevice> =
        bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

    /**
     * Conecta al escáner Eyoyo en modo SPP.
     * Lanza una coroutine que:
     *  1. Abre el socket
     *  2. Lee líneas del InputStream
     *  3. Emite cada línea (= código de barras) en [barcodeFlow]
     */
    @SuppressLint("MissingPermission")
    fun connectSpp(device: BluetoothDevice) {
        disconnectSpp()
        sppJob = scope.launch {
            _stateFlow.emit(ConnectionState.CONNECTING)
            try {
                val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothAdapter?.cancelDiscovery()
                socket.connect()
                sppSocket = socket
                _stateFlow.emit(ConnectionState.CONNECTED_SPP)
                Log.i(TAG, "SPP conectado a ${device.name}")
                readSppStream(socket.inputStream)
            } catch (e: IOException) {
                Log.e(TAG, "Error SPP: ${e.message}")
                _stateFlow.emit(ConnectionState.DISCONNECTED)
            }
        }
    }

    private suspend fun readSppStream(input: InputStream) {
        val buffer = ByteArray(1024)
        val lineBuffer = StringBuilder()
        try {
            while (true) {
                val bytes = input.read(buffer)
                if (bytes == -1) break
                val chunk = String(buffer, 0, bytes, Charsets.UTF_8)
                lineBuffer.append(chunk)
                // El Eyoyo en SPP termina cada lectura con \r\n o \n
                while (lineBuffer.contains('\n') || lineBuffer.contains('\r')) {
                    val nl = lineBuffer.indexOfFirst { it == '\n' || it == '\r' }
                    val line = lineBuffer.substring(0, nl).trim()
                    lineBuffer.delete(0, nl + 1)
                    if (line.isNotEmpty()) {
                        Log.d(TAG, "Código SPP: $line")
                        _barcodeFlow.emit(line)
                    }
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Stream SPP cerrado: ${e.message}")
        } finally {
            _stateFlow.emit(ConnectionState.DISCONNECTED)
        }
    }

    fun disconnectSpp() {
        sppJob?.cancel()
        try { sppSocket?.close() } catch (_: IOException) {}
        sppSocket = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  HID — MODO PASIVO
    //  En HID el SO Android maneja la conexión como teclado Bluetooth.
    //  No necesitamos un socket; solo capturamos el texto que el escáner
    //  "escribe" en el EditText con foco.
    //  Esta función solo cambia el estado para que la UI lo refleje.
    // ─────────────────────────────────────────────────────────────────────────

    fun setHidMode() {
        scope.launch { _stateFlow.emit(ConnectionState.HID_MODE) }
    }

    /**
     * Llama esto desde el Activity cuando detectes que el EditText HID
     * recibió un texto completo (el escáner HID termina con ENTER).
     */
    fun emitHidBarcode(raw: String) {
        val cleaned = raw.trim()
        if (cleaned.isNotEmpty()) {
            scope.launch { _barcodeFlow.emit(cleaned) }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  BLE — MODO ALTERNATIVO (Nordic UART)
    // ─────────────────────────────────────────────────────────────────────────

    private var bleGatt: android.bluetooth.BluetoothGatt? = null

    @SuppressLint("MissingPermission")
    fun connectBle(device: BluetoothDevice) {
        scope.launch(Dispatchers.Main) {
            _stateFlow.emit(ConnectionState.CONNECTING)
            bleGatt = device.connectGatt(context, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    private val gattCallback = object : android.bluetooth.BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: android.bluetooth.BluetoothGatt, status: Int, newState: Int) {
            if (newState == android.bluetooth.BluetoothProfile.STATE_CONNECTED) {
                gatt.discoverServices()
                scope.launch { _stateFlow.emit(ConnectionState.CONNECTED_BLE) }
            } else {
                scope.launch { _stateFlow.emit(ConnectionState.DISCONNECTED) }
            }
        }

        override fun onServicesDiscovered(gatt: android.bluetooth.BluetoothGatt, status: Int) {
            val char = gatt.getService(BLE_SERVICE_UUID)
                ?.getCharacteristic(BLE_NOTIFY_UUID) ?: return
            gatt.setCharacteristicNotification(char, true)
            val descriptor = char.getDescriptor(
                UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
            )
            descriptor?.let {
                it.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(it)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(
            gatt: android.bluetooth.BluetoothGatt,
            characteristic: android.bluetooth.BluetoothGattCharacteristic
        ) {
            val raw = characteristic.value?.toString(Charsets.UTF_8)?.trim() ?: return
            if (raw.isNotEmpty()) {
                Log.d(TAG, "Código BLE: $raw")
                scope.launch { _barcodeFlow.emit(raw) }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectBle() {
        bleGatt?.disconnect()
        bleGatt?.close()
        bleGatt = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  LIMPIEZA
    // ─────────────────────────────────────────────────────────────────────────

    fun destroy() {
        disconnectSpp()
        disconnectBle()
        scope.cancel()
    }
}