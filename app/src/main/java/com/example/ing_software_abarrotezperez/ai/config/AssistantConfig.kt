package com.example.ing_software_abarrotezperez.ai.config

import android.content.Context
import com.example.ing_software_abarrotezperez.ai.insights.InsightConfig

/** Preferencias del asistente. No toca la base de datos. */
class AssistantConfig(context: Context) : InsightConfig {

    private val prefs = context.applicationContext
        .getSharedPreferences("AsistentePrefs", Context.MODE_PRIVATE)

    /** Un producto con stock entre 1 y este valor se considera "con pocas existencias". */
    var lowStockThreshold: Int
        get() = prefs.getInt(KEY_LOW_STOCK, DEFAULT_LOW_STOCK).coerceIn(1, 1000)
        set(v) { prefs.edit().putInt(KEY_LOW_STOCK, v.coerceIn(1, 1000)).apply() }

    override var excludeTestProducts: Boolean
        get() = prefs.getBoolean(KEY_EXCLUDE_TEST, true)
        set(v) { prefs.edit().putBoolean(KEY_EXCLUDE_TEST, v).apply() }

    // ── Resumen de tienda (umbrales "ajustar") ──
    override var diasHistorialMinimo: Int
        get() = prefs.getInt(KEY_HISTORIAL_DIAS, DEFAULT_HISTORIAL_DIAS).coerceIn(1, 365)
        set(v) { prefs.edit().putInt(KEY_HISTORIAL_DIAS, v.coerceIn(1, 365)).apply() }

    override var ventasMinimasHistorico: Int
        get() = prefs.getInt(KEY_VENTAS_MIN, DEFAULT_VENTAS_MIN).coerceIn(1, 100000)
        set(v) { prefs.edit().putInt(KEY_VENTAS_MIN, v.coerceIn(1, 100000)).apply() }

    override var diasDeudaAntigua: Int
        get() = prefs.getInt(KEY_DEUDA_ANTIGUA, DEFAULT_DEUDA_ANTIGUA).coerceIn(1, 365)
        set(v) { prefs.edit().putInt(KEY_DEUDA_ANTIGUA, v.coerceIn(1, 365)).apply() }

    override var diasDeudaCritica: Int
        get() = prefs.getInt(KEY_DEUDA_CRITICA, DEFAULT_DEUDA_CRITICA).coerceIn(1, 3650)
        set(v) { prefs.edit().putInt(KEY_DEUDA_CRITICA, v.coerceIn(1, 3650)).apply() }

