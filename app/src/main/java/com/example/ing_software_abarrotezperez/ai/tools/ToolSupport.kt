package com.example.ing_software_abarrotezperez.ai.tools

import com.example.ing_software_abarrotezperez.ai.models.QueryParameters
import com.example.ing_software_abarrotezperez.ai.models.TimePeriod
import java.time.LocalDate
import java.util.Locale

/** Error de validación con mensaje apto para mostrar al propietario. */
class ToolException(message: String) : Exception(message)

internal object Fmt {
    fun money(v: Double): String = String.format(Locale.US, "$%,.2f", v)
    fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
    fun daysText(n: Long): String = when (n) {
        0L -> "hoy"
        1L -> "1 día"
        else -> "$n días"
    }
    fun pct(change: Double): String = String.format(Locale.US, "%+.1f%%", change)
}

/** Validación central de parámetros: ninguna herramienta usa valores sin pasar por aquí. */
internal object Params {
    const val MAX_LIMIT = 50

    fun limit(p: QueryParameters, default: Int): Int {
        val v = p.limit ?: default
        if (v < 1) throw ToolException("El número de resultados debe ser al menos 1.")
        return minOf(v, MAX_LIMIT)
    }

    fun days(p: QueryParameters, default: Int, max: Int = 365): Int {
        val v = p.days ?: default
        if (v < 1) throw ToolException("El número de días debe ser al menos 1.")
        if (v > max) throw ToolException("Solo puedo consultar hasta $max días a la vez.")
        return v
    }

    fun threshold(p: QueryParameters, default: Int): Int {
        val v = p.threshold ?: default
        if (v < 1 || v > 1000) throw ToolException("El mínimo de existencias debe estar entre 1 y 1000 unidades.")
        return v
    }

    fun period(period: TimePeriod?, default: TimePeriod): TimePeriod {
        val x = period ?: default
        if (x.days > TimePeriod.MAX_SPAN_DAYS) throw ToolException("El período es demasiado largo (máximo 5 años).")
        return x
    }
}

typealias Clock = () -> LocalDate
