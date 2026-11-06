# Proteção visual e notificações V1

Branch `codex/notificacoes-e-protecao`, baseada em `e0ab0ea` de `codex/online-core-compartilhado` (inclui o balanceamento animal V2). Este trabalho não altera catálogos, preços, alimentação, progressão, regras de VIP, proteção ou furto.

## Interface

O estado **Protegida** usa `assets/classic/icons/icone-cadeado.png`: chip principal web, painel alternativo de saúde, marca no terreno Canvas e indicação no tooltip desktop. O texto permanece. A colmeia continua sendo o animal produtor e mantém sua arte própria.

O sino usa `assets/classic/icons/notificacao-icone.png` e fica imediatamente à esquerda da Loja nas duas interfaces. O badge conta somente notificações não lidas. O painel permite ler uma entrada e marcar todas como lidas, conservando o histórico. Na web a lista tem rolagem, preservada nas atualizações; cada botão tem chave de foco por ID. No desktop há cinco entradas por página, com navegação para as 100 entradas e gravação ao marcar leitura.

## Eventos

| Tipo | Gatilho | Conteúdo |
|---|---|---|
| THEFT | Transferência bem-sucedida de uma temporada | Nome do visitante, cultura, quantidade 1 e horário observado |
| CROP_READY | Primeira observação da temporada madura | Nome da cultura, terreno e temporada |
| ANIMAL_READY | Primeira observação do animal pronto | Espécie e indicação de coletar ou vender; protetores não geram esse evento |
| VIP_EXPIRED | Primeira observação após o fim de uma validade | Expiração do cartão e referência ao prazo encerrado |

Prontidão e expiração são observadas no refresh real do GameState, incluindo restauração/offline. `createdAt` é o instante real da observação; não promete reconstruir o instante histórico exato da maturidade enquanto o jogador estava ausente. Um save antigo já maduro/expirado produz a primeira notificação na observação após atualizar o jogo. São notificações internas do jogo, com o polling web existente de 30 segundos e atualização ao retornar à aba; não há worker permanente para contas offline.

O furto online usa o nome conhecido pelo servidor, não um nome enviado junto à ação. Mensagens são persistidas com o nome daquele momento. A quantidade permanece uma unidade, como na regra existente. Falha, proteção, janela de cinco minutos, cooldown de 24 horas e revisão inválida não geram evento. A mesma transação grava transferência, cooldown, recibo e histórico do dono. Rollback desfaz todos esses efeitos. Replay do UUID devolve o recibo sem notificar de novo. VisitService local aceita o nome do chamador controlado e usa “Um visitante” quando não é fornecido; isso não acrescenta visitas online ao cliente desktop.

## Modelo e persistência

`GameNotification` guarda `id`, `type`, `message`, `createdAt`, `read` e `metadata`. Mensagens/metadata têm limites de tamanho. `NotificationInbox` conserva as 100 entradas mais recentes, incluindo lidas, e cursores de eventos separados do histórico. Assim, remover uma entrada antiga pelo limite não recria o mesmo evento a cada refresh.

O cursor da cultura usa terreno e revisão da temporada. O do animal usa espécie, aquisição e âncora de crescimento pronta, estável até coleta/venda. O do VIP usa o prazo que expirou; o de furto usa terreno/revisão consumida. IDs são determinísticos e únicos dentro da caixa de cada jogador; as ações de leitura sempre são resolvidas no jogador autenticado. Cursores são limitados a um conjunto pequeno de terrenos/espécies e não crescem com o tempo de jogo.

O snapshot acrescenta `notifications: {version: 1, entries, observed}`. Saves anteriores sem o campo inicializam caixa vazia. Formato v20, caminho local e ruleset econômico permanecem iguais. Leitores de arquivo e PostgreSQL verificam o JSON antes do Gson: versão inteira exata, estrutura, campos textuais e booleano de leitura, sem coerção de string. Records validam IDs, datas, limites, duplicação e cursores. Dados inválidos são recusados; a gravação não sobrescreve um arquivo que falhou ao carregar.

