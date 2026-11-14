# Web Push 2/3 — plantas prontas

Esta etapa acrescenta avisos automáticos de plantas prontas à infraestrutura da etapa 1. Animais, furtos e pragas continuam reservados à terceira entrega. A implementação é uma proposta em PR: configuração e publicação em produção precisam de aprovação separada.

## O que o jogador recebe

Em Ajustes, o jogador pode escolher Plantas para o dispositivo inscrito. A preferência é privada, persistida no servidor e recuperada ao reabrir o painel. Os outros tipos continuam marcados como em breve. A inscrição exige permissão por clique; no iPhone/iPad, pode exigir instalar o jogo na Tela de Início.

A mensagem é fixa: “Há plantas prontas para colher na sua fazenda. Toque para voltar ao jogo.” Não contém nome, saldo, cultura nem informações de amigos. O clique abre somente a origem do jogo.

Maturações são agrupadas por jogador, em no máximo um lote por minuto. Cada dispositivo elegível recebe no máximo uma tentativa por lote. O servidor verifica se a temporada ainda está madura antes de reservar o envio. Se uma colheita ou furto acontecer depois dessa verificação, o aviso pode ficar desatualizado; a rede não mantém a fazenda bloqueada.

Ao ativar ou reativar depois de um período sem dispositivo elegível, plantas já maduras formam o ponto inicial. Não enviar um histórico de avisos. Se a API reiniciar com trabalhos já agendados, retomar os vencidos em lotes e enviar somente se ainda existir planta disponível. Não prometer entrega pontual nem recebimento: conexão, provedor, permissão, sistema operacional e disponibilidade da API interferem.

## Processamento

Uma fila privada com índice de vencimento guarda o próximo trabalho de cada fazenda. A gravação direta e a gravação adiada por recibo econômico atualizam o agendamento na mesma transação. Rollback da ação também desfaz essa atualização. Mudanças de rega, fertilizante, praga, temporada, VIP e automações replanejam a previsão.

O trabalhador usa relógio do servidor e o motor existente, sob os bloqueios normais da fazenda. A previsão agenda uma verificação; não é prova de que a planta amadureceu. Seca, ausência de primeira rega, pausa e cuidados automáticos continuam seguindo as regras atuais. Se o snapshot tem um trecho de automação ainda não processado, inclusive de um serviço que já expirou, agendar uma observação inicial. O replay existente decide se houve retomada; o cursor atualizado impede repetir essa observação sem progresso. O ponto inicial de ativação continua suprimindo maturações históricas.

Não adicionar sorteios periódicos de pragas: as observações continuam no fluxo normal de atualização e no replay já existente das automações.

Esse caminho não concede presente de Natal, não reivindica notas de atualização e não executa colheita, venda, furto ou resgate. Cuidados automáticos já contratados podem ser processados pelo motor, com suas regras normais. O snapshot permanece no formato atual.

Com VAPID válido, um único trabalhador local consulta trabalhos vencidos a cada trinta segundos, com orçamento de dez fazendas por consulta e sem sobreposição local. A reconciliação inicial de inscrições antigas compartilha esse orçamento, então uma retomada pode exigir mais de uma rodada. Instâncias concorrentes precisam disputar a reserva no PostgreSQL. Sem VAPID, não iniciar o agendador. Não carregar todas as fazendas a cada consulta. Trabalhos com erro recuam ao menos um minuto; falhas individuais não devem abortar os demais. O custo inclui consultas limitadas e envios; não assumir custo zero.

## Segurança e falhas

Cursores por terreno/temporada ficam separados da lista de avisos internos. Apagar uma notificação lida não reinicia a deduplicação. Inscrições carregam endpoint, comprovante, binding e revisão; os mesmos controles da etapa 1 continuam necessários para preferências e destinatários.

Reservar o envio antes da rede. Uma reserva incerta não pode ser reenviada depois de crash: pode haver perda de aviso, mas não duplicação de tentativa. Resultado terminal não deve ser sobrescrito por resposta atrasada. Não manter conexão de banco durante HTTP. Respeitar os timeouts, limite de concorrência, validação DNS/TLS e Retry-After da etapa 1.

Antes de enviar, conferir jogador ativo, preferência e vínculo atuais. 404/410 desativam apenas a revisão correspondente, sem invalidar uma associação nova. O worker confere o binding antes e depois de mostrar. Logout offline continua suprimindo mensagens da conta anterior.

## Publicação e rollback

Não configurar VAPID, aplicar migração, enviar avisos reais ou publicar esta etapa durante a implementação. Tabelas e índices novos são aditivos e privados, com RLS e revogação de acesso público. A inicialização de uma versão futura aprovada aplicará a migração; validar backup e permissões antes desse deploy.

