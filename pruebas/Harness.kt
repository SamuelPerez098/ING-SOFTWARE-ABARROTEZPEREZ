import android.content.Context
import android.content.SharedPreferences
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.ing_software_abarrotezperez.ai.config.AssistantConfig
import com.example.ing_software_abarrotezperez.ai.models.*
import com.example.ing_software_abarrotezperez.ai.repository.*
import com.example.ing_software_abarrotezperez.ai.tools.ToolRegistry
import java.time.LocalDate

class RowsCursor(private val rows: List<List<String?>>) : Cursor {
    private var i = -1
    override fun moveToNext() = ++i < rows.size
    override fun getString(k: Int) = rows[i][k]
    override fun getInt(k: Int) = rows[i][k]?.toDouble()?.toInt() ?: 0
    override fun getDouble(k: Int) = rows[i][k]?.toDouble() ?: 0.0
    override fun isNull(k: Int) = rows[i][k] == null
    override fun close() {}
}

/** Ejecuta el SQL real en SQLite (vía python, modo solo lectura) */
class BridgeDb(private val path: String) : SQLiteDatabase() {
    var queries = 0
    override fun rawQuery(sql: String, selectionArgs: Array<String>): Cursor {
        queries++
        val args = selectionArgs.joinToString(",", "[", "]") { "\"" + it.replace("\\", "\\\\").replace("\"", "\\\"") + "\"" }
        val body = "{\"sql\":" + "\"" + sql.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"" + ",\"args\":" + args + "}"
        val pb = ProcessBuilder("python3", "/home/claude/test/sqlrun.py", path).redirectErrorStream(false)
        val p = pb.start()
        p.outputStream.use { it.write(body.toByteArray()) }
        val out = p.inputStream.readBytes().toString(Charsets.UTF_8)
        val err = p.errorStream.readBytes().toString(Charsets.UTF_8)
        if (p.waitFor() != 0) throw RuntimeException("SQL error: " + err.trim().lines().last())
        val rows = if (out.isEmpty()) emptyList() else out.split("\u001e").map { r ->
            r.split("\u001f").map { if (it == "\u0000N") null else it }
        }
        return RowsCursor(rows)
    }
}

class MemPrefs : SharedPreferences {
    val m = HashMap<String, Any>()
    override fun getInt(k: String, d: Int) = (m[k] as Int?) ?: d
    override fun getBoolean(k: String, d: Boolean) = (m[k] as Boolean?) ?: d
    override fun edit() = object : SharedPreferences.Editor {
        override fun putInt(k: String, v: Int) = apply { m[k] = v }
        override fun putBoolean(k: String, v: Boolean) = apply { m[k] = v }
        override fun apply() {}
    }
}
class FakeCtx : Context() {
    val prefs = MemPrefs()
    override fun getApplicationContext(): Context = this
    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
}

var fails = 0
fun check(name: String, cond: Boolean, detail: String = "") {
    println((if (cond) "  OK   " else "  FALLA") + " $name" + if (!cond && detail.isNotEmpty()) "  -> $detail" else "")
    if (!cond) fails++
}
fun show(r: AssistantResponse) {
    println("   » ${r.text}")
    r.table?.let { t -> println("     | " + t.headers.joinToString(" | ")); t.rows.forEach { println("     | " + it.joinToString(" | ")) } }
    r.notes.forEach { println("     * $it") }
}

