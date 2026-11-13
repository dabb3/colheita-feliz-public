# Web Push — primeira entrega

Data: 13/11/2026. Base conferida: `8954b81`, merge do PR #79 em `codex/online-core-compartilhado`.

## Objetivo e recorte

Implementar a primeira das três entregas do plano: infraestrutura, inscrição de dispositivos e teste de entrega. O jogador poderá ativar ou desativar notificações em Ajustes e enviar uma notificação de teste para o próprio dispositivo. As colheitas automáticas e os demais gatilhos de notificação não pertencem a este primeiro PR.

O próximo PR tratará plantas prontas com agendamento persistente e revalidação do estado real. O terceiro incluirá animais e eventos sociais. Não alterar economia, processamento de crescimento, furtos ou automações de cuidados para implementar o transporte.

## Alternativas e recomendação

1. **API Java existente + PostgreSQL existente:** recomendado para o grupo atual. A API autentica inscrições, mantém preferências e envia pelo protocolo Web Push usando VAPID. Evita um novo provedor de aplicação e mantém autorização no servidor existente. Exige biblioteca Java mantida para criptografia/protocolo, configuração de chaves e controle de destinos de rede.
2. **Serviço adicional de notificações:** reduz parte do trabalho de envio, mas cria cadastro, configuração, dependência operacional e tratamento de dados em outro fornecedor. Não há necessidade demonstrada nesta etapa.

As duas alternativas continuam dependendo dos serviços de push utilizados pelos navegadores. Não prometer ausência de custos de rede/infraestrutura nem entrega instantânea ou garantida.

## Interface

Em Ajustes, seção “Notificações no dispositivo”, exibir suporte, permissão, inscrição e disponibilidade da configuração do servidor. Pedir permissão somente após toque explícito em “Ativar notificações”. Não pedir permissão ao abrir o jogo ou insistir se ela for negada.

Mostrar “Enviar notificação de teste” apenas para uma inscrição válida da própria conta/dispositivo, com limite de frequência e proteção contra clique repetido. A mensagem será genérica e abrirá somente a origem do jogo. Exibir confirmação de envio ao provedor separada de confirmação de recebimento: aceite do provedor não prova exibição no aparelho.

Preparar controles independentes de plantas, produtos/necessidades dos animais, furtos e pragas. Deixar explícito que os avisos automáticos serão ativados nas próximas entregas; não anunciar gatilhos inexistentes.

No iPhone/iPad, orientar a instalação na Tela de Início quando necessária. Detectar capacidades em vez de bloquear por nome do navegador. O jogo deve funcionar sem suporte ou com permissão negada.

## Navegador

Service Worker dedicado a push e clique, sem interceptar `fetch` ou cachear API, autenticação e comandos. Manifesto instalável e ícones existentes. Não converter esta entrega em modo offline do jogo.

Persistir somente a vinculação local necessária para identificar conta/inscrição e suprimir mensagens de uma vinculação antiga. Não persistir tokens de sessão no worker nem incluir tokens em notificações. Na troca de conta e no logout, remover a vinculação e revogar a inscrição no servidor quando possível. Se a rede falhar, o worker deve suprimir mensagens da conta anterior, com revogação reconciliada posteriormente; isso precisa de teste específico.

## Servidor e persistência

Novas rotas autenticadas, separadas das ações econômicas: consultar configuração/preferências, cadastrar/reconciliar a inscrição, desativar e enviar teste. Validar jogador ativo e autorização de cada operação. Não usar o mecanismo de ação pendente do jogo.

Migração aditiva para inscrições privadas por jogador/dispositivo: identificador próprio, endpoint, chaves públicas de assinatura/criptografia da inscrição, preferências, estado e datas de criação/atualização. Endpoint e material da inscrição são dados sensíveis operacionais: não expor em visitas, diagnósticos, respostas públicas ou logs. Não modificar snapshot nem revisão econômica para salvar preferências de transporte.

Operações repetidas devem ser idempotentes. Uma conta não pode listar, modificar ou testar inscrições de outra. Reassociação do mesmo navegador após troca de conta deve invalidar a vinculação anterior sem aceitar sequestro por mero ID de inscrição. Detalhar o vínculo de posse do dispositivo no plano e testá-lo antes de implementar o envio.

O envio usa biblioteca de Web Push mantida, após verificar versão, licença e dependências. Não implementar criptografia própria. Validar HTTPS, hostname, porta, formato de endpoint e tamanho de chaves/corpo. Restringir provedores aceitos explicitamente; bloquear destinos privados/loopback, credenciais em URL, redirecionamentos e requisições arbitrárias para impedir SSRF.

Enviar fora de transações de fazendas, com timeout, limite de concorrência e frequência. Invalidar inscrições quando o provedor responder 404/410; respeitar 429/Retry-After sem retry ilimitado. Nesta primeira entrega não criar varredura contínua de fazendas ou fila de maturação.

## Configuração e segurança de publicação

Chave VAPID privada apenas em secret do servidor; chave pública disponibilizada por rota própria; contato VAPID configurável sem inventar endereço. Nunca versionar chaves ou imprimir inscrições completas. Sem configuração, funcionalidade desativada com mensagem amigável e inicialização normal da API.

Implementação, migração e testes somente locais. Criar branch exclusiva e PR para a base online. Não aplicar migração em produção, cadastrar secrets, solicitar permissões reais, enviar push aos jogadores, fazer merge ou deploy durante esta etapa. A configuração e o piloto real serão aprovados separadamente.

## Aprovação técnica e testes

Critérios adicionais aprovados: transferência exige comprovante privado do dispositivo, nunca somente ID; logout offline suprime imediatamente a vinculação local e mantém revogação para reconciliação; a interface confirma apenas o envio ao serviço e limita testes. A implementação usa revisões monotônicas por dispositivo para impedir cadastros atrasados após revogação e limita registros totais, inclusive inativos, por jogador.

- Java: validação de chaves/endpoints, autorização, limite de frequência, indisponibilidade e respostas de provedor simuladas.
- PostgreSQL descartável: migração idempotente, isolamento, cadastro repetido, concorrência, revogação e troca de conta.
- Web: capacidades, permissão solicitada por gesto, inscrição existente, permissão negada, worker, clique seguro e logout offline.
- Privacidade: nenhuma inscrição/token na visão de amigos, diagnóstico ou logs; mensagem de teste sem dados econômicos ou de terceiros.
- Builds, TypeScript, SpotBugs e revisão independente de segurança do diff.
- Evidência visual em computador/celular. Entrega real depende de permissão e teste em aparelhos reais: não equiparar emulação a teste no iPhone.

## Fontes consultadas

- MDN, via Context7 `/mdn/content`: https://developer.mozilla.org/en-US/docs/Web/API/PushManager/subscribe
- Apple: https://developer.apple.com/documentation/usernotifications/sending-web-push-notifications-in-web-apps-and-browsers
- WebKit: https://webkit.org/blog/13878/
