package io.github.douglasribeiro1.cubebaby.modelo

import kotlinx.serialization.Serializable
import java.util.UUID

/** Campo deslizante de um preset (bytes 0–9). [chaveSite] é o nome usado no site da comunidade. */
enum class Campo(val byte: Int, val rotulo: String, val max: Int, val chaveSite: String) {
    TYPE(0, "Amp (Type)", 8, "type"),
    GAIN(1, "Gain", 7, "gain"),
    TONE(2, "Tone", 15, "tone"),
    IR_CAB(9, "IR Cab", 8, "ir_cab"),
    VOLUME(5, "Volume", 127, "volume"),
    MOD(8, "Mod", 15, "mod"),
    TIME(6, "Time (delay)", 31, "time"),
    FEEDBACK(4, "Feedback (delay)", 127, "fb"),
    MIX(7, "Mix (delay)", 118, "mix"),
    REVERB(3, "Reverb", 15, "reverb");
}

/** Chaves de efeito (bytes 10–12), 0/1. */
val BYTES_CHAVES = listOf(10, 11, 12)

const val TAM_TIMBRE = 16
const val BYTES_EDITAVEIS = 13

/** Faixa máxima de cada byte 0–12 (índice = byte). */
val MAXIMO_POR_BYTE: IntArray = IntArray(BYTES_EDITAVEIS).also { m ->
    Campo.entries.forEach { m[it.byte] = it.max }
    BYTES_CHAVES.forEach { m[it] = 1 }
}

/** Garante 16 bytes; 13–15 sempre 0. Lança erro se algum valor estiver fora da faixa. */
fun validarBytes(bytes: List<Int>): List<Int> {
    require(bytes.size >= BYTES_EDITAVEIS) { "timbre com ${bytes.size} bytes (esperado 16)" }
    for (i in 0 until BYTES_EDITAVEIS) {
        val v = bytes[i]
        require(v in 0..MAXIMO_POR_BYTE[i]) { "byte $i = $v fora da faixa 0–${MAXIMO_POR_BYTE[i]}" }
    }
    return List(TAM_TIMBRE) { if (it < BYTES_EDITAVEIS) bytes[it] else 0 }
}

fun bytesValidos(bytes: List<Int>): Boolean = runCatching { validarBytes(bytes) }.isSuccess

enum class Slot(val letra: String) { A("A"), B("B"), C("C") }

@Serializable
data class Credito(val autor: String? = null, val link: String? = null, val idSite: Long? = null)

/** Timbre guardado na biblioteca. Campos `name`/`bytes` compatíveis com o protótipo web. */
@Serializable
data class Timbre(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val bytes: List<Int>,
    val data: String? = null,
    val credito: Credito? = null,
    val descricao: String? = null,
)

@Serializable
data class Musica(
    val id: String = UUID.randomUUID().toString(),
    val nome: String,
    /** IDs de timbres da biblioteca, na ordem de uso. */
    val timbres: List<String> = emptyList(),
    /** Mapeamento opcional "A"/"B"/"C" -> id do timbre. */
    val mapa: Map<String, String> = emptyMap(),
)

@Serializable
data class BackupPedal(val data: String, val bytes: List<Int>, val motivo: String = "automático")

@Serializable
enum class PoliticaChaves { PERGUNTAR, MANTER_ATUAIS, LIGAR_TODAS }

@Serializable
data class Ajustes(
    val nomesAmps: List<String> = List(9) { "Amp ${it + 1}" },
    val nomesCabs: List<String> = List(9) { "Cab ${it + 1}" },
    val politicaChaves: PoliticaChaves = PoliticaChaves.PERGUNTAR,
    val slotPalco: Slot = Slot.A,
    val nomesChaves: List<String> = listOf("Efeito 1", "Efeito 2", "Efeito 3"),
)

/** Tudo que fica salvo no aparelho. */
@Serializable
data class EstadoSalvo(
    val biblioteca: List<Timbre> = emptyList(),
    val repertorio: List<Musica> = emptyList(),
    val backups: List<BackupPedal> = emptyList(),
    val ajustes: Ajustes = Ajustes(),
)

/** Formato do arquivo de backup (compatível com o protótipo: `bytes` com 48 números e `biblioteca`). */
@Serializable
data class ArquivoBackup(
    val app: String = "cube-baby-presets",
    val versao: Int = 2,
    val data: String? = null,
    val bytes: List<Int>? = null,
    val biblioteca: List<Timbre> = emptyList(),
    val repertorio: List<Musica> = emptyList(),
    val ajustes: Ajustes? = null,
)
