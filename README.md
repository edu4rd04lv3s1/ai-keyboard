# AI Keyboard

Teclado de IA para Android (APK nativo, Kotlin + Jetpack Compose) com autocorreção local em PT-BR e ações de IA via **Groq** (Llama 3.3 70B, com modo rápido para WhatsApp), com fallback opcional para Gemini, para corrigir, reescrever e traduzir texto diretamente em qualquer campo de entrada do sistema.

## Recursos

- **Teclado QWERTY** funcional (português + inglês) declarado como IME (`InputMethodService`), desenhado em `Canvas` para latência mínima.
- **Digitação instantânea e multitoque**: o caractere é inserido no instante do toque (ACTION_DOWN), não ao soltar o dedo; dois polegares podem digitar em paralelo sem perder teclas.
- **Digitação por gesto (swipe)**: deslize o dedo pelas letras e o teclado decodifica a palavra usando o léxico PT-BR; as demais hipóteses ficam tocáveis na barra de sugestões. Só vira swipe com 3+ teclas, então um toque com leve deslize nunca é confundido com gesto.
- **Feedback ao digitar**: vibração tátil (`HapticFeedbackConstants.KEYBOARD_TAP`), *key-preview* (a letra ampliada que sobe acima do dedo) e som de tecla opcional — tudo configurável.
- **Tema do teclado** claro, escuro ou seguindo o sistema, resolvido em tempo real.
- **Números e acentos por toque-longo**: a fileira superior expõe `1–0` (com indicação no canto da tecla) junto dos acentos (ç, ã, ó…).
- **Modos `ABC / ?123` e emoji**, auto-capitalização e atalho de dois espaços para ponto final.
- **Autocorreção local PT-BR** ao fechar a palavra, com nível selecionável (Off / Leve / Média / Forte / Máxima) e *auto-undo*: um backspace logo após a correção a desfaz e registra o par como rejeitado.
- **Correção que não atrapalha**: gírias e abreviações (`vc`, `pra`, `pq`, `blz`, `vlw`…), risadas (`kkk`, `rsrs`, `haha`), alongamentos enfáticos (`siiim`), marcas em CamelCase (`iPhone`, `WhatsApp`) e tokens com dígito nunca são "corrigidos". É o que torna o nível agressivo confiável.
- **Consciente do campo**: senha, e-mail, URL e campos numéricos desligam automaticamente autocorreção, sugestões e auto-maiúscula.
- **Caminho de digitação sem IPC**: a palavra em composição e a posição do cursor são espelhadas localmente e sincronizadas via `onUpdateSelection`, eliminando os round-trips de processo (`getSelectedText`/`getTextBeforeCursor`) que travavam cada espaço/backspace.
- **Barra de IA rolável** acima do teclado com ações manuais: Corrigir, WhatsApp, Reescrever, Profissional, Formal, Simpático, Mais curto, Expandir, Resumo, Emojify e tradução EN/PT-BR (via **Groq**, com fallback **Gemini**).
- **Ditado por voz** (🎤) via `SpeechRecognizer` nativo (PT-BR, ao vivo).
- **Sugestões ricas** com correção local tocável, próximas palavras, dicionário pessoal, aprendizado local por frequência de uso e emoji contextual — em barra de altura fixa (as teclas nunca "pulam").
- **Transformações de IA somente sob comando**: nada é enviado à rede enquanto você digita; a IA só roda quando você toca numa ação.
- **Acentos via long-press**, substituição de texto in-place via `InputConnection` (funciona em qualquer app) e **API key criptografada** com `EncryptedSharedPreferences`.
- **Material 3** com tema claro/escuro na tela de configuração.

## Stack

| Camada                  | Tecnologia                                      |
|-------------------------|-------------------------------------------------|
| Linguagem               | Kotlin 2.0.21                                   |
| UI configuração         | Jetpack Compose + Material 3                    |
| UI teclado              | View clássica (`LinearLayout` programático)     |
| Build                   | Gradle 8.x + AGP 8.7.3                          |
| HTTP                    | OkHttp 4 + kotlinx.serialization                |
| Async                   | Kotlin Coroutines                               |
| IA                      | Groq API (`llama-3.3-70b-versatile`; WhatsApp usa `llama-3.1-8b-instant`) + fallback Gemini |
| Min SDK / Target SDK    | 26 (Android 8.0) / 35 (Android 15)              |

## Estrutura

```
.
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/aikeyboard/app/
│       │   ├── KeyboardApp.kt           # Application + DI manual
│       │   ├── MainActivity.kt          # Tela de setup (Compose)
│       │   ├── ai/
│       │   │   ├── GroqClient.kt        # Cliente HTTP Groq
│       │   │   ├── GeminiClient.kt      # Fallback opcional
│       │   │   └── TextCorrector.kt     # Estilos de transformação
│       │   ├── data/
│       │   │   ├── ApiKeyStore.kt       # API key criptografada
│       │   │   └── PersonalizationStore.kt # Aprendizado local do usuário
│       │   └── ime/
│       │       ├── AIKeyboardService.kt # InputMethodService
│       │       ├── KeyboardView.kt      # View do teclado
│       │       ├── PortugueseLexicon.kt # Índices do léxico local PT-BR
│       │       └── TypingEngine.kt      # Motor de sugestões locais
│       └── res/                          # Layouts, cores, strings, ícone
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── gradle/libs.versions.toml             # Version catalog
```

## Setup (passo a passo)

### 1. Pré-requisitos

