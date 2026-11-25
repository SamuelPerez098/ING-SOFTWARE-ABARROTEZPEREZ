package com.example.ing_software_abarrotezperez.ai.insights

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VistoTest {

    private fun insight(id: String, s: Severidad) =
        Insight(id, TipoInsight.COBRANZA, s, "título", "detalle", "pregunta")

    @Test
    fun noSuena_siNadaCambio() {
        val visto = mapOf("fiado_antiguo:cliente1" to Severidad.ATENCION)
        assertFalse(Visto.haySinVer(visto, listOf(insight("fiado_antiguo:cliente1", Severidad.ATENCION))))
    }

    @Test
    fun suena_siIdNuevo() {
        assertTrue(Visto.haySinVer(emptyMap(), listOf(insight("fiado_antiguo:cliente1", Severidad.ATENCION))))
    }

    @Test
    fun suena_siEmpeoro() {
        val visto = mapOf("fiado_antiguo:cliente1" to Severidad.ATENCION)
        assertTrue(Visto.haySinVer(visto, listOf(insight("fiado_antiguo:cliente1", Severidad.CRITICA))))
    }

    @Test
    fun noSuena_siMejoro() {
        val visto = mapOf("fiado_antiguo:cliente1" to Severidad.CRITICA)
        assertFalse(Visto.haySinVer(visto, listOf(insight("fiado_antiguo:cliente1", Severidad.ATENCION))))
    }

    @Test
    fun vuelveAAvisar_siReaparece() {
        val visto = mapOf("fiado_antiguo:cliente1" to Severidad.ATENCION)
        val limpio = Visto.marcarVisto(visto, emptyList())
        assertFalse(limpio.containsKey("fiado_antiguo:cliente1"))
        assertTrue(Visto.haySinVer(limpio, listOf(insight("fiado_antiguo:cliente1", Severidad.ATENCION))))
    }

    @Test
    fun noSuena_siSoloHayInfo() {
        val visto = emptyMap<String, Severidad>()
        assertFalse(Visto.haySinVer(visto, listOf(insight("ticket_promedio", Severidad.INFO))))
    }
}
