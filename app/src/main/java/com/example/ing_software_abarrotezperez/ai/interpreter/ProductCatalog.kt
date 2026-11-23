package com.example.ing_software_abarrotezperez.ai.interpreter

/** Fuente de nombres de productos (la implementa el ViewModel con una consulta de solo lectura). */
fun interface ProductNameSource {
    fun names(): List<String>
}

/**
 * Catálogo en memoria para reconocer productos mencionados en una pregunta.
 * Se recarga como máximo cada [ttlMs] para no consultar la base en cada mensaje.
 * Si la fuente falla, el parser sigue funcionando sin reconocimiento de productos.
 */
class ProductCatalog(
    private val source: ProductNameSource,
    private val ttlMs: Long = 60_000,
    private val clockMs: () -> Long = System::currentTimeMillis
) {
    class Entry(val name: String, val tokens: List<String>)

    private var cache: List<Entry> = emptyList()
    private var loadedAt = Long.MIN_VALUE

    @Synchronized
    private fun entries(): List<Entry> {
        val now = clockMs()
        if (loadedAt == Long.MIN_VALUE || now - loadedAt > ttlMs) {
            cache = try {
                source.names().map { Entry(it, TextNormalizer.words(it)) }
            } catch (e: Exception) { cache }
            loadedAt = now
        }
        return cache
    }

    /** Productos cuyo nombre contiene TODAS las palabras dadas (tolera plurales y prefijos de 4+ letras). */
    fun matching(words: List<String>, max: Int = 10): List<Entry> {
        val ws = words.filter { it.length >= 3 }
        if (ws.isEmpty() || ws.size != words.size) return emptyList()
        return entries().filter { e -> ws.all { w -> e.tokens.any { p -> same(w, p) } } }.take(max)
    }

    private fun same(user: String, prod: String): Boolean =
        user == prod || TextNormalizer.stem(user) == TextNormalizer.stem(prod) || (user.length >= 4 && prod.startsWith(user))

    companion object {
        val EMPTY = ProductCatalog({ emptyList() })
    }
}
