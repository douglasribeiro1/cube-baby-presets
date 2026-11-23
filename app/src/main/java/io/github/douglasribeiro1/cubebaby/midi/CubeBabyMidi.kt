package io.github.douglasribeiro1.cubebaby.midi

import android.content.Context
import android.hardware.usb.UsbDevice
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager
import android.media.midi.MidiOutputPort
import android.media.midi.MidiReceiver
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume

class ErroPedal(msg: String) : Exception(msg)

/**
 * Transporte USB-MIDI com o Cube Baby.
 * - Um comando por vez (Mutex) e tempo limite de 2 s por resposta.
 * - Só sabe ler os 48 bytes dos presets, identificar e gravar dentro do espaço 5
 *   (a validação de endereço fica em [Protocolo.payloadGravar]).
 */
class CubeBabyMidi(context: Context, private val log: (String) -> Unit) {

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager?
    private val handler = Handler(Looper.getMainLooper())
    private val fila = Mutex()

    private var device: MidiDevice? = null
    private var entrada: MidiInputPort? = null   // nós escrevemos aqui (vai para o pedal)
    private var saida: MidiOutputPort? = null    // o pedal escreve aqui (chega para nós)
    @Volatile private var pendente: CompletableDeferred<Protocolo.Quadro>? = null
    @Volatile private var esperado: Int = -1

    val midiDisponivel: Boolean get() = midiManager != null
    val conectado: Boolean get() = entrada != null

    // ---------- descoberta ----------

    @Suppress("DEPRECATION")
    fun dispositivos(): List<MidiDeviceInfo> {
        val m = midiManager ?: return emptyList()
        val todos = if (Build.VERSION.SDK_INT >= 33)
            m.getDevicesForTransport(MidiManager.TRANSPORT_MIDI_BYTE_STREAM).toList()
        else m.devices.toList()
        return todos.filter { pareceCubeBaby(it) }
    }

    fun pareceCubeBaby(info: MidiDeviceInfo): Boolean {
        if (info.inputPortCount < 1 || info.outputPortCount < 1) return false
        val usb = usbDe(info)
        if (usb != null && usb.vendorId == VID && usb.productId == PID) return true
        val nome = listOfNotNull(
            info.properties.getString(MidiDeviceInfo.PROPERTY_NAME),
            info.properties.getString(MidiDeviceInfo.PROPERTY_PRODUCT),
        ).joinToString(" ")
        return nome.contains("USB-Midi", ignoreCase = true) || nome.contains("CubeBaby", ignoreCase = true)
    }

