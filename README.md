# CleanGuard

Aplicativo Android (Kotlin + Jetpack Compose + Material 3) que combina **limpador de apps**, **analisador de armazenamento**, **gerenciador de apps não utilizados** e **assistente de segurança** — usando somente APIs oficiais, sem root, sem acessibilidade e **sem permissão de Internet**.

---

## Sumário

1. [Requisitos](#1-requisitos)
2. [Estrutura do projeto](#2-estrutura-do-projeto)
3. [Arquitetura](#3-arquitetura)
4. [Telas](#4-telas)
5. [Limitações do Android e alternativas oficiais](#5-limitações-do-android-e-alternativas-oficiais)
6. [Como gerar o APK (passo a passo)](#6-como-gerar-o-apk-passo-a-passo)
7. [Testes e modo demonstração](#7-testes-e-modo-demonstração)
8. [Publicação na Google Play](#8-publicação-na-google-play)
9. [Versões e atualização de dependências](#9-versões-e-atualização-de-dependências)
10. [Gerar o APK sem Android Studio (GitHub Actions)](#10-gerar-o-apk-sem-android-studio-github-actions)

---

## 1. Requisitos

| Item | Versão |
|---|---|
| Android Studio | 2025.1 ou mais recente (usa o JDK embutido) |
| JDK | 17 ou superior (o do Android Studio serve) |
| Android SDK Platform | 36 (o Android Studio oferece instalar no primeiro sync) |
| Gradle | 8.14.3 (via wrapper — baixado automaticamente) |
| Android Gradle Plugin | 8.11.1 |
| Kotlin | 2.2.0 (plugin Compose do Kotlin) |
| minSdk / targetSdk | 26 (Android 8.0) / 36 |

`minSdk 26` cobre praticamente todos os aparelhos em uso e é a versão em que surgem `StorageStatsManager` e ícones adaptativos, usados pelo app.

## 2. Estrutura do projeto

```
CleanGuard/
├── settings.gradle.kts · build.gradle.kts · gradle.properties
├── gradle/libs.versions.toml          ← catálogo de versões (todas as dependências)
├── gradle/wrapper/                    ← Gradle Wrapper 8.14.3
├── keystore.properties.example        ← modelo para assinar o release
└── app/
    ├── build.gradle.kts · proguard-rules.pro
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── res/                   ← ícone adaptativo (vetorial), splash, temas claro/escuro
        │   └── java/com/cleanguard/app/
        │       ├── MainActivity.kt · CleanGuardApplication.kt
        │       ├── di/                ← AppContainer (injeção manual)
        │       ├── core/util/         ← Formatters, HashUtils (SHA-256), PackageManagerCompat, SystemIntents
        │       ├── domain/            ← regras puras (testáveis): uso de apps, filtros, seleção, armazenamento
        │       ├── data/
        │       │   ├── apps/          ← apps instalados, dados de uso, tamanho, origem da instalação
        │       │   ├── storage/       ← MediaStore, pastas (SAF), exclusão com confirmação, análise
        │       │   ├── settings/      ← preferências locais
        │       │   └── session/       ← resultados em memória
        │       ├── security/          ← Security Scan: perfil, assinatura, SHA-256 do APK, classificador
        │       │   ├── db/            ← interface MalwareDatabase + banco local de DEMONSTRAÇÃO
        │       │   └── model/
        │       ├── demo/              ← dados fictícios do modo demonstração
        │       └── ui/                ← telas Compose, ViewModels, tema, navegação, componentes
        └── test/java/com/cleanguard/app/   ← testes unitários (JVM)
```

## 3. Arquitetura

- **MVVM + fluxo unidirecional**: cada tela tem um `ViewModel` que expõe `StateFlow<UiState>`; a UI coleta com `collectAsStateWithLifecycle()`.
- **Camada de domínio pura** (`domain/`, `security/RiskClassifier`, `security/db`, `core/util/HashUtils`): sem dependência do Android, coberta por testes na JVM.
- **Camada de dados** usa somente APIs públicas: `PackageManager`, `UsageStatsManager`, `StorageStatsManager`, `MediaStore`, `DocumentsContract`, `AccessibilityManager`, `DevicePolicyManager`.
- **Injeção manual** via `AppContainer` (sem bibliotecas extras de DI).
- **Banco de assinaturas plugável**: `MalwareDatabase` (interface) → `LocalDemoMalwareDatabase` (somente testes) → `ConsentGatedMalwareDatabase`, que só consultaria um serviço remoto (`RemoteThreatIntelClient`) com consentimento explícito. Nenhum cliente remoto vem incluído.

### Como a classificação de risco funciona

Cada indicador soma pontos e gera um **motivo explicado** ao usuário. Exemplos:

| Indicador | Pontos |
|---|---|
| Instalado fora de loja oficial / origem desconhecida | +2 |
| 3 ou mais grupos de permissões sensíveis (SMS, contatos, microfone…) | +2 |
| Serviço de acessibilidade **ativo** / administrador do dispositivo **ativo** | +3 cada |
| Sem ícone na tela de apps | +2 |
| Certificado de depuração | +2 |
| Nome de pacote imitando o sistema (fora da loja) | +3 |
| Combinação acessibilidade + sobreposição + SMS/instalar apps | +3 |
| Hash ou certificado no banco de assinaturas | 🔴 direto |

**🟢 0–2 · 🟡 3–6 · 🔴 7+**. Apps do sistema só são sinalizados por correspondência no banco (permissões amplas são normais para eles). Nenhum texto chama um app de "vírus" — o app sempre explica que permissões sensíveis podem ser legítimas.

## 4. Telas

| # | Tela | Destaques |
|---|---|---|
| 1 | Splash Screen | API oficial `core-splashscreen` (Android 8+), com saída animada |
| 2 | Painel | Armazenamento usado/livre (anel animado), nº de apps, pouco usados, suspeitos, espaço liberável e 4 botões grandes |
| 3 | Limpeza | Lista de apps com ícone, nome, pacote, tamanho, última utilização, indicador de uso, data de instalação, sistema/usuário; filtros; seleção múltipla; desinstalação pelo fluxo oficial |
| 4 | Aplicativos não utilizados | A mesma lista com o filtro "não utilizados" (limite configurável: 15/30/60/90 dias) |
| 5 | Análise de armazenamento | Arquivos grandes, Downloads, temporários, imagens duplicadas (SHA-256), vídeos/áudios/documentos grandes, pastas escolhidas, cache |
| 6 | Verificação de segurança | Progresso em tempo real, o que é verificado, banco de assinaturas em uso |
| 7 | Resultados | "Verificação concluída" com totais, itens sinalizados com motivo, permissões, pacote, SHA-256, nível de risco, **Ver detalhes** e **Desinstalar aplicativo** |
| 8 | Detalhes do app | Uso e armazenamento, análise de risco completa, permissões (concedidas ou não), origem, certificado, SHA-256 e um **guia de remoção** passo a passo |
| 9 | Configurações | Tema claro/escuro/sistema, Material You, limite de "sem uso", SHA-256 de apps do sistema, modo demonstração |
| 10 | Privacidade e permissões | Cada permissão, por que é usada e seu estado; o que o app **não** usa; o que fica no aparelho; o que seria enviado para fora (nada) |

## 5. Limitações do Android e alternativas oficiais

| O que foi pedido | O que o Android permite | O que o CleanGuard faz |
|---|---|---|
| Desinstalar apps | Apps comuns **não** podem desinstalar outros silenciosamente | Abre o diálogo oficial (`ACTION_DELETE`) para cada app, um por vez; o usuário confirma |
| Última utilização / tamanho real | Exige "Acesso ao uso", concedido **só** pelo usuário nas Configurações | Explica e abre a tela certa; sem ela, mostra o tamanho do APK e marca o uso como "indisponível" |
| Limpar cache de outros apps | Proibido desde o Android 6 (`CLEAR_APP_CACHE` é exclusiva do sistema) | Lista os apps com mais cache e abre "Informações do app" para o usuário tocar em "Limpar cache"; limpa o cache do próprio CleanGuard; abre "Liberar espaço" do sistema |
| Ler Downloads/documentos | Android 10+: com permissões de mídia, só fotos/vídeos/áudios são visíveis | Mídia via `MediaStore`; outros arquivos via pasta escolhida pelo usuário (SAF). O seletor não permite a raiz de Download nem `Android/data` |
| Excluir arquivos | Android 10+ exige confirmação do sistema para mídia de outros apps | Confirmação do CleanGuard (nome, local, tamanho, tipo) **e** a do sistema (`MediaStore.createDeleteRequest` no 11+, `RecoverableSecurityException` no 10) |
| Antivírus completo | Sem privilégios de sistema não há varredura de memória/processos nem arquivos privados | Heurísticas com dados públicos + SHA-256 do APK base + certificado; o app deixa claro que nenhuma ferramenta detecta 100% |
| Banco de malware | Não existe base completa embutível | Banco local **de demonstração** + interface pronta para uma API legítima, com consentimento |

## 6. Como gerar o APK (passo a passo)

### 6.1 Abrir o projeto no Android Studio
1. Descompacte `CleanGuard.zip`.
2. No Android Studio: **File › Open…** e selecione a pasta `CleanGuard` (a que contém `settings.gradle.kts`).
3. Se perguntado, escolha **Trust Project**.

### 6.2 Sincronizar o Gradle
1. O sync começa sozinho. Se não começar: **File › Sync Project with Gradle Files** (ícone do elefante).
2. Na primeira vez, o Gradle 8.14.3 e as dependências são baixados (precisa de internet).
3. Se aparecer *"Android SDK Platform 36 not found"*, clique no link **Install missing SDK** ou abra **Tools › SDK Manager** e marque **Android 16 (API 36)**.

### 6.3 Corrigir eventuais problemas de dependência
| Sintoma | Solução |
|---|---|
| `Unsupported Java` / `requires Java 17` | **Settings › Build, Execution, Deployment › Build Tools › Gradle › Gradle JDK** → escolha o JDK embutido (*jbr-21*) ou outro JDK 17+ |
| `SDK location not found` | O Studio cria `local.properties` sozinho; senão, crie com `sdk.dir=/caminho/para/Android/Sdk` |
| Falha ao baixar artefatos | Verifique proxy/firewall; depois **File › Invalidate Caches › Invalidate and Restart** |
| `Could not resolve ...` após mudar versões | Volte às versões de `gradle/libs.versions.toml` ou atualize-as juntas (ver seção 9) |
| Assistente sugere **AGP 9** | Recuse por enquanto (ou veja a seção 9): o AGP 9 muda a forma de aplicar o plugin Kotlin |

### 6.4 Executar no celular
1. No celular: **Configurações › Sobre o telefone** → toque 7 vezes em **Número da versão** para liberar as opções do desenvolvedor.
2. **Configurações › Sistema › Opções do desenvolvedor** → ative **Depuração USB** (ou **Depuração por Wi-Fi**).
3. Conecte o cabo e aceite a autorização no celular.
4. No Android Studio, selecione o aparelho na barra superior e clique em **Run ▶** (ou `Shift+F10`).
5. Na primeira execução, toque em **Permitir acesso ao uso** no painel para ativar todos os recursos.

> O build de debug é instalado como `com.cleanguard.app.debug` e pode conviver com o release.

### 6.5 Gerar o APK de debug
- Menu: **Build › Generate App Bundles or APKs › Generate APKs** (em versões mais antigas do Studio: **Build › Build Bundle(s) / APK(s) › Build APK(s)**).
- Ou pelo terminal, na pasta do projeto:
  ```bash
  ./gradlew assembleDebug        # Linux/macOS
  gradlew.bat assembleDebug      # Windows
  ```

### 6.6 Gerar o APK de release (assinado)
Um APK de release precisa ser assinado para ser instalado. Duas formas:

**A) Pelo assistente do Android Studio (mais simples)**
1. **Build › Generate Signed App Bundle or APK…** → **APK** → **Next**.
2. **Create new…** para criar um keystore (guarde o arquivo e as senhas — sem eles você não consegue atualizar o app).
3. Selecione a variante **release** → **Create**.

**B) Pelo Gradle, com `keystore.properties`**
1. Crie o keystore (uma única vez):
   ```bash
   keytool -genkeypair -v -keystore cleanguard-release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias cleanguard
   ```
2. Copie `keystore.properties.example` para `keystore.properties` na raiz do projeto e preencha as senhas.
3. Rode:
   ```bash
   ./gradlew assembleRelease
   ```
O release usa R8 (minificação e remoção de recursos não usados). `keystore.properties` e `*.jks` já estão no `.gitignore`.

### 6.7 Onde o `.apk` é criado
| Build | Caminho |
|---|---|
| Debug | `app/build/outputs/apk/debug/app-debug.apk` |
| Release via Gradle **com** `keystore.properties` | `app/build/outputs/apk/release/app-release.apk` |
| Release via Gradle **sem** keystore | `app/build/outputs/apk/release/app-release-unsigned.apk` (não instalável até assinar) |
| Release via assistente (A) | `app/release/app-release.apk` (pasta escolhida no assistente) |

Após o build, o Android Studio mostra um aviso com o link **locate** que abre a pasta do APK.

## 7. Testes e modo demonstração

### Testes unitários (JVM, sem aparelho)
```bash
./gradlew testDebugUnitTest
```
Ou clique com o botão direito em `app/src/test` › **Run 'Tests in ...'**.

| Arquivo | O que cobre |
|---|---|
| `AppAnalysisTest` | Regras de "não utilizado", indicador de frequência, filtros, ordenação, busca, quem pode ser desinstalado |
| `StorageCalculationTest` | Espaço usado/livre, categorização, arquivos grandes, duplicatas por SHA-256, relatório, formatação |
| `Sha256Test` | Vetores oficiais FIPS 180-2, hash em streaming, hash do arquivo de teste EICAR |
| `RiskClassificationTest` | Níveis 🟢🟡🔴, explicações, apps do sistema, origem da instalação, banco demo |
| `SelectionTest` | Seleção múltipla de apps e de arquivos, somas, itens não removíveis, sugestão de duplicatas |

### Modo demonstração
**Configurações › Testes › Modo demonstração.** Adiciona apps e arquivos **fictícios** (marcados como DEMO):

- *Lanterna Turbo (DEMO)* — sem ícone, fora da loja, acessibilidade ativa, SMS e sobreposição → 🔴 por heurística;
- *Atualização do Banco (DEMO)* — hash presente no banco local de demonstração → 🔴 por assinatura;
- *Gravador de Voz (DEMO)* — loja alternativa + microfone, localização e contatos → 🟡;
- apps não usados há meses, vídeos grandes, temporários e 3 imagens "idênticas".

Nada disso existe no aparelho; "excluir" um item DEMO só o remove da lista. **Nenhum malware real é usado** — o banco demo contém apenas o hash do arquivo de teste EICAR (padrão inofensivo da indústria) e hashes de textos fictícios. O próprio texto EICAR não está no app, só o hash, para o APK não ser sinalizado por antivírus.

## 8. Publicação na Google Play

Algumas permissões exigem declaração no Play Console:

- **`QUERY_ALL_PACKAGES`** — declarar o uso como *antivírus / segurança do dispositivo*.
- **`READ_MEDIA_IMAGES` / `READ_MEDIA_VIDEO`** — a política de fotos e vídeos exige justificar acesso amplo (análise de arquivos grandes e duplicados).
- **`REQUEST_DELETE_PACKAGES`** — usada só para abrir o diálogo oficial de desinstalação.
- **`PACKAGE_USAGE_STATS`** — concedida pelo usuário; descreva o uso na política de privacidade.

O app não declara `INTERNET`, acessibilidade, administrador do dispositivo, `MANAGE_EXTERNAL_STORAGE` nem `REQUEST_INSTALL_PACKAGES`.

## 9. Versões e atualização de dependências

Todas as versões ficam em `gradle/libs.versions.toml`. Para atualizar com segurança, mude-as **juntas** e rode os testes:

- **AGP + Gradle**: cada AGP exige uma versão mínima do Gradle (atualize `gradle/wrapper/gradle-wrapper.properties`).
- **Kotlin**: o plugin `org.jetbrains.kotlin.plugin.compose` usa a mesma versão do Kotlin.
- **Compose**: atualize só o `composeBom`; as bibliotecas Compose seguem o BOM.
- **AGP 9.x**: traz Kotlin embutido e deixa de aceitar o plugin `org.jetbrains.kotlin.android`. Ao migrar, remova esse plugin de `build.gradle.kts` e de `app/build.gradle.kts` (o assistente de upgrade do Android Studio faz isso).

## 10. Gerar o APK sem Android Studio (GitHub Actions)

O arquivo `.github/workflows/build-apk.yml` compila o projeto nos servidores do GitHub (gratuito para repositórios públicos e com cota mensal grátis para privados).

1. Crie um repositório em github.com (**New repository**).
2. **Add file › Upload files** e arraste o conteúdo da pasta `CleanGuard` (não a pasta em si). Clique em **Commit changes**.
3. Se a pasta `.github` não tiver sido enviada (pastas com ponto às vezes ficam ocultas), crie o arquivo pelo navegador: **Add file › Create new file**, nome `.github/workflows/build-apk.yml`, cole o conteúdo e confirme.
4. Abra a aba **Actions**. A execução "Gerar APK" começa sozinha (ou clique em **Run workflow**).
5. Ao terminar com ✅, abra a execução e baixe **CleanGuard-debug-apk** em **Artifacts**. É um `.zip` com o `app-debug.apk` dentro.
