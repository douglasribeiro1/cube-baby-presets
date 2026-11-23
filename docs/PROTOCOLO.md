# Protocolo USB-MIDI do M-Vave/Cuvave Cube Baby

Obtido por análise estática do CubeSuite (Windows, Qt, site da M-Vave) e **confirmado no pedal real** em 23/11/2026 (pedal identificado como `SINCO-CubeBaby`, firmware respondendo `01 13 14 15 16 17 18 19 1E` no comando de identificação).

## 1. Transporte

- O pedal é um dispositivo USB composto, compatível com a classe padrão: **VID 0x301A, PID 0x5555**. Interfaces: USB-Audio (MI_00) e **USB-MIDI** (MI_03). No Windows a porta MIDI aparece como `USB-Midi`.
- Não precisa de driver. No Android: `android.media.midi` (MidiManager), porta de entrada e de saída do mesmo dispositivo.
- Toda comunicação é por **SysEx**: `F0` + *quadro empacotado em 7 bits* + `F7`.
- Não há nenhuma mensagem de inicialização: basta abrir as portas e mandar comandos.
- **Program Change não é aceito** (testado C0 00/01/02 no canal 1: o pedal ignora).
- O pedal não envia nada espontaneamente (nem ao trocar preset nem ao girar botões).

### Empacotamento 7 bits (os dois lados)
Fluxo de bits com o **bit menos significativo primeiro**:

```
pack7(bytes):   acc=0, bits=0
  para cada b: acc |= b << bits; bits += 8
               enquanto bits >= 7: emite acc & 0x7F; acc >>= 7; bits -= 7
  no fim, se bits > 0: emite acc & 0x7F

unpack7(bytes): acc=0, bits=0
  para cada b (sem F0/F7): acc |= (b & 0x7F) << bits; bits += 7
               se bits >= 8: emite acc & 0xFF; acc >>= 8; bits -= 8
```

## 2. Quadro (antes do empacotamento)

```
00 59 | CMD | LEN0 LEN1 LEN2 | PAYLOAD (LEN bytes) | CHK
```
- Cabeçalho fixo **`00 59`** (atenção à ordem: é `00` e depois `59`).
- LEN = tamanho do payload, 24 bits little-endian.
- CHK = `(~soma(payload)) & 0xFF` (só o payload entra na soma). Payload vazio → CHK = `FF`.
- Respostas usam o mesmo formato. Sempre validar cabeçalho, CMD e CHK.

Exemplos reais:
| Pedido | Quadro | SysEx enviado |
|---|---|---|
| Identificação | `00 59 11 00 00 00 FF` | `F0 00 32 45 00 00 00 40 7F F7` |
| Ler 48 bytes dos presets | `00 59 23 08 00 00 05 00 00 00 00 30 00 00 CA` | `F0 00 32 0D 41 00 00 40 02 00 00 00 00 00 06 00 00 4A 01 F7` |

## 3. Comandos

| CMD | Nome | Payload do pedido | Resposta |
|---|---|---|---|
| `0x11` | Identificação | (vazio) | CMD 0x11, 27 bytes: nome ASCII em 20 bytes (`SINCO-CubeBaby` + espaços) + 7 bytes de versão |
| `0x23` | Ler memória | `[espaço u8][endereço u32 LE][tamanho u24 LE]` | CMD 0x23, payload `[espaço][endereço u32][tamanho u24][dados...]` |
| `0x22` | Gravar memória | `[espaço u8][endereço u32 LE][tamanho u24 LE][dados...]` | **ACK**: `00 59 00 01 00 00 ST CHK` → CMD 0x00, ST = 0 significa sucesso |
| `0x21` | Apagar setor (flash) | `[espaço][endereço u32]` | ACK | 
| `0x24` | Verificar checksum de bloco | `[espaço][endereço u32][tamanho u32]` | CMD 0x24 com soma de 16 bits |
| `0x50` | (modo; payload `7F 7F`) | — | não usado |

- Gravação: no máximo **1024 bytes** por pedido (o CubeSuite fatia em blocos de 0x400).
- Leitura: no máximo **1009 bytes** por pedido (0x400 − 15).
- **Não usar `0x21`/escrita fora do espaço 5** no app: é por aí que o CubeSuite grava IR e firmware na flash.

