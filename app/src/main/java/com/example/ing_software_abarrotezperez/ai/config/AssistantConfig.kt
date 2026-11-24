package com.example.ing_software_abarrotezperez.ai.config

import android.content.Context

/** Preferencias del asistente. No toca la base de datos. */
class AssistantConfig(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("AsistentePrefs", Context.MODE_PRIVATE)

    /** Un producto con stock entre 1 y este valor se considera "con pocas existencias". */
    var lowStockThreshold: Int
        get() = prefs.getInt(KEY_LOW_STOCK, DEFAULT_LOW_STOCK).coerceIn(1, 1000)
        set(v) { prefs.edit().putInt(KEY_LOW_STOCK, v.coerceIn(1, 1000)).apply() }

    /** Excluye los productos PRUEBA-n que inserta ListaInventarioActivity. */
    var excludeTestProducts: Boolean
        get() = prefs.getBoolean(KEY_EXCLUDE_TEST, true)
        set(v) { prefs.edit().putBoolean(KEY_EXCLUDE_TEST, v).apply() }

    /** Deuda de fiado más vieja que este número de días se considera "deuda antigua". */
    var diasDeudaAntigua: Int
        get() = prefs.getInt(KEY_DEUDA_ANTIGUA, DEFAULT_DEUDA_ANTIGUA).coerceIn(1, 365)
        set(v) { prefs.edit().putInt(KEY_DEUDA_ANTIGUA, v.coerceIn(1, 365)).apply() }

    /** Hora (0-23) a partir de la cual "sin ventas hoy" es una alerta válida. */
    var horaSinVentas: Int
        get() = prefs.getInt(KEY_HORA_SIN_VENTAS, DEFAULT_HORA_SIN_VENTAS).coerceIn(0, 23)
        set(v) { prefs.edit().putInt(KEY_HORA_SIN_VENTAS, v.coerceIn(0, 23)).apply() }

    /** Días de historial mínimo antes de generar indicadores comparativos. */
    var diasHistorialMinimo: Int
        get() = prefs.getInt(KEY_HISTORIAL_DIAS, DEFAULT_HISTORIAL_DIAS).coerceIn(1, 365)
        set(v) { prefs.edit().putInt(KEY_HISTORIAL_DIAS, v.coerceIn(1, 365)).apply() }

    /** Mínimo de ventas en el historial para generar indicadores comparativos. */
    var ventasMinimasHistorico: Int
        get() = prefs.getInt(KEY_VENTAS_MIN, DEFAULT_VENTAS_MIN).coerceIn(1, 100000)
        set(v) { prefs.edit().putInt(KEY_VENTAS_MIN, v.coerceIn(1, 100000)).apply() }

    /** Días sin respaldo a partir de los cuales se alerta. */
    var diasRespaldoAlerta: Int
        get() = prefs.getInt(KEY_RESPALDO_DIAS, DEFAULT_RESPALDO_DIAS).coerceIn(1, 365)
        set(v) { prefs.edit().putInt(KEY_RESPALDO_DIAS, v.coerceIn(1, 365)).apply() }

    companion object {
        const val DEFAULT_LOW_STOCK = 5
        const val DEFAULT_DEUDA_ANTIGUA = 30
        const val DEFAULT_HORA_SIN_VENTAS = 12
        const val DEFAULT_HISTORIAL_DIAS = 14
        const val DEFAULT_VENTAS_MIN = 5
        const val DEFAULT_RESPALDO_DIAS = 7
        private const val KEY_LOW_STOCK = "low_stock_threshold"
        private const val KEY_EXCLUDE_TEST = "exclude_test_products"
        private const val KEY_DEUDA_ANTIGUA = "dias_deuda_antigua"
        private const val KEY_HORA_SIN_VENTAS = "hora_sin_ventas"
        private const val KEY_HISTORIAL_DIAS = "dias_historial_minimo"
        private const val KEY_VENTAS_MIN = "ventas_minimas_historico"
        private const val KEY_RESPALDO_DIAS = "dias_respaldo_alerta"
    }
}
