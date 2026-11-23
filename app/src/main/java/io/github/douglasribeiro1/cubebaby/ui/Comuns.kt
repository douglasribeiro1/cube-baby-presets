package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.douglasribeiro1.cubebaby.modelo.AMPS_FABRICA
import io.github.douglasribeiro1.cubebaby.modelo.Ajustes
import io.github.douglasribeiro1.cubebaby.modelo.CABS_FABRICA
import io.github.douglasribeiro1.cubebaby.modelo.MOD_CENTRO
import io.github.douglasribeiro1.cubebaby.modelo.BYTES_CHAVES
import io.github.douglasribeiro1.cubebaby.modelo.Campo
import io.github.douglasribeiro1.cubebaby.modelo.PoliticaChaves
import io.github.douglasribeiro1.cubebaby.modelo.Slot

@Composable
fun SeletorSlot(slot: Slot, aoMudar: (Slot) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        Slot.entries.forEachIndexed { i, s ->
            SegmentedButton(
                selected = s == slot,
                onClick = { aoMudar(s) },
                shape = SegmentedButtonDefaults.itemShape(i, Slot.entries.size),
            ) { Text(s.letra) }
        }
    }
}

@Composable
fun DialogoTexto(
    titulo: String,
    inicial: String = "",
    rotulo: String = "Nome",
    confirmar: String = "Salvar",
    aoFechar: () -> Unit,
    aoConfirmar: (String) -> Unit,
) {
    var texto by remember { mutableStateOf(inicial) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = { OutlinedTextField(texto, { texto = it }, label = { Text(rotulo) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { aoConfirmar(texto); aoFechar() }) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
fun DialogoConfirmar(titulo: String, texto: String, confirmar: String = "Confirmar", aoFechar: () -> Unit, aoConfirmar: () -> Unit) {
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(titulo) },
        text = { Text(texto) },
        confirmButton = { TextButton(onClick = { aoConfirmar(); aoFechar() }) { Text(confirmar) } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

/** Pergunta, na primeira importação, o que fazer com as chaves de efeito (o site não as informa). */
@Composable
fun DialogoChaves(aoFechar: () -> Unit, aoEscolher: (PoliticaChaves, lembrar: Boolean) -> Unit) {
    var lembrar by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Chaves de efeito") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("O site não informa as 3 chaves de efeito (bytes 10–12). O que fazer com elas?")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(lembrar, { lembrar = it })
                    Text("Lembrar a escolha (dá para mudar em Ajustes)")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { aoEscolher(PoliticaChaves.LIGAR_TODAS, lembrar); aoFechar() }) { Text("Ligar as três") }
        },
        dismissButton = {
            TextButton(onClick = { aoEscolher(PoliticaChaves.MANTER_ATUAIS, lembrar); aoFechar() }) { Text("Manter as atuais") }
        },
    )
}

fun Ajustes.nomeAmp(v: Int) = nomesAmps.getOrNull(v)?.ifBlank { null } ?: AMPS_FABRICA.getOrNull(v)?.nome ?: "Amp $v"
fun Ajustes.nomeCab(v: Int) = nomesCabs.getOrNull(v)?.ifBlank { null } ?: CABS_FABRICA.getOrNull(v) ?: "Cab $v"
fun tipoAmp(v: Int) = AMPS_FABRICA.getOrNull(v)?.tipo ?: ""

/** "4 · Cali JP A — Overdrive" (número da posição, nome e tipo). */
fun Ajustes.textoAmp(v: Int) = "$v · ${nomeAmp(v)}" + tipoAmp(v).let { if (it.isEmpty()) "" else " — $it" }
fun Ajustes.textoCab(v: Int) = "$v · ${nomeCab(v)}"

fun textoMod(v: Int) = when {
    v < MOD_CENTRO -> "Chorus ($v)"
    v == MOD_CENTRO -> "Desligado ($v)"
    else -> "Phaser ($v)"
}

/** Texto do valor de um campo, igual em todas as telas. */
fun Ajustes.textoValor(c: Campo, v: Int): String = when (c) {
    Campo.TYPE -> textoAmp(v)
    Campo.IR_CAB -> textoCab(v)
    Campo.MOD -> textoMod(v)
    Campo.MIX -> if (v == 0) "0 · delay desligado" else v.toString()
    Campo.REVERB -> if (v == 0) "0 · desligado" else v.toString()
    else -> v.toString()
}

/** Resumo em duas linhas curtas: amp/tipo e caixa; depois efeitos. */
fun Ajustes.resumo(bytes: List<Int>): String {
    if (bytes.size < 13) return "—"
    val ch = BYTES_CHAVES.joinToString("") { if (bytes[it] == 1) "●" else "○" }
    val mod = textoMod(bytes[8]).substringBefore(" (")
    val delay = if (bytes[7] == 0) "Delay off" else "Delay ${bytes[7]}"
    val rev = if (bytes[3] == 0) "Reverb off" else "Reverb ${bytes[3]}"
    return "${textoAmp(bytes[0])} · Gain ${bytes[1]}\n${textoCab(bytes[9])}\nMod $mod · $delay · $rev · Vol ${bytes[5]} · $ch"
}

@Composable
fun TabelaValores(ajustes: Ajustes, bytes: List<Int>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Campo.entries.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                Text(c.rotulo, Modifier.weight(0.8f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(ajustes.textoValor(c, bytes[c.byte]), Modifier.weight(1.2f), style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }
    }
}
