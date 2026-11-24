import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.insights.InsightsEngine
import com.example.ing_software_abarrotezperez.ai.insights.Severidad
import com.example.ing_software_abarrotezperez.ai.insights.TipoInsight
import com.example.ing_software_abarrotezperez.ai.repository.SqliteAssistantRepository
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime

var fails = 0
fun check(name: String, cond: Boolean, detail: String = "") {
    println((if (cond) "  OK   " else "  FALLA") + " $name" + if (!cond && detail.isNotEmpty()) "  -> $detail" else "")
    if (!cond) fails++
}

/**
 * Pruebas del motor de resumen con el seed de `tienda_test.db` (hoy = domingo 2026-11-22).
 * Casos límite: tienda nueva (sin historial), domingo cerrado, día a medias y deudor con pago
 * parcial. El seed fija "hoy" con 1 venta y 2 fiados; los comparativos se verifican con
 * historial insuficiente y con ids estables.
 */
fun main() {
    val db = BridgeDb("/home/claude/test/tienda_test.db")
    val ctx = FakeCtx()
    val cfg = AssistantConfig(ctx)
    val repo = SqliteAssistantRepository { db }
    val domingo = LocalDateTime.of(2026, 11, 22, 14, 30)

    fun engine() = InsightsEngine(repo, cfg, { domingo }, { null })

    runBlocking {
        var ins = engine().calcular()
        println("── Resumen (domingo 14:30, historial completo) ──")
        ins.forEach { println("   • [${it.severidad}] ${it.titulo} → ${it.detalle}   (${it.id})") }

        check("fiado pendiente: 2 fiados · $130.00", ins.any { it.id == "fiado_pendiente:2026-11-22" && it.detalle.contains("$130.00") && it.detalle.contains("2 fiados") })
        check("mayor deudor: Doña Mary $130.00", ins.any { it.id.startsWith("mayor_deudor:") && it.detalle.contains("Doña Mary") && it.detalle.contains("$130.00") })
        check("mermas al alza: $48.00 vs $12.00", ins.any { it.id.startsWith("mermas_mes:2026-11") && it.detalle.contains("$48.00") && it.detalle.contains("$12.00") })
        check("mayor pérdida: Leche $40.00", ins.any { it.id.startsWith("merma_producto:") && it.detalle.contains("Leche") && it.detalle.contains("$40.00") })
        check("ticket promedio presente", ins.any { it.id == "ticket_promedio:30d" })
        check("respaldo ausente = INFO", ins.any { it.id.startsWith("respaldo:") && it.severidad == Severidad.INFO })
        // hoy vendió $54 → no dispara "sin ventas"
        check("sin ventas NO se dispara (hoy sí vendió)", ins.none { it.id.startsWith("sin_ventas") })
        // el domingo anterior (11-15) no vendió → no hay comparativo justo
        check("comparativo hoy vs semana pasada se omite (domingo sin ventas)", ins.none { it.id.startsWith("ventas_hoy_vs_semana") })
        check("sin margen negativo en el seed", ins.none { it.tipo == TipoInsight.GANANCIA && it.id.startsWith("margen_bajo") })

        // Id estable: dos cálculos → mismos ids
        val ids1 = engine().calcular().map { it.id }.toSet()
        val ids2 = engine().calcular().map { it.id }.toSet()
        check("ids estables entre cálculos", ids1 == ids2, (ids1 - ids2).toString() + " / " + (ids2 - ids1).toString())

        // Tienda nueva: sin historial → solo cobranza/respaldo, sin comparativos
        cfg.ventasMinimasHistorico = 1000
        ins = engine().calcular()
        println("── Tienda nueva (historial mínimo 1000) ──")
        ins.forEach { println("   • [${it.severidad}] ${it.titulo} (${it.id})") }
        check("tienda nueva: no genera comparativos (ventas/ganancia/mermas)", ins.none { it.tipo in setOf(TipoInsight.VENTAS, TipoInsight.GANANCIA, TipoInsight.MERMAS) })
        check("tienda nueva: sí genera cobranza y respaldo", ins.any { it.tipo == TipoInsight.COBRANZA } && ins.any { it.tipo == TipoInsight.RESPALDO })
        cfg.ventasMinimasHistorico = 5

        // Día a medias: a las 09:00 (antes de la hora de cierre) no se exige historial de ventas
        val madrugada = InsightsEngine(repo, cfg, { LocalDateTime.of(2026, 11, 22, 9, 0) }, { null }).calcular()
        check("día a medias: a las 09:00 no hay 'sin ventas' aunque cierre sea 12:00", madrugada.none { it.id.startsWith("sin_ventas") })
    }

    println("\nFallos: $fails")
    if (fails > 0) System.exit(1)
}
