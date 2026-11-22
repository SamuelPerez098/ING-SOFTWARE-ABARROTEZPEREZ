package com.example.ing_software_abarrotezperez.scanner

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

/**
 * CamaraScanner
 *
 * Envuelve un [DecoratedBarcodeView] (vista previa de la cámara) y entrega cada
 * código leído a [onCodigo]. Es independiente del módulo Bluetooth.
 *
 * Uso:
 *   val camara = CamaraScanner(this, dbvVenta) { codigo -> procesarEscaneoVenta(codigo) }
 *   camara.iniciar()   // onResume / al activar el modo cámara
 *   camara.pausar()    // onPause  / al desactivar el modo cámara
 *
 * Antirrebote: mientras el mismo código siga frente a la cámara no se vuelve a
 * emitir; se emite otra vez cuando deja de verse más de [VENTANA_REPETICION_MS].
 */
class CamaraScanner(
    private val context: Context,
    private val vista: DecoratedBarcodeView,
    private val vibrarAlLeer: Boolean = true,
    private val onCodigo: (String) -> Unit
) {

    companion object {
        private const val VENTANA_REPETICION_MS = 1200L

        // Formatos típicos de productos de abarrotes (+ Code 128, el que genera Inventario)
        private val FORMATOS = listOf(
            BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8,
            BarcodeFormat.UPC_A,
            BarcodeFormat.UPC_E,
            BarcodeFormat.CODE_128,
            BarcodeFormat.CODE_39,
            BarcodeFormat.CODE_93,
            BarcodeFormat.ITF
        )
    }

    private var ultimoCodigo: String? = null
    private var ultimoTiempo = 0L

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            val codigo = result.text?.trim().orEmpty()
            if (codigo.isEmpty()) return

            val ahora = SystemClock.elapsedRealtime()
            val repetido = codigo == ultimoCodigo && (ahora - ultimoTiempo) < VENTANA_REPETICION_MS
            ultimoCodigo = codigo
            ultimoTiempo = ahora          // se refresca mientras siga a la vista
            if (repetido) return

            if (vibrarAlLeer) vibrarCorto()
            onCodigo(codigo)
        }

        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
    }

    init {
        vista.barcodeView.decoderFactory = DefaultDecoderFactory(FORMATOS)
        vista.setStatusText("Apunta la cámara al código de barras")
        vista.decodeContinuous(callback)
    }

    /** Abre la cámara y empieza a leer. Requiere el permiso CAMERA ya concedido. */
    fun iniciar() {
        ultimoCodigo = null
        vista.resume()
    }

    /** Libera la cámara. */
    fun pausar() {
        vista.pause()
    }

    fun linterna(encendida: Boolean) {
        if (encendida) vista.setTorchOn() else vista.setTorchOff()
    }

    private fun vibrarCorto() {
        try {
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION") v.vibrate(70)
            }
        } catch (_: Exception) { }
    }
}
