# Web Push: infraestrutura e teste por dispositivo

## Entrega

O primeiro PR adiciona transporte, inscrição e teste manual em Ajustes. Não envia alertas automáticos de plantas, animais, furtos ou pragas. As opções desses eventos aparecem como “em breve”; os quatro campos de preferência já são privados e persistidos para as próximas entregas.

Sem chaves VAPID, a API inicia normalmente e a interface informa que o recurso não está configurado. Não é necessário habilitar nenhum serviço novo no Supabase, Render ou Cloudflare. A entrega não muda economia, snapshots, relógios de crescimento ou comandos pendentes.

## Fluxo e posse do dispositivo

A ativação exige clique, HTTPS ou localhost seguro, suporte às APIs e permissão do navegador. A inscrição usa `userVisibleOnly: true` e a chave pública VAPID. Não há solicitação automática de permissão no login. No iPhone/iPad, orientar instalação na Tela de Início e verificar capacidades; os testes locais não substituem validação em aparelho real.

IndexedDB compartilha com o worker a vinculação local e a fila de revogações. O comprovante `deviceToken` tem 32 bytes de aleatoriedade; somente seu SHA-256 é gravado no banco. `bindingId` é opaco e não contém nome ou ID de jogador. Revisões inteiras crescentes são produzidas atomicamente no IndexedDB. Nenhum token de autenticação é disponibilizado ao worker.

O cadastro de endpoint já existente exige o comprovante correto e revisão nova. A transferência para a conta autenticada ocorre em uma única transação sob bloqueio por ator/endpoint. Uma revisão antiga não reativa uma associação revogada; uma repetição exata da mesma revisão é idempotente. Conhecer endpoint, ID ou binding isoladamente não autoriza transferência.

No logout, a vinculação local é suspensa antes do encerramento da sessão e notificações exibidas são fechadas. A tentativa remota acontece separadamente, sem bloquear a saída. Sem internet, a fila permanece no dispositivo e é reconciliada depois de nova conexão/login, inclusive com outra conta autenticada que detenha o comprovante. Um `revoke` anterior a um cadastro atrasado cria um registro inativo que bloqueia esse cadastro.

Quando a gravação local falha, o cliente tenta desregistrar o worker e fechar as notificações como medida de segurança. A próxima entrada pode recuperar a associação antiga e reconciliar sua revogação quando o armazenamento voltar. Se tanto IndexedDB quanto o desregistro do navegador falharem, não há uma garantia adicional fornecida pela plataforma; não afirmar que esse caso extremo está resolvido. Não apagar dados do usuário automaticamente.

O worker só exibe o texto fixo de teste se o binding corresponder ao ativo no dispositivo. Também revalida depois da exibição e fecha uma notificação quando o logout concorreu com o processamento. Pushes antigos/inválidos não produzem mensagens do jogo. Alguns navegadores podem produzir avisos genéricos próprios quando um push obrigatório não exibe mensagem; não prometer silêncio absoluto do sistema operacional. O clique sempre abre a própria origem, nunca uma URL recebida no payload. Não há interceptação de fetch, cache de API ou modo offline de jogo.

## API

Todas as rotas usam autenticação e origem permitida, com validação adicional de jogador ativo:

- GET `/v1/push/config`: disponibilidade, chave pública e `automaticEventsEnabled:false`.
- POST `/v1/push/subscribe`: endpoint, chaves `p256dh`/`auth`, comprovante, binding, revisão e quatro preferências booleanas.
- POST `/v1/push/revoke`: endpoint, comprovante e revisão. Pode revogar a vinculação anterior do mesmo dispositivo mesmo após troca de conta; não funciona apenas com ID.
- POST `/v1/push/test`: associação própria atual, comprovante, binding, revisão e `testId` UUID. Uma tentativa é reservada antes da rede e a repetição não envia novamente. Se o processo interromper depois da reserva, o recibo permanece `pending`, sem reenvio automático.

`accepted` significa somente “Enviado ao serviço de notificações”. `pending`, `unavailable` e `expired` não afirmam recebimento. Respostas 404/410 desativam somente a associação/revisão ainda correspondente. Falhas não expõem corpo ou mensagem do provedor. Não há operação de compra, revisão de fazenda ou uso de `retry()`/identificador econômico.

Limites: cinco inscrições ativas e cinquenta registros totais por jogador, contando inativos. A quota total vale também para endpoints desconhecidos recebidos em revogações e para transferências, impedindo crescimento ilimitado por esse caminho. Registros existentes podem ser revogados com a quota cheia; não remover registros inativos indiscriminadamente, pois protegem contra reenvios antigos. Ao atingir cinquenta, a criação de outro endpoint exige análise administrativa futura, não exclusão automática.

Teste no máximo uma vez por minuto por conta e endpoint. O serviço reserva recibo durável e envia fora da transação, com até dois envios concorrentes; não há agendador contínuo. O adaptador limita DNS e HTTP, não segue redirecionamentos e aplica espera em memória para Retry-After em 429/503. Depois de reinício, o limite de sessenta segundos continua no banco, mas essa espera adicional do provedor em memória não é preservada. Histórico de testes é persistente nesta entrega; acompanhar seu volume antes de ampliar o público ou adicionar novos usos ao endpoint de teste.

## Persistência e implantação futura

