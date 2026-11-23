package io.github.douglasribeiro1.cubebaby

import io.github.douglasribeiro1.cubebaby.comunidade.ComunidadeRepository
import io.github.douglasribeiro1.cubebaby.dados.FormatoBackup
import io.github.douglasribeiro1.cubebaby.midi.Protocolo
import io.github.douglasribeiro1.cubebaby.modelo.validarBytes
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test fun identificacao_bate_com_exemplo_real() {
        assertArrayEquals(bytes(0x00, 0x59, 0x11, 0x00, 0x00, 0x00, 0xFF), Protocolo.montarQuadro(0x11, ByteArray(0)))
        assertArrayEquals(
            bytes(0xF0, 0x00, 0x32, 0x45, 0x00, 0x00, 0x00, 0x40, 0x7F, 0xF7),
            Protocolo.montarSysEx(0x11, ByteArray(0)),
        )
    }

    @Test fun leitura_dos_presets_bate_com_exemplo_real() {
        assertArrayEquals(
            bytes(0x00, 0x59, 0x23, 0x08, 0x00, 0x00, 0x05, 0x00, 0x00, 0x00, 0x00, 0x30, 0x00, 0x00, 0xCA),
            Protocolo.montarQuadro(Protocolo.CMD_LER, Protocolo.payloadLerPresets()),
        )
        assertArrayEquals(
            bytes(0xF0, 0x00, 0x32, 0x0D, 0x41, 0x00, 0x00, 0x40, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x06, 0x00, 0x00, 0x4A, 0x01, 0xF7),
            Protocolo.montarSysEx(Protocolo.CMD_LER, Protocolo.payloadLerPresets()),
        )
    }

    @Test fun pack_unpack_ida_e_volta() {
        val r = java.util.Random(1)
        repeat(200) {
            val dados = ByteArray(r.nextInt(80)).also { r.nextBytes(it) }
            val volta = Protocolo.unpack7(Protocolo.pack7(dados))
            assertArrayEquals(dados, volta.copyOf(dados.size))
            Protocolo.pack7(dados).forEach { b -> assertTrue(b.toInt() in 0..0x7F) }
        }
    }

    @Test fun analisa_resposta_de_leitura() {
        val fabrica = intArrayOf(
            7, 7, 9, 5, 0x34, 0x41, 0x14, 0x1E, 7, 4, 1, 1, 1, 0, 0, 0,
            4, 2, 5, 5, 0x2C, 0x46, 0x17, 0x1E, 7, 5, 1, 1, 1, 0, 0, 0,
            1, 7, 7, 5, 0x34, 0x6F, 0x14, 0x0C, 5, 2, 1, 1, 1, 0, 0, 0,
        )
        val payload = bytes(5) + Protocolo.le32(0) + Protocolo.le24(48) + ByteArray(48) { fabrica[it].toByte() }
        val sysex = Protocolo.montarSysEx(Protocolo.CMD_LER, payload)
        val a = Protocolo.analisarSysEx(sysex) as Protocolo.Analise.Ok
        val p = Protocolo.extrairPresets(a.quadro)
        assertEquals(fabrica.toList(), p.map { it.toInt() and 0xFF })
        // os presets de fábrica passam na validação de faixas
        for (s in 0 until 3) validarBytes(fabrica.slice(s * 16 until s * 16 + 16))
    }

    @Test fun checksum_errado_e_recusado() {
        val q = Protocolo.montarQuadro(0x00, bytes(0)).also { it[it.size - 1] = 0x12 }
        assertTrue(Protocolo.analisarQuadro(q) is Protocolo.Analise.Erro)
    }

    @Test fun ack_de_sucesso() {
        val q = (Protocolo.analisarQuadro(bytes(0x00, 0x59, 0x00, 0x01, 0x00, 0x00, 0x00, 0xFF)) as Protocolo.Analise.Ok).quadro
        assertEquals(Protocolo.CMD_ACK, q.cmd)
        assertEquals(0, q.payload[0].toInt())
    }

    @Test fun gravacao_so_no_espaco_5_bytes_0_a_12() {
        // permitido
        Protocolo.payloadGravar(0, ByteArray(13))
        Protocolo.payloadGravar(0x10 + 12, ByteArray(1))
        Protocolo.payloadGravar(0x20 + 5, ByteArray(8))
        assertEquals(5, Protocolo.payloadGravar(0x20, ByteArray(1))[0].toInt())
        // proibido
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(13, ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(0x0F, ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(10, ByteArray(4)) }
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(0x30, ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(-1, ByteArray(1)) }
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(0, ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { Protocolo.payloadGravar(0, ByteArray(14)) }
    }

    @Test fun validacao_de_faixas() {
        val ok = listOf(8, 7, 15, 15, 127, 127, 31, 118, 15, 8, 1, 1, 1, 9, 9, 9)
        assertEquals(listOf(0, 0, 0), validarBytes(ok).takeLast(3)) // 13–15 zerados
        assertThrows(IllegalArgumentException::class.java) { validarBytes(ok.toMutableList().also { it[0] = 9 }) }
        assertThrows(IllegalArgumentException::class.java) { validarBytes(ok.toMutableList().also { it[7] = 119 }) }
        assertThrows(IllegalArgumentException::class.java) { validarBytes(ok.toMutableList().also { it[10] = 2 }) }
        assertThrows(IllegalArgumentException::class.java) { validarBytes(ok.take(12)) }
    }

    @Test fun comunidade_extrai_id() {
        assertEquals(901L, ComunidadeRepository.extrairId("https://cube-baby-presets.vercel.app/presets/901"))
        assertEquals(901L, ComunidadeRepository.extrairId("Olha esse: https://cube-baby-presets.vercel.app/presets/901?x=1"))
        assertEquals(42L, ComunidadeRepository.extrairId(" 42 "))
        assertNull(ComunidadeRepository.extrairId("https://exemplo.com"))
    }

    @Test fun comunidade_preset_901_vira_bytes() {
        val kv = mapOf("volume" to 41, "ir_cab" to 1, "reverb" to 6, "mix" to 30, "fb" to 84, "time" to 22, "mod" to 7, "tone" to 6, "gain" to 7, "type" to 4)
            .mapValues { JsonPrimitive(it.value) }
        val p = ComunidadeRepository.PresetSite(id = 901, name = "Drive bem worship", knobs_values = kv, model_id = "cube-baby")
        // byte: 0 type, 1 gain, 2 tone, 3 reverb, 4 fb, 5 volume, 6 time, 7 mix, 8 mod, 9 ir cab
        assertEquals(listOf(4, 7, 6, 6, 84, 41, 22, 30, 7, 1), ComunidadeRepository.paraBytes(p).toList())

        assertThrows(IllegalArgumentException::class.java) { ComunidadeRepository.paraBytes(p.copy(model_id = "cube-baby-bass")) }
        assertThrows(IllegalArgumentException::class.java) { ComunidadeRepository.paraBytes(p.copy(knobs_values = kv + ("mix" to JsonPrimitive(119)))) }
        assertThrows(IllegalArgumentException::class.java) { ComunidadeRepository.paraBytes(p.copy(knobs_values = kv - "fb")) }
    }

    @Test fun comunidade_json_real_do_supabase() {
        val json = """[{"id":901,"name":"Drive bem worship","description":"Drive worship para stratocaster ","knobs_values":{"fb": 84, "mix": 30, "mod": 7, "gain": 7, "time": 22, "tone": 6, "type": 4, "ir_cab": 1, "reverb": 6, "volume": 41},"created_at":"2026-01-22T18:57:16.953769+00:00","model_id":"cube-baby","custom_ir":null,"vote_up_count":0,"vote_down_count":0,"user":{"username": "g.queirox"}}]"""
        val p = Json { ignoreUnknownKeys = true; coerceInputValues = true }.decodeFromString<List<ComunidadeRepository.PresetSite>>(json).single()
        assertEquals("g.queirox", p.autor)
        assertEquals("https://cube-baby-presets.vercel.app/presets/901", p.link)
        assertEquals(41, ComunidadeRepository.paraBytes(p)[5])
    }

    @Test fun importa_backup_do_prototipo() {
        val texto = """{"app":"cube-baby-presets","versao":1,"data":"2026-11-23T12:00:00.000Z",
            "bytes":[7,7,9,5,52,65,20,30,7,4,1,1,1,0,0,0,4,2,5,5,44,70,23,30,7,5,1,1,1,0,0,0,1,7,7,5,52,111,20,12,5,2,1,1,1,0,0,0],
            "biblioteca":[{"name":"Limpo","bytes":[7,7,9,5,52,65,20,30,7,4,1,1,1,0,0,0],"data":"2026-11-23T12:00:00.000Z"},
                          {"name":"Quebrado","bytes":[99,7,9,5,52,65,20,30,7,4,1,1,1,0,0,0]}]}"""
        val (b, descartados) = FormatoBackup.importar(texto)
        assertEquals(48, b.bytes!!.size)
        assertEquals(1, b.biblioteca.size)
        assertEquals("Limpo", b.biblioteca[0].name)
        assertEquals(1, descartados)
        // e o que exportamos volta igual
        val (b2, _) = FormatoBackup.importar(FormatoBackup.exportar(b))
        assertEquals(b.biblioteca, b2.biblioteca)
    }
}
