# Integração nativa e avaliação de 2026

## Alternativas consultadas

Consulta realizada em 9 de novembro de 2026, diretamente nos READMEs e código publicados:

- [Instaloader](https://github.com/instaloader/instaloader): biblioteca Python de leitura/download,
  com sessões e login opcional conforme o conteúdo. Não é uma interface Android nem um backend anônimo garantido.
- [instagrapi](https://github.com/subzeroid/instagrapi): biblioteca Python para APIs web e mobile
  não oficiais, sessões, desafios e diversas operações. Seu README destaca a fragilidade de APIs
  privadas; trazê-la ao Android exigiria runtime Python ou servidor intermediário.
- Barinsta neste checkout: 314 arquivos Java e 238 Kotlin no aplicativo, SDK 30 e dependências de 2021,
  incluindo três pacotes JitPack cujo acesso foi bloqueado pelo proxy de nuvem.
- Versão web oficial: alternativa sem manter endpoints privados, descartada por preferência do usuário
  por interface nativa. WebView é usado exclusivamente para login/verificação.

A consulta não é um levantamento exaustivo de todos os clientes existentes. O ambiente bloqueou
Instagram e a API REST do GitHub com HTTP 403; os READMEs públicos em raw.githubusercontent.com foram acessíveis.
Não foi possível confirmar funcionamento ao vivo ou cadência de manutenção dos projetos pela API do GitHub.

## Escolha e limites

O código ativo é Kotlin, sem dependências dos antigos serviços Java. As telas usam Compose e
Material 3 Expressive. O código histórico fica fora da compilação para permitir uma migração
incremental consciente, sem apresentar recursos antigos como se já tivessem sido portados.

A camada de rede consulta endpoints web v1 em www.instagram.com, sem assinatura mobile forjada:

- `users/web_profile_info/?username=…`
- `feed/user/{id}/?count=12&max_id=…`
- `feed/timeline/?count=12&max_id=…` (exige sessão e requer validação específica)
- `media/{id}/comments/`

São contratos não oficiais e podem deixar de aceitar GET, cookies web ou os cabeçalhos atuais.
O aplicativo expõe erros em vez de fazer scraping de HTML ou contornar desafios. O parser suporta
payloads v1 e mídia GraphQL, mas isso não significa que consultas GraphQL estejam implementadas.
Não existe backend externo, proxy de conteúdo ou serviço pago na arquitetura.

A ausência de conta é tentada ao abrir um perfil. Se o Instagram a rejeitar, a interface informa
que login pode ser necessário. O feed exige sessão antes de fazer uma requisição.

## Segurança e privacidade

Cookies usados somente no host da API; redirecionamentos não são seguidos. Cliente de imagens
separado, sem os cookies da conta. Cookies e CSRF nunca são registrados no log. API sem cache HTTP
em disco, sem repetição automática e sem consultas periódicas. O usuário solicita a próxima página.
WebView sem bridge JavaScript, acesso a arquivos, conteúdo misto ou permissões de captura;
navegação de login restrita a HTTPS instagram.com e www.instagram.com. Erros TLS são cancelados.
A sessão não é exportada nem importada do Barinsta. Captura de tela do app fica bloqueada.
A sessão privada do WebView não constitui uma promessa de criptografia adicional ao sandbox Android.

## Validação que exige dispositivo e conta

1. Instalar APK em Android 8 ou superior com Android System WebView atualizado.
2. Abrir um perfil público sem conta; verificar resposta nativa ou erro claro de login.
3. Entrar no domínio oficial e concluir eventual 2FA/checkpoint; voltar pelo botão Concluir.
4. Repetir perfil, feed e próxima página. Confirmar autores, mídia, carrosséis e ausência de duplicatas.
5. Abrir publicação e carregar comentários; tocar no vídeo para iniciar reprodução.
6. Salvar favorito, fechar o app e conferir persistência; confirmar que não há requisições periódicas.
7. Apagar sessão, reabrir o app e confirmar login ausente e favoritos mantidos.
8. Testar conta privada não seguida, conta expirada, offline e limite de requisições.
9. Verificar legibilidade com fontes grandes, TalkBack, rotação, tema escuro e cores dinâmicas.

Nenhuma credencial deve ser enviada pelo chat ou incorporada em testes. Testes simulados cobrem
contratos e proteção de sessão; não substituem esta validação.

## Evidências desta máquina

- Compilação debug e release concluídas com o código Kotlin atual.
- 16 testes de contrato passaram, zero falhas, erros ou testes ignorados.
- Lint debug sem erros; avisos incluem versões mais novas de dependências, habilitação
  de JavaScript necessária ao login e sugestões de extensões Kotlin. A combinação de
  Compose/Material selecionada foi compilada com SDK 36; a RC mais nova consultada exigia SDK 37.
- Manifesto do APK: INTERNET e permissão interna de receiver não exportado gerada pelo AndroidX.
- APK otimizado de aproximadamente 1,6 MB; `barmural-preview.apk` tem assinatura de
  desenvolvimento, verificada com apksigner (v2/v3), e alinhamento de 16 KB conferido com zipalign.
- O APK de release original permanece sem assinatura de distribuição. Nenhuma versão foi publicada.
- Instagram ao vivo não validado: a rede de nuvem retornou 403 do proxy para www.instagram.com.
  Liberar instagram.com e www.instagram.com nas configurações de rede é pré-requisito para
  continuar a validação web/API nesta máquina; mídia poderá precisar dos hosts CDN efetivamente retornados.

### Tentativa de execução Android

Foi iniciado um emulador Android 15 (API 35) sem KVM e uma imagem API 28 foi preparada
como alternativa. O API 35 chegou a disponibilizar ADB e serviços package/activity, mas
permaneceu na primeira inicialização, em dexopt de pacotes do sistema. A instalação
inicial encontrou `Can't find service: package`; depois o sistema registrou
`Too early to start/bind service ... Phase=200` para o instalador incremental.
A tentativa de UIAutomator antes do serviço de acessibilidade falhou dentro da ferramenta,
sem executar o aplicativo. Portanto, abertura e navegação do APK não foram confirmadas.
A imagem API 28 foi encerrada para liberar recursos. Isso limita a validação de runtime;
os resultados de build, testes de contrato e lint são independentes desse bloqueio.

A tentativa final sem instalador incremental também falhou no Android do emulador:
`StorageManagerService.allocateBytes` encontrou `PackageManagerInternal` nulo durante
o bootstrap, antes de instalar o app. O emulador foi encerrado; não houve um teste
de execução bem-sucedido.
