package io.github.douglasribeiro1.cubebaby.dados

import android.content.Context
import io.github.douglasribeiro1.cubebaby.modelo.ArquivoBackup
import io.github.douglasribeiro1.cubebaby.modelo.EstadoSalvo
import io.github.douglasribeiro1.cubebaby.modelo.Timbre
import io.github.douglasribeiro1.cubebaby.modelo.normalizado
import io.github.douglasribeiro1.cubebaby.modelo.validarBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

val JsonApp = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
    coerceInputValues = true
}

/** Guarda biblioteca, repertório, backups e ajustes num arquivo JSON local (sem nuvem). */
class Armazenamento(context: Context) {
    private val arquivo = File(context.filesDir, "estado.json")

    suspend fun carregar(): EstadoSalvo = withContext(Dispatchers.IO) {
        if (!arquivo.exists()) return@withContext EstadoSalvo()
        runCatching { JsonApp.decodeFromString(EstadoSalvo.serializer(), arquivo.readText()).let { it.copy(ajustes = it.ajustes.normalizado()) } }
            .getOrElse {
                // Arquivo corrompido: guarda uma cópia para não perder nada e começa do zero.
                arquivo.copyTo(File(arquivo.parentFile, "estado-corrompido-${System.currentTimeMillis()}.json"), overwrite = true)
                EstadoSalvo()
            }
    }

    suspend fun salvar(estado: EstadoSalvo) = withContext(Dispatchers.IO) {
        val tmp = File(arquivo.parentFile, "estado.json.tmp")
        tmp.writeText(JsonApp.encodeToString(EstadoSalvo.serializer(), estado))
        if (!tmp.renameTo(arquivo)) { arquivo.delete(); tmp.renameTo(arquivo) }
    }
}

object FormatoBackup {
    fun exportar(b: ArquivoBackup): String = JsonApp.encodeToString(ArquivoBackup.serializer(), b)

    /** Lê o arquivo do app ou do protótipo web. Descarta timbres inválidos. */
    fun importar(texto: String): Pair<ArquivoBackup, Int> {
        val b = JsonApp.decodeFromString(ArquivoBackup.serializer(), texto)
        b.bytes?.let { require(it.size == 48) { "o arquivo deveria ter 48 bytes dos presets (tem ${it.size})" } }
        val validos = mutableListOf<Timbre>()
        var descartados = 0
        for (t in b.biblioteca) {
            val ok = runCatching { t.copy(bytes = validarBytes(t.bytes)) }.getOrNull()
            if (ok != null) validos += ok else descartados++
        }
        return b.copy(biblioteca = validos) to descartados
    }
}
