package com.example.ing_software_abarrotezperez.scanner

import android.content.Context
import android.hardware.input.InputManager
import android.hardware.usb.UsbManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice

/**
 * LectoresConectados
 *
 * Responde "¿Android está viendo mi lector como un teclado externo?".
 * Un lector en modo HID (Bluetooth) o por cable USB aparece en el sistema como un teclado externo.
 * Si NO aparece aquí, el problema está en la conexión/modo del lector y no en la app.
 */
object LectoresConectados {

    data class Lector(
        val nombre: String,
        val vendorId: Int,
        val productId: Int,
        /** true si coincide con un dispositivo USB conectado (cable / receptor 2.4G); false = Bluetooth u otro. */
        val porUsb: Boolean
    ) {
        val etiqueta: String
            get() = "$nombre (${if (porUsb) "USB" else "Bluetooth"})"
    }

    fun listar(context: Context): List<Lector> {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
        val dispositivosUsb = try {
            usbManager?.deviceList?.values?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        return InputDevice.getDeviceIds().toList()
            .mapNotNull { InputDevice.getDevice(it) }
            .filter { esTecladoExterno(it) }
            .map { d ->
                Lector(
                    nombre = d.name ?: "Teclado",
                    vendorId = d.vendorId,
                    productId = d.productId,
                    porUsb = dispositivosUsb.any {
                        it.vendorId == d.vendorId && it.productId == d.productId
                    }
                )
            }
    }

    private fun esTecladoExterno(d: InputDevice): Boolean {
        if (d.isVirtual) return false
        if ((d.sources and InputDevice.SOURCE_KEYBOARD) != InputDevice.SOURCE_KEYBOARD) return false
        // isExternal = conectado por USB o Bluetooth (no son las teclas del propio teléfono)
        return try { d.isExternal } catch (_: Throwable) { true }
    }

    /** Texto multilínea para el diálogo de diagnóstico. */
    fun descripcion(context: Context): String {
        val lectores = listar(context)
        if (lectores.isEmpty()) {
            return "Ningún teclado externo detectado.\n" +
                    "Android NO está viendo el lector como teclado."
        }
        return lectores.joinToString("\n") {
            "• ${it.etiqueta}  vid=%04x pid=%04x".format(it.vendorId, it.productId)
        }
    }
}

/**
 * Avisa cuando se conecta o desconecta un teclado (por ejemplo, el lector).
 * Llamar [iniciar] en onResume y [detener] en onPause.
 */
class VigilanteLectores(
    context: Context,
    private val alCambiar: () -> Unit
) : InputManager.InputDeviceListener {

    private val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    private val handler = Handler(Looper.getMainLooper())

    fun iniciar() {
        inputManager.registerInputDeviceListener(this, handler)
    }

    fun detener() {
        inputManager.unregisterInputDeviceListener(this)
    }

    override fun onInputDeviceAdded(deviceId: Int) = alCambiar()
    override fun onInputDeviceRemoved(deviceId: Int) = alCambiar()
    override fun onInputDeviceChanged(deviceId: Int) = alCambiar()
}
