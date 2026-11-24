# Cube Baby Presets

App Android para editar e organizar os presets do pedal **M-Vave/Cuvave Cube Baby** pelo cabo USB.

- Edita os presets A, B e C (amp, gain, tone, IR cab, volume, mod, delay, reverb e as 3 chaves de efeito).
- Biblioteca ilimitada de timbres com nome, repertório por música e modo palco (pedal fixo no A, troca de timbre pelo celular).
- Backup e restauração em JSON.

Projeto independente, sem relação com a M-Vave/Cuvave/Sinco. Protocolo documentado em [`docs/PROTOCOLO.md`](docs/PROTOCOLO.md).

## Instalação
Baixe o APK mais recente em **Releases** e instale no Android. Conecte o pedal com cabo USB-C (OTG).

## Limitações
- Não troca o preset ativo do pedal (o aparelho não permite pelo USB); use as chaves do pedal ou o modo palco.
- Não carrega IR nem atualiza firmware.
- Testado só no Cube Baby de guitarra (`SINCO-CubeBaby`).

## Gerar uma versão (release)
O APK assinado é gerado pelo GitHub Actions (workflow **Release**), de dois jeitos:
- **Pelo site:** aba **Actions → Release → Run workflow**, informe a versão (ex.: `0.2.0`) e confirme. O workflow cria a tag `v0.2.0` e publica o APK em **Releases**.
- **Por tag:** `git tag -a v0.2.0 -m "v0.2.0"` e `git push origin v0.2.0`.

A versão define o `versionName` (`0.2.0`) e o `versionCode` (`maior*10000 + menor*100 + correção`, ex.: 200). Use sempre uma versão maior que a anterior para o Android aceitar a atualização. A assinatura usa dois Secrets obrigatórios, `KEYSTORE_BASE64` (o arquivo da keystore em Base64) e `KEYSTORE_PASSWORD`. `KEY_ALIAS` e `KEY_PASSWORD` são opcionais: o alias é detectado na keystore e, em keystores PKCS12, a senha da chave é a mesma da keystore. Guarde uma cópia da keystore e da senha fora do GitHub: sem elas não dá para atualizar o app instalado.
