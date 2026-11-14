# Plano — Web Push 3/3: animais e eventos sociais

## Escopo

Adicionar avisos opcionais para animal pronto, furto bem-sucedido e praga aplicada, usando o vínculo por dispositivo e a fila persistente existentes. A etapa não altera economia, regras de furto, proteção, XP ou snapshots.

## Contratos

- `animals`, `thefts` e `pests` exigem opt-in explícito independente de `plants`, com marcadores `animalsConfirmed`, `theftsConfirmed` e `pestsConfirmed`.
- Inscrições antigas e preferências ausentes permanecem desativadas.
- Mensagens não incluem saldo, identificadores de outros jogadores, cultura, quantidade econômica ou endpoint.
- Cada proprietário recebe no máximo um lote por minuto compartilhado entre plantas, animais e eventos sociais; cada dispositivo recebe uma tentativa por lote.
- Furto e praga são registrados na mesma transação da ação bem-sucedida; bloqueios, falhas e replays não criam eventos.
- Animal pronto usa agendamento temporal e revalidação do estado, inclusive após cuidador automático recolher o produto.
- O servidor valida proprietário, vínculo, revisão e evento antes de reservar; replay não cria nova entrega.
- Logout e troca de conta suprimem mensagens pendentes da conta anterior.

## Implementação em ordem

1. Generalizar a fila privada para tipos de evento, cursores por tipo e lotes compartilhados.
2. Expor preferências independentes no servidor e em Ajustes, mantendo consentimento explícito.
3. Emitir eventos nos fluxos já transacionais de coleta, furto e pragas.
4. Adaptar o Service Worker para mensagens fixas por tipo e validação de consentimento.
5. Cobrir concorrência, reentrada, replay, troca de conta, logout offline e isolamento entre jogadores.
6. Validar a atualização do Service Worker legado, troca de conta e logout offline.
7. Executar testes Java, PostgreSQL, web, TypeScript, build e SpotBugs.

## Aceitação

- Uma ação social gera no máximo um aviso por proprietário/dispositivo/lote.
- Nenhum evento é enviado quando a preferência correspondente está ausente ou falsa.
- Um furto bloqueado ou uma praga não aplicada não gera evento.
- VAPID ausente mantém a API e o jogo operacionais sem envio.
- O evento continua privado e não concede recompensa nem executa ação no jogo.
- A etapa 2 de plantas permanece sem regressão.

## Incremento do consumidor social — 13/11/2026

Decisão: implementar somente consumo e reserva transacional neste incremento. Despacho, animais e lote compartilhado continuam separados; nenhum trabalhador consulta essas reservas automaticamente. Detalhes em [Consumidor de eventos sociais](../../web-push-eventos.md).

- [x] Cobrir com regressões baseline por categoria/dispositivo, reinício, rollback, isolamento e revalidação.
- [x] Adicionar `event_order` sob bloqueio de fazenda e tabelas privadas para baseline, cursor e reservas.
- [x] Reconciliar baseline junto de preferência, inscrição, revogação e transferência, sem alterar o snapshot.
- [x] Validar concorrência, paginação e migração, executar `test build :server:integrationTest :server:spotbugsMain :server:spotbugsTest` e revisão independente.
- [x] Próximo incremento: animais, lote compartilhado e despacho, concluídos na seção abaixo.

Evidência final: 572 testes aprovados, zero falhas/skips; 183 integrações PostgreSQL 17, incluindo 11 novas do consumidor. Build e SpotBugs main/test aprovados, zero achados. A suíte revelou replay de preferência `false` retornando 409; corrigido distinguindo confirmação registrada de consentimento ativo. Fixture legada agora remove a tabela filha vazia antes de recriar `push_events`. Revisão independente somente leitura, com modelo herdado sem identificação adicional no resultado, não encontrou problemas confirmados; a correção de replay também foi revisada. Nenhum commit, push, deploy ou envio real.

## Conclusão autorizada da etapa 3/3

Decisão: reutilizar `crop_push_jobs.last_batch_at` como limite compartilhado e `crop_push_deliveries` como reserva de transporte, com tipos aditivos. Evitar trabalhador paralelo ou quota independente. O consumidor social mantém cursor e recibos por evento, associados atomicamente ao lote. `AnimalPushSchedule` calcula previsão/cursor no JSON privado do job; o motor existente revalida após cuidador. Payload legado permanece para categoria única; `BATCH` leva somente tipos conhecidos e binding, com uma tentativa por dispositivo. Sem dependências ou mudanças de snapshot.

