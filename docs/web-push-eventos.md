# Web Push 3/3 — animais, eventos sociais e lote compartilhado

O trabalhador existente agenda animais e plantas e consome `THEFT` e `PEST`. Todas as categorias compartilham uma reserva de transporte e um limite por proprietário de um lote a cada 60 segundos. Cada dispositivo recebe no máximo uma tentativa por lote. A implementação não altera economia, snapshot ou regras sociais. Sem VAPID, o trabalhador não inicia; cada categoria exige opt-in independente.

## Ordem, consentimento e recuperação

`push_events.event_order` é uma sequência persistente. A emissão trava a fazenda do proprietário antes de alocar a sequência; o consumidor usa o mesmo bloqueio. Isso impede que um commit atrasado fique atrás de um cursor já consumido. O horário do evento não define a ordem. Na migração, a ordem dos registros antigos é arbitrária; todos ficam antes do baseline inicial.

`social_push_cursors` guarda o último evento processado por proprietário. Cada chamada lê até 100 eventos em ordem e grava cursor e reservas na mesma transação. Eventos sem destinatário elegível também avançam o cursor. Rollback desfaz ambos; reinício não repete reservas. Eventos e reservas não alteram snapshots, revisões econômicas ou notificações internas.

`social_push_devices` guarda baseline independente por endpoint, vínculo e categoria. O opt-in autenticado cria esse baseline na mesma transação da preferência. Novo dispositivo, novo vínculo e reativação de categoria ignoram eventos anteriores. Alterar outra categoria ou revisão preserva o baseline das categorias já habilitadas. Inscrições legadas confirmadas sem baseline recebem o ponto inicial na primeira reconciliação, sem backlog histórico.

Antes de reservar, o consumidor verifica conta ativa, proprietário, inscrição ativa, vínculo e booleanos de consentimento/confirmado. A reserva captura a revisão atual do dispositivo. O trabalhador revalida esses controles antes da rede, sem manter conexão de banco durante HTTP. Reservas não são retomadas para nova tentativa após reinício. Os recibos sociais usam `batch_id` para acompanhar o estado terminal da reserva compartilhada. A expiração processa no máximo 100 reservas por tabela em uma consulta; uma resposta tardia não altera um estado terminal nem uma nova revisão de vínculo.

A ordem de bloqueios permanece conta, `push-subscriptions`, fazendas em ordem UUID e cursor. Transferências de endpoint travam ambas as fazendas em ordem antes de reconciliar as contas. Tabelas novas têm RLS e revogação para PUBLIC, anon e authenticated, seguindo o schema privado existente.

O lock global é uma escolha conservadora do protocolo atual: inscrição, transferência entre proprietários, quotas e reconciliação usam o mesmo bloqueio. O consumidor social também o adquire antes das fazendas. Trocar somente o trabalhador por um lock por proprietário misturaria protocolos e poderia inverter a ordem dos locks. A consequência atual é contenção entre contas durante avanço e persistência; os limites de dez fazendas por consulta e 100 eventos por proprietário não eliminam essa serialização. Não há conexão de banco nem esse lock durante HTTP. Uma mudança para locks por proprietário precisa tratar transferências e todos os mutadores juntos, com medição de contenção e testes de concorrência.

A migração dos formatos intermediários de `push_events` foi mantida para compatibilidade com bancos existentes e ensaios de atualização já validados. Isso não afirma que essas versões foram publicadas em produção. As regressões verificam preservação de registros, PK por autor/pedido e inicialização repetida; remover essa compatibilidade não corrige uma falha atual.

Uma futura implantação deve atualizar todos os produtores antes de ativar consumo. Versões antigas não serializam a alocação de `event_order` com o consumidor; não misturar esses produtores com consumo habilitado. Preservar eventos, baselines, cursores e reservas em backups e rollback.

## Agendamento, lote e Service Worker

`AnimalPushSchedule` reconstrói o estado de domínio para prever maturação, sem alterar a fazenda. Identidade usa tipo/aquisição, sem reiniciar a deduplicação ao alimentar. Fome sem cobertura de alimentação não possui previsão; com cuidador ativo, a previsão é uma hipótese revalidada pelo motor. Replay histórico de automação expirada agenda uma observação inicial e não gera loop após o cursor avançar. Ativação e reativação suprimem maturações anteriores ao baseline. Protetores ficam excluídos; produtores prontos e animais de crescimento adultos são elegíveis.

