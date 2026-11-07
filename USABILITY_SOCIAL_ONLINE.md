# Usabilidade e interação social online

Base sincronizada: `70255a5`, branch `codex/online-core-compartilhado`. Implementação: `codex/usabilidade-social-online`.

## Comportamento entregue

### Compra de terreno

Clicar em terreno bloqueado abre confirmação no desktop e na web. O painel mostra coordenadas, nível mínimo, preço real, moeda e saldo. Cancelar, X e Escape não debitam moedas. O cliente web bloqueia confirmação repetida e reutiliza os mecanismos existentes para pedidos com resultado desconhecido. O desktop fecha a intenção antes de executar a compra. O servidor continua verificando nível, sequência e saldo na confirmação; o cliente não determina preço nem moeda de débito.

### Avatar e perfil público

O snapshot opcional `avatar` contém somente `id` e `photoUrl`. Os seis IDs clássicos continuam disponíveis; Google exige URL HTTPS no host exato `lh3.googleusercontent.com`, sem credenciais, porta explícita, query ou fragmento. IDs desconhecidos, campos extras e coerções de tipos em saves são recusados. Não armazenamos tokens, e-mail ou metadados privados.

SET_AVATAR salva a escolha da conta autenticada. IMPORT_AVATAR importa uma preferência válida do navegador somente se a escolha ainda estiver ausente no servidor. A escolha explícita, inclusive o avatar clássico, prevalece sobre novas importações. Ambos usam revisão, recibos idempotentes e a transação existente da própria fazenda; não são ações de visita.

Não é necessário copiar a imagem Google para outro armazenamento. A URL permitida é suficiente para a apresentação; sua disponibilidade futura não é garantida. A imagem tem fallback clássico e `no-referrer` na carteira, Ajustes e seletor. Se deixar de funcionar, o usuário pode escolher outro avatar; sua preferência não é sobrescrita no login.

A visita autorizada projeta o avatar, nome, nível, XP e saldos do proprietário. Estoques, notificações, recibos, missões e validade VIP permanecem privados. Os controles de avatar aparecem somente na própria conta. O desktop local não possui identidade Google nem uma tela nova de login; o campo permanece compatível com seu save.

### Fertilizantes

O formatter de efeitos mostra 1 hora, 2 horas e 30 minutos e 5 horas e 30 minutos, com singular/plural. Loja e bandeja usam a mesma função em cada cliente. Os valores `reductionSeconds`, preços e efeitos permanecem intactos. Os testes web utilizam o catálogo real.

### Descarte de avisos

O X individual aparece somente para entradas lidas. Limpar lidas preserva as não lidas; Marcar todas como lidas continua disponível. A lista web mantém rolagem e restauração de foco, e o desktop mantém páginas de cinco entradas.

DISMISS_NOTIFICATION e DISMISS_READ_NOTIFICATIONS só operam na própria conta. O descarte remove a entrada, nunca os cursores `observed`. A semente e o recibo permanente de Natal não são removidos. Repetir pedidos, carregar a fazenda, fazer login ou restaurar o snapshot não recria um evento já observado.

### Pragas

SPREAD_INSECTS e SPREAD_WEEDS usam o timestamp histórico do último sucesso mais 12 horas, independentemente por tipo e globalmente entre fazendas. Nenhum timestamp é apagado ou reiniciado. As verificações de alvo, amizade, proteção, crescimento e repetição são mantidas. Furto continua em 24 horas; exclusividade do dono continua em cinco minutos por temporada.

## Compatibilidade e implantação futura

- Não há nova tabela, coluna, bucket nem serviço. O avatar usa o JSONB já persistido por proprietário. Não foram alterados dados de produção.
- Formato local v20 e caminho `save/game-rules-v4.json` preservados. Avatar ausente significa preferência ainda não escolhida.
- Novo ruleset: `game-rules-2026-11-usability-v1`. Os quatro anteriores, incluindo `economy-2026-11-mini-bis-v1`, continuam aceitos. Ele impede escritores antigos de descartar a nova preferência silenciosamente.
- Antes de publicar, fazer backup verificável e publicar servidor/clientes compatíveis. Não misturar escritores antigos e novos. Rollback requer binário que preserve o avatar e os cursores ou restauração coordenada do backup.
- Catálogos de plantas V1, animais V2, Mini-Bis, terrenos, progressão, VIP, tarefas e arquivos de mídia não foram modificados.

## Evidências de teste

Com JDK 21 e PostgreSQL 17 descartável, somente em `127.0.0.1:55439/colheita_test`:

```powershell
.\gradlew.bat build :server:integrationTest spotbugsMain spotbugsTest :core:spotbugsMain :core:spotbugsTest :server:spotbugsMain :server:spotbugsTest visualReview :server:installDist
```

Aprovado em 4 min 44 s. Foram verificados 298 testes raiz, um core, 40 unitários do servidor, quatro de capacidade e a suíte PostgreSQL completa. Os seis relatórios SpotBugs não apresentaram achados. O harness produziu 50 cenas, sem carregar o save do jogador.

Um teste adicional de compra confirmada verificou saldo insuficiente com nível liberado, preço/moeda autoritativos e replay concorrente que debita uma única vez. Depois dessa adição, a suíte específica `UsabilityIntegrationTest` passou com sete casos e o SpotBugs dos testes do servidor foi repetido com sucesso.

As integrações também cobrem importações concorrentes, escolha explícita, rollback, reentrada, URLs negativas, privacidade em visitas, descarte de presente de Natal com recibo preservado e limpeza concorrente. Os testes sociais verificam 11h59, 12h00 e 12h01 para os dois tipos de praga, incluindo timestamps anteriores à inicialização; a suíte anterior de furto e exclusividade continua passando.