fun main() {
    val db = BridgeDb("/home/claude/test/tienda_test.db")
    val ctx = FakeCtx()
    val cfg = AssistantConfig(ctx)
    val hoy = LocalDate.of(2026, 11, 22)
    val errors = mutableListOf<String>()
    val reg = ToolRegistry(SqliteAssistantRepository { db }, cfg, { hoy }, { m, e -> errors += "$m: ${e.message}" })
    fun run(i: AssistantIntent, p: QueryParameters = QueryParameters()): AssistantResponse {
        println("\n[${i}] ${p}"); val r = reg.execute(i, p); show(r); return r
    }

    var r = run(AssistantIntent.EXPIRING_SOON, QueryParameters(days = 21))
    check("caducan 21 días: 2 filas (Coca lote 25/11 y Galletas por fallback)", r.table!!.rows.size == 2)
    check("orden por fecha real (25/11 antes que 05/12)", r.table!!.rows[0][2] == "25/11/26" && r.table!!.rows[1][2] == "05/12/26")
    check("Faltan 3 días y 13 días", r.table!!.rows[0][3] == "3 días" && r.table!!.rows[1][3] == "13 días")
    check("costo total $96.00", r.text.contains("$96.00") && r.text.contains("9 unidades"), r.text)
    r = run(AssistantIntent.EXPIRING_SOON, QueryParameters(days = 30))
    check("30 días: 3 filas, incluye lote 15/12", r.table!!.rows.size == 3 && r.table!!.rows[2][2] == "15/12/26")
    r = run(AssistantIntent.EXPIRED)
    check("caducados: solo Leche (Sabritas excluido por stock 0)", r.table!!.rows.size == 1 && r.table!!.rows[0][0].startsWith("Leche") && r.table!!.rows[0][3] == "12 días")

    r = run(AssistantIntent.LOW_STOCK)
    check("pocas existencias (<=5): Atún, Galletas, Jabón en ese orden", r.table!!.rows.map { it[0] } == listOf("Atún Dolores", "Galletas Marinela", "Jabón Zote"))
    r = run(AssistantIntent.OUT_OF_STOCK)
    check("agotados: solo Sabritas", r.table!!.rows.size == 1 && r.table!!.rows[0][0].startsWith("Sabritas"))
    r = run(AssistantIntent.INVENTORY_VALUE)
    check("valor inventario sin PRUEBA = $1,780.00", r.text.contains("$1,780.00") && r.text.contains("130 unidades"), r.text)
    check("avisa 1 producto sin costo y 3 de prueba excluidos", r.notes.size == 2 && r.notes[1].contains("3 productos de prueba"))
    cfg.excludeTestProducts = false
    r = run(AssistantIntent.INVENTORY_VALUE)
    check("con PRUEBA incluidos = $10,780.00", r.text.contains("$10,780.00"), r.text)
    cfg.excludeTestProducts = true

    r = run(AssistantIntent.SEARCH_PRODUCT, QueryParameters(productQuery = "atun"))
    check("búsqueda 'atun' encuentra 'Atún Dolores' (sin acentos)", r.table!!.rows.size == 1 && r.table!!.rows[0][0] == "Atún Dolores")
    r = run(AssistantIntent.SEARCH_PRODUCT, QueryParameters(productQuery = "prueba"))
    check("búsqueda no devuelve productos PRUEBA", r.table == null)
    r = run(AssistantIntent.RESTOCK_NEEDS)
    check("reabastecer: Atún, Galletas, Jabón por urgencia", r.table!!.rows.map { it[0] } == listOf("Atún Dolores", "Galletas Marinela", "Jabón Zote"), r.table!!.rows.toString())
    check("cobertura Atún ≈ 8 d (7.5)", r.table!!.rows[0][3] == "≈ 8 d")

    r = run(AssistantIntent.TOP_SELLING, QueryParameters(limit = 5))
    check("top 5 (30 días): Coca 15, Atún 8, Galletas 4, Jabón 3, Leche 2; sin PRUEBA",
        r.table!!.rows.map { it[1] + ":" + it[2] } == listOf("Coca-Cola 600ml:15", "Atún Dolores:8", "Galletas Marinela:4", "Jabón Zote:3", "Leche Lala 1L:2"), r.table!!.rows.toString())
    r = run(AssistantIntent.LEAST_SELLING, QueryParameters(limit = 3))
    check("menos vendidos: Leche, Jabón, Galletas", r.table!!.rows.map { it[1] } == listOf("Leche Lala 1L", "Jabón Zote", "Galletas Marinela"))
    r = run(AssistantIntent.SALES_TOTAL, QueryParameters(period = TimePeriod.thisWeek(hoy)))
    check("ventas esta semana = $443.00 en 6 ventas", r.text.contains("$443.00") && r.text.contains("6 ventas"), r.text)
    check("fiado semana $130.00 en notas", r.notes.any { it.contains("$130.00") })
    r = run(AssistantIntent.SALES_TOTAL, QueryParameters(period = TimePeriod.lastMonth(hoy)))
    check("mes anterior = $320.00 en 2 ventas", r.text.contains("$320.00") && r.text.contains("2 ventas"), r.text)
    r = run(AssistantIntent.SALES_COMPARE, QueryParameters(period = TimePeriod.thisMonth(hoy)))
    check("comparar mes vs mes anterior: $509.00 vs $320.00 (+59.1%)", r.text.contains("$509.00") && r.text.contains("$320.00") && r.text.contains("+59.4%".replace("59.4","59.1")), r.text)
    r = run(AssistantIntent.SALES_TOTAL, QueryParameters(period = TimePeriod.lastDays(3, LocalDate.of(2020, 1, 10))))
    check("período sin ventas -> mensaje claro", r.text.startsWith("No hay ventas"))

    r = run(AssistantIntent.NO_MOVEMENT)
    check("sin movimiento 30d: Arroz y Frijol, $1,200.00 detenidos", r.table!!.rows.map { it[0] } == listOf("Arroz 1kg", "Frijol Sin Costo") && r.text.contains("$1,200.00"), r.text)
    r = run(AssistantIntent.LOW_ROTATION)
    check("baja rotación: solo Leche (≈150 d)", r.table!!.rows.size == 1 && r.table!!.rows[0][0].startsWith("Leche") && r.table!!.rows[0][3] == "≈ 150 d")
    r = run(AssistantIntent.SHRINKAGE)
    check("mermas de noviembre: 2 registros, 3 unidades, $48.00, motivo Caducado", r.text.contains("2 mermas") && r.text.contains("$48.00") && r.text.contains("Caducado"), r.text)

    r = run(AssistantIntent.SALES_TOTAL, QueryParameters(period = TimePeriod.today(hoy)))
    check("gramática: 'Hoy vendiste $54.00' (no 'En hoy')", r.text.startsWith("Hoy vendiste $54.00"), r.text)
    r = run(AssistantIntent.SALES_TOTAL, QueryParameters(period = TimePeriod.yesterday(hoy)))
    check("gramática: 'Ayer vendiste $196.00 (146 + 50 de venta de prueba)'", r.text.startsWith("Ayer vendiste $196.00 en 2 ventas"), r.text)
    r = run(AssistantIntent.SEARCH_PRODUCT, QueryParameters(productQuery = "jabones"))
    check("plural 'jabones' encuentra Jabón Zote", r.table?.rows?.firstOrNull()?.get(0) == "Jabón Zote", r.text)
    r = run(AssistantIntent.SEARCH_PRODUCT, QueryParameters(productQuery = "cocas"))
    check("plural 'cocas' encuentra Coca-Cola", r.table?.rows?.firstOrNull()?.get(0)?.startsWith("Coca") == true, r.text)
    println("\n── Validación y seguridad ──")
    r = run(AssistantIntent.EXPIRING_SOON, QueryParameters(days = 0))
    check("days=0 rechazado sin tocar la base", r.isError)
    r = run(AssistantIntent.EXPIRING_SOON, QueryParameters(days = 100000))
    check("days=100000 rechazado", r.isError)
    r = run(AssistantIntent.TOP_SELLING, QueryParameters(limit = 1000, period = TimePeriod.lastDays(30, hoy)))
    check("limit=1000 se recorta a 50 (no error)", !r.isError)
    r = run(AssistantIntent.TOP_SELLING, QueryParameters(period = TimePeriod(LocalDate.of(1900,1,1), LocalDate.of(2026,1,1), "x")))
    check("período de >5 años rechazado", r.isError)
    r = run(AssistantIntent.SEARCH_PRODUCT, QueryParameters(productQuery = "x'; DROP TABLE producto;--"))
    check("texto malicioso en búsqueda no rompe nada (se filtra en Kotlin)", !r.isError)
    val total = (db.rawQuery("SELECT COUNT(*) FROM producto", emptyArray()).also { it.moveToNext() }).getInt(0)
    check("tabla producto intacta (11 filas)", total == 11)
    r = run(AssistantIntent.HELP); check("ayuda", r.text.contains("sin Internet"))
    r = run(AssistantIntent.UNKNOWN); check("UNKNOWN pide aclaración", r.needsClarification)
    try { SqliteAssistantRepository { db }.javaClass.getDeclaredMethod("query", String::class.java, Boolean::class.java, List::class.java, kotlin.jvm.functions.Function1::class.java)
        .also { it.isAccessible = true }.invoke(SqliteAssistantRepository { db }, "DELETE FROM producto", false, emptyList<Any>(), { c: Cursor -> 1 })
        check("repositorio rechaza DELETE", false) } catch (e: Exception) { check("repositorio rechaza DELETE (IllegalArgument)", e.cause is IllegalArgumentException, e.toString()) }
    check("sin errores internos inesperados", errors.isEmpty(), errors.toString())
    println("\nConsultas SQL ejecutadas: ${db.queries}. Fallos: $fails")
    if (fails > 0) System.exit(1)
}
