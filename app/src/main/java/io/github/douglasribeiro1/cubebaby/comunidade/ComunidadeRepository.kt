package io.github.douglasribeiro1.cubebaby.comunidade

import io.github.douglasribeiro1.cubebaby.modelo.Campo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Leitura (somente leitura, sob demanda) dos presets do site https://cube-baby-presets.vercel.app.
 *
 * Como o site carrega os dados (inspecionado em 23/11/2026): é um Next.js que, no navegador,
 * consulta um Supabase público com a chave "anon" embutida no JavaScript do próprio site:
 *   GET {SUPABASE}/rest/v1/presets?select=...&id=eq.{id}
 *   colunas: id, name, description, knobs_values (json com volume, ir_cab, reverb, mix, fb,
 *            time, mod, tone, gain, type), created_at, user:user_id(username), model_id
 *            ("cube-baby" | "cube-baby-bass" | "cube-baby-ac"), custom_ir, vote_up_count, vote_down_count.
 * O HTML renderizado não traz os valores (são carregados no cliente), por isso usamos a mesma API.
 * Se o site mudar, só este arquivo precisa ser ajustado.
 */
class ComunidadeRepository {

    @Serializable
    data class Autor(val username: String? = null)

    @Serializable
    data class PresetSite(
        val id: Long,
        val name: String = "",
        val description: String? = null,
        val knobs_values: Map<String, JsonElement> = emptyMap(),
        val created_at: String? = null,
        val model_id: String? = null,
        val user: Autor? = null,
        val vote_up_count: Int = 0,
        val vote_down_count: Int = 0,
        val custom_ir: JsonObject? = null,
    ) {
        val autor: String? get() = user?.username?.trim()?.ifBlank { null }
        val link: String get() = "$SITE/presets/$id"
    }

    data class Pagina(val presets: List<PresetSite>, val total: Int?)

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    suspend fun buscarPorId(id: Long): PresetSite {
        val lista = get("presets?select=$COLUNAS&id=eq.$id")
        return json.decodeFromString<List<PresetSite>>(lista.first).firstOrNull()
            ?: throw IOException("Preset $id não encontrado no site")
    }

    /** Lista/busca só presets do Cube Baby de guitarra, mais recentes primeiro. */
    suspend fun listar(busca: String, pagina: Int, porPagina: Int = 30): Pagina {
        val sb = StringBuilder("presets?select=$COLUNAS&model_id=eq.$MODELO_GUITARRA&order=created_at.desc,id.desc")
        val termo = busca.trim().replace(Regex("[%_\",()*]"), "")
        if (termo.isNotEmpty()) {
            val t = URLEncoder.encode("\"%$termo%\"", "UTF-8").replace("+", "%20")
            sb.append("&or=(name.ilike.$t,description.ilike.$t)")
        }
        val ini = pagina * porPagina
        val (corpo, total) = get(sb.toString(), range = "$ini-${ini + porPagina - 1}")
        return Pagina(json.decodeFromString(corpo), total)
    }

    private suspend fun get(caminho: String, range: String? = null): Pair<String, Int?> = withContext(Dispatchers.IO) {
        val c = URL("$SUPABASE/rest/v1/$caminho").openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("apikey", CHAVE_PUBLICA)
            c.setRequestProperty("Authorization", "Bearer $CHAVE_PUBLICA")
            c.setRequestProperty("Accept", "application/json")
            if (range != null) {
                c.setRequestProperty("Range", range)
                c.setRequestProperty("Prefer", "count=exact")
            }
            val code = c.responseCode
            if (code !in 200..299) throw IOException("O site respondeu erro $code")
            val total = c.getHeaderField("Content-Range")?.substringAfter('/')?.toIntOrNull()
            c.inputStream.bufferedReader().use { it.readText() } to total
        } finally {
            c.disconnect()
        }
    }

    companion object {
        const val SITE = "https://cube-baby-presets.vercel.app"
        const val MODELO_GUITARRA = "cube-baby"
        private const val SUPABASE = "https://xtmilygwjtudiiyvvsla.supabase.co"
        // Chave "anon" pública, a mesma que o site entrega a qualquer navegador (só leitura).
        private const val CHAVE_PUBLICA =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Inh0bWlseWd3anR1ZGlpeXZ2c2xhIiwicm9sZSI6ImFub24iLCJpYXQiOjE2NjA5NTE4OTQsImV4cCI6MTk3NjUyNzg5NH0.I3py_T8mCJWLTnncBvyWBm4yy62aaPoMAI7tTaPJefQ"
        private const val COLUNAS =
            "id,name,description,knobs_values,created_at,model_id,custom_ir,vote_up_count,vote_down_count,user:user_id(username)"

        private val REGEX_ID = Regex("""/presets/(\d+)""")

        /** Extrai o id de um link (ou texto compartilhado que contenha o link), ou de um número puro. */
        fun extrairId(texto: String): Long? {
            val t = texto.trim()
            REGEX_ID.find(t)?.let { return it.groupValues[1].toLongOrNull() }
            return t.toLongOrNull()
        }

        /**
         * Converte os valores do site para os bytes 0–9. Recusa outros modelos e valores fora da faixa.
         * Bytes 10–12 (chaves de efeito) ficam por conta de quem chama.
         */
        fun paraBytes(p: PresetSite): IntArray {
            if (p.model_id != null && p.model_id != MODELO_GUITARRA)
                throw IllegalArgumentException("Este preset é para outro modelo (${p.model_id}); o app só aceita o Cube Baby de guitarra.")
            val out = IntArray(10)
            for (c in Campo.entries) {
                val v = p.knobs_values[c.chaveSite]?.jsonPrimitive?.intOrNull
                    ?: throw IllegalArgumentException("O preset não tem o campo \"${c.chaveSite}\"")
                if (v !in 0..c.max) throw IllegalArgumentException("${c.rotulo} = $v está fora da faixa 0–${c.max}")
                out[c.byte] = v
            }
            return out
        }
    }
}