```powershell
cd web
npm test
npm run build:local
```

150 testes web, TypeScript, bundle e limite de tamanho dos assets aprovados. Incluem confirmação/cancelamento/envio duplo, perfil do proprietário visitado, importação conservadora, resposta de avatar após fechar/trocar diálogo ou sessão, política de referência, descarte de lidas e duração dos fertilizantes do catálogo.

Os testes negativos novos reproduziram as falhas antes da implementação. A revisão independente encontrou um callback de avatar que reabria Ajustes após fechar ou trocar de sessão; o guard e as regressões foram adicionados. Uma ressalva de `no-referrer` nos Ajustes também foi corrigida e testada. O SpotBugs detectou uma condição de compra que ficou inalcançável após o modal; o desvio foi removido.

Persistem apenas os avisos preexistentes de depreciação do Gradle para a versão 9 e de `stripTypeScriptTypes` experimental no Node; não impediram as verificações.

## Evidências visuais

Todas as contas e saldos abaixo são sintéticos, em ambiente local descartável. Alice mantém 500 ouro e avatar clássico; a visita a Bob mostra 200 ouro e o avatar feminino escolhido por Bob. Nenhum dado de amigos reais foi usado.

- [Confirmação web](review/usabilidade-social-online/web-plot-confirmation.png): terreno (2, 1), nível 2, 50 verdes, saldo de cinco; Escape fecha e preserva o saldo.
- [Perfil visitado web](review/usabilidade-social-online/web-visited-profile.png): avatar e saldos do proprietário, sem seletor do visitante; pragas em 1/12h.
- [Loja web](review/usabilidade-social-online/web-fertilizers.png): quatro fertilizantes com horas/minutos.
- [Avisos web](review/usabilidade-social-online/web-notifications.png): X apenas na entrada lida.
- [Confirmação desktop](review/usabilidade-social-online/49-confirmar-terreno.png).
- [Avisos desktop](review/usabilidade-social-online/50-notificacoes-lidas.png).

Não foi realizado teste em iPhone/Android físico nem login Google real nesta rodada. As imagens clássicas e o fallback são locais; a disponibilidade futura de uma foto Google continua sob responsabilidade do provedor. Não houve merge ou deploy.

## Arquivos alterados

O inventário completo desta entrega aparece abaixo. Exclusões locais preexistentes de CONTRIBUTING.md e LATENCY_REPORT.md não integram o commit.

- `docs/game-rule.md`
- `review/usabilidade-social-online/49-confirmar-terreno.png`
- `review/usabilidade-social-online/50-notificacoes-lidas.png`
- `review/usabilidade-social-online/web-fertilizers.png`
- `review/usabilidade-social-online/web-notifications.png`
- `review/usabilidade-social-online/web-plot-confirmation.png`
- `review/usabilidade-social-online/web-visited-profile.png`
- `server/src/main/java/br/com/colheitafeliz/server/Command.java`
- `server/src/main/java/br/com/colheitafeliz/server/GameHttpServer.java`
- `server/src/main/java/br/com/colheitafeliz/server/LatencyBenchmark.java`
- `server/src/main/java/br/com/colheitafeliz/server/OnlineGameService.java`
- `server/src/main/java/br/com/colheitafeliz/server/OnlineViews.java`
- `server/src/test/java/br/com/colheitafeliz/server/OnlineGameIntegrationTest.java`
- `server/src/test/java/br/com/colheitafeliz/server/UsabilityIntegrationTest.java`
- `src/main/java/br/com/colheitafeliz/domain/notification/NotificationInbox.java`
- `src/main/java/br/com/colheitafeliz/game/GameSnapshot.java`
- `src/main/java/br/com/colheitafeliz/game/GameState.java`
- `src/main/java/br/com/colheitafeliz/game/PlayerAvatar.java`
- `src/main/java/br/com/colheitafeliz/input/InputController.java`
- `src/main/java/br/com/colheitafeliz/persistence/GameSave.java`
- `src/main/java/br/com/colheitafeliz/persistence/NotificationJsonValidation.java`
- `src/main/java/br/com/colheitafeliz/render/FarmRenderer.java`
- `src/main/java/br/com/colheitafeliz/ui/FertilizerDuration.java`
- `src/main/java/br/com/colheitafeliz/ui/MarketPanel.java`
- `src/main/java/br/com/colheitafeliz/ui/NotificationPanel.java`
- `src/main/java/br/com/colheitafeliz/ui/PlotPurchasePanel.java`
- `src/main/java/br/com/colheitafeliz/ui/Toolbar.java`
- `src/test/java/br/com/colheitafeliz/game/NotificationTest.java`
- `src/test/java/br/com/colheitafeliz/persistence/AvatarPersistenceTest.java`
- `src/test/java/br/com/colheitafeliz/ui/NotificationPanelTest.java`
- `src/test/java/br/com/colheitafeliz/ui/PlotPurchasePanelTest.java`
- `src/test/java/br/com/colheitafeliz/visual/VisualReviewLauncher.java`
- `USABILITY_SOCIAL_ONLINE.md`
- `web/review/scenario.ts`
- `web/src/avatar.ts`
- `web/src/main.ts`
- `web/src/notifications.ts`
- `web/src/types.ts`
- `web/src/ui-presentation.ts`
- `web/src/ui.ts`
- `web/test/avatar.test.ts`
- `web/test/lifecycle.test.ts`
- `web/test/notifications.test.ts`
- `web/test/ui-presentation.test.ts`
