package com.example.ing_software_abarrotezperez.ai.interpreter

import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent.*
import com.example.ing_software_abarrotezperez.ai.models.InterpretationResult
import com.example.ing_software_abarrotezperez.ai.models.InterpretationSource
import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Motor de interpretación por reglas: clasifica la intención ([IntentClassifier]), extrae datos
 * ([EntityExtractor]), reconoce productos ([ProductCatalog]) y decide si ejecutar o pedir aclaración.
 * Solo produce una [AssistantIntent] de la lista blanca más parámetros; nunca SQL.
 */
class QueryParser(
    private val catalog: ProductCatalog = ProductCatalog.EMPTY,
    private val clock: () -> LocalDate = { LocalDate.now() }
) {
    private val classifier = IntentClassifier()
    private val extractor = EntityExtractor(clock)

    fun parse(raw: String): InterpretationResult {
        val t = TextNormalizer.clean(raw)
        if (t.isBlank()) return InterpretationResult(UNKNOWN)

        if (THANKS.containsMatchIn(t) && t.split(" ").size <= 4)
            return InterpretationResult(HELP, directReply = "¡Con gusto! Aquí sigo para lo que necesites de tu tienda.")

        val cls = classifier.classify(t)
        val e = extractor.extract(t)
        val top = cls.top
        val products = catalog.matching(e.residual)

        // Fiados: aún no hay herramienta; se dice con claridad en vez de adivinar.
        if (FIADOS.containsMatchIn(t) && (top == null || top.score < CONFIDENT_STRONG))
            return InterpretationResult(UNKNOWN, clarification =
                "Por ahora consulto inventario, caducidades, ventas y mermas. " +
                    "Los fiados y adeudos los puedes ver en la sección Clientes del menú.")

        // Producto mencionado sin otra intención clara: se interpreta como búsqueda de producto.
        if (products.isNotEmpty() && (cls.status == ClassificationStatus.NONE || cls.status == ClassificationStatus.WEAK ||
                    top?.intent == SEARCH_PRODUCT))
            return build(SEARCH_PRODUCT, e, t, 0.85f)

        // Producto + consulta que aún no se puede filtrar por producto.
        if (products.isNotEmpty() && cls.status == ClassificationStatus.CONFIDENT && top != null && top.intent in NOT_PER_PRODUCT)
            return InterpretationResult(UNKNOWN, candidates = listOf(top.intent, SEARCH_PRODUCT), clarification =
                "Todavía no puedo filtrar eso por producto (${products.first().name}). " +
                    "Puedo mostrarte su stock y precio si preguntas \"cuántas ${products.first().name} tengo\", " +
                    "o la consulta general sin filtrar por producto.")

        return when (cls.status) {
            ClassificationStatus.NONE ->
                InterpretationResult(UNKNOWN)
            ClassificationStatus.WEAK, ClassificationStatus.AMBIGUOUS -> {
                val options = cls.candidates.take(3).filter { it.intent != HELP }.map { it.intent }
                if (options.isEmpty()) build(cls.candidates.first().intent, e, t, 0.9f)
                else InterpretationResult(UNKNOWN, candidates = options, clarification = askWhich(options))
            }
            ClassificationStatus.CONFIDENT -> {
                val second = cls.candidates.getOrNull(1)?.score ?: 0
                build(top!!.intent, e, t, if (second == 0) 0.9f else top.score.toFloat() / (top.score + second))
            }
        }
    }

    /**
     * Construye la interpretación para una intención ya decidida (p. ej. por el modelo local).
     * Los datos explícitos del texto (fechas, cantidades) mandan; [hint] solo rellena lo que falte.
     */
    fun interpretAs(
        intent: AssistantIntent, raw: String, hint: QueryParameters,
        source: InterpretationSource, confidence: Float
    ): InterpretationResult {
        val t = TextNormalizer.clean(raw)
        val base = build(intent, extractor.extract(t), t, confidence).params
        val now = clock()
        val usesDays = intent in setOf(EXPIRING_SOON, RESTOCK_NEEDS, NO_MOVEMENT, LOW_ROTATION)
        val usesPeriod = intent in setOf(TOP_SELLING, LEAST_SELLING, SALES_TOTAL, SHRINKAGE, SALES_COMPARE)
        val merged = base.copy(
            limit = base.limit ?: hint.limit,
            threshold = base.threshold ?: hint.threshold,
            days = if (usesDays) base.days ?: hint.days ?: hint.period?.days?.toInt() else base.days,
            period = if (usesPeriod) base.period ?: hint.period ?: hint.days?.let { TimePeriod.lastDays(it, now) } else base.period,
            comparePeriod = base.comparePeriod ?: hint.comparePeriod,
            productQuery = base.productQuery ?: hint.productQuery
        )
        return InterpretationResult(intent, merged, confidence, source)
    }

    private fun askWhich(options: List<AssistantIntent>): String {
        val names = options.map { LABELS.getValue(it) }
        val list = if (names.size == 1) names[0] else names.dropLast(1).joinToString(", ") + " o " + names.last()
        return "No estoy seguro de qué necesitas. ¿Quieres ver $list? " +
            "Dímelo con otras palabras, por ejemplo: \"${EXAMPLES.getValue(options.first())}\"."
    }

    private fun build(intent: AssistantIntent, e: Entities, t: String, confidence: Float): InterpretationResult {
        val now = clock()
        fun rolling(): TimePeriod? = e.periods.firstOrNull()?.period
            ?: e.durationDays?.let { TimePeriod.lastDays(it.coerceAtMost(3650), now) }

        val params = when (intent) {
            EXPIRING_SOON -> {
                val p = e.periods.firstOrNull()?.period
                val days = e.durationDays
                    // El rango de la herramienta incluye el último día: restamos 1 al límite exclusivo.
                    ?: p?.let { (ChronoUnit.DAYS.between(now, it.toExclusive).toInt() - 1).coerceAtLeast(1) }
                QueryParameters(days = days, limit = e.limit)
            }
            EXPIRED, OUT_OF_STOCK, INVENTORY_VALUE -> QueryParameters(limit = e.limit)
            LOW_STOCK -> QueryParameters(limit = e.limit, threshold = e.threshold)
            RESTOCK_NEEDS, NO_MOVEMENT, LOW_ROTATION ->
                QueryParameters(
                    days = rolling()?.days?.toInt(), limit = e.limit,
                    threshold = if (intent == RESTOCK_NEEDS) e.threshold else null
                )
            TOP_SELLING, LEAST_SELLING -> QueryParameters(limit = e.limit, period = rolling())
            SALES_TOTAL -> QueryParameters(period = rolling())
            SHRINKAGE -> QueryParameters(period = rolling(), limit = e.limit)
            SALES_COMPARE -> QueryParameters(
                period = e.periods.getOrNull(0)?.period, comparePeriod = e.periods.getOrNull(1)?.period
            )
            SEARCH_PRODUCT -> QueryParameters(productQuery = e.residual.joinToString(" ").ifBlank { null }, limit = e.limit)
            HELP, UNKNOWN -> QueryParameters()
        }
        return InterpretationResult(intent, params, confidence)
    }

    private companion object {
        const val CONFIDENT_STRONG = 5
        val THANKS = Regex("\\b(?:gracias|muchas gracias|mil gracias)\\b")
        val FIADOS = Regex("\\b(?:fiad\\w*|deuda\\w*|deben|debe|adeudo\\w*|cobrar|abono\\w*)\\b")
        val NOT_PER_PRODUCT = setOf(
            SALES_TOTAL, TOP_SELLING, LEAST_SELLING, SALES_COMPARE, EXPIRING_SOON, EXPIRED,
            NO_MOVEMENT, LOW_ROTATION, SHRINKAGE, INVENTORY_VALUE
        )
        val LABELS = mapOf(
            LOW_STOCK to "los productos con pocas existencias",
            OUT_OF_STOCK to "los productos agotados",
            INVENTORY_VALUE to "el valor de tu inventario",
            SEARCH_PRODUCT to "buscar un producto",
            RESTOCK_NEEDS to "qué productos conviene reabastecer",
            EXPIRING_SOON to "los productos próximos a caducar",
            EXPIRED to "los productos ya caducados",
            TOP_SELLING to "los productos más vendidos",
            LEAST_SELLING to "los productos menos vendidos",
            SALES_TOTAL to "tus ventas",
            SALES_COMPARE to "una comparación de ventas",
            NO_MOVEMENT to "los productos sin ventas",
            LOW_ROTATION to "los productos de baja rotación",
            SHRINKAGE to "las mermas y pérdidas",
            HELP to "ayuda", UNKNOWN to "otra cosa"
        )
        val EXAMPLES = mapOf(
            LOW_STOCK to "productos con pocas existencias",
            OUT_OF_STOCK to "productos agotados",
            INVENTORY_VALUE to "cuánto tengo invertido en inventario",
            SEARCH_PRODUCT to "cuántas Coca Cola tengo",
            RESTOCK_NEEDS to "qué productos necesito comprar",
            EXPIRING_SOON to "productos que caducan en 3 semanas",
            EXPIRED to "productos caducados",
            TOP_SELLING to "mis 5 productos más vendidos",
            LEAST_SELLING to "los productos que menos se venden",
            SALES_TOTAL to "cuánto vendí esta semana",
            SALES_COMPARE to "compara las ventas de este mes con el mes anterior",
            NO_MOVEMENT to "productos sin ventas el último mes",
            LOW_ROTATION to "productos de baja rotación",
            SHRINKAGE to "mermas de este mes",
            HELP to "qué puedes hacer", UNKNOWN to "qué puedes hacer"
        )
    }
}
