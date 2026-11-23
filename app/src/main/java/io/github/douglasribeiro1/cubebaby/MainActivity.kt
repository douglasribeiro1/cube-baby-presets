package io.github.douglasribeiro1.cubebaby

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf
import io.github.douglasribeiro1.cubebaby.ui.AppRaiz
import io.github.douglasribeiro1.cubebaby.ui.TemaCubeBaby

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels()

    /** Link recebido por "Compartilhar" (abre a aba Comunidade). */
    private val linkCompartilhado = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tratarIntent(intent)
        setContent {
            TemaCubeBaby {
                AppRaiz(vm, linkCompartilhado.value) { linkCompartilhado.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tratarIntent(intent)
    }

    private fun tratarIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_SEND -> {
                val texto = intent.getStringExtra(Intent.EXTRA_TEXT)
                if (!texto.isNullOrBlank()) {
                    linkCompartilhado.value = texto
                    vm.abrirDaComunidade(texto)
                }
            }
            // Pedal plugado: tenta conectar (o MidiManager pode demorar um instante a anunciar).
            "android.hardware.usb.action.USB_DEVICE_ATTACHED" -> vm.conectar()
        }
    }
}
