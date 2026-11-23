package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.douglasribeiro1.cubebaby.AppViewModel
import io.github.douglasribeiro1.cubebaby.modelo.BYTES_CHAVES
import io.github.douglasribeiro1.cubebaby.modelo.Campo
import io.github.douglasribeiro1.cubebaby.modelo.Slot
import kotlin.math.roundToInt

@Composable
fun TelaEditor(vm: AppViewModel) {
    var slot by rememberSaveable { mutableStateOf(Slot.A) }
    var dialogoSalvar by remember { mutableStateOf(false) }
    val bytes = vm.timbreDoSlot(slot)
    val ajustes = vm.estado.ajustes

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TabRow(selectedTabIndex = slot.ordinal) {
            Slot.entries.forEach { s ->
                Tab(selected = s == slot, onClick = { slot = s }, text = { Text("Preset ${s.letra}", fontSize = 18.sp) })
            }
        }
        Text(
            "Você está editando o preset ${slot.letra}. As abas não trocam o preset ativo no pedal — use as chaves do pedal para isso. Cada mudança é gravada e conferida na hora.",
            Modifier.padding(16.dp, 10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (bytes == null) {
            PrecisaDoPedal(vm)
            return@Column
        }

        Campo.entries.forEach { c ->
            // remember com chave no valor do pedal: ao reler, o slider volta ao valor verdadeiro.
            LinhaSlider(
                rotulo = c.rotulo,
                valorPedal = bytes[c.byte],
                max = c.max,
                chave = "${slot.name}-${c.name}",
                texto = { ajustes.textoValor(c, it) },
                habilitado = !vm.ocupado,
            ) { vm.alterarByte(slot, c.byte, it) }
        }

        Text("Chaves de efeito", Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), style = MaterialTheme.typography.titleSmall)
        FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BYTES_CHAVES.forEachIndexed { i, b ->
                val ligado = bytes[b] == 1
                FilterChip(
                    selected = ligado,
                    enabled = !vm.ocupado,
                    onClick = { vm.alterarByte(slot, b, if (ligado) 0 else 1) },
                    label = { Text("${ajustes.nomesChaves.getOrElse(i) { "Efeito ${i + 1}" }}: ${if (ligado) "ligado" else "desligado"}") },
                    leadingIcon = if (ligado) { { Icon(Icons.Filled.Check, null) } } else null,
                )
            }
        }

        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialogoSalvar = true }) {
                Icon(Icons.Filled.Save, null); Text("  Salvar na biblioteca")
            }
            OutlinedButton(onClick = { vm.relerPresets() }) {
                Icon(Icons.Filled.Refresh, null); Text("  Reler")
            }
        }
        Text(
            "Os nomes dos campos ainda são provisórios (ver docs/PROTOCOLO.md). Nomes de amps, caixas e chaves podem ser editados em Ajustes.",
            Modifier.padding(16.dp, 0.dp, 16.dp, 24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (dialogoSalvar && bytes != null) {
        DialogoTexto("Salvar preset ${slot.letra} na biblioteca", aoFechar = { dialogoSalvar = false }) {
            vm.salvarNaBiblioteca(it, bytes)
        }
    }
}

@Composable
private fun LinhaSlider(
    rotulo: String,
    valorPedal: Int,
    max: Int,
    chave: String,
    texto: (Int) -> String,
    habilitado: Boolean,
    aoSoltar: (Int) -> Unit,
) {
    var valor by remember(chave, valorPedal) { mutableFloatStateOf(valorPedal.toFloat()) }
    val v = valor.roundToInt()
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rotulo, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text(
                texto(v),
                Modifier.width(120.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                color = if (v != valorPedal) Aviso else MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = valor,
            onValueChange = { valor = it },
            onValueChangeFinished = { val n = valor.roundToInt(); if (n != valorPedal) aoSoltar(n) },
            valueRange = 0f..max.toFloat(),
            steps = if (max <= 32) max - 1 else 0,
            enabled = habilitado,
        )
    }
}
