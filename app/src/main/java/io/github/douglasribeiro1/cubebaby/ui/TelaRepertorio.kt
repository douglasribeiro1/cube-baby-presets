package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.douglasribeiro1.cubebaby.AppViewModel
import io.github.douglasribeiro1.cubebaby.modelo.Musica
import io.github.douglasribeiro1.cubebaby.modelo.Slot

@Composable
fun TelaRepertorio(vm: AppViewModel) {
    var editando by rememberSaveable { mutableStateOf<String?>(null) }
    val musica = vm.estado.repertorio.firstOrNull { it.id == editando }
    if (musica != null) EditorMusica(vm, musica) { editando = null } else ListaMusicas(vm) { editando = it }
}

@Composable
private fun ListaMusicas(vm: AppViewModel, aoEditar: (String) -> Unit) {
    var nova by remember { mutableStateOf(false) }
    var carregar by remember { mutableStateOf<Musica?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Button(onClick = { nova = true }) { Icon(Icons.Filled.Add, null); Text("  Nova música") }
        }
        if (vm.estado.repertorio.isEmpty()) item {
            Text("Nenhuma música ainda. Cada música guarda uma lista de timbres (para o modo palco) e, se quiser, quais vão para A, B e C.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(vm.estado.repertorio, key = { it.id }) { m ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.nome, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { vm.moverMusica(m.id, -1) }) { Icon(Icons.Filled.ArrowUpward, "Subir") }
                        IconButton(onClick = { vm.moverMusica(m.id, 1) }) { Icon(Icons.Filled.ArrowDownward, "Descer") }
                    }
                    val lib = vm.estado.biblioteca.associateBy { it.id }
                    Text("${m.timbres.size} timbre(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val mapa = Slot.entries.joinToString("   ") { s -> "${s.letra}: ${m.mapa[s.letra]?.let { lib[it]?.name } ?: "—"}" }
                    Text(mapa, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { aoEditar(m.id) }) { Text("Editar") }
                        FilledTonalButton(onClick = { carregar = m }, enabled = vm.presets != null && m.mapa.isNotEmpty() && !vm.ocupado) { Text("Carregar música") }
                    }
                }
            }
        }
    }
    if (nova) DialogoTexto("Nova música", aoFechar = { nova = false }) { nome ->
        val m = Musica(nome = nome.ifBlank { "Música" })
        vm.salvarMusica(m)
        aoEditar(m.id)
    }
    carregar?.let { m ->
        val slots = Slot.entries.filter { m.mapa.containsKey(it.letra) }.joinToString(", ") { it.letra }
        DialogoConfirmar("Carregar \"${m.nome}\"", "Gravar os timbres da música nos presets $slots do pedal?", "Gravar", { carregar = null }) { vm.carregarMusica(m) }
    }
}

@Composable
private fun EditorMusica(vm: AppViewModel, m: Musica, aoVoltar: () -> Unit) {
    var renomear by remember { mutableStateOf(false) }
    var adicionar by remember { mutableStateOf(false) }
    var apagar by remember { mutableStateOf(false) }
    val lib = vm.estado.biblioteca.associateBy { it.id }
    fun salvar(n: Musica) = vm.salvarMusica(n)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = aoVoltar) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                Text(m.nome, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { renomear = true }) { Text("Renomear") }
            }
            Text("Timbres (ordem do modo palco)", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
        }
        itemsIndexed(m.timbres, key = { i, id -> "$i-$id" }) { i, id ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${i + 1}. ${lib[id]?.name ?: "(apagado)"}", Modifier.weight(1f))
                IconButton(onClick = { if (i > 0) salvar(m.copy(timbres = m.timbres.toMutableList().apply { add(i - 1, removeAt(i)) })) }) { Icon(Icons.Filled.ArrowUpward, "Subir") }
                IconButton(onClick = { if (i < m.timbres.size - 1) salvar(m.copy(timbres = m.timbres.toMutableList().apply { add(i + 1, removeAt(i)) })) }) { Icon(Icons.Filled.ArrowDownward, "Descer") }
                IconButton(onClick = { salvar(m.copy(timbres = m.timbres.toMutableList().apply { removeAt(i) })) }) { Icon(Icons.Filled.Close, "Remover") }
            }
        }
        item {
            OutlinedButton(onClick = { adicionar = true }, enabled = vm.estado.biblioteca.isNotEmpty()) { Icon(Icons.Filled.Add, null); Text("  Adicionar timbre da biblioteca") }
            Text("Mapeamento para o pedal (opcional)", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.titleSmall)
            Text("Usado pelo botão \"Carregar música\", que grava os três de uma vez.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(Slot.entries) { s ->
            var menu by remember { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Preset ${s.letra}", Modifier.width(90.dp))
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
                        Text(m.mapa[s.letra]?.let { lib[it]?.name } ?: "— não mudar —", Modifier.weight(1f), maxLines = 1)
                        Icon(Icons.Filled.ArrowDropDown, null)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("— não mudar —") }, { salvar(m.copy(mapa = m.mapa - s.letra)); menu = false })
                        // primeiro os timbres da música, depois o resto da biblioteca
                        val ordem = m.timbres.mapNotNull { lib[it] } + vm.estado.biblioteca.filterNot { it.id in m.timbres }
                        ordem.forEach { t ->
                            DropdownMenuItem({ Text(t.name) }, { salvar(m.copy(mapa = m.mapa + (s.letra to t.id))); menu = false })
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.carregarMusica(m) }, enabled = vm.presets != null && m.mapa.isNotEmpty() && !vm.ocupado) { Text("Carregar música") }
                TextButton(onClick = { apagar = true }) { Text("Apagar música", color = Perigo) }
            }
        }
    }

    if (renomear) DialogoTexto("Renomear música", inicial = m.nome, aoFechar = { renomear = false }) { salvar(m.copy(nome = it.ifBlank { m.nome })) }
    if (apagar) DialogoConfirmar("Apagar música", "Apagar \"${m.nome}\"? Os timbres continuam na biblioteca.", "Apagar", { apagar = false }) { vm.apagarMusica(m.id); aoVoltar() }
    if (adicionar) {
        AlertDialog(
            onDismissRequest = { adicionar = false },
            title = { Text("Adicionar timbre") },
            text = {
                LazyColumn {
                    items(vm.estado.biblioteca, key = { it.id }) { t ->
                        DropdownMenuItem({ Text(t.name) }, { salvar(m.copy(timbres = m.timbres + t.id)); adicionar = false })
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { adicionar = false }) { Text("Fechar") } },
        )
    }
}
