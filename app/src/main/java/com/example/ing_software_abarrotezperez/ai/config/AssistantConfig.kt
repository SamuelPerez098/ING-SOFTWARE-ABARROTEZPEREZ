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

    companion object {
        const val DEFAULT_LOW_STOCK = 5
        private const val KEY_LOW_STOCK = "low_stock_threshold"
        private const val KEY_EXCLUDE_TEST = "exclude_test_products"
    }
}
