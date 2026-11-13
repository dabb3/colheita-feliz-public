# Diagnóstico de conexão no navegador

Em Ajustes, “Testar conexão” abre o painel e executa duas consultas independentes. O jogador pode copiar o resultado ou selecionar o texto quando a área de transferência estiver indisponível. O teste continua disponível quando há ação pendente e não exige ferramentas de desenvolvedor.

## Contrato e privacidade

- GET `/health`: disponibilidade pública, sem autenticação.
- GET `/v1/diagnostics`: autenticação e origem pelos mesmos validadores das rotas do jogo. Retorna somente `ok`, `serverTime` (precisão de milissegundos) e `originPresent`. Esse último campo indica a presença do cabeçalho Origin; uma origem presente já passou pela validação anterior, que rejeita origens proibidas com HTTP 403. Aplica os limites por IP/conta existentes e `Cache-Control: no-store`.
- A rota não chama `OnlineGameService`, banco, fazendas, automações, notas, reservas de identificador ou recibos. Não mede a disponibilidade do PostgreSQL.
- A sonda usa transporte separado do fluxo de comandos. Não chama `retry()`, não grava/remove armazenamento, não substitui o identificador reservado e não altera a intenção pendente. A autenticação reutiliza o provedor de sessão existente.
- Cada etapa tem prazo de oito segundos, incluindo obtenção da sessão e leitura do corpo; o resultado parcial é retornado mesmo se uma delas falhar. O teste é limitado a duas consultas, não a tentativas contínuas.
- O relatório inclui horário do navegador, commit web quando disponível, família/versão do navegador, status, durações e diferença estimada de relógio pelo ponto médio da consulta autenticada. O intervalo do relógio começa imediatamente antes do envio, excluindo a obtenção/renovação da sessão; a duração total da etapa continua incluindo essa espera. A incerteza de rede é indicada; isso não constitui sincronização precisa do relógio. Tokens específicos de Edge, Opera, Samsung Internet e navegadores iOS têm prioridade sobre os tokens de compatibilidade Chrome/Safari.
- Não copia token, cookie, nome, e-mail, ID de jogador, URL completa, conteúdo da ação pendente, mensagens brutas de erro ou respostas da API. Não envia automaticamente o relatório ao suporte.
- Fechar o painel ou trocar a conta impede um resultado atrasado de atualizar a interface. Uma sonda já iniciada pode terminar, limitada pelo timeout.

## Interpretação

`ok` indica resposta válida. `http-401` indica rejeição de autenticação pela API; `session-unavailable` indica sessão indisponível no navegador. `http-403` indica recusa HTTP e não prova, isoladamente, problema de origem. `http-404` pode indicar que o frontend foi publicado antes da API com a nova rota. `timeout` indica prazo excedido. `invalid-response` indica corpo recebido incompatível com o contrato. `network` não distingue DNS, TLS, CORS, bloqueadores ou falhas de transporte: não apresentar uma dessas hipóteses como causa comprovada.

O teste autenticado acontece pela página, incluindo o preflight do navegador quando necessário. Uma navegação direta para `/health` não equivale a esse teste. Não é possível obter o motivo interno completo de uma rejeição de `fetch` somente pela API JavaScript.

## Publicação e compatibilidade

Sem migração de PostgreSQL, snapshot ou secrets. O build incorpora o SHA de `CF_PAGES_COMMIT_SHA`, ou de Git local quando disponível, aceitando somente hexadecimal. Arquivos sem metadados de Git continuam compilando com versão não identificada.

Publicar a API com a rota antes ou junto do frontend evita o diagnóstico `http-404`. Este documento não autoriza deploy. Não alterar CORS, SSL ou mecanismos de idempotência para fazer o teste passar. Não apagar uma pendência para ocultar um erro.

Os itens de Web Push, caviar e cachorro do plano geral não fazem parte desta entrega. O estado “Supabase resolvido” foi informado no plano, não verificado novamente por este diagnóstico.