Não misturar escritores antigos e novos: binários anteriores ao recurso desconhecem o campo opcional e podem descartá-lo ao salvar. Uma restauração de backup também restaura a leitura e os cursores daquele backup. Metadados não são assinatura criptográfica; a autoridade online é o banco controlado pela API.

## API e privacidade

A projeção própria oferece `notifications` (lista) e `unreadNotifications` (contagem derivada). A projeção pública de visita omite ambos. Durante visitas, o sino continua mostrando a caixa do próprio jogador.

Os comandos `READ_NOTIFICATION` com `itemId` e `READ_ALL_NOTIFICATIONS` usam a rota própria, autenticação, revisão da fazenda, locks e recibos existentes. Não registram missões nem concedem XP/moedas. Não é aceito conteúdo de notificação fornecido pelo cliente. O cliente insere as mensagens como texto, sem HTML. Não há rota para marcar notificações de outro dono.

## Arquivos responsáveis

- `domain/notification/GameNotification` e `NotificationInbox`: entradas imutáveis, cursores, retenção e leitura.
- `game/NotificationEvents`, `GameState`, `GameSnapshot`, `VisitService`: observação de prontidão/expiração, snapshot e furto.
- `persistence/NotificationJsonValidation`, `JsonSaveRepository` e `server/JdbcOnlineStore`: leitura estrita e compatibilidade.
- `server/Command`, `OnlineGameService`, `OnlineViews`: comandos próprios, nome autoritativo e projeção privada.
- `ui/NotificationPanel`, `Hud`, `input/InputController`, `render/GameAssets`: painel/badge desktop, assets e proteção visual.
- `web/src/notifications.ts`, `main.ts`, `types.ts`, `scene.ts`, `ui-panels.ts`, `index.html`, `style.css`: painel, badge, rolagem, tipos e cadeados web.
- `server/LatencyBenchmark`: propagação do novo campo na cópia do snapshot, sem mudança no benchmark econômico.

## Aceitação e verificações

Os testes verificam retenção das últimas 100, deduplicação após refresh/restauração/retenção, leitura persistida, temporada seguinte, furto com identidade, ausência de recompensas, campos inválidos sem sobrescrita, navegação desktop até a última página, sino antes da Loja, texto seguro, badge, caixa própria durante visitas e rolagem web após atualização. A integração PostgreSQL verifica evento de furto + transferência + recibo atomicamente, replay, rollback, recusa de leitura de ID alheio e leitura depois de reabrir o store.

Comandos: `./gradlew.bat build :server:integrationTest spotbugsMain spotbugsTest :core:spotbugsMain :core:spotbugsTest :server:spotbugsMain :server:spotbugsTest`, com JDK 21 e PostgreSQL 17 descartável em localhost; `npm test` e `npm run build:local` em web. Não usar banco ou save dos jogadores. Resultados finais registrados na descrição do PR.

Resultados locais: 264 testes desktop/domínio/persistência, um core, 40 unitários server, quatro de capacidade e 68 de integração PostgreSQL, sem falhas. Os 138 testes web, TypeScript e build local passaram. Os seis relatórios SpotBugs main/test dos três módulos ficaram sem achados. Após os ajustes finais, build e testes afetados foram reexecutados. `git diff --check` passou. Nenhum catálogo ou fórmula econômica aparece no diff.

`visualReview` concluiu 45 cenas sem usar save do jogador. As cenas `build/visual-review/44-notificacoes.png` e `45-protegida-cadeado.png` foram conferidas visualmente: painel, badge, ordem dos ícones e cadeado junto de Protegida. O fixture e a navegação desktop estão versionados nos testes; as capturas ficam nos artefatos locais de build.

A revisão independente somente leitura apontou perda da rolagem da lista web no refresh. O contêiner foi incluído na preservação existente e ganhou teste de regressão. Os padrões de texto, janelas, botões e persistência existentes foram reutilizados, sem nova dependência. Não houve merge, deploy ou alteração de provedor.

Limites: observação de eventos offline e entrega dependem de carregar/atualizar a fazenda; não há teste manual em um iPhone real nem ensaio de recuperação em produção. Os avisos transitórios de ações e os estados de saúde das plantas/animais continuam separados deste histórico persistente.
