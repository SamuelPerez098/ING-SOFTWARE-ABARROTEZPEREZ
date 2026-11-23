import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.engine.AssistantEngine
import com.example.ing_software_abarrotezperez.ai.interpreter.*
import com.example.ing_software_abarrotezperez.ai.repository.SqliteAssistantRepository
import com.example.ing_software_abarrotezperez.ai.tools.ToolRegistry
import java.time.LocalDate
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine

fun <T> runSusp(block: suspend () -> T): T {
    var res: Result<T>? = null
    block.startCoroutine(Continuation(EmptyCoroutineContext) { res = it })
    return res!!.getOrThrow()
}

fun main() {
    val db = BridgeDb("/home/claude/test/tienda_test.db")
    val cfg = AssistantConfig(FakeCtx())
    val hoy = LocalDate.of(2026, 11, 22)
    val repo = SqliteAssistantRepository { db }
    val engine = AssistantEngine(
        QueryParser(ProductCatalog(ProductNameSource { repo.listarProductos(cfg.excludeTestProducts).map { it.nombre } })) { hoy },
        ToolRegistry(repo, cfg, { hoy })
    )
    val preguntas = listOf(
        "Muéstrame todos los productos que caducan en las próximas 3 semanas",
        "¿Cuáles son mis 5 productos más vendidos?",
        "¿Qué productos tienen pocas existencias?",
        "¿Cuáles son los productos agotados?",
        "¿Cuánto vendí durante esta semana?",
        "¿Cuáles son los productos que menos se venden?",
        "¿Qué productos no han tenido ventas durante el último mes?",
        "¿Cuánto dinero tengo invertido en inventario?",
        "¿Qué productos necesito comprar?",
        "Muéstrame las ventas del mes anterior",
        "cuantas cocas me quedan",
        "ventas y caducidad",
        "cuanto me deben"
    )
    var bad = 0
    for (p in preguntas) {
        val r = runSusp { engine.respond(p) }
        println("\n> $p\n  ${r.text.replace("\n", " / ")}")
        r.table?.let { t -> t.rows.take(6).forEach { println("    | " + it.joinToString(" | ")) } }
        if (r.isError) bad++
    }
    println("\nRespuestas con error interno: $bad")
}
