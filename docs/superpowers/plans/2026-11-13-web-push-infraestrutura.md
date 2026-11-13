# Web Push — implementação da primeira entrega

> Execução por tarefas com subagent-driven-development; um escritor por arquivo e revisão final do diff.

**Objetivo:** inscrição por dispositivo e envio de teste, sem gatilhos automáticos ou operações econômicas.

**Spec:** `docs/web-push-design.md`, aprovado com transferência atômica, supressão no logout offline e confirmação limitada ao aceite pelo serviço.

## Contrato entre componentes

- GET `/v1/push/config`: autenticado, `{enabled,publicKey,automaticEventsEnabled:false}`.
- POST `/v1/push/subscribe`: `{endpoint,keys:{p256dh,auth},deviceToken,bindingId,deviceRevision,preferences:{plants,animals,thefts,pests}}`.
- POST `/v1/push/revoke`: `{endpoint,deviceToken,deviceRevision}`. O comprovante privado de dispositivo permite revogar a vinculação anterior mesmo após entrar em outra conta, mas exige jogador ativo autenticado.
- POST `/v1/push/test`: `{endpoint,deviceToken,bindingId,deviceRevision,testId}`. Somente associação própria atual. Resultado `{status:"accepted"|"pending"|"unavailable"|"expired"}`; accepted significa apenas aceite pelo provedor. HTTP 429 limita a frequência.
- `deviceToken`: 32 bytes aleatórios base64url gerados no navegador; somente hash no banco, nunca logs. Conhecer ID/endpoint sem esse comprovante não transfere associação existente.
- `bindingId`: 32 bytes aleatórios base64url, não contém identidade. Cada ativação/troca usa nova vinculação.
- `deviceRevision`: inteiro seguro crescente, atualizado atomicamente no IndexedDB por intenção. Revisões antigas não podem restaurar inscrição depois de logout/troca. Repetição exata da mesma inscrição é idempotente.
- Payload: somente `{type:"TEST",bindingId,title:"Colheita Feliz",body:"As notificações deste dispositivo estão funcionando."}`. Worker compara bindingId com vinculação local ativa antes de exibir. URL de clique sempre própria.
- Cinco inscrições ativas por jogador; teste no máximo uma vez por minuto por conta/dispositivo, sem reenvio automático. `testId` impede repetição da mesma tentativa após resultado incerto.

## Tarefas

- [x] Persistência/serviço: tabela privada aditiva, prova de posse, revisões, associação atômica, preferências, reserva de testes e testes PostgreSQL descartável. Arquivos: novos `PushStore`/`PushService`, integração mínima em `JdbcOnlineStore`.
- [x] Navegador: módulo independente do fluxo econômico, IndexedDB compartilhado com worker, ativação por gesto, suspensão local antes de logout, reconciliação posterior, manifesto e testes. Arquivos novos Web Push e worker; integração em main/api/build.
- [x] Transporte/HTTP: avaliar biblioteca oficial publicada, licença/dependências/vulnerabilidades, validação de endpoint e SSRF, chaves opcionais, timeout e destinos restritos; rotas autenticadas sem chamar ações de fazenda.
- [x] Integração: revisar contratos, executar testes Java/PostgreSQL/web, TypeScript, builds e SpotBugs, revisar privacidade e evidência visual.
- [x] Documentar configuração e limitações; commit, branch e PR para `codex/online-core-compartilhado`, sem merge/deploy/configuração remota. PR #80 aberto.

## Decisões e limites

O servidor já usa pool JDBC e migrações aditivas no schema privado. Reutilizar esse mecanismo sem trocar Auth, SSL ou permissões remotas. A entrega começa desativada quando VAPID não estiver configurado.

Não implementar cache offline ou interceptação de fetch no worker. Os mecanismos do navegador/provedor não garantem entrega ou silêncio absoluto de avisos genéricos do próprio navegador ao suprimir um push obrigatório; não incluir dados pessoais/econômicos nem mostrar uma mensagem da vinculação antiga. Testar supressão em código e registrar a necessidade de piloto em dispositivos reais.

Comandos de aceitação: `npm --prefix web test`, `npm --prefix web run build:local`, `.\gradlew.bat :server:test :server:integrationTest :server:installDist :server:spotbugsMain :server:spotbugsTest` com PostgreSQL descartável e revisão do diff. Não usar banco de produção.
