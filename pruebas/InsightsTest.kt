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
 * El seed incluye un pago parcial: Doña Mary debía $100 en el fiado 2 y abonó $40 (saldo $60);
 * más el fiado 1 de $30, la deuda total reportada es $90 (no $130).
 */
fun main() {
    val db = BridgeDb("/home/claude/test/tienda_test.db")
    val ctx = FakeCtx()
    val cfg = AssistantConfig(ctx)
    val repo = SqliteAssistantRepository { db }
    val domingo = LocalDateTime.of(2026, 11, 22, 14, 30)

    fun engine(ahora: LocalDateTime = domingo) = InsightsEngine(repo, cfg, { ahora }, { null })

    runBlocking {
        var ins = engine().calcular()
        println("── Resumen (domingo 14:30) ──")
        ins.forEach { println("   • [${it.severidad}] ${it.titulo} → ${it.detalle}   (${it.id})") }

        // pago parcial: 100 − 40 + 30 = 90
        check("pago parcial: saldo $90.00 (no $130.00)", ins.any { it.id == "fiado_pendiente" && it.detalle.contains("$90.00") && it.detalle.contains("2 fiados") && !it.detalle.contains("$130.00") })
        check("mayor deudor: Doña Mary $90.00", ins.any { it.id.startsWith("mayor_deudor:") && it.detalle.contains("Doña Mary") && it.detalle.contains("$90.00") })
        check("mermas al alza: $48.00 vs $12.00", ins.any { it.id.startsWith("mermas_mes:2026-11") && it.detalle.contains("$48.00") && it.detalle.contains("$12.00") })
        check("mayor pérdida: Leche $40.00", ins.any { it.id.startsWith("merma_producto:") && it.detalle.contains("Leche") && it.detalle.contains("$40.00") })
        check("ticket promedio presente", ins.any { it.id == "ticket_promedio" })
        check("respaldo sin registro = INFO", ins.any { it.id == "respaldo" && it.severidad == Severidad.INFO && it.detalle.contains("Aún no hay registro") })
        check("sin ventas NO se dispara (hoy sí vendió)", ins.none { it.id.startsWith("sin_ventas") })
        check("comparativo hoy vs semana pasada se omite (domingo sin ventas)", ins.none { it.id.startsWith("ventas_hoy_vs_semana") })
        check("sin margen negativo en el seed", ins.none { it.tipo == TipoInsight.GANANCIA && it.id.startsWith("margen_bajo") })

        // ids de estado no llevan fecha; dos días distintos → mismos ids de estado
        val idsDom = engine(domingo).calcular().map { it.id }.toSet()
        val idsLun = engine(LocalDateTime.of(2026, 11, 23, 14, 30)).calcular().map { it.id }.toSet()
        check("ids de estado estables entre días", listOf("fiado_pendiente", "respaldo", "ticket_promedio").all { it in idsDom && it in idsLun })
        check("id de estado no lleva fecha", idsDom.contains("fiado_pendiente") && idsDom.none { it.startsWith("fiado_pendiente:2026-") })

        // tienda nueva: sin historial → solo cobranza/respaldo
        cfg.ventasMinimasHistorico = 1000
        ins = engine().calcular()
        check("tienda nueva: no genera comparativos", ins.none { it.tipo in setOf(TipoInsight.VENTAS, TipoInsight.GANANCIA, TipoInsight.MERMAS) })
        check("tienda nueva: sí genera cobranza y respaldo", ins.any { it.tipo == TipoInsight.COBRANZA } && ins.any { it.tipo == TipoInsight.RESPALDO })
        cfg.ventasMinimasHistorico = 5

        // día a medias: a las 09:00 no hay "sin ventas"
        val madrugada = engine(LocalDateTime.of(2026, 11, 22, 9, 0)).calcular()
        check("día a medias: a las 09:00 no hay 'sin ventas'", madrugada.none { it.id.startsWith("sin_ventas") })
    }

    println("\nFallos: $fails")
    if (fails > 0) System.exit(1)
}
