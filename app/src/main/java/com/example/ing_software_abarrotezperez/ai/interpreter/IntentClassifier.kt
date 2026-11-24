package com.example.ing_software_abarrotezperez.ai.interpreter

import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent
import com.example.ing_software_abarrotezperez.ai.models.AssistantIntent.*

data class IntentCandidate(val intent: AssistantIntent, val score: Int)

enum class ClassificationStatus {
    /** Una intención clara. */
    CONFIDENT,
    /** Dos o más intenciones con puntaje parecido: conviene preguntar. */
    AMBIGUOUS,
    /** Solo señales débiles (p. ej. la palabra "inventario" sola). */
    WEAK,
    /** Ninguna señal. */
    NONE
}

data class Classification(val candidates: List<IntentCandidate>, val status: ClassificationStatus) {
    val top: IntentCandidate? get() = candidates.firstOrNull()
}

/**
 * Clasificador por puntaje. Cada intención tiene reglas con peso (sinónimos y expresiones coloquiales).
 * Trabaja sobre texto ya limpio (minúsculas, sin acentos ni signos), ver [TextNormalizer.clean].
 */
class IntentClassifier {

    /** Suma [weight] si TODOS los patrones de [all] aparecen y NINGUNO de [none]. */
    private class Rule(val intent: AssistantIntent, val weight: Int, val all: List<Regex>, val none: List<Regex>)

    private fun re(p: String) = Regex("\\b(?:$p)\\b")

    private fun rule(i: AssistantIntent, w: Int, vararg all: String, none: List<String> = emptyList()) =
        Rule(i, w, all.map(::re), none.map(::re))

    private val forward = "proxim\\w*|pronto|por caducar|por vencer|a punto de (?:caducar|vencer)|caducan|caduquen|vencen|caducaran|venceran|dentro de"
    private val expiredWord = "caducad\\w*|vencid\\w*|expirad\\w*|caducaron|vencieron|pasad\\w* de (?:fecha|caducidad)"
    private val salesWord = "venta\\w*|vend\\w*|ingres\\w*|gane|ganancia\\w*|ganado|factur\\w*"