- [x] Testes de animais offline, baseline, cuidador, quatro categorias juntas, limite na fronteira e concorrência.
- [x] Integrar agendamento, reserva compartilhada, ciclo de expiração/status e transporte existente.
- [x] Testar worker atualizado sobre legado, BATCH inválido, consentimento, logout offline e troca de conta.
- [x] Executar suíte Java/PostgreSQL, frontend, TypeScript/build e SpotBugs; revisar diff completo.

Publicação autorizada: enviar a branch atual e abrir PR contra a branch padrão pública, sem merge ou deploy. Revisão humana do PR permanece obrigatória para integração.

Base conferida por fetch e GitHub: `codex/online-core-compartilhado` em `207e1e4` é a branch padrão e contém o PR de plantas #81. `main` permanece antiga; usar essa referência como base introduziria centenas de arquivos alheios. A branch de trabalho atual tem somente os 11 commits da etapa 3 sobre a base, mais este incremento. Nenhum histórico do backup local foi importado.

Conclusão em 14/11/2026: 593 testes Java, incluindo 195 integrações PostgreSQL 17, mais 271 testes web; zero falhas/skips. Build, installDist, TypeScript e SpotBugs main/test aprovados, zero achados. A conferência dos callers reproduziu a allowlist HTTP antiga que recusava novas categorias com 400; o teste end-to-end agora cobre true/false e replay para animais/furtos/pragas. Corrigida a fronteira e repetida a suíte integral em seis minutos. A correção também recebeu revisão estática independente, sem achados pendentes.

Chromium 147.0.7727.15 confirmou atualização do worker antigo com duas abas abertas, lote misto de quatro categorias, consentimento estrito, logout offline e troca de conta. Notificações foram simuladas; nenhuma permissão ou entrega real. Evidências locais: `.tools/push3-release-java.log`, `.tools/push3-complete-web.log`, `.tools/push3-complete-web-build.log` e `.tools/push3-browser-evidence.json`. Agentes usaram modelo herdado, sem troca de configuração; integração e verificação final executadas pelo coordenador.

## Comentários do PR #82 — 14/11/2026

- Manter o lock global do protocolo existente e documentar a contenção e a necessidade de alteração coordenada das transferências/mutadores.
- Reter entregas terminais por 30 dias desde a reserva, limpando até 100 por tabela/consulta. Recibo social exige cursor cobrindo o evento. Preservar eventos, cursores, baselines e testes anti-replay, inclusive antes do opt-in; filtrar a persistência ou apagá-la mudaria a garantia já validada.
- Remover metadados de ação das novas gravações, preservando a coluna legada sem migração destrutiva.
- Documentar descarte integral do lote após revogação e o limite atual `max=1` do cursor animal, sem adicionar suporte especulativo a vários animais do mesmo tipo.
- Remover métodos usados só por testes e migrar os testes para `reserveBatch`/`CropPushStore.current`.
- Manter a migração compatível e suas regressões de preservação de dados.
- Usar o mesmo conjunto filtrado de eventos para os tipos e o vínculo de `batch_id`.
- Reutilizar uma instância de `SocialPushStore` no agendamento.

Aceitação: limpeza limitada e sem reabertura de pedidos após reinício, cursor atrasado/ausente preservado, resposta tardia inofensiva, revisão antiga sem associação ao lote novo e metadados de push vazios numa ação social real. Comando final: `test build :server:integrationTest :server:spotbugsMain :server:spotbugsTest :server:installDist`. Não alterar frontend, chaves, configuração de produção ou realizar merge/deploy.

Validação: regressões de associação, retenção e metadados falharam antes das correções. Suíte final aprovada em 6 min 4 s: 599 testes Java, incluindo 201 integrações PostgreSQL 17, sem falhas/skips; build, installDist e SpotBugs aprovados, zero achados. Revisão independente somente leitura não encontrou problemas pendentes; apontamentos de documentação foram incorporados. Modelo herdado, sem alteração de configuração. Logs locais: `.tools/push3-review-match-red.log`, `.tools/push3-review-retention-red.log`, `.tools/push3-review-focused.log` e `.tools/push3-review-final.log`.
