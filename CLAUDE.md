# Cube Baby Presets — instruções para o Claude Code

App Android nativo (Kotlin) para editar e gerenciar os presets do pedal **M-Vave/Cuvave Cube Baby** pelo cabo USB (USB-MIDI). Uso pessoal do Douglas, num Galaxy S24. Idioma da interface e dos commits: **português do Brasil**.

## Leia antes de programar
- `docs/PROTOCOLO.md`: protocolo SysEx completo, mapa de memória e o que já foi testado no pedal. É a fonte de verdade.
- `prototipo/cube-baby-presets.html`: protótipo web **funcionando** (Web MIDI) com o mesmo protocolo. Use como referência de lógica (pack7, quadro, checksum, fila de comandos, verificação por releitura).
- `ferramentas/`: páginas e script de diagnóstico usados no PC. Não fazem parte do app.

## Pilha
- Kotlin + Jetpack Compose (Material 3, tema escuro), minSdk 26, targetSdk atual.
- MIDI: `android.media.midi.MidiManager`. Filtrar o dispositivo pelo VID 0x301A / PID 0x5555 (ou nome contendo "USB-Midi"/"CubeBaby") e confirmar com o comando 0x11 (nome `SINCO-CubeBaby`). Recusar outros aparelhos.
- `<uses-feature android:name="android.software.midi" android:required="true"/>` e intent-filter `USB_DEVICE_ATTACHED` com `device_filter.xml` (vendor-id 12314, product-id 21845) para abrir o app ao plugar o pedal.
- Persistência local: Room ou DataStore + JSON. Sem internet, sem conta, sem analytics.

## Regras de segurança (obrigatórias)
- O app **só grava no espaço 5, endereços 0x00–0x2F**, e só os bytes 0–12 de cada preset. Nunca usar os comandos 0x21 (apagar) nem gravar em outros espaços (IR/firmware ficam fora do escopo).
- Validar faixa de cada campo antes de gravar (ver tabela no PROTOCOLO.md).
- Depois de cada gravação: conferir ACK (status 0) e reler os 48 bytes para confirmar.
- Não regravar bytes que não mudaram (economia de flash).
- Fazer backup automático dos 48 bytes antes da primeira alteração em cada conexão.
- Um comando por vez (fila); tempo limite de 2 s por resposta.

## Funcionalidades (v1)
1. **Conexão**: detectar o pedal, identificar, ler os 3 presets; mostrar estado (conectado/desconectado) e reconectar ao replugar.
2. **Editor**: abas A/B/C (= qual preset está sendo editado; deixar claro que não troca o preset ativo no pedal), sliders por campo, 3 chaves de efeito.
3. **Biblioteca de timbres**: salvar o preset atual com nome; enviar um timbre para A, B ou C; renomear, duplicar, apagar.
4. **Modo palco**: escolher uma música e tocar em botões grandes com os timbres dela; cada toque grava o timbre no slot escolhido (padrão: A), para o caso de uso "pedal sempre no A, troca pelo celular".
5. **Repertório**: lista de músicas, cada uma com timbres ordenados e um mapeamento opcional para A/B/C ("carregar música" grava os três de uma vez).
6. **Backup**: exportar/importar JSON (presets do pedal + biblioteca + repertório), compatível com o formato do protótipo (`bytes`: 48 números; `biblioteca`: [{name, bytes}]).
7. **Importar da comunidade** (https://cube-baby-presets.vercel.app): cada preset do site mostra os mesmos 10 campos e na mesma escala dos bytes do pedal (ex.: preset 901 "Drive bem worship": volume 41, ir cab 1, reverb 6, mix 30, fb 84, time 22, mod 7, tone 6, gain 7, type 4). Fluxo: o usuário abre o preset no navegador → "Compartilhar" → Cube Baby Presets (intent `ACTION_SEND` com a URL `/presets/{id}`), ou cola o link no app. O app baixa os valores, mostra a prévia, e oferece "Testar no A" (grava no slot escolhido guardando o timbre anterior para desfazer) e "Salvar na biblioteca". Também ter uma tela de busca/lista dentro do app se houver um endpoint de dados utilizável.
   - O site não tem API documentada (`/api/presets/{id}` responde erro). Antes de implementar, inspecionar como a página carrega os dados (provavelmente um backend com chave pública, ex. Supabase) e usar a mesma fonte, só leitura, sob demanda (sem varrer o site). Se não houver endpoint estável, extrair os valores do HTML renderizado. Isolar isso num módulo `ComunidadeRepository`, porque o site pode mudar.
   - O site não informa as chaves de efeito (bytes 10–12): ao importar, manter as do timbre atual ou ligar as três (perguntar na primeira vez).
   - Validar faixas antes de gravar; recusar presets de outros modelos (Cube Baby Bass/AC).
   - Mostrar autor e link do preset original na biblioteca (crédito).
8. **Nomes editáveis** dos 9 amps e 8 caixas (padrão "Amp 1…9", "Cab 1…8"), porque os nomes reais ainda não foram confirmados.

## Fora do escopo
Carregar IR, atualizar firmware, trocar o preset ativo (o pedal não permite pelo USB), efeitos processados no celular (latência inviável).

## Build e releases
- Repositório no GitHub com **GitHub Actions**: a cada tag `v*` gerar APK release **assinado** e publicar em Releases.
- Keystore e senhas ficam em **GitHub Secrets** (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`). Nunca commitar keystore. Sempre a mesma chave, para atualizar o app sem desinstalar.
- `versionCode`/`versionName` derivados da tag.
- Nunca commitar arquivos do CubeSuite (exe, dll, bin de firmware).
