package io.github.douglasribeiro1.cubebaby

import android.app.Application
import android.media.midi.MidiManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.douglasribeiro1.cubebaby.comunidade.ComunidadeRepository
import io.github.douglasribeiro1.cubebaby.dados.Armazenamento
import io.github.douglasribeiro1.cubebaby.dados.FormatoBackup
import io.github.douglasribeiro1.cubebaby.midi.CubeBabyMidi
import io.github.douglasribeiro1.cubebaby.midi.Protocolo
import io.github.douglasribeiro1.cubebaby.modelo.Ajustes
import io.github.douglasribeiro1.cubebaby.modelo.ArquivoBackup
import io.github.douglasribeiro1.cubebaby.modelo.BYTES_CHAVES
import io.github.douglasribeiro1.cubebaby.modelo.BYTES_EDITAVEIS
import io.github.douglasribeiro1.cubebaby.modelo.BackupPedal
import io.github.douglasribeiro1.cubebaby.modelo.Credito
import io.github.douglasribeiro1.cubebaby.modelo.EstadoSalvo
import io.github.douglasribeiro1.cubebaby.modelo.Musica
import io.github.douglasribeiro1.cubebaby.modelo.PoliticaChaves
import io.github.douglasribeiro1.cubebaby.modelo.Slot
import io.github.douglasribeiro1.cubebaby.modelo.TAM_TIMBRE
import io.github.douglasribeiro1.cubebaby.modelo.Timbre
import io.github.douglasribeiro1.cubebaby.modelo.normalizado
import io.github.douglasribeiro1.cubebaby.modelo.validarBytes
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID

sealed class Conexao {
    data object SemMidi : Conexao()
    data object Desconectado : Conexao()
    data object Conectando : Conexao()
    data class Conectado(val nome: String) : Conexao()
    data class Erro(val msg: String) : Conexao()
}

/** Prévia de um preset do site da comunidade. [bytes10] são os bytes 0–9. */
data class PreviaComunidade(val preset: ComunidadeRepository.PresetSite, val bytes10: IntArray)

