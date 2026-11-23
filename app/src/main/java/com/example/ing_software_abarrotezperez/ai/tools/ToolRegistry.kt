package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantResponse
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import java.time.LocalDate

/**
 * Lista blanca de herramientas. El motor de interpretación (reglas o LLM) solo puede elegir una
 * [AssistantIntent]; aquí se mapea a código Kotlin fijo. No existe ningún camino para ejecutar SQL libre.
 */
class ToolRegistry(
    repo: AssistantRepository,
    config: AssistantConfig,
    clock: Clock = { LocalDate.now() },
    private val logError: (String, Throwable) -> Unit = { _, _ -> }
) {
    private val inventory = InventoryTools(repo, config, clock)
    private val expiration = ExpirationTools(repo, config, clock)
    private val sales = SalesTools(repo, config, clock)
    private val stats = StatisticsTools(repo, config, clock)

    fun execute(intent: AssistantIntent, params: QueryParameters): AssistantResponse = try {
        when (intent) {
            AssistantIntent.LOW_STOCK -> inventory.lowStock(params)
            AssistantIntent.OUT_OF_STOCK -> inventory.outOfStock(params)
            AssistantIntent.INVENTORY_VALUE -> inventory.inventoryValue(params)
            AssistantIntent.SEARCH_PRODUCT -> inventory.searchProduct(params)
            AssistantIntent.RESTOCK_NEEDS -> inventory.restockNeeds(params)
            AssistantIntent.EXPIRING_SOON -> expiration.expiringSoon(params)
            AssistantIntent.EXPIRED -> expiration.expired(params)
            AssistantIntent.TOP_SELLING -> sales.topSelling(params)
            AssistantIntent.LEAST_SELLING -> sales.leastSelling(params)
            AssistantIntent.SALES_TOTAL -> sales.salesTotal(params)
            AssistantIntent.SALES_COMPARE -> sales.compare(params)
            AssistantIntent.NO_MOVEMENT -> stats.noMovement(params)
            AssistantIntent.LOW_ROTATION -> stats.lowRotation(params)
            AssistantIntent.SHRINKAGE -> stats.shrinkage(params)
            AssistantIntent.HELP -> help()
            AssistantIntent.UNKNOWN -> AssistantResponse(
                AssistantIntent.UNKNOWN,
                "No estoy seguro de qué quieres consultar. Prueba con algo como \"productos que caducan en 3 semanas\" " +
                    "o \"mis 5 productos más vendidos\".",
                needsClarification = true
            )
        }
    } catch (e: ToolException) {
        AssistantResponse(intent, e.message ?: "Los datos de la consulta no son válidos.", isError = true)
    } catch (e: Exception) {
        logError("Error ejecutando $intent", e)
        AssistantResponse(intent, "No pude consultar los datos en este momento. Intenta de nuevo en un momento.", isError = true)
    }

    private fun help() = AssistantResponse(
        AssistantIntent.HELP,
        "Puedo consultar tu tienda sin Internet. Pregúntame, por ejemplo:\n" +
            "• Productos que caducan en las próximas 3 semanas\n" +
            "• Mis 5 productos más vendidos / menos vendidos\n" +
            "• Productos con pocas existencias o agotados\n" +
            "• Cuánto vendí esta semana o el mes anterior\n" +
            "• Cuánto tengo invertido en inventario\n" +
            "• Qué productos necesito comprar\n" +
            "• Productos sin ventas el último mes\n" +
            "• Mermas y pérdidas"
    )
}
