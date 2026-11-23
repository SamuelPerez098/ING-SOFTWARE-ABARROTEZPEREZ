package com.example.ing_software_abarrotezperez.ai.interpreter

import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import java.time.LocalDate

class PeriodHit(val range: IntRange, val period: TimePeriod)

/**
 * Datos extraídos del texto.
 * @property durationDays "3 semanas" -> 21, "próximo mes" -> 30, "últimos 15 días" -> 15.
 * @property residual palabras que quedan tras quitar fechas, números y palabras de la consulta;
 *                    suelen ser el nombre de un producto.
 */
class Entities(
    val periods: List<PeriodHit>,
    val durationDays: Int?,
    val limit: Int?,
    val threshold: Int?,
    val residual: List<String>
)

/** Extrae fechas, períodos, cantidades y umbrales de texto ya limpio ([TextNormalizer.clean]). */
class EntityExtractor(private val clock: () -> LocalDate = { LocalDate.now() }) {

    fun extract(t: String): Entities {
        val now = clock()
        val periods = findPeriods(t, now)
        val duration = findDuration(t)
        var rest = t
        duration?.let { rest = rest.removeRange(it.second) }
        val thr = THRESHOLD.find(rest)
        thr?.let { rest = rest.removeRange(it.range) }
        return Entities(
            periods = periods,
            durationDays = duration?.first,
            limit = findLimit(rest),
            threshold = thr?.groupValues?.get(1)?.toIntOrNull(),
            residual = residual(rest)
        )
    }

    private fun findPeriods(t: String, now: LocalDate): List<PeriodHit> {
        val defs = listOf(
            Regex("""\bsemana (?:pasada|anterior)\b""") to TimePeriod.lastWeek(now),
            Regex("""\b(?:esta semana|semana actual|(?:en|durante|de) la semana(?! (?:pasada|anterior)))""") to TimePeriod.thisWeek(now),
            Regex("""\bmes (?:anterior|pasado|previo)\b""") to TimePeriod.lastMonth(now),
            Regex("""\b(?:este mes|mes actual|(?:en|durante|del?) (?:el )?mes(?! (?:anterior|pasado|previo)))""") to TimePeriod.thisMonth(now),
            Regex("""\bayer\b""") to TimePeriod.yesterday(now),
            Regex("""\b(?:hoy|del dia|durante el dia)\b""") to TimePeriod.today(now),
            Regex("""\b(?:este ano|ano actual)\b""") to
                TimePeriod(now.withDayOfYear(1), now.withDayOfYear(1).plusYears(1), "este año")
        )
        val hits = mutableListOf<PeriodHit>()
        for ((re, p) in defs) re.findAll(t).forEach { m ->
            if (hits.none { it.range.first <= m.range.last && m.range.first <= it.range.last })
                hits += PeriodHit(m.range, p)
        }
        return hits.sortedBy { it.range.first }
    }

    private fun findDuration(t: String): Pair<Int, IntRange>? {
        val unit = "(dia|dias|semana|semanas|mes|meses)"
        val a = Regex("""\b(?:proxim[oa]s?|ultim[oa]s?|siguientes?)\s+(?:($NUM)\s+)?$unit\b""").find(t)
        val b = Regex("""\b($NUM)\s+$unit\b""").find(t)
        val m = a ?: b ?: return null
        val n = if (m.groupValues[1].isEmpty()) 1 else number(m.groupValues[1]) ?: return null
        val mult = when (m.groupValues[2]) {
            "dia", "dias" -> 1
            "semana", "semanas" -> 7
            else -> 30
        }
        return n * mult to m.range
    }

    private fun findLimit(t: String): Int? {
        if (Regex("\\b(?:todos|todas)\\b").containsMatchIn(t)) return 50
        val m = Regex("""\b(\d{1,2}|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|veinte)\b""").find(t)
            ?: return null
        return number(m.groupValues[1])?.takeIf { it in 1..50 }
    }

    private fun residual(t: String): List<String> =
        t.split(" ").filter { it.isNotEmpty() && it !in STOP && !NOISE.matches(it) && !it.all(Char::isDigit) }

    private fun number(s: String): Int? = s.toIntOrNull() ?: WORDS[s]

    private companion object {
        const val NUM = "\\d{1,3}|uno|una|un|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|veinte|treinta"
        val WORDS = mapOf(
            "un" to 1, "uno" to 1, "una" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6,
            "siete" to 7, "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "quince" to 15,
            "veinte" to 20, "treinta" to 30
        )
        val THRESHOLD = Regex("""\b(?:menos de|menor a|menos que|maximo|hasta|minimo de|debajo de)\s+(\d{1,4})\b""")

        val STOP = setOf(
            "que", "cual", "cuales", "de", "del", "la", "el", "los", "las", "un", "una", "unos", "unas", "mi", "mis", "me",
            "en", "y", "o", "u", "a", "al", "por", "favor", "para", "con", "se", "lo", "es", "son", "esta", "estan", "si",
            "no", "tienda", "aun", "todavia", "ya", "saber", "producto", "productos", "inventario", "stock", "unidades",
            "piezas", "algun", "alguna", "tenemos", "tienes", "tiene", "tienen", "tengo", "hay"
        )
        /** Palabras que expresan la consulta (no son parte de un nombre de producto). */
        val NOISE = Regex(
            "(?:vend\\w*|venta\\w*|caduc\\w*|caduq\\w*|venc\\w*|semana\\w*|mes|meses|dias?|hoy|ayer|anos?|pasad\\w*|" +
                "anterior\\w*|ultim\\w*|proxim\\w*|siguiente\\w*|actual|mas|menos|mejor\\w*|peor\\w*|top|compar\\w*|" +
                "total\\w*|cuant\\w*|dinero|merma\\w*|ganancia\\w*|ingres\\w*|corte|caja|valor|invert\\w*|comprar|surtir|" +
                "necesito|quiero|muestra\\w*|dame|dime|mostrar|lista\\w*|quien\\w*|donde|como|cuando|poc[oa]s?|baj[oa]s?|" +
                "agotad\\w*|existenc\\w*|rotacion|queda\\w*|busca\\w*|encuentra\\w*|precio\\w*|cuesta\\w*|todos?|todas?|" +
                "este|estos|estas|durante|dentro|entre|desde|hasta|sobre|sin|segun|fueron|fue|cuales|ayuda\\w*|favor)"
        ).let { Regex("^" + it.pattern + "$") }
    }
}
