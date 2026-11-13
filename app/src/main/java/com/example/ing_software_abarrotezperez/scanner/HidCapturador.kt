package com.example.ing_software_abarrotezperez.scanner

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * HidCapturador
 *
 * Captura las lecturas de un lector de códigos que trabaja como TECLADO (HID):
 *  - por Bluetooth (modo "Bluetooth HID" del Eyoyo EY-009L), o
 *  - por cable USB (el lector conectado con adaptador OTG se comporta igual: es un teclado USB).
 *
 * En ambos casos Android entrega las teclas como KeyEvent, así que se usa la misma lógica.
 *
 * Uso en una Activity:
 *
 *   private val hid = HidCapturador(consumirTodo = true, ignorarTecladoVirtual = false) { codigo -> ... }
 *
 *   override fun dispatchKeyEvent(event: KeyEvent): Boolean {
 *       if (hid.onKeyEvent(event)) return true
 *       return super.dispatchKeyEvent(event)
 *   }
 *   override fun onPause() { super.onPause(); hid.detener() }
 *
 * Diagnóstico: cada tecla recibida se escribe en Logcat con la etiqueta "ScannerHID" y se guarda en
 * un registro corto que se puede mostrar con [textoDiagnostico].
 *
 * @param consumirTodo           ver [ParserTeclado].
 * @param ignorarTecladoVirtual  true -> se ignora el teclado en pantalla (útil si la pantalla tiene
 *                               campos de texto que el usuario llena con el teclado del teléfono).
 * @param alDetectarCodigo       se llama (en el hilo principal) con cada código completo.
 */
class HidCapturador(
    consumirTodo: Boolean,
    private val ignorarTecladoVirtual: Boolean,
    private val alDetectarCodigo: (String) -> Unit
) {

    companion object {
        const val TAG = "ScannerHID"
        private const val SILENCIO_MS = 250L
        private const val MAX_REGISTRO = 60
    }

    private val parser = ParserTeclado(consumirTodo)
    private val handler = Handler(Looper.getMainLooper())
    private val consumidos = HashSet<Int>()          // teclas cuyo DOWN consumimos (para consumir su UP)
    private val registro = ArrayDeque<String>()

    private val envioPorSilencio = Runnable {
        parser.alVencerSilencio()?.let { entregar(it, "sin ENTER") }
    }

    /** @return true si el evento fue consumido y NO debe seguir hacia las vistas. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val virtual = event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD ||
                event.device?.isVirtual == true
        observar(event)

        if (virtual && ignorarTecladoVirtual) return false

        if (event.action == KeyEvent.ACTION_UP) {
            return consumidos.remove(event.keyCode)
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        val r = parser.alPresionar(
            keyCode = event.keyCode,
            unicode = event.unicodeChar,
            shift = event.isShiftPressed,
            repeticion = event.repeatCount,
            t = event.eventTime
        )

        handler.removeCallbacks(envioPorSilencio)
        if (r.codigo != null) {
            entregar(r.codigo, "ENTER")
        } else if (parser.hayPendiente) {
            handler.postDelayed(envioPorSilencio, SILENCIO_MS)
        }

        if (r.consumir) consumidos.add(event.keyCode)
        return r.consumir
    }

    /** Solo registra el evento (para el diálogo de diagnóstico); no procesa nada. */
    fun observar(event: KeyEvent) {
        val d = event.device
        val linea = String.format(
            "%s %s  uni=%d  meta=0x%x  dev=%d \"%s\" vid=%04x pid=%04x%s",
            if (event.action == KeyEvent.ACTION_DOWN) "↓" else "↑",
            KeyEvent.keyCodeToString(event.keyCode),
            event.unicodeChar,
            event.metaState,
            event.deviceId,
            d?.name ?: "-",
            d?.vendorId ?: 0,
            d?.productId ?: 0,
            if (event.deviceId == KeyCharacterMap.VIRTUAL_KEYBOARD) "  [virtual]" else ""
        )
        Log.d(TAG, linea)
        if (event.action == KeyEvent.ACTION_DOWN) anotar(linea)
    }

    /** Últimas teclas recibidas, la más reciente primero. */
    fun textoDiagnostico(): String {
        if (registro.isEmpty()) return "(todavía no llegó ninguna tecla)"
        return registro.reversed().joinToString("\n")
    }

    /** Cancela el envío pendiente y limpia el buffer. Llamar en onPause. */
    fun detener() {
        handler.removeCallbacks(envioPorSilencio)
        parser.reiniciar()
        consumidos.clear()
    }

    private fun entregar(codigo: String, origen: String) {
        Log.i(TAG, "Código leído ($origen): $codigo")
        anotar("✔ CÓDIGO ($origen): $codigo")
        alDetectarCodigo(codigo)
    }

    private fun anotar(linea: String) {
        registro.addLast(linea)
        while (registro.size > MAX_REGISTRO) registro.removeFirst()
    }
}
