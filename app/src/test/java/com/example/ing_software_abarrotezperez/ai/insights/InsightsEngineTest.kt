package com.example.ing_software_abarrotezperez.ai.insights

import com.example.ing_software_abarrotezperez.ai.repository.AssistantRepository
import com.example.ing_software_abarrotezperez.ai.repository.DeudaRow
import com.example.ing_software_abarrotezperez.ai.repository.MermasResultado
import com.example.ing_software_abarrotezperez.ai.repository.ProductoBusqueda
import com.example.ing_software_abarrotezperez.ai.repository.VentasResumen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class InsightsEngineTest {

    class FakeConfig : InsightConfig {
        override var excludeTestProducts = true
        override var diasHistorialMinimo = 14
        override var ventasMinimasHistorico = 5
        override var diasDeudaAntigua = 30
        override var diasDeudaCritica = 60
        override var montoDeudaAlerta = 200f
        override var montoDeudaCritica = 500f
        override var montoDeudaTotalCritica = 1000f
        override var horaSinVentas = 12
        override var horaCierre = 20
        override var ticketsMinimosDiaActivo = 2
        override var caidaVentasCritica = 0.30f
        override var pisoGananciaSemanal = 50f
        override var perdidaMargenCritica = 100f
        override var mermasPisoPesos = 20f
        override var mermasPisoCritico = 100f
        override var factorMermaCritica = 2.0f
        override var diasRespaldoAlerta = 7
    }

    data class Venta(val fecha: String, val total: Double, val ganancia: Double = 0.0)

    class FakeRepo : AssistantRepository {
        var ventas = mutableListOf<Venta>()
        var fiados = mutableListOf<DeudaRow>()
        var mayor: DeudaRow? = null
        var productos = mutableListOf<ProductoBusqueda>()
        var mermasResultado = MermasResultado(emptyList(), 0, 0, 0.0, emptyList())
        var perdidaMargen = 0.0

        override fun resumenVentas(desde: String, hastaExclusivo: String): VentasResumen {
            val en = ventas.filter { it.fecha >= desde && it.fecha < hastaExclusivo }
            return VentasResumen(en.sumOf { it.total }, en.size, 0, en.sumOf { it.ganancia }, 0.0)
        }
        override fun fiadoPendiente() = fiados.sumOf { it.monto } to fiados.size
        override fun fiadoAntiguo(limiteIso: String) = fiados
        override fun mayorDeudor() = mayor
        override fun deudores(limite: Int) = fiados
        override fun listarProductos(excluirPrueba: Boolean) = productos
        override fun mermas(desde: String, hastaExclusivo: String, limite: Int, excluirPrueba: Boolean) = mermasResultado
        override fun perdidaMargenNegativo(desde: String, hastaExclusivo: String) = perdidaMargen

        // no usados por el motor
        override fun productosPocoStock(umbral: Int, limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun productosAgotados(limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun valorInventario(excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun reabastecer(desde: String, hasta: String, dias: Int, umbral: Int, coberturaObjetivo: Int, limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun caducanEntre(desdeIso: String, hastaIso: String, limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun caducados(hoyIso: String, limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun ventasPorProducto(desde: String, hastaExclusivo: String, limite: Int, masVendidos: Boolean, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun sinMovimiento(desde: String, hastaExclusivo: String, limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
        override fun bajaRotacion(desde: String, hastaExclusivo: String, dias: Int, coberturaMin: Int, limite: Int, excluirPrueba: Boolean) = throw UnsupportedOperationException()
    }

    private fun correr(cfg: FakeConfig, repo: FakeRepo, ahora: LocalDateTime, respaldo: Long? = null) =
        runBlocking { InsightsEngine(repo, cfg, { ahora }, { respaldo }).calcular() }

    // domingo 2026-11-22, 14:30
    private val domingo = LocalDateTime.of(2026, 11, 22, 14, 30)

    @Test
    fun pagoParcial_reportaSaldo() {
        val repo = FakeRepo().apply {
            fiados = mutableListOf(DeudaRow(1, "Doña Mary", 60.0), DeudaRow(1, "Doña Mary", 30.0))
        }
        val ins = correr(FakeConfig(), repo, domingo)
        val f = ins.first { it.id == "fiado_pendiente" }
        assertEquals("2 fiados por cobrar · $90.00 en total.", f.detalle)
    }

    @Test
    fun tiendaNueva_noGeneraComparativos() {
        val cfg = FakeConfig().apply { ventasMinimasHistorico = 1000 }
        val repo = FakeRepo().apply {
            ventas = mutableListOf(Venta("2026-11-22 09:10:00", 54.0))
            fiados = mutableListOf(DeudaRow(1, "Doña Mary", 90.0))
        }
        val ins = correr(cfg, repo, domingo)
        assertTrue(ins.any { it.tipo == TipoInsight.COBRANZA })
        assertTrue(ins.any { it.tipo == TipoInsight.RESPALDO })
        assertFalse(ins.any { it.tipo in setOf(TipoInsight.VENTAS, TipoInsight.GANANCIA, TipoInsight.MERMAS) })
    }

    @Test
    fun idsEstables_entreDias() {
        val repo = FakeRepo().apply {
            ventas = mutableListOf(Venta("2026-11-22 09:10:00", 54.0), Venta("2026-11-21 10:00:00", 146.0, 42.0))
            fiados = mutableListOf(DeudaRow(1, "Doña Mary", 90.0))
        }
        val cfg = FakeConfig()
        val dom = correr(cfg, repo, domingo).map { it.id }.toSet()
        val lun = correr(cfg, repo, LocalDateTime.of(2026, 11, 23, 14, 30)).map { it.id }.toSet()
        assertTrue(dom.contains("fiado_pendiente") && lun.contains("fiado_pendiente"))
        assertTrue(dom.contains("respaldo") && lun.contains("respaldo"))
        assertFalse(dom.any { it.startsWith("fiado_pendiente:2026-") })
    }

    @Test
    fun deudaCritica_limiteJustoDebajo() {
        val cfg = FakeConfig().apply { montoDeudaCritica = 500f }
        val repo = FakeRepo().apply { fiados = mutableListOf(DeudaRow(1, "Doña Mary", 499.0)) }
        val ins = correr(cfg, repo, domingo)
        assertFalse(ins.any { it.severidad == Severidad.CRITICA && it.id.startsWith("fiado_antiguo") })
        assertTrue(ins.any { it.severidad == Severidad.ATENCION && it.id.startsWith("fiado_antiguo") })

        repo.fiados = mutableListOf(DeudaRow(1, "Doña Mary", 500.0))
        val ins2 = correr(cfg, repo, domingo)
        assertTrue(ins2.any { it.severidad == Severidad.CRITICA && it.id.startsWith("fiado_antiguo") })
    }

    @Test
    fun sinVentas_atencionYCritica() {
        val cfg = FakeConfig()
        // 5 ventas en el historial (>=5), hoy 0; domingo anterior vendió -> ATENCION a las 13:00
        val repo = FakeRepo().apply {
            ventas = mutableListOf(
                Venta("2026-11-15 09:00:00", 50.0),
                Venta("2026-11-16 09:00:00", 50.0),
                Venta("2026-11-17 09:00:00", 50.0),
                Venta("2026-11-18 09:00:00", 50.0),
                Venta("2026-11-19 09:00:00", 50.0)
            )
        }
        val atencion = correr(cfg, repo, LocalDateTime.of(2026, 11, 22, 13, 0))
        assertTrue(atencion.any { it.id == "sin_ventas:2026-11-22" && it.severidad == Severidad.ATENCION })

        // 4 domingos con 2 tickets c/u (promedio 2) + 1 venta extra para historial >= 5 -> CRITICA a las 21:00
        val repo2 = FakeRepo().apply {
            ventas = mutableListOf(
                Venta("2026-11-15 09:00:00", 50.0), Venta("2026-11-15 10:00:00", 30.0),
                Venta("2026-11-08 09:00:00", 40.0), Venta("2026-11-08 10:00:00", 20.0),
                Venta("2026-11-01 09:00:00", 30.0), Venta("2026-11-01 10:00:00", 10.0),
                Venta("2026-10-25 09:00:00", 20.0), Venta("2026-10-25 10:00:00", 10.0),
                Venta("2026-11-16 09:00:00", 40.0)
            )
        }
        val critica = correr(cfg, repo2, LocalDateTime.of(2026, 11, 22, 21, 0))
        assertTrue(critica.any { it.id == "sin_ventas:2026-11-22" && it.severidad == Severidad.CRITICA })
    }

    @Test
    fun campanitaNoSuena_siNadaCambio() {
        val cfg = FakeConfig()
        val repo = FakeRepo().apply {
            ventas = mutableListOf(Venta("2026-11-22 09:10:00", 54.0), Venta("2026-11-21 10:00:00", 146.0, 42.0))
            fiados = mutableListOf(DeudaRow(1, "Doña Mary", 90.0))
        }
        // dos cálculos el mismo día -> mismos ids y mismas severidades
        val a = correr(cfg, repo, domingo)
        val b = correr(cfg, repo, domingo)
        assertEquals(a.map { it.id to it.severidad }, b.map { it.id to it.severidad })
    }
}
