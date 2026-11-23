package com.example.ing_software_abarrotezperez.ai.interpreter

import java.text.Normalizer

/** Minúsculas y sin acentos, para comparar texto del usuario con nombres de productos. */
object TextNormalizer {
    private val MARKS = "\\p{M}+".toRegex()
    private val SPACES = "\\s+".toRegex()

    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(MARKS, "")
            .lowercase()
            .replace(SPACES, " ")
            .trim()

    fun tokens(text: String): List<String> =
        normalize(text).split(" ").filter { it.isNotEmpty() }

    private val NON_ALNUM = "[^a-z0-9]+".toRegex()

    /** Texto limpio: sin acentos, minúsculas y solo letras/dígitos separados por un espacio. */
    fun clean(text: String): String = normalize(text).replace(NON_ALNUM, " ").trim()

    /** Palabras (solo letras/dígitos) del texto, sin acentos y en minúsculas. */
    fun words(text: String): List<String> = clean(text).split(" ").filter { it.isNotEmpty() }

    /** Raíz simple para tolerar plurales: "cocas" -> "coca", "jabones" -> "jabon". */
    fun stem(w: String): String = when {
        w.length > 4 && w.endsWith("es") -> w.dropLast(2)
        w.length > 3 && w.endsWith("s") -> w.dropLast(1)
        else -> w
    }
}