## 4. Mapa de memória usado

### Espaço 5, endereço 0x00–0x2F: os 3 presets (48 bytes)
Preset A = 0x00–0x0F, B = 0x10–0x1F, C = 0x20–0x2F. Cada um com 16 bytes:

| Byte | Campo (nome provisório) | Faixa no CubeSuite |
|---|---|---|
| 0 | Type (amp/preamp) | 0–8 (9 modelos) |
| 1 | Gain | 0–7 |
| 2 | Tone | 0–15 |
| 3 | Reverb | 0–15 |
| 4 | Feedback (delay) | 0–127 |
| 5 | Volume | 0–127 |
| 6 | Time (delay) | 0–31 |
| 7 | Mix (delay) | 0–118 |
| 8 | Mod | 0–15 |
| 9 | IR Cab | 0–8 |
| 10 | Chave de efeito 1 (liga/desliga) | 0/1 — **confirmado: acende/apaga LED no pedal** |
| 11 | Chave de efeito 2 | 0/1 — confirmado |
| 12 | Chave de efeito 3 | 0/1 — confirmado |
| 13–15 | não usados (sempre 0) | não gravar |

Mapeamento bytes ↔ widgets extraído do diálogo `CCubeBabyJunIREditDlg` do CubeSuite. **A posição está confirmada; os nomes de 0–9 ainda precisam de checagem auditiva com guitarra.** Quais efeitos as chaves 10/11/12 ligam (Mod/Delay/Reverb?) também falta confirmar.

Valores de fábrica lidos do pedal do Douglas (backup):
```
A: 07 07 09 05 34 41 14 1E 07 04 01 01 01 00 00 00
B: 04 02 05 05 2C 46 17 1E 07 05 01 01 01 00 00 00
C: 01 07 07 05 34 6F 14 0C 05 02 01 01 01 00 00 00
```

### Compatibilidade com a comunidade
O site https://cube-baby-presets.vercel.app usa os mesmos nomes do CubeSuite (volume, ir cab, reverb, mix, fb, time, mod, tone, gain, type) e valores na mesma escala dos bytes 0–9 (ex.: fb 84 e volume 41, dentro de 0–127). Ou seja, os valores do site podem ser gravados direto nos bytes correspondentes. O site não traz as chaves de efeito (bytes 10–12) nem a IR personalizada.

### Comportamento observado
- Gravar um byte no espaço 5 → ACK com status 0 → a releitura mostra o valor novo. Funciona.
- Gravar no preset **que está ativo** muda o som **na hora** (confirmado pelas chaves de efeito). Isso permite o "modo palco": deixar o pedal sempre no A e mandar timbres do celular para o A.
- Girar os botões físicos **não altera** os bytes salvos (só o som ao vivo).
- Trocar A/B/C pelas chaves do pedal **não altera** nada legível: espaço 5 0x30–0x4F e espaço 0 0x3000–0x300F ficam idênticos. O app **não consegue saber nem mudar** o preset ativo.

### Outras áreas vistas (só leitura, significado desconhecido)
- Espaço 5, 0x30 (32 bytes): `91 01 00 00 00 00 00 00 00 00 00 00 8C 0A 01 01 03 00 00 00 03 00 00 80 00 00 00 00 00 50 08 40`
- Espaço 0, 0x3000 (16 bytes): `BF 01 00 00 00 00 00 00 00 00 00 00 8C 0A 01 01` (o CubeSuite lê/grava 1 byte aqui para "USB Loopback").
- IR: espaço 4, endereço 0 = 512 amostras float32 (IR ao vivo); 0x764 = flag de 1 byte; 0x768 = ganho float + 512 floats (0x804 bytes). **Fora do escopo do app.**

## 5. Pendências a validar com guitarra ligada
1. Nome real de cada campo 0–9 e de cada chave 10–12.
2. Se Type, Gain e IR Cab mudam ao vivo sem estalo.
3. Se a gravação no espaço 5 vai para flash (desgaste) ou RAM — o app deve evitar gravar bytes que não mudaram.
4. Comportamento quando o usuário gira um botão físico depois de o app gravar.
