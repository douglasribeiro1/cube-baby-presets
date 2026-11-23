package io.github.douglasribeiro1.cubebaby.midi

/**
 * Protocolo SysEx do Cube Baby (ver docs/PROTOCOLO.md).
 *
 * Quadro: 00 59 | CMD | LEN (24 bits LE) | PAYLOAD | CHK, com CHK = ~soma(payload) & 0xFF.
 * SysEx:  F0 + pack7(quadro) + F7.
 *
 * Funções puras, sem dependência do Android, cobertas por testes unitários.
 */
object Protocolo {
    const val CMD_ACK = 0x00
    const val CMD_INFO = 0x11
    const val CMD_GRAVAR = 0x22
    const val CMD_LER = 0x23

    /** Único espaço de memória que o app pode tocar. */
    const val ESPACO_PRESETS = 5
    const val TAM_PRESET = 16
    const val NUM_PRESETS = 3
    const val TAM_TOTAL = TAM_PRESET * NUM_PRESETS // 48 bytes, 0x00–0x2F
    /** Bytes 0–12 de cada preset são editáveis; 13–15 nunca são gravados. */
    const val BYTES_EDITAVEIS = 13

    const val NOME_ESPERADO = "SINCO-CubeBaby"

    fun pack7(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size * 8 / 7 + 2)
        var acc = 0L
        var bits = 0
        for (b in bytes) {
            acc = acc or ((b.toLong() and 0xFF) shl bits)
            bits += 8
            while (bits >= 7) {
                out.add((acc and 0x7F).toByte())
                acc = acc ushr 7
                bits -= 7
            }
        }
        if (bits > 0) out.add((acc and 0x7F).toByte())
        return out.toByteArray()
    }

    fun unpack7(bytes: ByteArray): ByteArray {
        val out = ArrayList<Byte>(bytes.size * 7 / 8 + 1)
        var acc = 0L
        var bits = 0
        for (b in bytes) {
            acc = acc or ((b.toLong() and 0x7F) shl bits)
            bits += 7
            if (bits >= 8) {
                out.add((acc and 0xFF).toByte())
                acc = acc ushr 8
                bits -= 8
            }
        }
        return out.toByteArray()
    }

    fun checksum(payload: ByteArray): Int {
        var s = 0
        for (b in payload) s = (s + (b.toInt() and 0xFF)) and 0xFF
        return s.inv() and 0xFF
    }

    fun montarQuadro(cmd: Int, payload: ByteArray): ByteArray {
        val n = payload.size
        val q = ByteArray(7 + n)
        q[0] = 0x00; q[1] = 0x59; q[2] = cmd.toByte()
        q[3] = (n and 0xFF).toByte(); q[4] = ((n shr 8) and 0xFF).toByte(); q[5] = ((n shr 16) and 0xFF).toByte()
        payload.copyInto(q, 6)
        q[6 + n] = checksum(payload).toByte()
        return q
    }

    fun montarSysEx(cmd: Int, payload: ByteArray): ByteArray {
        val p = pack7(montarQuadro(cmd, payload))
        return byteArrayOf(0xF0.toByte()) + p + byteArrayOf(0xF7.toByte())
    }

    class Quadro(val cmd: Int, val payload: ByteArray)

    sealed class Analise {
        data class Ok(val quadro: Quadro) : Analise()
        data class Erro(val motivo: String) : Analise()
    }

    /** Analisa um SysEx completo (com F0 ... F7) recebido do pedal. */
    fun analisarSysEx(sysex: ByteArray): Analise {
        if (sysex.isEmpty() || sysex[0] != 0xF0.toByte()) return Analise.Erro("não é SysEx")
        val fim = if (sysex.last() == 0xF7.toByte()) sysex.size - 1 else sysex.size
        return analisarQuadro(unpack7(sysex.copyOfRange(1, fim)))
    }

    fun analisarQuadro(r: ByteArray): Analise {
        if (r.size < 7 || r[0] != 0x00.toByte() || r[1] != 0x59.toByte()) return Analise.Erro("cabeçalho inválido")
        val cmd = r[2].toInt() and 0xFF
        val len = (r[3].toInt() and 0xFF) or ((r[4].toInt() and 0xFF) shl 8) or ((r[5].toInt() and 0xFF) shl 16)
        if (r.size < 7 + len) return Analise.Erro("quadro curto")
        val payload = r.copyOfRange(6, 6 + len)
        if ((r[6 + len].toInt() and 0xFF) != checksum(payload)) return Analise.Erro("checksum")
        return Analise.Ok(Quadro(cmd, payload))
    }

    fun le32(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte(), ((v ushr 24) and 0xFF).toByte())
    fun le24(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(), ((v shr 16) and 0xFF).toByte())

    fun payloadLerPresets(): ByteArray =
        byteArrayOf(ESPACO_PRESETS.toByte()) + le32(0) + le24(TAM_TOTAL)

    /**
     * Monta o payload de gravação. Recusa qualquer coisa fora do espaço 5,
     * endereços 0x00–0x2F e bytes 0–12 de cada preset (regra de segurança).
     */
    fun payloadGravar(endereco: Int, dados: ByteArray): ByteArray {
        verificarGravacaoPermitida(endereco, dados.size)
        return byteArrayOf(ESPACO_PRESETS.toByte()) + le32(endereco) + le24(dados.size) + dados
    }

    fun verificarGravacaoPermitida(endereco: Int, tamanho: Int) {
        require(tamanho in 1..BYTES_EDITAVEIS) { "tamanho de gravação inválido: $tamanho" }
        val slot = endereco / TAM_PRESET
        val off = endereco % TAM_PRESET
        require(endereco >= 0 && slot in 0 until NUM_PRESETS) { "endereço fora dos presets: $endereco" }
        require(off + tamanho <= BYTES_EDITAVEIS) { "gravação atingiria bytes 13–15 do preset" }
    }

    /** Extrai os 48 bytes de uma resposta ao comando de leitura dos presets. */
    fun extrairPresets(q: Quadro): ByteArray {
        val p = q.payload
        require(q.cmd == CMD_LER) { "resposta inesperada" }
        require(p.size >= 8 + TAM_TOTAL) { "leitura curta" }
        val len = (p[5].toInt() and 0xFF) or ((p[6].toInt() and 0xFF) shl 8) or ((p[7].toInt() and 0xFF) shl 16)
        require(p[0].toInt() == ESPACO_PRESETS && len == TAM_TOTAL) { "leitura não confere" }
        val endereco = (p[1].toInt() and 0xFF) or ((p[2].toInt() and 0xFF) shl 8)
        require(endereco == 0) { "endereço da leitura não confere" }
        return p.copyOfRange(8, 8 + TAM_TOTAL)
    }

    fun extrairNome(q: Quadro): String {
        require(q.cmd == CMD_INFO) { "resposta inesperada" }
        val n = minOf(20, q.payload.size)
        return String(q.payload.copyOfRange(0, n), Charsets.US_ASCII).trim { it <= ' ' || it == '\u0000' }
    }

    fun hex(b: ByteArray, max: Int = 80): String =
        b.take(max).joinToString(" ") { "%02X".format(it.toInt() and 0xFF) } + if (b.size > max) " …" else ""
}
