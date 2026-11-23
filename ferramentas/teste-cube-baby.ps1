# Teste do Cube Baby pelo próprio Windows (WinMM), sem instalar nada.
# Somente leitura: pede a identificação do pedal e lê os 48 bytes dos presets.
$ErrorActionPreference = "Stop"

Add-Type -TypeDefinition @"
using System;
using System.Text;
using System.Threading;
using System.Runtime.InteropServices;

public static class CubeTest {
    [StructLayout(LayoutKind.Sequential)]
    public struct MIDIHDR {
        public IntPtr lpData; public uint dwBufferLength; public uint dwBytesRecorded;
        public IntPtr dwUser; public uint dwFlags; public IntPtr lpNext; public IntPtr reserved;
        public uint dwOffset;
        [MarshalAs(UnmanagedType.ByValArray, SizeConst = 8)] public IntPtr[] dwReserved;
    }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct MIDIINCAPS { public ushort wMid; public ushort wPid; public uint vDriverVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szPname; public uint dwSupport; }
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct MIDIOUTCAPS { public ushort wMid; public ushort wPid; public uint vDriverVersion;
        [MarshalAs(UnmanagedType.ByValTStr, SizeConst = 32)] public string szPname;
        public ushort wTechnology; public ushort wVoices; public ushort wNotes; public ushort wChannelMask; public uint dwSupport; }

    [DllImport("winmm.dll")] static extern uint midiInGetNumDevs();
    [DllImport("winmm.dll")] static extern uint midiOutGetNumDevs();
    [DllImport("winmm.dll", CharSet = CharSet.Unicode)] static extern int midiInGetDevCapsW(UIntPtr id, ref MIDIINCAPS c, uint sz);
    [DllImport("winmm.dll", CharSet = CharSet.Unicode)] static extern int midiOutGetDevCapsW(UIntPtr id, ref MIDIOUTCAPS c, uint sz);
    [DllImport("winmm.dll")] static extern int midiInOpen(out IntPtr h, uint id, IntPtr cb, IntPtr inst, uint flags);
    [DllImport("winmm.dll")] static extern int midiOutOpen(out IntPtr h, uint id, IntPtr cb, IntPtr inst, uint flags);
    [DllImport("winmm.dll")] static extern int midiInPrepareHeader(IntPtr h, IntPtr hdr, uint sz);
    [DllImport("winmm.dll")] static extern int midiInUnprepareHeader(IntPtr h, IntPtr hdr, uint sz);
    [DllImport("winmm.dll")] static extern int midiInAddBuffer(IntPtr h, IntPtr hdr, uint sz);
    [DllImport("winmm.dll")] static extern int midiInStart(IntPtr h);
    [DllImport("winmm.dll")] static extern int midiInReset(IntPtr h);
    [DllImport("winmm.dll")] static extern int midiInClose(IntPtr h);
    [DllImport("winmm.dll")] static extern int midiOutPrepareHeader(IntPtr h, IntPtr hdr, uint sz);
    [DllImport("winmm.dll")] static extern int midiOutUnprepareHeader(IntPtr h, IntPtr hdr, uint sz);
    [DllImport("winmm.dll")] static extern int midiOutLongMsg(IntPtr h, IntPtr hdr, uint sz);
    [DllImport("winmm.dll")] static extern int midiOutClose(IntPtr h);

    static string Hex(byte[] b, int n) { var sb = new StringBuilder(); for (int i = 0; i < n; i++) sb.Append(b[i].ToString("X2")).Append(' '); return sb.ToString().Trim(); }

    public static string ListPorts() {
        var sb = new StringBuilder();
        for (uint i = 0; i < midiInGetNumDevs(); i++) { var c = new MIDIINCAPS(); midiInGetDevCapsW((UIntPtr)i, ref c, (uint)Marshal.SizeOf(c)); sb.AppendLine("Entrada " + i + ": " + c.szPname); }
        for (uint i = 0; i < midiOutGetNumDevs(); i++) { var c = new MIDIOUTCAPS(); midiOutGetDevCapsW((UIntPtr)i, ref c, (uint)Marshal.SizeOf(c)); sb.AppendLine("Saida " + i + ": " + c.szPname); }
        return sb.ToString();
    }

    static IntPtr NewHdr(IntPtr data, int len) {
        var h = new MIDIHDR(); h.lpData = data; h.dwBufferLength = (uint)len; h.dwReserved = new IntPtr[8];
        IntPtr p = Marshal.AllocHGlobal(Marshal.SizeOf(typeof(MIDIHDR))); Marshal.StructureToPtr(h, p, false); return p;
    }

