package com.example.ing_software_abarrotezperez.ai.models

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Período [from, toExclusive) en fechas de calendario local.
 * El límite superior es EXCLUSIVO para que las comparaciones con texto ISO
 * ("yyyy-MM-dd HH:mm:ss" o "yyyy-MM-dd HH:mm") sean correctas.
 */
data class TimePeriod(val from: LocalDate, val toExclusive: LocalDate, val label: String) {

    init {
        require(from.isBefore(toExclusive)) { "El período no es válido" }
    }

    val days: Long get() = ChronoUnit.DAYS.between(from, toExclusive)

    /** Frase con preposición: "en esta semana", pero "hoy" / "ayer" sin "en". */
    val inLabel: String get() = if (label == "hoy" || label == "ayer") label else "en $label"
    val inLabelCap: String get() = inLabel.replaceFirstChar { it.uppercase() }

    /** Período inmediatamente anterior: mes completo anterior si este es de meses completos; si no, misma duración. */
    fun previous(): TimePeriod {
        val p = Period.between(from, toExclusive)
        return if (from.dayOfMonth == 1 && p.days == 0 && p.toTotalMonths() > 0) {
            val m = p.toTotalMonths()
            TimePeriod(from.minusMonths(m), from, "el período anterior")
        } else {
            TimePeriod(from.minusDays(days), from, "el período anterior")
        }
    }

    companion object {
        const val MAX_SPAN_DAYS = 366L * 5

        fun today(now: LocalDate = LocalDate.now()) =
            TimePeriod(now, now.plusDays(1), "hoy")

        fun yesterday(now: LocalDate = LocalDate.now()) =
            TimePeriod(now.minusDays(1), now, "ayer")

        /** Semana de lunes a domingo. */
        fun thisWeek(now: LocalDate = LocalDate.now()): TimePeriod {
            val mon = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            return TimePeriod(mon, mon.plusDays(7), "esta semana")
        }

        fun lastWeek(now: LocalDate = LocalDate.now()): TimePeriod {
            val mon = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusDays(7)
            return TimePeriod(mon, mon.plusDays(7), "la semana pasada")
        }

        fun thisMonth(now: LocalDate = LocalDate.now()): TimePeriod {
            val first = now.withDayOfMonth(1)
            return TimePeriod(first, first.plusMonths(1), "este mes")
        }

        fun lastMonth(now: LocalDate = LocalDate.now()): TimePeriod {
            val first = now.withDayOfMonth(1).minusMonths(1)
            return TimePeriod(first, first.plusMonths(1), "el mes anterior")
        }

        /** Últimos [n] días incluyendo hoy. */
        fun lastDays(n: Int, now: LocalDate = LocalDate.now()) =
            TimePeriod(now.minusDays(n.toLong() - 1), now.plusDays(1), "los últimos $n días")
    }
}