O catálogo atual limita cada tipo a um animal (`max=1`). Por isso, o cursor guarda uma aquisição por tipo. Aumentar esse limite exige mudar primeiro a chave do cursor para suportar aquisições simultâneas; não há suporte implícito a vários animais do mesmo tipo nesta entrega.

Gravação direta e gravação adiada por recibo reconciliam `crop_push_jobs`, incluindo o estado privado de animais em `cursors.animals`. O trabalhador avança o motor e o cuidador antes da reserva: produto já recolhido não gera aviso. A revisão econômica da fazenda não muda por esse avanço em segundo plano.

`crop_push_jobs.last_batch_at` é a quota compartilhada, sob o mesmo lock de fazenda da reserva. O trabalhador agrega todos os tipos atuais por dispositivo em `crop_push_deliveries.types`. Cursor social, recibos, observações e quota são confirmados na mesma transação. Dois trabalhadores não dividem um proprietário em lotes simultâneos. Erros individuais recuam ao menos um minuto; a consulta continua limitada a dez fazendas e não carrega todas as fazendas.

Tipo único preserva `CROP_READY`, `ANIMAL_READY`, `THEFT` ou `PEST`. Lote misto usa `BATCH` com um a quatro tipos conhecidos e distintos. O worker rejeita lote inteiro se qualquer categoria não estiver explicitamente habilitada. O corpo é fixo e genérico; nenhum metadado de ação, saldo, nome ou endpoint é incluído. `install/skipWaiting` e `activate/clients.claim` substituem o worker antigo com abas abertas. Revalidação local antes/depois de exibir preserva logout offline e troca de conta.

Se uma categoria for revogada depois da reserva, o servidor descarta o lote inteiro; uma mudança da revisão também invalida a reserva. O navegador descarta o lote se faltar qualquer consentimento. Os cursores já avançaram e os demais tipos desse lote não são reenviados. Essa perda conservadora é intencional: não filtrar nem reconstruir uma tentativa depois da reserva.

No rollback, desligar envios antes de iniciar versões anteriores e conservar os registros anti-replay. Não configurar VAPID ou aplicar migrações em produção durante testes.

## Retenção e minimização de dados

Depois de expirar reservas incertas, cada consulta remove até 100 entregas de transporte e até 100 recibos sociais com estado `accepted`, `expired` ou `unavailable`, cuja reserva tenha mais de 30 dias. A idade é contada desde `reserved_at`, não desde a resposta do provedor. A limpeza usa índices parciais e `FOR UPDATE SKIP LOCKED`; estados desconhecidos e entregas recentes ficam preservados. O recibo social só é removido se o cursor do proprietário já cobrir o `event_order` correspondente. Uma resposta tardia para reserva removida não desativa o dispositivo.

`push_events`, cursores, baselines e `push_tests` não são apagados. Os eventos continuam registrados mesmo sem consentimento: a chave `(actor, request_id)` garante deduplicação permanente, e o baseline impede backlog anterior ao opt-in. Apagar eventos ou recibos de teste sem uma janela explícita de validade permitiria reabrir pedidos antigos. Esses registros anti-replay continuam crescendo e exigem acompanhamento de volume; uma política de arquivamento precisa preservar a mesma garantia.

Novos eventos gravam `metadata={}`. Coordenada, ação e temporada não são mais construídas para o push, pois o consumidor usa apenas identidade, proprietário, tipo e ordem. A coluna legada permanece para manter a migração compatível, sem apagar metadados históricos como efeito desta correção. Os detalhes das notificações internas do jogo não mudam.

## Verificação e limites

Os testes usam PostgreSQL 17 descartável, relógio fixo e nenhum transporte real. Comandos:

```powershell
.\gradlew.bat :server:integrationTest --tests '*SocialPushIntegrationTest'
.\gradlew.bat test build :server:integrationTest :server:spotbugsMain :server:spotbugsTest
```

`TEST_DATABASE_URL` precisa apontar ao banco local descartável `colheita_test`. Não configurar VAPID, publicar ou executar migração em produção durante esses checks.