- **Android Studio** já instalado em `/Applications/Android Studio.app` (via Homebrew).
- macOS com pelo menos 8 GB livres (Android SDK ocupa ~5 GB após download).

### 2. Configurar o Android SDK (primeira execução)

1. Abrir o Android Studio:
   ```bash
   open -a "Android Studio"
   ```
2. Na primeira execução, o wizard pede para baixar:
   - **Android SDK Platform 34** (obrigatório)
   - **Android SDK Build-Tools 34.x**
   - **Android Emulator** (opcional, mas útil)
3. Deixar instalar (~15 min na primeira vez).

### 3. Abrir o projeto

1. **File → Open** → selecionar a pasta `ai-keyboard` (a raiz do projeto).
2. Android Studio detecta o `build.gradle.kts` e faz **Gradle Sync** automaticamente.
3. Se aparecer pedido para gerar `gradle wrapper`, aceitar.
4. Aguardar todas as dependências baixarem (~5 min na primeira vez).

### 4. Obter API key da Groq (grátis)

1. Acessar [console.groq.com](https://console.groq.com).
2. Criar conta (Google login funciona).
3. Em **API Keys** → **Create API Key**, copiar a string `gsk_...`.

### 5. Build e instalação

#### Opção A: rodar em emulador

1. **Tools → Device Manager → Create Device** → escolher Pixel 7 (ou similar) → API 34.
2. Iniciar o emulador.
3. Clicar no botão verde **▶ Run 'app'** na toolbar.

#### Opção B: rodar em celular físico

1. No celular, ativar **Modo desenvolvedor** (tocar 7× em "Número da versão" em Sobre).
2. Ativar **Depuração USB** nas opções de desenvolvedor.
3. Conectar via USB e aceitar o prompt de autorização.
4. Selecionar o dispositivo na toolbar do Android Studio e **▶ Run**.

#### Opção C: gerar APK para distribuir

```bash
./gradlew assembleRelease
```

APK gerado em `app/build/outputs/apk/release/app-release-unsigned.apk`. Para instalar você precisa assinar o APK — veja [Assinatura](#assinatura-para-distribuição).

#### Opção D: gerar APK otimizado para testar desempenho

```bash
./gradlew assembleOptimizedDebug
```

Esse APK usa otimizações de release, mas é assinado com a chave debug para poder ser instalado rapidamente em testes locais.

### 6. Configurar o teclado no Android

Depois de instalado:

1. Abrir o app **AI Keyboard**.
2. **Passo 1** — tocar em "Abrir configurações de teclado" e habilitar o AI Keyboard.
3. **Passo 2** — tocar em "Selecionar teclado" e escolher o AI Keyboard como atual.
4. **Passo 3** — colar a API key da Groq e tocar em "Salvar chave".
5. Pronto: abra qualquer app (WhatsApp, Notes, Chrome…) e digite. Toque em **Corrigir com IA** para corrigir.

## Como funciona

```
┌─────────────────────────────────────────────────────────┐
│  Usuário digita ou seleciona texto em qualquer app      │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  AIKeyboardService (InputMethodService)                 │
│  ├─ Lê texto via InputConnection                        │
│  ├─ Aplica system prompt (Corrigir/Reescrever/Formal)   │
│  └─ Envia para GroqClient                               │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  Groq API (api.groq.com)                                │
│  └─ llama-3.3-70b-versatile retorna texto corrigido     │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│  AIKeyboardService substitui o texto no campo via       │
│  InputConnection.beginBatchEdit + commitText            │
└─────────────────────────────────────────────────────────┘
```

## Privacidade

- A API key fica **criptografada com AES-256-GCM** via `EncryptedSharedPreferences` (Tink/Keystore).
- Nenhum texto é enviado a lugar nenhum **exceto** quando o usuário invoca manualmente uma ação de IA. Nesses casos, o texto vai direto para o provedor configurado via HTTPS com a chave dele.
- Não há analytics, telemetria ou backup do que é digitado.

## Assinatura para distribuição

Para distribuir o APK fora do Play Store:

```bash
# 1. Gerar keystore (uma vez só, guardar com cuidado)
keytool -genkey -v -keystore release.keystore \
  -alias aikeyboard -keyalg RSA -keysize 2048 -validity 10000

# 2. Adicionar em ~/.gradle/gradle.properties:
#    RELEASE_STORE_FILE=/caminho/para/release.keystore
#    RELEASE_STORE_PASSWORD=...
#    RELEASE_KEY_ALIAS=aikeyboard
#    RELEASE_KEY_PASSWORD=...

# 3. Build assinado
./gradlew assembleRelease
```

## Próximos passos (roadmap)

- [x] Tema claro / escuro / seguir o sistema
- [x] Feedback tátil + key-preview + som ao digitar
- [x] Digitação por gesto (swipe)
- [x] Números por toque-longo na fileira superior
- [x] Caminho de digitação sem IPC (espelho de cursor + `onUpdateSelection`)
- [x] Correção consciente do tipo de campo e à prova de gírias/risadas
- [x] Atalho de dois espaços para ponto final
- [x] Digitação instantânea (caractere no toque/ACTION_DOWN, não ao soltar)
- [x] Multitoque (digitar veloz com dois polegares sem perder teclas)
- [x] Gesto à prova de falso-positivo (só vira swipe com 3+ teclas)
- [ ] Decodificação de swipe com pontuação geométrica (curvatura/velocidade)
- [ ] Ações de IA customizáveis (criar prompts próprios)
- [ ] Testes instrumentados do IME

## Licença

MIT