    public static string Exchange(uint inId, uint outId, byte[] sysex, int timeoutMs) {
        var log = new StringBuilder();
        IntPtr hin, hout; int r;
        r = midiInOpen(out hin, inId, IntPtr.Zero, IntPtr.Zero, 0); if (r != 0) return "Erro ao abrir entrada: codigo " + r + (r == 4 ? " (porta em uso por outro programa)" : "");
        r = midiOutOpen(out hout, outId, IntPtr.Zero, IntPtr.Zero, 0); if (r != 0) { midiInClose(hin); return "Erro ao abrir saida: codigo " + r + (r == 4 ? " (porta em uso por outro programa)" : ""); }
        uint hsz = (uint)Marshal.SizeOf(typeof(MIDIHDR));
        IntPtr inBuf = Marshal.AllocHGlobal(8192); IntPtr inHdr = NewHdr(inBuf, 8192);
        midiInPrepareHeader(hin, inHdr, hsz); midiInAddBuffer(hin, inHdr, hsz); midiInStart(hin);
        IntPtr outBuf = Marshal.AllocHGlobal(sysex.Length); Marshal.Copy(sysex, 0, outBuf, sysex.Length);
        IntPtr outHdr = NewHdr(outBuf, sysex.Length);
        midiOutPrepareHeader(hout, outHdr, hsz);
        r = midiOutLongMsg(hout, outHdr, hsz);
        log.AppendLine("TX (" + sysex.Length + " bytes, resultado " + r + "): " + Hex(sysex, sysex.Length));
        var sw = System.Diagnostics.Stopwatch.StartNew(); bool got = false;
        while (sw.ElapsedMilliseconds < timeoutMs) {
            var h = (MIDIHDR)Marshal.PtrToStructure(inHdr, typeof(MIDIHDR));
            if ((h.dwFlags & 1) != 0 && h.dwBytesRecorded > 0) {
                var b = new byte[h.dwBytesRecorded]; Marshal.Copy(inBuf, b, 0, b.Length);
                log.AppendLine("RX (" + b.Length + " bytes): " + Hex(b, b.Length));
                // desempacota 7 bits -> 8 bits
                var raw = new System.Collections.Generic.List<byte>(); int acc = 0, bits = 0;
                for (int i = 1; i < b.Length && b[i] != 0xF7; i++) { acc |= (b[i] & 0x7F) << bits; bits += 7; if (bits >= 8) { raw.Add((byte)(acc & 0xFF)); acc >>= 8; bits -= 8; } }
                var ra = raw.ToArray(); log.AppendLine("Quadro decodificado: " + Hex(ra, ra.Length));
                var asc = new StringBuilder(); foreach (var x in ra) asc.Append(x >= 32 && x < 127 ? (char)x : '.'); log.AppendLine("Texto: " + asc);
                got = true; break;
            }
            Thread.Sleep(20);
        }
        if (!got) log.AppendLine("Sem resposta em " + timeoutMs + " ms.");
        midiInReset(hin); midiInUnprepareHeader(hin, inHdr, hsz); midiOutUnprepareHeader(hout, outHdr, hsz);
        midiInClose(hin); midiOutClose(hout);
        Marshal.FreeHGlobal(inHdr); Marshal.FreeHGlobal(outHdr); Marshal.FreeHGlobal(inBuf); Marshal.FreeHGlobal(outBuf);
        return log.ToString();
    }
}
"@

Write-Host "=== Portas MIDI ===" -ForegroundColor Cyan
Write-Host ([CubeTest]::ListPorts())

$inId  = [uint32](Read-Host "Numero da ENTRADA do pedal (USB-Midi)")
$outId = [uint32](Read-Host "Numero da SAIDA do pedal (USB-Midi)")

# Comando 0x11 (identificacao) e 0x23 (ler 48 bytes dos presets), ja empacotados
$info  = [byte[]](0xF0,0x00,0x32,0x45,0x00,0x00,0x00,0x40,0x7F,0xF7)
$leitura = [byte[]](0xF0,0x00,0x32,0x0D,0x41,0x00,0x00,0x40,0x02,0x00,0x00,0x00,0x00,0x00,0x06,0x00,0x00,0x4A,0x01,0xF7)

Write-Host "`n=== Teste 1: identificacao ===" -ForegroundColor Cyan
Write-Host ([CubeTest]::Exchange($inId, $outId, $info, 3000))
Write-Host "=== Teste 2: leitura dos presets ===" -ForegroundColor Cyan
Write-Host ([CubeTest]::Exchange($inId, $outId, $leitura, 3000))
Read-Host "Pronto. Copie todo o texto acima e mande para o Claude. Tecle Enter para sair"
