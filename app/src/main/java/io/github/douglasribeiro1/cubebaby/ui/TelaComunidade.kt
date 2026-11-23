package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.douglasribeiro1.cubebaby.AppViewModel
import io.github.douglasribeiro1.cubebaby.PreviaComunidade
import io.github.douglasribeiro1.cubebaby.comunidade.ComunidadeRepository
import io.github.douglasribeiro1.cubebaby.modelo.PoliticaChaves
import io.github.douglasribeiro1.cubebaby.modelo.Slot
import kotlinx.coroutines.launch

@Composable
fun TelaComunidade(vm: AppViewModel) {
    var link by rememberSaveable { mutableStateOf("") }
    var busca by rememberSaveable { mutableStateOf("") }
    val resultados = remember { mutableStateListOf<ComunidadeRepository.PresetSite>() }
    var pagina by remember { mutableIntStateOf(0) }
    var total by remember { mutableStateOf<Int?>(null) }
    var buscando by remember { mutableStateOf(false) }
    var erroBusca by remember { mutableStateOf<String?>(null) }
    val escopo = rememberCoroutineScope()
    @Suppress("DEPRECATION") val clip = LocalClipboardManager.current

    fun carregar(nova: Boolean) {
        escopo.launch {
            buscando = true; erroBusca = null
            try {
                val p = if (nova) 0 else pagina + 1
                val r = vm.comunidade.listar(busca, p)
                if (nova) resultados.clear()
                resultados.addAll(r.presets)
                pagina = p; total = r.total
            } catch (e: Exception) {
                erroBusca = e.message ?: "Falha na busca"
            } finally {
                buscando = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text(
                "No navegador, abra um preset em cube-baby-presets.vercel.app e toque em Compartilhar → Cube Baby Presets. Ou cole o link aqui.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(link, { link = it }, Modifier.weight(1f), label = { Text("Link ou número do preset") }, singleLine = true)
                IconButton(onClick = { clip.getText()?.text?.let { link = it } }) { Icon(Icons.Filled.ContentPaste, "Colar") }
            }
            Button(onClick = { vm.abrirDaComunidade(link) }, Modifier.padding(top = 6.dp), enabled = link.isNotBlank() && !vm.carregandoPrevia) { Text("Abrir preset") }
        }
        item {
            when {
                vm.carregandoPrevia -> CircularProgressIndicator()
                vm.erroPrevia != null -> Text(vm.erroPrevia!!, color = Perigo)
                vm.previa != null -> CartaoPrevia(vm, vm.previa!!)
            }
        }
        item {
            Text("Buscar no site", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(busca, { busca = it }, Modifier.weight(1f), label = { Text("Nome ou descrição (vazio = recentes)") }, singleLine = true)
                IconButton(onClick = { carregar(true) }, enabled = !buscando) { Icon(Icons.Filled.Search, "Buscar") }
            }
            erroBusca?.let { Text(it, color = Perigo) }
            total?.let { Text("$it presets do Cube Baby encontrados", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(resultados, key = { it.id }) { p ->
            Card(Modifier.fillMaxWidth().clickable { vm.mostrarPrevia(p) }) {
                Column(Modifier.padding(12.dp)) {
                    Text(p.name.ifBlank { "Preset ${p.id}" }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        "#${p.id}" + (p.autor?.let { " · por $it" } ?: "") + " · 👍 ${p.vote_up_count}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (buscando) item { CircularProgressIndicator() }
        else if (total != null && resultados.size < (total ?: 0)) item {
            OutlinedButton(onClick = { carregar(false) }) { Text("Carregar mais") }
        }
    }
}

@Composable
private fun CartaoPrevia(vm: AppViewModel, p: PreviaComunidade) {
    var slot by rememberSaveable { mutableStateOf(Slot.A) }
    var perguntar by remember { mutableStateOf<((PoliticaChaves) -> Unit)?>(null) }
    val uri = LocalUriHandler.current
    val ajustes = vm.estado.ajustes

    /** Executa [acao] com a política de chaves; pergunta na primeira vez. */
    fun comPolitica(acao: (PoliticaChaves) -> Unit) {
        if (ajustes.politicaChaves == PoliticaChaves.PERGUNTAR) perguntar = acao else acao(ajustes.politicaChaves)
    }

    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.preset.name.ifBlank { "Preset ${p.preset.id}" }, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { vm.fecharPrevia() }) { Text("Fechar") }
            }
            Text(
                "#${p.preset.id}" + (p.preset.autor?.let { " · por $it" } ?: "") + " — abrir no site",
                Modifier.clickable { uri.openUri(p.preset.link) },
                color = Acento, textDecoration = TextDecoration.Underline, style = MaterialTheme.typography.bodySmall,
            )
            p.preset.description?.takeIf { it.isNotBlank() }?.let { Text(it.trim(), style = MaterialTheme.typography.bodyMedium) }
            if (p.preset.custom_ir != null) Text("Atenção: o autor usou uma IR personalizada, que o app não carrega. O som pode ficar diferente.", color = Aviso, style = MaterialTheme.typography.bodySmall)
            TabelaValores(ajustes, List(16) { if (it < 10) p.bytes10[it] else 0 }, Modifier.padding(vertical = 6.dp))
            Text("As chaves de efeito não vêm do site: ${textoPolitica(ajustes.politicaChaves)}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Slot:")
                SeletorSlot(slot, { slot = it }, Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Button(onClick = { comPolitica { vm.testarPrevia(p, slot, it) } }, enabled = vm.presets != null && !vm.ocupado) { Text("Testar no ${slot.letra}") }
                FilledTonalButton(onClick = { comPolitica { vm.salvarPrevia(p, it, slot) } }) { Text("Salvar na biblioteca") }
            }
            vm.desfazer?.let { d ->
                OutlinedButton(onClick = { vm.desfazerUltimo() }, enabled = !vm.ocupado) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null); Text("  Desfazer: voltar o ${d.slot.letra} ao timbre anterior")
                }
            }
            if (vm.presets == null) Text("Conecte o pedal para testar.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    perguntar?.let { acao ->
        DialogoChaves(aoFechar = { perguntar = null }) { pol, lembrar ->
            if (lembrar) vm.alterarAjustes { it.copy(politicaChaves = pol) }
            acao(pol)
        }
    }
}

fun textoPolitica(p: PoliticaChaves) = when (p) {
    PoliticaChaves.PERGUNTAR -> "vou perguntar o que fazer"
    PoliticaChaves.MANTER_ATUAIS -> "ficam como estão no slot escolhido"
    PoliticaChaves.LIGAR_TODAS -> "as três serão ligadas"
}
