package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.douglasribeiro1.cubebaby.AppViewModel

@Composable
fun TelaPalco(vm: AppViewModel) {
    // Tela sempre acesa enquanto o modo palco estiver aberto.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val repertorio = vm.estado.repertorio
    var musicaId by rememberSaveable { mutableStateOf(repertorio.firstOrNull()?.id) }
    var menu by remember { mutableStateOf(false) }
    val musica = repertorio.firstOrNull { it.id == musicaId } ?: repertorio.firstOrNull()
    val slot = vm.estado.ajustes.slotPalco
    val lib = vm.estado.biblioteca.associateBy { it.id }
    // Sem música: mostra a biblioteca inteira, para uso rápido.
    val timbres = musica?.timbres?.mapNotNull { lib[it] } ?: vm.estado.biblioteca

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(onClick = { menu = true }, Modifier.fillMaxWidth()) {
                    Text(musica?.nome ?: "Biblioteca inteira", Modifier.weight(1f), maxLines = 1)
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(menu, { menu = false }) {
                    repertorio.forEach { m -> DropdownMenuItem({ Text(m.nome) }, { musicaId = m.id; menu = false }) }
                    if (repertorio.isEmpty()) DropdownMenuItem({ Text("Crie músicas na aba Músicas") }, { menu = false })
                }
            }
            SeletorSlot(slot, { s -> vm.alterarAjustes { it.copy(slotPalco = s) } }, Modifier.weight(0.8f))
        }
        Text(
            "Cada toque grava o timbre no preset ${slot.letra}. Deixe o pedal no ${slot.letra}.",
            Modifier.padding(vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (vm.presets == null) { PrecisaDoPedal(vm); return@Column }
        if (timbres.isEmpty()) {
            Text("Nenhum timbre nesta música. Adicione timbres na aba Músicas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(timbres, key = { i, t -> "$i-${t.id}" }) { i, t ->
                val ativo = vm.ultimoPalco == t.id
                Button(
                    onClick = { vm.tocarPalco(t, slot) },
                    enabled = !vm.ocupado,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    colors = if (ativo) ButtonDefaults.buttonColors()
                    else ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                    shape = MaterialTheme.shapes.large,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${i + 1}", fontSize = 14.sp)
                        Text(t.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3)
                    }
                }
            }
        }
    }
}
