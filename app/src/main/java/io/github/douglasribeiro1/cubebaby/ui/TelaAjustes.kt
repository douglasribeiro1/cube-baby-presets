package io.github.douglasribeiro1.cubebaby.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.douglasribeiro1.cubebaby.AppViewModel
import io.github.douglasribeiro1.cubebaby.modelo.BackupPedal
import io.github.douglasribeiro1.cubebaby.modelo.PoliticaChaves
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TelaAjustes(vm: AppViewModel) {
    val ctx = LocalContext.current
    @Suppress("DEPRECATION") val clip = LocalClipboardManager.current
    var importacao by remember { mutableStateOf<AppViewModel.Importacao?>(null) }
    var restaurar by remember { mutableStateOf<BackupPedal?>(null) }

    val exportar = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(vm.gerarExportacao().toByteArray()) }
        }.onFailure { vm.registro.add("Falha ao exportar: ${it.message}") }
    }
    val importar = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val texto = runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull()
            if (texto != null) importacao = vm.lerImportacao(texto)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Secao("Backup em arquivo")
        Text("Exporta presets do pedal, biblioteca, repertório e nomes num JSON (compatível com o protótipo web).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { exportar.launch("cube-baby-${LocalDate.now()}.json") }) { Text("Exportar") }
            OutlinedButton(onClick = { importar.launch(arrayOf("application/json", "text/plain", "*/*")) }) { Text("Importar…") }
        }

        Secao("Backups automáticos do pedal")
        Text("Feitos antes da primeira alteração em cada conexão (últimos 30).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.estado.backups.isEmpty()) Text("Nenhum ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        vm.estado.backups.take(10).forEach { b ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatarData(b.data), Modifier.weight(1f))
                TextButton(onClick = { restaurar = b }, enabled = vm.presets != null && !vm.ocupado) { Text("Restaurar") }
            }
        }

        Secao("Chaves de efeito ao importar da comunidade")
        PoliticaChaves.entries.forEach { p ->
            Row(
                Modifier.fillMaxWidth().selectable(vm.estado.ajustes.politicaChaves == p) { vm.alterarAjustes { it.copy(politicaChaves = p) } },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(vm.estado.ajustes.politicaChaves == p, null)
                Text(
                    when (p) {
                        PoliticaChaves.PERGUNTAR -> "Perguntar"
                        PoliticaChaves.MANTER_ATUAIS -> "Manter as do timbre atual"
                        PoliticaChaves.LIGAR_TODAS -> "Ligar as três"
                    },
                    Modifier.padding(start = 8.dp),
                )
            }
        }

        Secao("Nomes dos amps (Type)")
        ListaNomes(vm.estado.ajustes.nomesAmps) { i, n -> vm.alterarAjustes { a -> a.copy(nomesAmps = a.nomesAmps.toMutableList().also { it[i] = n }) } }
        Secao("Nomes das caixas (IR Cab)")
        ListaNomes(vm.estado.ajustes.nomesCabs) { i, n -> vm.alterarAjustes { a -> a.copy(nomesCabs = a.nomesCabs.toMutableList().also { it[i] = n }) } }
        Secao("Nomes das chaves de efeito")
        ListaNomes(vm.estado.ajustes.nomesChaves) { i, n -> vm.alterarAjustes { a -> a.copy(nomesChaves = a.nomesChaves.toMutableList().also { it[i] = n }) } }

        Secao("Registro técnico")
        Text("Presets no pedal: ${vm.hexPresets()}", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        OutlinedButton(onClick = { clip.setText(AnnotatedString(vm.registro.joinToString("\n"))) }) { Text("Copiar registro") }
        SelectionContainer {
            Text(vm.registro.takeLast(80).joinToString("\n"), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("Cube Baby Presets ${versao(ctx)} — projeto independente, sem relação com M-Vave/Cuvave/Sinco.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    importacao?.let { imp -> DialogoImportacao(vm, imp) { importacao = null } }
    restaurar?.let { b ->
        DialogoConfirmar(
            "Restaurar backup", "Gravar o backup de ${formatarData(b.data)} nos presets A, B e C? Os valores atuais serão substituídos.", "Gravar",
            { restaurar = null },
        ) { vm.restaurar48(b.bytes, "backup de ${formatarData(b.data)}") }
    }
}

@Composable
private fun Secao(t: String) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(t, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ListaNomes(nomes: List<String>, aoMudar: (Int, String) -> Unit) {
    nomes.forEachIndexed { i, n ->
        OutlinedTextField(n, { aoMudar(i, it) }, Modifier.fillMaxWidth(), label = { Text("Posição ${i + 1} (valor $i)") }, singleLine = true)
    }
}

@Composable
private fun DialogoImportacao(vm: AppViewModel, imp: AppViewModel.Importacao, aoFechar: () -> Unit) {
    val a = imp.arquivo
    var dados by remember { mutableStateOf(a.biblioteca.isNotEmpty() || a.repertorio.isNotEmpty()) }
    var ajustes by remember { mutableStateOf(false) }
    var pedal by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text("Importar backup") },
        text = {
            Column {
                Text("O arquivo tem ${a.biblioteca.size} timbre(s), ${a.repertorio.size} música(s)" + (if (a.bytes != null) " e os 3 presets do pedal." else "."))
                if (imp.descartados > 0) Text("${imp.descartados} timbre(s) com valores fora da faixa serão ignorados.", color = Aviso)
                Opcao("Juntar biblioteca e músicas às atuais", dados, a.biblioteca.isNotEmpty() || a.repertorio.isNotEmpty()) { dados = it }
                Opcao("Usar os nomes e ajustes do arquivo", ajustes, a.ajustes != null) { ajustes = it }
                Opcao("Gravar os presets A, B e C no pedal", pedal, a.bytes != null && vm.presets != null) { pedal = it }
            }
        },
        confirmButton = { TextButton(onClick = { vm.aplicarImportacao(imp, dados, ajustes, pedal); aoFechar() }, enabled = dados || ajustes || pedal) { Text("Importar") } },
        dismissButton = { TextButton(onClick = aoFechar) { Text("Cancelar") } },
    )
}

@Composable
private fun Opcao(texto: String, marcado: Boolean, habilitado: Boolean, aoMudar: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(marcado && habilitado, aoMudar, enabled = habilitado)
        Text(texto, color = if (habilitado) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatarData(iso: String): String = runCatching {
    OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
}.getOrDefault(iso)

private fun versao(ctx: android.content.Context): String = runCatching {
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
}.getOrDefault("")
