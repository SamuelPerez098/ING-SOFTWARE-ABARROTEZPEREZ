package com.example.ing_software_abarrotezperez.scanner

import android.view.KeyEvent

/**
 * ParserTeclado
 *
 * Lógica pura (no toca vistas ni el sistema) que arma un código de barras a partir de
 * las teclas que "escribe" un lector en modo teclado (HID), ya sea por Bluetooth o por cable USB.
 *
 * Lo usa [HidCapturador]. Está separado para poder probarlo sin un teléfono.
 *
 * Qué resuelve:
 *  - Terminadores: ENTER, ENTER del teclado numérico y TAB (según cómo esté configurado el sufijo del lector).
 *  - Lectores sin terminador: [alVencerSilencio] entrega el código cuando dejan de llegar teclas.
 *  - Lectores que mandan los dígitos como teclado numérico: con Bloq Num apagado Android entrega
 *    unicodeChar = 0; aquí se recupera el dígito a partir del keyCode.
 *
 * @param consumirTodo true  -> todas las teclas con carácter se consumen (pantallas sin campos de texto).
 *                     false -> solo se consumen cuando llegan a velocidad de lector (pantallas con campos).
 */
class ParserTeclado(
    private val consumirTodo: Boolean,
    private val gapMaxMs: Long = 700L,
    private val velocidadLectorMs: Long = 200L,
    private val velocidadAutoEnvioMs: Long = 80L,
    private val longMinSinTerminador: Int = 6,
    private val terminadorMaxMs: Long = 350L
) {

    /** @property consumir true si la tecla NO debe seguir hacia las vistas. */
    class Resultado(val consumir: Boolean, val codigo: String? = null)

    private val buffer = StringBuilder()
    private var primeraTecla = 0L
    private var ultimaTecla = 0L

    val hayPendiente: Boolean get() = buffer.isNotEmpty()
    val pendiente: String get() = buffer.toString()

    fun reiniciar() {
        buffer.setLength(0)
    }

    /**
     * Procesa una tecla presionada (ACTION_DOWN).
     * @param t tiempo del evento en ms (el mismo reloj para todas las teclas).
     */
    fun alPresionar(
        keyCode: Int,
        unicode: Int,
        shift: Boolean,
        repeticion: Int,
        t: Long
    ): Resultado {
        if (repeticion > 0) return Resultado(false)   // un lector nunca repite teclas

        val dt = if (buffer.isEmpty()) 0L else t - ultimaTecla
        if (buffer.isNotEmpty() && dt > gapMaxMs) buffer.setLength(0)

        // ── Terminador ──────────────────────────────────────────────────────
        if (esTerminador(keyCode)) {
            // Un TAB suelto (persona navegando) no cuenta como fin de lectura
            if (keyCode == KeyEvent.KEYCODE_TAB && buffer.length < longMinSinTerminador) {
                return Resultado(false)
            }
            val codigo = buffer.toString().trim()
            // Solo es lectura si llegó a velocidad de lector: así lo que escribe una persona
            // en un teclado físico y termina con ENTER no se confunde con un código de barras.
            val promedio = if (buffer.length > 1) (ultimaTecla - primeraTecla) / (buffer.length - 1) else 0L
            val esLector = dt <= terminadorMaxMs && promedio <= velocidadLectorMs
            buffer.setLength(0)
            return if (codigo.isNotEmpty() && esLector) Resultado(true, codigo) else Resultado(false)
        }

        // ── Carácter ────────────────────────────────────────────────────────
        val c = caracter(keyCode, unicode, shift) ?: return Resultado(false)
        if (buffer.isEmpty()) primeraTecla = t
        buffer.append(c)
        ultimaTecla = t

        val aVelocidadLector = dt <= velocidadLectorMs
        val consumir = consumirTodo || (buffer.length >= 3 && aVelocidadLector)
        return Resultado(consumir)
    }

    /**
     * Se llama cuando pasó un rato sin recibir teclas. Si hay un código pendiente que llegó a
     * velocidad de lector (no de persona) lo entrega; así funcionan lectores sin sufijo ENTER.
     */
    fun alVencerSilencio(): String? {
        if (buffer.length < longMinSinTerminador) return null
        val promedio = (ultimaTecla - primeraTecla) / (buffer.length - 1).coerceAtLeast(1)
        if (promedio > velocidadAutoEnvioMs) return null
        val codigo = buffer.toString().trim()
        buffer.setLength(0)
        return codigo.ifEmpty { null }
    }

    companion object {
        fun esTerminador(keyCode: Int): Boolean =
            keyCode == KeyEvent.KEYCODE_ENTER ||
                    keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                    keyCode == KeyEvent.KEYCODE_TAB

        /**
         * Devuelve el carácter de la tecla, o null si no es un carácter imprimible
         * (Shift, Ctrl, flechas, etc.).
         */
        fun caracter(keyCode: Int, unicode: Int, shift: Boolean): Char? {
            // Caso normal: Android ya resolvió el carácter
            if (unicode in 32..0xFFFF && unicode != 127) return unicode.toChar()

            // Respaldo: unicodeChar == 0 (p. ej. teclado numérico con Bloq Num apagado)
            return when (keyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
                    '0' + (keyCode - KeyEvent.KEYCODE_0)
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 ->
                    '0' + (keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z ->
                    (if (shift) 'A' else 'a') + (keyCode - KeyEvent.KEYCODE_A)
                KeyEvent.KEYCODE_MINUS, KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> '-'
                KeyEvent.KEYCODE_PERIOD, KeyEvent.KEYCODE_NUMPAD_DOT -> '.'
                KeyEvent.KEYCODE_SLASH -> '/'
                KeyEvent.KEYCODE_SPACE -> ' '
                else -> null
            }
        }
    }
}
