package com.example.ing_software_abarrotezperez.ai

import com.example.ing_software_abarrotezperez.ai.interpreter.IntentClassifier
import com.example.ing_software_abarrotezperez.ai.interpreter.TextNormalizer
import org.junit.Test
import kotlin.math.roundToInt

/**
 * Línea base (C0) del clasificador sobre frases en español de México.
 * Lee `src/test/resources/nlu_casos.tsv` (frase \t intento \t categoría) e imprime
 * exactitud global, por intención y por categoría, más los fallos.
 */
class NluBaselineTest {

    @Test
    fun baseline() {
        val clasificador = IntentClassifier()
        val casos = javaClass.getResourceAsStream("/nlu_casos.tsv")!!
            .bufferedReader().readLines()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .map { it.split("\t") }

        var ok = 0
        val porIntento = sortedMapOf<String, IntArray>()
        val porCategoria = sortedMapOf<String, IntArray>()

        for (c in casos) {
            val frase = c[0]
            val esperado = c[1]
            val categoria = c.getOrElse(2) { "general" }
            val limpio = TextNormalizer.clean(frase)
            val predicho = clasificador.classify(limpio).top?.intent?.name ?: "NONE"
            val acierto = predicho == esperado
            if (acierto) ok++ else println("  ✗ \"$frase\" → $predicho (esperado $esperado)")
            porIntento.getOrPut(esperado) { intArrayOf(0, 0) }.let { it[1]++; if (acierto) it[0]++ }
            porCategoria.getOrPut(categoria) { intArrayOf(0, 0) }.let { it[1]++; if (acierto) it[0]++ }
        }

        println("Exactitud global: $ok/${casos.size} = ${(ok * 100.0 / casos.size).roundToInt()}%")
        println("Por intención:")
        porIntento.forEach { (k, v) -> println("  $k: ${v[0]}/${v[1]}") }
        println("Por categoría:")
        porCategoria.forEach { (k, v) -> println("  $k: ${v[0]}/${v[1]}") }
    }
}
