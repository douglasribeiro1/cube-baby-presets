package io.github.douglasribeiro1.cubebaby

import io.github.douglasribeiro1.cubebaby.modelo.Ajustes
import io.github.douglasribeiro1.cubebaby.modelo.Campo
import io.github.douglasribeiro1.cubebaby.modelo.normalizado
import io.github.douglasribeiro1.cubebaby.ui.textoValor
import org.junit.Assert.assertEquals
import org.junit.Test

class NomesTest {
    private val a = Ajustes()

    @Test fun ordem_do_painel() {
        assertEquals(
            listOf("type", "gain", "tone", "mod", "time", "fb", "mix", "reverb", "ir_cab", "volume"),
            Campo.entries.map { it.chaveSite },
        )
    }

    @Test fun amps_e_caixas_de_fabrica() {
        assertEquals("0 · Power-Zone Clean — Clean", a.textoValor(Campo.TYPE, 0))
        assertEquals("4 · Cali JP A — Overdrive", a.textoValor(Campo.TYPE, 4))
        assertEquals("8 · Mr Smith Dist — Distortion", a.textoValor(Campo.TYPE, 8))
        assertEquals("0 · IR desligada", a.textoValor(Campo.IR_CAB, 0))
        assertEquals("1 · Line 6 Vetta 2×12", a.textoValor(Campo.IR_CAB, 1))
        assertEquals("8 · Diezel V30 SM57 (Soldano HR PowerAmp) 4×12", a.textoValor(Campo.IR_CAB, 8))
    }

    @Test fun nome_personalizado_e_volta_a_fabrica() {
        val p = a.copy(nomesCabs = a.nomesCabs.toMutableList().also { it[3] = "Minha IR" })
        assertEquals("3 · Minha IR", p.textoValor(Campo.IR_CAB, 3))
        // nome em branco cai no de fábrica; lista curta é completada
        val curta = a.copy(nomesAmps = listOf("", "X")).normalizado()
        assertEquals(9, curta.nomesAmps.size)
        assertEquals("0 · Power-Zone Clean — Clean", curta.textoValor(Campo.TYPE, 0))
        assertEquals("1 · X — Clean", curta.textoValor(Campo.TYPE, 1))
    }

    @Test fun mod_mix_reverb() {
        assertEquals("Chorus (0)", a.textoValor(Campo.MOD, 0))
        assertEquals("Desligado (7)", a.textoValor(Campo.MOD, 7))
        assertEquals("Phaser (15)", a.textoValor(Campo.MOD, 15))
        assertEquals("0 · delay desligado", a.textoValor(Campo.MIX, 0))
        assertEquals("30", a.textoValor(Campo.MIX, 30))
        assertEquals("0 · desligado", a.textoValor(Campo.REVERB, 0))
    }
}
