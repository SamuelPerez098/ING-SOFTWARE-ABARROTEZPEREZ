package com.example.ing_software_abarrotezperez.ai.models

/** Intenciones que Pérez AI sabe resolver. Cada una se asocia con UNA herramienta autorizada. */
enum class AssistantIntent {
    // Inventario
    LOW_STOCK, OUT_OF_STOCK, INVENTORY_VALUE, SEARCH_PRODUCT, RESTOCK_NEEDS,
    // Caducidades
    EXPIRING_SOON, EXPIRED,
    // Ventas
    TOP_SELLING, LEAST_SELLING, SALES_TOTAL, SALES_COMPARE,
    // Análisis comercial
    NO_MOVEMENT, LOW_ROTATION, SHRINKAGE,
    // Conversación
    HELP, UNKNOWN,
    // Resumen de tienda (paso 4)
    STORE_SUMMARY
}
