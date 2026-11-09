# BarMural

Cliente Android nativo em Kotlin e Jetpack Compose, com Material 3 Expressive.
Derivado do [Barinsta](https://github.com/austinhuang0131/barinsta), sob GPL-3.0.

**Estado: reescrita inicial, não uma migração completa das funcionalidades do Barinsta.**
A integração usa APIs não oficiais do Instagram. Compilar ou passar em testes simulados
não comprova que o Instagram aceita estes endpoints em uma conta real.

## Prévia v0.1.0-native

Esta versão é uma **prévia de desenvolvimento**, com interface nativa Material 3 Expressive.
O APK otimizado gerado localmente tem aproximadamente **1,6 MB**. Compilações debug e release,
**16 testes de contrato** e lint sem erros foram verificados. O uso real do Instagram ainda
não foi validado: o ambiente bloqueou o serviço, e o emulador falhou durante a instalação.

- [Releases e downloads](https://github.com/Et-Nyx/BarMural/releases).
- [Notas da prévia](docs/releases/v0.1.0-native.md).
- [Acompanhar a publicação no GitHub Actions](https://github.com/Et-Nyx/BarMural/actions/workflows/native_release.yml).

A tag `v0.1.0-native` inicia o workflow de publicação. O Release só fica disponível após
os checks passarem no GitHub Actions. O APK de prévia usa assinatura de desenvolvimento,
sem garantia de atualização entre builds; reinstalar pode apagar sessão e favoritos.
O código e os APKs são experimentais; não há confirmação de compatibilidade com uma conta real em 2026.

## O aplicativo ativo

- Interface nativa Material 3 Expressive, cores dinâmicas e tema escuro.
- Busca exata por nome de perfil; leitura de perfil, publicações, carrosséis e comentários.
- Feed de leitura com sessão, sujeito à compatibilidade do endpoint.
- Vídeos iniciados por toque e paginação manual.
- Favoritos locais de perfis, independentes da conta Instagram.
- Login e verificação no domínio oficial dentro de WebView restrito; restante do app nativo.
- Somente permissão de Internet. Sem Sentry, telemetria própria ou serviços em segundo plano.
- Sem senha armazenada pelo código nativo, exportação de cookies ou backup da sessão.

O Instagram ainda recebe IP, cookies e consultas. Isto não é anonimato perante a Meta.
Login pode ser exigido inclusive para conteúdo público. Não há contorno de conta privada,
verificação ou limite de acesso. A sessão fica no armazenamento privado do WebView;
logout remove cookies, Web Storage e cache web. Imagens nativas não têm cache de disco.

**Ainda não migrados:** Stories, DMs, publicar, curtir, seguir, salvar no Instagram,
notificações, downloads e importação de contas do Barinsta. O novo applicationId
`me.barmural` permite instalar lado a lado sem acessar a sessão antiga.

## Desenvolvimento

JDK 21, Android SDK 36, Build Tools 36.0.0. Gradle 8.13 tem checksum fixado;
AGP 8.13.2 e Kotlin 2.3.20. Material 3 1.5.0-alpha07 é usado por expor publicamente
`MaterialExpressiveTheme`, `MotionScheme.expressive` e `LoadingIndicator`.

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.
Código ativo: `app/src/kotlin/`; testes ativos: `app/src/kotlinTest/`.
Os diretórios `app/src/main`, `app/src/test` e `app/src/androidTest` preservam a
implementação histórica para consulta, mas estão excluídos dos source sets ativos.
Não se trata de uma tradução automática dos 314 arquivos Java.

Na máquina de nuvem preparada nesta conversa:

```sh
source /workspace/toolchains/env.sh
unset ANDROID_SDK_HOME
export JAVA_HOME=/workspace/toolchains/jdk-21.0.9+10
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease
```

Consulte [a avaliação técnica e o roteiro de validação](docs/NATIVE_2026.md).
Créditos e documentação histórica: [README original](docs/LEGACY_README.md).