Context7 confirmou a semântica de locks transacionais do PostgreSQL 17, após selecionar a conexão do plugin. Fontes: [locks](https://www.postgresql.org/docs/17/explicit-locking.html#ADVISORY-LOCKS) e [funções de sequência](https://www.postgresql.org/docs/17/functions-sequence.html). Nenhum fonte ou dado privado foi enviado na consulta.

Em 13/11/2026, a verificação integral passou em 5 min 58 s: 322 testes domínio/desktop, um core, 62 unitários servidor, quatro de capacidade e 183 integrações PostgreSQL 17; zero falhas ou skips. SpotBugs main/test servidor: zero achados. As 11 novas integrações cobrem baseline histórico, categorias, reativação, revisão/conta, rollback, isolamento, novo dispositivo, transferência, concorrência, paginação, migração privada, legado confirmado e ordem de commits. A revisão independente somente leitura não encontrou problemas confirmados.

A rodada inicial reproduziu duas falhas: a fixture de migração recriava o pai sem remover a nova tabela filha; o replay de preferência desativada confundia confirmação registrada com consentimento ativo. Ambas foram corrigidas e a suíte integral repetida. Os 21 testes de persistência anteriores continuam aprovados. Logs locais: `.tools/push3-consumer-red.log`, `.tools/push3-regressions-green.log` e `.tools/push3-final.log`.

Essas evidências são do incremento inicial do consumidor. A conclusão integra frontend, animais e transporte simulado; os resultados completos estão registrados abaixo. O aviso de depreciação Gradle permanece e não impede o build atual. Testes não comprovam entrega real em aparelhos.

## Evidências da conclusão — 14/11/2026

A suíte final executou `test build :server:integrationTest :server:spotbugsMain :server:spotbugsTest :server:installDist` em seis minutos: 322 testes domínio/desktop, um core, 71 unitários servidor, quatro de capacidade e 195 integrações PostgreSQL 17, sem falhas ou skips. SpotBugs main/test servidor: zero achados. Frontend: 271 testes, TypeScript e build local aprovados. As novas regressões incluem cuidador, maturação offline, fome, baseline, quatro categorias no mesmo lote, fronteira de 60 segundos, trabalhadores concorrentes, dispositivos com consentimentos diferentes, conta inativa, erro de transporte, crash e resposta 410 de vínculo anterior.

O teste HTTP de preferências reproduziu a recusa das novas categorias pela allowlist da rota. A correção mantém uma única categoria booleana por pedido, proteção de confirmação, posse, vínculo e revisão; o teste cobre ativação, desativação e replay de animais, furtos e pragas. A suíte integral foi repetida após a correção. Revisão independente somente leitura não deixou achados pendentes; o coordenador verificou a integração pessoalmente.

Chromium 147.0.7727.15 validou a substituição do worker legado com duas abas abertas, sem recarregá-las. O bundle real foi servido em localhost, com estado IndexedDB real e notificações simuladas: lote de quatro categorias aceito; consentimento retirado, logout offline e troca de conta suprimiram mensagens. Não houve inscrição, permissão nativa ou envio real. A semântica de atualização foi conferida via Context7 nas referências MDN de [skipWaiting](https://developer.mozilla.org/en-US/docs/Web/API/ServiceWorkerGlobalScope/skipWaiting) e [clients.claim](https://developer.mozilla.org/en-US/docs/Web/API/Clients/claim).

Logs locais da conclusão: `.tools/push3-release-java.log`, `.tools/push3-complete-web.log`, `.tools/push3-complete-web-build.log` e `.tools/push3-browser-evidence.json`. O banco descartável foi separado de fazendas/saves reais. A publicação do PR não autoriza merge, deploy ou configurar VAPID; o piloto de entrega em aparelhos reais permanece separado.

Após os comentários do PR #82, a revisão de retenção, minimização de dados e uso do caminho real de produção passou em 6 min 4 s: 599 testes Java, incluindo 201 integrações PostgreSQL 17, sem falhas ou skips. Build, installDist e SpotBugs main/test passaram com zero achados. As regressões reproduziram a associação de recibo de revisão antiga, a ausência de limpeza e os metadados desnecessários antes das correções. Testes novos verificam a fronteira de 30 dias, limite de 100 por tabela, proteção do cursor, replay/reinício e resposta tardia após remoção. A revisão independente somente leitura não encontrou problemas pendentes. Logs locais: `.tools/push3-review-match-red.log`, `.tools/push3-review-retention-red.log`, `.tools/push3-review-focused.log` e `.tools/push3-review-final.log`. Nenhuma configuração ou entrega real foi executada.