    private val rules: List<Rule> = listOf(
        // ── Caducidades ──
        rule(EXPIRING_SOON, 3, "caduc\\w*|caduq\\w*|venc\\w*|vencimiento\\w*|expir\\w*"),
        rule(EXPIRING_SOON, 2, forward),
        rule(EXPIRING_SOON, -3, expiredWord, none = listOf(forward)),
        rule(EXPIRED, 4, expiredWord, none = listOf(forward)),
        rule(EXPIRED, 2, "ya (?:caduc\\w*|venc\\w*)"),

        // ── Mermas ──
        rule(SHRINKAGE, 5, "merma\\w*|desperdicio\\w*"),
        rule(SHRINKAGE, 3, "perdida\\w*|perdi|perdimos|perdemos", none = listOf(salesWord)),
        rule(SHRINKAGE, 2, "danad\\w*|rot[oa]s?|echad\\w* a perder"),

        // ── Valor del inventario ──
        rule(INVENTORY_VALUE, 5, "invertid\\w*|inversion\\w*|invierto|invertir"),
        rule(INVENTORY_VALUE, 3, "capital"),
        rule(INVENTORY_VALUE, 4, "valor|vale|valen|cuesta|cuestan|costo|cuanto|cuanta|dinero|lana|pesos",
            "inventario|mercancia|almacen|bodega", none = listOf("vend\\w*|venta\\w*|caduc\\w*|merma\\w*")),
        rule(INVENTORY_VALUE, 4, "cuanto (?:tengo|hay) en (?:productos|mercancia)"),
        rule(INVENTORY_VALUE, 1, "inventario"),

        // ── Reabastecimiento ──
        rule(RESTOCK_NEEDS, 5, "comprar|reabastec\\w*|resurtir|surtir|surtido|reponer|reposicion|pedir|pedido\\w*|abastecer"),
        rule(RESTOCK_NEEDS, 4, "falta|faltan|hace falta|hacen falta", none = listOf("caduc\\w*|venc\\w*")),

        // ── Agotados / pocas existencias ──
        rule(OUT_OF_STOCK, 5, "agotad\\w*"),
        rule(OUT_OF_STOCK, 5, "sin (?:existencias?|stock|inventario)"),
        rule(OUT_OF_STOCK, 4, "se (?:me )?(?:acabo|acabaron|agoto|agotaron|termino|terminaron)"),
        rule(OUT_OF_STOCK, 4, "no (?:hay|tengo) (?:existencias?|stock)"),
        rule(OUT_OF_STOCK, 3, "ya no (?:hay|tengo|queda\\w*|me queda\\w*)"),
        rule(OUT_OF_STOCK, 3, "en cero"),
        rule(OUT_OF_STOCK, 1, "existencias?|stock|inventario"),

        rule(LOW_STOCK, 5, "poc[oa]s?|baj[oa]s?|escas\\w*|minim\\w*|reducid\\w*", "existenc\\w*|stock|inventario|unidades|piezas"),
        rule(LOW_STOCK, 5, "por (?:acabar|agotar|terminar)\\w*|(?:se )?estan (?:acabando|agotando|terminando)|" +
            "(?:casi|a punto de) (?:se )?(?:acab|agot|termin)\\w*|ya casi no"),
        rule(LOW_STOCK, 5, "menos de \\d+", "unidades|piezas|existencias|stock"),
        rule(LOW_STOCK, 1, "existencias?|stock|inventario|unidades"),

        // ── Análisis comercial ──
        rule(NO_MOVEMENT, 5, "(?:no|sin|nunca|ni)\\b.*\\b(?:venta|ventas|vendido|vendidos|vende|venden|vendi|vendieron|movimiento|rotacion|mueven|mueve|salida|salidas)"),
        rule(NO_MOVEMENT, 5, "estancad\\w*|inmovil\\w*|dormid\\w*|parad\\w*"),
        rule(LOW_ROTATION, 6, "(?:baja|poca|lenta) rotacion|rotacion (?:baja|lenta)"),
        rule(LOW_ROTATION, 4, "rotacion", none = listOf("sin rotacion")),
        rule(LOW_ROTATION, 4, "despacio|lent[oa]s?|sobre ?stock|exceso"),

        // ── Ventas ──
        rule(SALES_COMPARE, 6, "compar\\w*|versus|vs|contra|frente a|respecto a|en relacion con",
            "venta\\w*|vend\\w*|ingres\\w*|gane|ganancia\\w*|semana|mes|ano|hoy|ayer"),
        rule(LEAST_SELLING, 6, "menos (?:se )?vend\\w*|menos venta\\w*|menor(?:es)? venta\\w*|menor salida|peor(?:es)? (?:venta|vendido)\\w*"),
        rule(LEAST_SELLING, 6, "vend\\w* menos", none = listOf("cuanto|cuanta")),
        rule(LEAST_SELLING, 5, "(?:se )?vende(?:n)? poco"),
        rule(TOP_SELLING, 6, "mas (?:se )?vend\\w*|mas venta\\w*|(?:mejor(?:es)?|mayor(?:es)?) (?:venta|vendido)\\w*|mas popular\\w*|mas salida"),
        rule(TOP_SELLING, 6, "vend\\w* mas", none = listOf("cuanto|cuanta")),
        rule(TOP_SELLING, 5, "estrella\\w*|favorit\\w*|mejores productos|top|los que mas (?:salen|se mueven|se llevan)"),
        rule(SALES_TOTAL, 3, "$salesWord|corte de caja|cuanto (?:he )?(?:hecho|entrado|sacado)|caja"),

        // ── Búsqueda de producto ──
        rule(SEARCH_PRODUCT, 3, "busca\\w*|encuentra\\w*|precio\\w*|cuesta\\w*|cuantos|cuantas|hay de|tengo de|tienes|tienen|queda\\w*|" +
            "existencias de|stock de|cuanto hay|donde esta|que tanto",
            none = listOf("venta\\w*|vend\\w*|caduc\\w*|venc\\w*|merma\\w*|ganancia\\w*")),

        // ── Resumen de tienda ──
        rule(STORE_SUMMARY, 6, "resumen"),
        rule(STORE_SUMMARY, 6, "balance|panorama"),
        rule(STORE_SUMMARY, 5, "como (?:va|esta|anda|vamos)\\w*", "tienda|negocio|local|todo"),

        // ── Ayuda ──
        rule(HELP, 6, "que puedes|que sabes|como funciona|en que me ayudas|que haces|ayuda|ayudame|comandos|opciones|que preguntas|que te puedo")
    )

    private val greeting = re("hola|holi|buenas|buenos|buen dia|hey|saludos|que onda")

    fun classify(cleanText: String): Classification {
        val scores = HashMap<AssistantIntent, Int>()
        for (r in rules) {
            if (r.all.all { it.containsMatchIn(cleanText) } && r.none.none { it.containsMatchIn(cleanText) })
                scores[r.intent] = (scores[r.intent] ?: 0) + r.weight
        }
        if (cleanText.split(" ").size <= 3 && greeting.containsMatchIn(cleanText))
            scores[HELP] = (scores[HELP] ?: 0) + 6

        val ranked = scores.filter { it.value > 0 }
            .map { IntentCandidate(it.key, it.value) }
            .sortedWith(compareByDescending<IntentCandidate> { it.score }.thenBy { it.intent.ordinal })
        val top = ranked.firstOrNull() ?: return Classification(emptyList(), ClassificationStatus.NONE)
        val second = ranked.getOrNull(1)

        val status = when {
            top.score < CONFIDENT_MIN -> ClassificationStatus.WEAK
            second != null && second.score >= CONFIDENT_MIN && top.score - second.score < AMBIGUOUS_MARGIN ->
                ClassificationStatus.AMBIGUOUS
            else -> ClassificationStatus.CONFIDENT
        }
        return Classification(ranked, status)
    }

    companion object {
        const val CONFIDENT_MIN = 3
        const val AMBIGUOUS_MARGIN = 2
    }
}