`JdbcOnlineStore.initialize` cria duas tabelas aditivas no schema privado:

- `push_subscriptions`: endpoint único, proprietário, hash do comprovante, binding, revisão, chaves da inscrição, preferências, estado e datas.
- `push_tests`: UUID, ator, endpoint, binding/revisão, hash da requisição, status e horário da reserva.

Ambas recebem RLS, revogação de acesso público e o tratamento existente de `anon`/`authenticated`. Não conceder acesso direto aos clientes. Inscrições/chaves são dados operacionais privados; não aparecem na visão de amigos, diagnóstico ou logs. Snapshots e suas revisões permanecem intactos. A migração é automática ao iniciar uma versão futura da API, portanto deve constar na revisão/aprovação do deploy.

Variáveis novas, ainda não configuradas em produção:

- `VAPID_PUBLIC_KEY`: ponto P-256 não comprimido, Base64 URL-safe sem padding.
- `VAPID_PRIVATE_KEY`: escalar privado de 32 bytes, Base64 URL-safe sem padding; somente secret do servidor.
- `VAPID_SUBJECT`: contato `mailto:` válido, a definir antes do piloto.

Não colocar a chave privada, comprovantes, endpoints ou arquivos de inscrição em Git, argumentos de comando, prints ou logs. Não gerar um par novo a cada reinício. Rotacionar VAPID exige planejar reinscrição dos dispositivos. Configuração parcial/inválida desativa o recurso com mensagem fixa de log, sem imprimir os valores.

Antes do piloto, revisar migração, definir contato, gerar chaves por procedimento privado e aprovar separadamente sua instalação e o deploy. Publicar API/cliente compatíveis e testar com poucos dispositivos voluntários. Um rollback deve desabilitar envios antes de retirar o suporte de vinculação/logout do cliente; manter as tabelas privadas e registros de proteção. O TTL do teste é de sessenta segundos, sem garantia de prazo de entrega.

## Dependências, validação e limites

Foi adotado `nl.martijndwars:web-push:5.1.2` (MIT), versão publicada conferida no Maven Central. Usa-se apenas `AbstractPushService` para VAPID/criptografia; dependências transitivas antigas de CLI/HTTP/Netty foram excluídas. O transporte é `java.net.http.HttpClient`, com `org.bouncycastle:bcprov-jdk18on:1.87` e `org.bitbucket.b_c:jose4j:0.9.7`. Os três pacotes/versões foram consultados no OSV em 13/11/2026, sem avisos retornados; isso não garante ausência de vulnerabilidades futuras. O relatório Gradle confirmou que os clientes HTTP antigos não fazem parte do runtime.

Endpoint exige HTTPS, host de provedor aceito, ausência de credenciais/fragmento/porta alternativa e validação estrita de chaves. FCM, Mozilla, Apple e WNS têm regras explícitas. Antes do envio, os resultados DNS não podem ser privados, loopback, locais ou multicast; não é permitido fornecer URLs arbitrárias ou seguir redirecionamentos. A confiança ainda inclui DNS/TLS dos domínios oficiais permitidos; não transformar a lista em wildcard geral.

Testes automatizados cobrem prova de posse, troca de conta, revisões antigas, revogação offline, reserva/replay, concorrência, cotas, migração/RLS, rollback, HTTP, timeout, texto de aceite, criptografia e isolamento de fazendas/recibos econômicos. O envio é simulado: não houve inscrição real ou envio a Apple/Google/Mozilla. O piloto em celulares reais, inclusive iPhone instalado, depende de configuração e autorização posteriores.

Fontes: [webpush-java](https://github.com/web-push-libs/webpush-java), [Maven Central](https://repo.maven.apache.org/maven2/nl/martijndwars/web-push/5.1.2/), [MDN](https://developer.mozilla.org/en-US/docs/Web/API/PushManager/subscribe), [Apple](https://developer.apple.com/documentation/usernotifications/sending-web-push-notifications-in-web-apps-and-browsers).

## Evidência da implementação — 13/11/2026

- Suíte Java completa: 322 testes de domínio/save, um do core, 52 unitários do servidor. Quatro testes de capacidade também passaram.
- PostgreSQL 17 descartável: 131 integrações na suíte completa; depois foi adicionado e aprovado um caso de migração/escrita com papel SQL local sem superusuário. A classe HTTP foi reexecutada com os dois casos aprovados. Não houve teste no Supabase de produção.
- Frontend: 204 testes, TypeScript e builds local/produção aprovados; a configuração usada no build de produção era fictícia. O build local foi restaurado ao final.
- SpotBugs do servidor em main/test: nenhum achado. Revisão independente somente leitura confirmou a correção da quota de registros e não encontrou novo P1/P2 nos deltas de logout/timeout.
- Revisão visual isolada em computador e 390 × 844: painel e mensagem de aceite simulados, sem transbordamento horizontal. Nenhuma permissão nativa, inscrição ou entrega real foi testada. Evidências locais em `.tools/push-visual/desktop.png` e `mobile.png`, fora do Git.

O trabalho foi dividido entre implementadores com arquivos exclusivos e um revisor somente leitura, usando o modelo herdado da sessão sem troca de provedor/autenticação. O coordenador integrou e executou os checks finais. Limitações: os testes de navegador usam mocks/fixture local e não comprovam comportamento dos serviços de push ou do iPhone real.