    @Suppress("DEPRECATION")
    private fun usbDe(info: MidiDeviceInfo): UsbDevice? =
        if (Build.VERSION.SDK_INT >= 33)
            info.properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE, UsbDevice::class.java)
        else info.properties.getParcelable(MidiDeviceInfo.PROPERTY_USB_DEVICE)

    fun registrarCallback(aoMudar: () -> Unit): MidiManager.DeviceCallback? {
        val m = midiManager ?: return null
        val cb = object : MidiManager.DeviceCallback() {
            override fun onDeviceAdded(device: MidiDeviceInfo) { if (pareceCubeBaby(device)) aoMudar() }
            override fun onDeviceRemoved(device: MidiDeviceInfo) {
                if (this@CubeBabyMidi.device?.info?.id == device.id) { fechar(); aoMudar() }
            }
        }
        @Suppress("DEPRECATION")
        m.registerDeviceCallback(cb, handler)
        return cb
    }

    fun removerCallback(cb: MidiManager.DeviceCallback?) {
        if (cb != null) midiManager?.unregisterDeviceCallback(cb)
    }

    // ---------- conexão ----------

    /** Abre o dispositivo, confirma pelo comando 0x11 e devolve o nome. Recusa outros aparelhos. */
    suspend fun conectar(info: MidiDeviceInfo): String {
        fechar()
        val m = midiManager ?: throw ErroPedal("Este aparelho não tem suporte a MIDI")
        val dev = suspendCancellableCoroutine { cont ->
            m.openDevice(info, { d -> if (cont.isActive) cont.resume(d) }, handler)
        } ?: throw ErroPedal("Não foi possível abrir o dispositivo MIDI")
        val inPort = dev.openInputPort(0)
        val outPort = dev.openOutputPort(0)
        if (inPort == null || outPort == null) {
            runCatching { inPort?.close(); outPort?.close(); dev.close() }
            throw ErroPedal("Portas MIDI ocupadas (feche outros apps de MIDI)")
        }
        device = dev; entrada = inPort; saida = outPort
        outPort.connect(receptor)
        try {
            val nome = Protocolo.extrairNome(enviar(Protocolo.CMD_INFO, ByteArray(0), Protocolo.CMD_INFO))
            if (!nome.startsWith(Protocolo.NOME_ESPERADO)) throw ErroPedal("Aparelho \"$nome\" não é um Cube Baby de guitarra")
            log("Pedal identificado: $nome")
            return nome
        } catch (e: Exception) {
            fechar()
            throw e
        }
    }

    fun fechar() {
        pendente?.completeExceptionally(ErroPedal("conexão encerrada"))
        pendente = null
        runCatching { saida?.disconnect(receptor) }
        runCatching { saida?.close() }
        runCatching { entrada?.close() }
        runCatching { device?.close() }
        saida = null; entrada = null; device = null
    }

    // ---------- comandos ----------

    suspend fun lerPresets(): ByteArray =
        Protocolo.extrairPresets(enviar(Protocolo.CMD_LER, Protocolo.payloadLerPresets(), Protocolo.CMD_LER))

    /** Grava [dados] a partir de [endereco] no espaço 5 e confere o ACK (status 0). */
    suspend fun gravar(endereco: Int, dados: ByteArray) {
        val payload = Protocolo.payloadGravar(endereco, dados) // valida faixa de endereços
        val ack = enviar(Protocolo.CMD_GRAVAR, payload, Protocolo.CMD_ACK)
        val st = ack.payload.firstOrNull()?.toInt()?.and(0xFF) ?: -1
        if (st != 0) throw ErroPedal("O pedal recusou a gravação (código $st)")
    }

    private suspend fun enviar(cmd: Int, payload: ByteArray, esperar: Int): Protocolo.Quadro = fila.withLock {
        val porta = entrada ?: throw ErroPedal("Pedal não conectado")
        val d = CompletableDeferred<Protocolo.Quadro>()
        esperado = esperar
        pendente = d
        val sysex = Protocolo.montarSysEx(cmd, payload)
        log("TX " + Protocolo.hex(Protocolo.montarQuadro(cmd, payload)))
        try {
            porta.send(sysex, 0, sysex.size)
            withTimeout(TEMPO_LIMITE_MS) { d.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw ErroPedal("Sem resposta do pedal (2 s)")
        } finally {
            pendente = null
        }
    }

    // ---------- recepção (remonta SysEx que pode chegar em pedaços) ----------

    private val buffer = ByteArrayOutputStream()
    private var dentroSysEx = false

    private val receptor = object : MidiReceiver() {
        override fun onSend(msg: ByteArray, offset: Int, count: Int, timestamp: Long) {
            synchronized(buffer) {
                for (i in offset until offset + count) {
                    val b = msg[i].toInt() and 0xFF
                    when {
                        b >= 0xF8 -> Unit // tempo real (clock, active sensing): ignora
                        b == 0xF0 -> { buffer.reset(); buffer.write(b); dentroSysEx = true }
                        b == 0xF7 && dentroSysEx -> { buffer.write(b); dentroSysEx = false; entregar(buffer.toByteArray()) }
                        dentroSysEx -> buffer.write(b)
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun entregar(sysex: ByteArray) {
        val p = pendente
        when (val a = Protocolo.analisarSysEx(sysex)) {
            is Protocolo.Analise.Erro -> {
                log("RX inválido (${a.motivo})")
                p?.completeExceptionally(ErroPedal("Resposta com erro de ${a.motivo}"))
            }
            is Protocolo.Analise.Ok -> {
                log("RX " + Protocolo.hex(Protocolo.montarQuadro(a.quadro.cmd, a.quadro.payload)))
                if (p == null) return
                if (a.quadro.cmd != esperado) p.completeExceptionally(ErroPedal("Resposta inesperada 0x%02X".format(a.quadro.cmd)))
                else p.complete(a.quadro)
            }
        }
    }

    companion object {
        const val VID = 0x301A
        const val PID = 0x5555
        const val TEMPO_LIMITE_MS = 2000L
    }
}
