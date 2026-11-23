package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import io.github.douglasribeiro1.cubebaby.AppViewModel
import io.github.douglasribeiro1.cubebaby.modelo.Slot
import io.github.douglasribeiro1.cubebaby.modelo.Timbre

@Composable
fun TelaBiblioteca(vm: AppViewModel) {
    var busca by remember { mutableStateOf("") }
    var salvarDe by remember { mutableStateOf<Slot?>(null) }
    var renomear by remember { mutableStateOf<Timbre?>(null) }
    var apagar by remember { mutableStateOf<Timbre?>(null) }
    var enviar by remember { mutableStateOf<Pair<Timbre, Slot>?>(null) }
    var adicionarMusica by remember { mutableStateOf<Timbre?>(null) }

    val lista = vm.estado.biblioteca.filter { busca.isBlank() || it.name.contains(busca, ignoreCase = true) }
    val conectado = vm.presets != null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Salvar o preset atual do pedal:", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot.entries.forEach { s ->
                    FilledTonalButton(onClick = { salvarDe = s }, enabled = conectado) { Text("Salvar ${s.letra}") }
                }
            }
            OutlinedTextField(busca, { busca = it }, Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Buscar na biblioteca") }, singleLine = true)
        }
        if (lista.isEmpty()) item {
            Text(
                if (vm.estado.biblioteca.isEmpty()) "Nenhum timbre guardado ainda. Salve um preset do pedal ou importe da Comunidade." else "Nada encontrado.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(lista, key = { it.id }) { t ->
            CartaoTimbre(
                vm, t, conectado,
                aoEnviar = { s -> enviar = t to s },
                aoRenomear = { renomear = t },
                aoDuplicar = { vm.duplicarTimbre(t) },
                aoApagar = { apagar = t },
                aoMusica = { adicionarMusica = t },
            )
        }
    }

    salvarDe?.let { s ->
        DialogoTexto("Salvar preset ${s.letra}", aoFechar = { salvarDe = null }) { nome ->
            vm.timbreDoSlot(s)?.let { vm.salvarNaBiblioteca(nome, it) }
        }
    }
    renomear?.let { t ->
        DialogoTexto("Renomear", inicial = t.name, aoFechar = { renomear = null }) { vm.renomearTimbre(t.id, it) }
    }
    apagar?.let { t ->
        DialogoConfirmar("Apagar timbre", "Apagar \"${t.name}\" da biblioteca? Ele também sai das músicas.", "Apagar", { apagar = null }) { vm.apagarTimbre(t.id) }
    }
    enviar?.let { (t, s) ->
        DialogoConfirmar("Gravar no pedal", "Gravar \"${t.name}\" no preset ${s.letra}? O timbre atual do ${s.letra} será substituído.", "Gravar", { enviar = null }) { vm.enviarTimbre(t, s) }
    }
    adicionarMusica?.let { t ->
        DialogoEscolherMusica(vm, t) { adicionarMusica = null }
    }
}

@Composable
private fun CartaoTimbre(
    vm: AppViewModel,
    t: Timbre,
    conectado: Boolean,
    aoEnviar: (Slot) -> Unit,
    aoRenomear: () -> Unit,
    aoDuplicar: () -> Unit,
    aoApagar: () -> Unit,
    aoMusica: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Mais") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Renomear") }, { menu = false; aoRenomear() })
                        DropdownMenuItem({ Text("Duplicar") }, { menu = false; aoDuplicar() })
                        DropdownMenuItem({ Text("Adicionar a uma música") }, { menu = false; aoMusica() })
                        DropdownMenuItem({ Text("Apagar") }, { menu = false; aoApagar() })
                    }
                }
            }
            Text(vm.estado.ajustes.resumo(t.bytes), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            t.credito?.let { c ->
                val autor = c.autor?.let { "por $it" } ?: "da comunidade"
                Text(
                    "Preset ${c.idSite ?: ""} $autor — abrir original",
                    Modifier.padding(top = 4.dp).let { m -> if (c.link != null) m.then(Modifier.clickableSemEfeito { uri.openUri(c.link) }) else m },
                    style = MaterialTheme.typography.bodySmall,
                    color = Acento,
                    textDecoration = if (c.link != null) TextDecoration.Underline else null,
                )
            }
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Slot.entries.forEach { s ->
                    FilledTonalButton(onClick = { aoEnviar(s) }, enabled = conectado && !vm.ocupado) { Text("→ ${s.letra}") }
                }
            }
        }
    }
}

@Composable
private fun DialogoEscolherMusica(vm: AppViewModel, t: Timbre, aoFechar: () -> Unit) {
    var nova by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Adicionar \"${t.name}\" a…") },
        text = {
            Column {
                vm.estado.repertorio.forEach { m ->
                    DropdownMenuItem({ Text(m.nome) }, {
                        if (t.id !in m.timbres) vm.salvarMusica(m.copy(timbres = m.timbres + t.id))
                        aoFechar()
                    })
                }
                DropdownMenuItem({ Text("+ Nova música") }, { nova = true })
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
    if (nova) {
        DialogoTexto("Nova música", aoFechar = { nova = false }) { nome ->
            vm.salvarMusica(io.github.douglasribeiro1.cubebaby.modelo.Musica(nome = nome.ifBlank { "Música" }, timbres = listOf(t.id)))
            aoFechar()
        }
    }
}

fun Modifier.clickableSemEfeito(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