No rollback, desligar primeiro os envios automáticos e preservar tabelas, cursores e recibos. Usar API e frontend compatíveis com os tipos de mensagem e preferências. No piloto, conferir que o novo Service Worker foi instalado/ativado; um worker antigo não reconhece CROP_READY. Fechar as janelas antigas e reabrir pode ser necessário na atualização. Não apagar registros anti-replay para reduzir volume sem uma política de validade verificável.

Os testes locais usam PostgreSQL descartável, relógio controlado e transporte simulado. Eles não comprovam entrega no Android ou no iPhone real. O piloto em aparelhos permanece uma etapa separada antes de liberar globalmente.

## Referências

- [Infraestrutura e proteção por dispositivo](web-push.md).
- [Desenho aprovado](superpowers/specs/2026-11-13-web-push-plantas-design.md).
- [Plano de implementação](superpowers/plans/2026-11-13-web-push-plantas.md).
- [PostgreSQL 17 — bloqueios de SELECT](https://www.postgresql.org/docs/17/sql-select.html#SQL-FOR-UPDATE-SHARE), consultado via Context7 em 13/11/2026. SKIP LOCKED serve para filas; a reserva precisa ser persistida na transação antes de liberar o bloqueio.

## Estrutura aditiva

- `crop_push_jobs`: proprietário, habilitação, próxima verificação, horário do último lote e cursores JSON por terreno, incluindo o ponto inicial de ativação. Uma linha por fazenda; índice parcial em `next_at` para trabalhos habilitados.
- `crop_push_deliveries`: identificador, proprietário, endpoint, binding/revisão, estado e horário da reserva. Índice parcial nas reservas pendentes. Recibos incertos expiram sem reenvio; o histórico permanece privado e exige acompanhamento de volume.

Não há coluna nova no snapshot nem mudança de ruleset. A inicialização idempotente cria tabelas/índices e aplica RLS/revogações no schema privado. Apenas a API acessa esses registros.

## Evidências locais

A revisão visual isolada usou o componente real de Ajustes, API simulada e vínculo fictício em origem localhost separada. Conferidos 1280 × 800 e 390 × 844, com texto legível, checkbox Plantas habilitado, outros tipos indisponíveis e ausência de transbordamento horizontal. Evidências fora do Git: `.tools/push2-visual/desktop.png` e `mobile.png`. Nenhuma permissão nativa foi solicitada.

Rodada final em 13/11/2026: `gradlew.bat test build :server:integrationTest :server:capacityTest :server:spotbugsMain :server:spotbugsTest :server:installDist` passou em 4 min 20 s. Relatórios: 322 testes domínio/save, um core, 62 unitários servidor, 163 integrações PostgreSQL 17 completas e quatro de capacidade, sem falhas ou skips. SpotBugs main/test servidor: zero achados. Frontend: 245 testes, TypeScript e build local aprovados.

As 27 regressões novas de PostgreSQL cobrem ativação, baseline, retomada, preferências, concorrência, rollback, crash, seis temporadas, automações/VIP, privacidade, jobs inválidos e colheita antes da reserva. A revisão independente encontrou um P2 na reativação com automação histórica expirada; quatro testes reproduziram a falha. A correção e a re-revisão foram aprovadas, sem novos P1/P2 confirmados. Não houve inscrição, envio ou teste no banco de produção.

Limite operacional de rollback: se uma versão anterior gravar fazendas sem atualizar esta fila, uma futura retomada precisa revalidar também os jobs existentes, não somente fazer bootstrap dos ausentes. Planejar essa reconciliação administrativa antes de reabilitar envios; nenhum procedimento de escrita foi executado nesta entrega.

PR de revisão: [#81](https://github.com/dabb3/colheita-feliz-public/pull/81). Notas web-push-plantas-v1 vinculadas ao número real; permanecem desativadas fora da publicação correspondente pelo pipeline. Nenhum merge ou deploy foi executado.
### Correção visual após relato de sobreposição

O cartão isolado da primeira revisão não reproduzia sua interação com a grade de Ajustes. Com cartões vizinhos, a direção flex em coluna combinada com wrap e flex-basis de 100% fazia os parágrafos ocuparem colunas laterais. Reprodução local: cartão de 327 px com scrollWidth de 1.051 px, sobrepondo Sobre.

A correção remove a base percentual dos títulos/parágrafos e mantém uma única coluna sem wrap no cartão. Na mesma grade, o scrollWidth ficou em 325 px, dentro do cartão de 327 px. Conferidos 1280 × 800 e 390 × 844, com configuração simulada ativa e inativa, sem filhos transbordando horizontalmente. Evidências locais: `.tools/push2-visual/layout-before.png`, `layout-desktop-fixed.png`, `layout-mobile-fixed.png` e `layout-desktop-configured.png`.

O texto “Notificações ainda não configuradas no servidor” é separado do defeito visual: indica indisponibilidade da configuração, sem autorizar instalar chaves ou habilitar envios. Nenhuma configuração de produção foi alterada nesta correção.
