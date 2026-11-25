package com.example.ing_software_abarrotezperez.ai.insights

/**
 * Umbrales del motor de resumen. Lo implementa [com.example.ing_software_abarrotezperez.ai.config.AssistantConfig]
 * (Android/SharedPreferences) y un fake en pruebas. Valores por defecto pensados para una
 * abarrotería pequeña: marcados "ajustar" para que el dueño los calibre.
 */
interface InsightConfig {
    val excludeTestProducts: Boolean
    val diasHistorialMinimo: Int
    val ventasMinimasHistorico: Int
    val diasDeudaAntigua: Int
    val diasDeudaCritica: Int
    val montoDeudaAlerta: Float
    val montoDeudaCritica: Float
    val montoDeudaTotalCritica: Float
    val horaSinVentas: Int
    val horaCierre: Int
    val ticketsMinimosDiaActivo: Int
    val caidaVentasCritica: Float
    val pisoGananciaSemanal: Float
    val perdidaMargenCritica: Float
    val mermasPisoPesos: Float
    val mermasPisoCritico: Float
    val factorMermaCritica: Float
    val diasRespaldoAlerta: Int
}