/** Guarda o timbre que estava no slot antes de um "testar", para desfazer. */
data class Desfazer(val slot: Slot, val bytes: List<Int>, val rotulo: String)

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val midi = CubeBabyMidi(app, ::registrar)
    private val armazenamento = Armazenamento(app)
    val comunidade = ComunidadeRepository()

    var conexao by mutableStateOf<Conexao>(if (midi.midiDisponivel) Conexao.Desconectado else Conexao.SemMidi)
        private set
    /** Os 48 bytes lidos do pedal (null = ainda não lidos). */
    var presets by mutableStateOf<List<Int>?>(null)
        private set
    var ocupado by mutableStateOf(false)
        private set
    var estado by mutableStateOf(EstadoSalvo())
        private set
    var carregado by mutableStateOf(false)
        private set
    var desfazer by mutableStateOf<Desfazer?>(null)
        private set
    var ultimoPalco by mutableStateOf<String?>(null)
        private set

    val registro = mutableStateListOf<String>()
    private val _mensagens = Channel<String>(Channel.BUFFERED)
    val mensagens = _mensagens.receiveAsFlow()

    /** Serializa operações compostas (gravar + reler) além da fila de comandos do MIDI. */
    private val operacao = Mutex()
    private var backupFeitoNestaConexao = false
    private var callback: MidiManager.DeviceCallback? = null
    private var jobConexao: Job? = null

    init {
        viewModelScope.launch {
            estado = armazenamento.carregar()
            carregado = true
        }
        callback = midi.registrarCallback { aoMudarDispositivos() }
        conectar()
    }

    override fun onCleared() {
        midi.removerCallback(callback)
        midi.fechar()
    }

    private fun registrar(m: String) {
        val hora = SimpleDateFormat("HH:mm:ss", Locale.ROOT).format(Date())
        viewModelScope.launch {
            registro.add("[$hora] $m")
            if (registro.size > 300) registro.removeRange(0, registro.size - 300)
        }
    }

    private fun avisar(m: String) { _mensagens.trySend(m) }

    // ===================== conexão =====================

    private fun aoMudarDispositivos() {
        if (!midi.conectado) {
            presets = null
            if (conexao is Conexao.Conectado) conexao = Conexao.Desconectado
            conectar()
        }
    }

    fun conectar() {
        if (!midi.midiDisponivel) { conexao = Conexao.SemMidi; return }
        if (jobConexao?.isActive == true) return
        jobConexao = viewModelScope.launch {
            val candidatos = midi.dispositivos()
            if (candidatos.isEmpty()) {
                conexao = Conexao.Desconectado
                return@launch
            }
            conexao = Conexao.Conectando
            var ultimoErro: String? = null
            for (info in candidatos) {
                try {
                    val nome = operacao.withLock { midi.conectar(info) }
                    backupFeitoNestaConexao = false
                    desfazer = null
                    conexao = Conexao.Conectado(nome)
                    relerPresets()
                    return@launch
                } catch (e: Exception) {
                    ultimoErro = e.message
                    registrar("Falha ao conectar: ${e.message}")
                }
            }
            conexao = Conexao.Erro(ultimoErro ?: "Pedal não respondeu")
        }
    }

    fun relerPresets() {
        viewModelScope.launch {
            try {
                operacao.withLock { presets = midi.lerPresets().map { it.toInt() and 0xFF } }
            } catch (e: Exception) {
                erro(e)
            }
        }
    }

    private fun erro(e: Exception) {
        registrar("Erro: ${e.message}")
        avisar("Erro: ${e.message}")
        if (!midi.conectado) {
            presets = null
            conexao = Conexao.Desconectado
        }
    }

    // ===================== gravação segura =====================

    fun timbreDoSlot(slot: Slot): List<Int>? =
        presets?.subList(slot.ordinal * TAM_TIMBRE, slot.ordinal * TAM_TIMBRE + TAM_TIMBRE)

    /**
     * Único caminho de gravação do app:
     * valida faixas -> backup automático (1ª alteração da conexão) -> grava só os bytes 0–12 que
     * mudaram (em blocos contíguos) -> confere ACK -> relê os 48 bytes e confirma.
     */
    private suspend fun gravarSlot(slot: Slot, novo: List<Int>): Boolean {
        val alvo = validarBytes(novo)
        val atual = presets ?: throw IllegalStateException("Presets ainda não foram lidos do pedal")
        val base = slot.ordinal * TAM_TIMBRE
        val mudou = (0 until BYTES_EDITAVEIS).filter { atual[base + it] != alvo[it] }
        if (mudou.isEmpty()) return false

        garantirBackup(atual)

        // agrupa bytes alterados contíguos
        var i = 0
        while (i < mudou.size) {
            var j = i
            while (j + 1 < mudou.size && mudou[j + 1] == mudou[j] + 1) j++
            val ini = mudou[i]
            val dados = ByteArray(mudou[j] - ini + 1) { alvo[ini + it].toByte() }
            midi.gravar(base + ini, dados)
            i = j + 1
        }
        val relido = midi.lerPresets().map { it.toInt() and 0xFF }
        presets = relido
        val divergentes = (0 until BYTES_EDITAVEIS).filter { relido[base + it] != alvo[it] }
        if (divergentes.isNotEmpty()) {
            throw IllegalStateException("Gravou, mas a releitura não confere (bytes ${divergentes.joinToString()})")
        }
        registrar("Preset ${slot.letra}: bytes ${mudou.joinToString()} gravados e conferidos")
        return true
    }

    private suspend fun garantirBackup(atual: List<Int>) {
        if (backupFeitoNestaConexao) return
        val b = BackupPedal(Instant.now().toString(), atual.toList(), "automático (antes da 1ª alteração)")
        atualizarEstado { it.copy(backups = (listOf(b) + it.backups).take(30)) }
        backupFeitoNestaConexao = true
        registrar("Backup automático dos 48 bytes salvo")
    }

    private fun executarGravacao(sucesso: String? = null, bloco: suspend () -> Unit) {
        viewModelScope.launch {
            if (presets == null) { avisar("Conecte o pedal primeiro"); return@launch }
            ocupado = true
            try {
                operacao.withLock { bloco() }
                if (sucesso != null) avisar(sucesso)
            } catch (e: Exception) {
                erro(e)
                runCatching { operacao.withLock { presets = midi.lerPresets().map { it.toInt() and 0xFF } } }
            } finally {
                ocupado = false
            }
        }
    }

    fun alterarByte(slot: Slot, byte: Int, valor: Int) {
        val atual = timbreDoSlot(slot) ?: return
        val novo = atual.toMutableList().also { it[byte] = valor }
        executarGravacao { gravarSlot(slot, novo) }
    }

    fun enviarTimbre(t: Timbre, slot: Slot, guardarDesfazer: Boolean = false) {
        executarGravacao("\"${t.name}\" gravado no ${slot.letra}") {
            val anterior = timbreDoSlot(slot)
            if (gravarSlot(slot, t.bytes) && guardarDesfazer && anterior != null)
                desfazer = Desfazer(slot, anterior, t.name)
        }
    }

    fun desfazerUltimo() {
        val d = desfazer ?: return
        executarGravacao("Preset ${d.slot.letra} voltou ao timbre anterior") {
            gravarSlot(d.slot, d.bytes)
            desfazer = null
        }
    }

    fun tocarPalco(t: Timbre, slot: Slot) {
        ultimoPalco = t.id
        enviarTimbre(t, slot)
    }

    fun carregarMusica(m: Musica) {
        val lib = estado.biblioteca.associateBy { it.id }
        val itens = Slot.entries.mapNotNull { s -> m.mapa[s.letra]?.let { lib[it] }?.let { s to it } }
        if (itens.isEmpty()) { avisar("A música não tem timbres mapeados para A/B/C"); return }
        executarGravacao("\"${m.nome}\" carregada no pedal") {
            for ((s, t) in itens) gravarSlot(s, t.bytes)
        }
    }

    fun restaurar48(bytes: List<Int>, rotulo: String) {
        require(bytes.size == 48)
        val slots = Slot.entries.map { s -> s to bytes.subList(s.ordinal * 16, s.ordinal * 16 + 16) }
        // valida tudo antes de gravar qualquer coisa
        val invalido = slots.firstOrNull { runCatching { validarBytes(it.second) }.isFailure }
        if (invalido != null) { avisar("O preset ${invalido.first.letra} de $rotulo tem valores fora da faixa"); return }
        executarGravacao("Presets restaurados de $rotulo") {
            for ((s, b) in slots) gravarSlot(s, b)
        }
    }

    // ===================== biblioteca =====================

    private fun atualizarEstado(f: (EstadoSalvo) -> EstadoSalvo) {
        val novo = f(estado)
        estado = novo
        viewModelScope.launch { armazenamento.salvar(novo) }
    }

    fun salvarNaBiblioteca(nome: String, bytes: List<Int>, credito: Credito? = null, descricao: String? = null): Timbre? {
        val b = runCatching { validarBytes(bytes) }.getOrElse { avisar("Timbre inválido: ${it.message}"); return null }
        val t = Timbre(name = nome.trim().ifBlank { "Sem nome" }, bytes = b, data = Instant.now().toString(), credito = credito, descricao = descricao)
        atualizarEstado { it.copy(biblioteca = it.biblioteca + t) }
        avisar("\"${t.name}\" salvo na biblioteca")
        return t
    }

    fun renomearTimbre(id: String, nome: String) = atualizarEstado { e ->
        e.copy(biblioteca = e.biblioteca.map { if (it.id == id) it.copy(name = nome.trim().ifBlank { it.name }) else it })
    }

    fun duplicarTimbre(t: Timbre) = atualizarEstado { e ->
        val idx = e.biblioteca.indexOfFirst { it.id == t.id }
        val copia = t.copy(id = UUID.randomUUID().toString(), name = "${t.name} (cópia)", data = Instant.now().toString())
        e.copy(biblioteca = e.biblioteca.toMutableList().apply { add(if (idx < 0) size else idx + 1, copia) })
    }

    fun apagarTimbre(id: String) = atualizarEstado { e ->
        e.copy(
            biblioteca = e.biblioteca.filterNot { it.id == id },
            repertorio = e.repertorio.map { m -> m.copy(timbres = m.timbres - id, mapa = m.mapa.filterValues { it != id }) },
        )
    }

    // ===================== repertório =====================

    fun salvarMusica(m: Musica) = atualizarEstado { e ->
        val existe = e.repertorio.any { it.id == m.id }
        e.copy(repertorio = if (existe) e.repertorio.map { if (it.id == m.id) m else it } else e.repertorio + m)
    }

    fun apagarMusica(id: String) = atualizarEstado { e -> e.copy(repertorio = e.repertorio.filterNot { it.id == id }) }

    fun moverMusica(id: String, delta: Int) = atualizarEstado { e ->
        val l = e.repertorio.toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in l.indices) e else e.copy(repertorio = l.apply { add(j, removeAt(i)) })
    }

    // ===================== ajustes =====================

    fun alterarAjustes(f: (Ajustes) -> Ajustes) = atualizarEstado { it.copy(ajustes = f(it.ajustes)) }

    // ===================== backup em arquivo =====================

    fun gerarExportacao(): String = FormatoBackup.exportar(
        ArquivoBackup(
            data = Instant.now().toString(),
            bytes = presets,
            biblioteca = estado.biblioteca,
            repertorio = estado.repertorio,
            ajustes = estado.ajustes,
        )
    )

    data class Importacao(val arquivo: ArquivoBackup, val descartados: Int)

    fun lerImportacao(texto: String): Importacao? = runCatching {
        val (a, d) = FormatoBackup.importar(texto)
        Importacao(a, d)
    }.getOrElse { avisar("Arquivo inválido: ${it.message}"); null }

    /** Junta biblioteca e repertório do arquivo com os atuais (sem duplicar ids). */
    fun aplicarImportacao(imp: Importacao, juntarDados: Boolean, ajustes: Boolean, gravarPedal: Boolean) {
        if (juntarDados || ajustes) {
            atualizarEstado { e ->
                var n = e
                if (juntarDados) {
                    val ids = e.biblioteca.map { it.id }.toSet()
                    val mids = e.repertorio.map { it.id }.toSet()
                    n = n.copy(
                        biblioteca = e.biblioteca + imp.arquivo.biblioteca.filterNot { it.id in ids },
                        repertorio = e.repertorio + imp.arquivo.repertorio.filterNot { it.id in mids },
                    )
                }
                if (ajustes && imp.arquivo.ajustes != null) n = n.copy(ajustes = imp.arquivo.ajustes.normalizado())
                n
            }
            avisar("Dados importados" + if (imp.descartados > 0) " (${imp.descartados} timbres inválidos ignorados)" else "")
        }
        if (gravarPedal) imp.arquivo.bytes?.let { restaurar48(it, "o arquivo") }
    }

    // ===================== comunidade =====================

    var previa by mutableStateOf<PreviaComunidade?>(null)
        private set
    var carregandoPrevia by mutableStateOf(false)
        private set
    var erroPrevia by mutableStateOf<String?>(null)
        private set

    fun abrirDaComunidade(texto: String) {
        val id = ComunidadeRepository.extrairId(texto)
        if (id == null) { erroPrevia = "Não achei o número do preset nesse link"; return }
        viewModelScope.launch {
            carregandoPrevia = true
            erroPrevia = null
            try {
                val p = comunidade.buscarPorId(id)
                previa = PreviaComunidade(p, ComunidadeRepository.paraBytes(p))
            } catch (e: Exception) {
                previa = null
                erroPrevia = e.message ?: "Falha ao carregar"
            } finally {
                carregandoPrevia = false
            }
        }
    }

    fun mostrarPrevia(p: ComunidadeRepository.PresetSite) {
        erroPrevia = null
        previa = runCatching { PreviaComunidade(p, ComunidadeRepository.paraBytes(p)) }
            .getOrElse { erroPrevia = it.message; null }
    }

    fun fecharPrevia() { previa = null; erroPrevia = null }

    /** Monta os 16 bytes de um preset do site, decidindo as chaves 10–12 conforme a política. */
    fun bytesDaPrevia(p: PreviaComunidade, politica: PoliticaChaves, slotReferencia: Slot): List<Int> {
        val chaves = when (politica) {
            PoliticaChaves.LIGAR_TODAS, PoliticaChaves.PERGUNTAR -> listOf(1, 1, 1)
            PoliticaChaves.MANTER_ATUAIS -> timbreDoSlot(slotReferencia)?.let { t -> BYTES_CHAVES.map { t[it] } } ?: listOf(1, 1, 1)
        }
        return List(TAM_TIMBRE) { i ->
            when {
                i < 10 -> p.bytes10[i]
                i < 13 -> chaves[i - 10]
                else -> 0
            }
        }
    }

    fun testarPrevia(p: PreviaComunidade, slot: Slot, politica: PoliticaChaves) {
        val t = Timbre(name = p.preset.name, bytes = bytesDaPrevia(p, politica, slot))
        enviarTimbre(t, slot, guardarDesfazer = true)
    }

    fun salvarPrevia(p: PreviaComunidade, politica: PoliticaChaves, slotReferencia: Slot) {
        salvarNaBiblioteca(
            p.preset.name.ifBlank { "Preset ${p.preset.id}" },
            bytesDaPrevia(p, politica, slotReferencia),
            Credito(autor = p.preset.autor, link = p.preset.link, idSite = p.preset.id),
            p.preset.description?.trim()?.ifBlank { null },
        )
    }

    fun hexPresets(): String = presets?.let { Protocolo.hex(ByteArray(it.size) { i -> it[i].toByte() }, 48) } ?: "—"
}
