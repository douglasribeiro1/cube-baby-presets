package io.github.douglasribeiro1.cubebaby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stadium
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.douglasribeiro1.cubebaby.AppViewModel
import io.github.douglasribeiro1.cubebaby.Conexao

val Acento = Color(0xFF33EAB8)
val Aviso = Color(0xFFFFB74D)
val Perigo = Color(0xFFFF6B6B)

private val Cores: ColorScheme = darkColorScheme(
    primary = Acento,
    onPrimary = Color(0xFF0B1A15),
    secondary = Color(0xFF7FD9C0),
    background = Color(0xFF14161A),
    surface = Color(0xFF14161A),
    surfaceVariant = Color(0xFF1E2127),
    surfaceContainer = Color(0xFF1E2127),
    surfaceContainerHigh = Color(0xFF252931),
    surfaceContainerHighest = Color(0xFF2F343C),
    onSurface = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF9AA0A6),
    error = Perigo,
)

@Composable
fun TemaCubeBaby(conteudo: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Cores) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { conteudo() }
    }
}

enum class Aba(val titulo: String, val icone: ImageVector) {
    EDITOR("Editor", Icons.Filled.Tune),
    BIBLIOTECA("Biblioteca", Icons.AutoMirrored.Filled.LibraryBooks),
    PALCO("Palco", Icons.Filled.Stadium),
    REPERTORIO("Músicas", Icons.Filled.MusicNote),
    COMUNIDADE("Comunidade", Icons.Filled.Download),
    AJUSTES("Ajustes", Icons.Filled.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRaiz(vm: AppViewModel, linkCompartilhado: String?, aoConsumirLink: () -> Unit) {
    var aba by rememberSaveable { mutableStateOf(Aba.EDITOR) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.mensagens.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(linkCompartilhado) {
        if (linkCompartilhado != null) { aba = Aba.COMUNIDADE; aoConsumirLink() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(if (aba == Aba.AJUSTES) "Ajustes" else "Cube Baby Presets", style = MaterialTheme.typography.titleLarge)
                        LinhaConexao(vm)
                    }
                },
                actions = {
                    if (vm.ocupado) CircularProgressIndicator(Modifier.size(22.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                    IconButton(onClick = { aba = if (aba == Aba.AJUSTES) Aba.EDITOR else Aba.AJUSTES }) {
                        Icon(Icons.Filled.Settings, "Ajustes")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Aba.entries.filter { it != Aba.AJUSTES }.forEach { a ->
                    NavigationBarItem(
                        selected = aba == a,
                        onClick = { aba = a },
                        icon = { Icon(a.icone, null) },
                        label = { Text(a.titulo, maxLines = 1) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            if (!vm.carregado) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else when (aba) {
                Aba.EDITOR -> TelaEditor(vm)
                Aba.BIBLIOTECA -> TelaBiblioteca(vm)
                Aba.PALCO -> TelaPalco(vm)
                Aba.REPERTORIO -> TelaRepertorio(vm)
                Aba.COMUNIDADE -> TelaComunidade(vm)
                Aba.AJUSTES -> TelaAjustes(vm)
            }
        }
    }
}

@Composable
private fun LinhaConexao(vm: AppViewModel) {
    val c = vm.conexao
    val (cor, texto) = when (c) {
        is Conexao.Conectado -> Acento to "Conectado: ${c.nome}"
        Conexao.Conectando -> Aviso to "Conectando…"
        Conexao.Desconectado -> MaterialTheme.colorScheme.onSurfaceVariant to "Desconectado — plugue o pedal"
        Conexao.SemMidi -> Perigo to "Este celular não tem suporte a MIDI"
        is Conexao.Erro -> Perigo to c.msg
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(Modifier.size(8.dp), shape = CircleShape, color = cor) {}
        Spacer(Modifier.width(6.dp))
        Text(texto, style = MaterialTheme.typography.bodySmall, color = cor, maxLines = 1)
        if (c !is Conexao.Conectado && c !is Conexao.Conectando && c !is Conexao.SemMidi) {
            TextButton(onClick = { vm.conectar() }, Modifier.padding(start = 4.dp)) { Text("Tentar") }
        }
    }
}

/** Aviso padrão quando a tela precisa do pedal. */
@Composable
fun PrecisaDoPedal(vm: AppViewModel, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Conecte o Cube Baby pelo cabo USB-C (OTG).", style = MaterialTheme.typography.titleMedium)
        Text(
            "O app abre sozinho quando o pedal é plugado. Se não conectar, feche outros apps de MIDI e toque em Conectar.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = { vm.conectar() }) { Text("Conectar") }
    }
}