    override var montoDeudaAlerta: Float
        get() = prefs.getFloat(KEY_DEUDA_ALERTA, DEFAULT_DEUDA_ALERTA).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_DEUDA_ALERTA, v.coerceAtLeast(0f)).apply() }

    override var montoDeudaCritica: Float
        get() = prefs.getFloat(KEY_DEUDA_CRITICA_M, DEFAULT_DEUDA_CRITICA_M).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_DEUDA_CRITICA_M, v.coerceAtLeast(0f)).apply() }

    override var montoDeudaTotalCritica: Float
        get() = prefs.getFloat(KEY_DEUDA_TOTAL, DEFAULT_DEUDA_TOTAL).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_DEUDA_TOTAL, v.coerceAtLeast(0f)).apply() }

    override var horaSinVentas: Int
        get() = prefs.getInt(KEY_HORA_SIN_VENTAS, DEFAULT_HORA_SIN_VENTAS).coerceIn(0, 23)
        set(v) { prefs.edit().putInt(KEY_HORA_SIN_VENTAS, v.coerceIn(0, 23)).apply() }

    override var horaCierre: Int
        get() = prefs.getInt(KEY_HORA_CIERRE, DEFAULT_HORA_CIERRE).coerceIn(0, 23)
        set(v) { prefs.edit().putInt(KEY_HORA_CIERRE, v.coerceIn(0, 23)).apply() }

    override var ticketsMinimosDiaActivo: Int
        get() = prefs.getInt(KEY_TICKETS_DIA, DEFAULT_TICKETS_DIA).coerceIn(1, 1000)
        set(v) { prefs.edit().putInt(KEY_TICKETS_DIA, v.coerceIn(1, 1000)).apply() }

    override var caidaVentasCritica: Float
        get() = prefs.getFloat(KEY_CAIDA_VENTAS, DEFAULT_CAIDA_VENTAS).coerceIn(0f, 1f)
        set(v) { prefs.edit().putFloat(KEY_CAIDA_VENTAS, v.coerceIn(0f, 1f)).apply() }

    override var pisoGananciaSemanal: Float
        get() = prefs.getFloat(KEY_PISO_GANANCIA, DEFAULT_PISO_GANANCIA).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_PISO_GANANCIA, v.coerceAtLeast(0f)).apply() }

    override var perdidaMargenCritica: Float
        get() = prefs.getFloat(KEY_PERDIDA_MARGEN, DEFAULT_PERDIDA_MARGEN).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_PERDIDA_MARGEN, v.coerceAtLeast(0f)).apply() }

    override var mermasPisoPesos: Float
        get() = prefs.getFloat(KEY_MERMAS_PISO, DEFAULT_MERMAS_PISO).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_MERMAS_PISO, v.coerceAtLeast(0f)).apply() }

    override var mermasPisoCritico: Float
        get() = prefs.getFloat(KEY_MERMAS_CRITICO, DEFAULT_MERMAS_CRITICO).coerceAtLeast(0f)
        set(v) { prefs.edit().putFloat(KEY_MERMAS_CRITICO, v.coerceAtLeast(0f)).apply() }

    override var factorMermaCritica: Float
        get() = prefs.getFloat(KEY_FACTOR_MERMA, DEFAULT_FACTOR_MERMA).coerceAtLeast(1f)
        set(v) { prefs.edit().putFloat(KEY_FACTOR_MERMA, v.coerceAtLeast(1f)).apply() }

    override var diasRespaldoAlerta: Int
        get() = prefs.getInt(KEY_RESPALDO_DIAS, DEFAULT_RESPALDO_DIAS).coerceIn(1, 365)
        set(v) { prefs.edit().putInt(KEY_RESPALDO_DIAS, v.coerceIn(1, 365)).apply() }

    companion object {
        const val DEFAULT_LOW_STOCK = 5
        const val DEFAULT_HISTORIAL_DIAS = 14
        const val DEFAULT_VENTAS_MIN = 5
        const val DEFAULT_DEUDA_ANTIGUA = 30
        const val DEFAULT_DEUDA_CRITICA = 60
        const val DEFAULT_DEUDA_ALERTA = 200f
        const val DEFAULT_DEUDA_CRITICA_M = 500f
        const val DEFAULT_DEUDA_TOTAL = 1000f
        const val DEFAULT_HORA_SIN_VENTAS = 12
        const val DEFAULT_HORA_CIERRE = 20
        const val DEFAULT_TICKETS_DIA = 2
        const val DEFAULT_CAIDA_VENTAS = 0.30f
        const val DEFAULT_PISO_GANANCIA = 50f
        const val DEFAULT_PERDIDA_MARGEN = 100f
        const val DEFAULT_MERMAS_PISO = 20f
        const val DEFAULT_MERMAS_CRITICO = 100f
        const val DEFAULT_FACTOR_MERMA = 2.0f
        const val DEFAULT_RESPALDO_DIAS = 7

        private const val KEY_LOW_STOCK = "low_stock_threshold"
        private const val KEY_EXCLUDE_TEST = "exclude_test_products"
        private const val KEY_HISTORIAL_DIAS = "dias_historial_minimo"
        private const val KEY_VENTAS_MIN = "ventas_minimas_historico"
        private const val KEY_DEUDA_ANTIGUA = "dias_deuda_antigua"
        private const val KEY_DEUDA_CRITICA = "dias_deuda_critica"
        private const val KEY_DEUDA_ALERTA = "monto_deuda_alerta"
        private const val KEY_DEUDA_CRITICA_M = "monto_deuda_critica"
        private const val KEY_DEUDA_TOTAL = "monto_deuda_total_critica"
        private const val KEY_HORA_SIN_VENTAS = "hora_sin_ventas"
        private const val KEY_HORA_CIERRE = "hora_cierre"
        private const val KEY_TICKETS_DIA = "tickets_minimos_dia_activo"
        private const val KEY_CAIDA_VENTAS = "caida_ventas_critica"
        private const val KEY_PISO_GANANCIA = "piso_ganancia_semanal"
        private const val KEY_PERDIDA_MARGEN = "perdida_margen_critica"
        private const val KEY_MERMAS_PISO = "mermas_piso_pesos"
        private const val KEY_MERMAS_CRITICO = "mermas_piso_critico"
        private const val KEY_FACTOR_MERMA = "factor_merma_critica"
        private const val KEY_RESPALDO_DIAS = "dias_respaldo_alerta"
    }
}
