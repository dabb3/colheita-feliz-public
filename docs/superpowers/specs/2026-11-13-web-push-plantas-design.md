# Web Push 2/3 — plantas prontas

Estado: aprovado pelo usuário para implementação em 13/11/2026. Base: `7d3abed`, merge do PR #80. Branch: `codex/web-push-plantas`.

## Objetivo e limites

Avisar dispositivos inscritos quando plantas próprias estiverem prontas, inclusive sem navegador aberto. Reutilizar API Java, PostgreSQL, VAPID, transporte fixado ao DNS e vínculo privado por dispositivo da etapa 1. Não adicionar provedor, dependência de produção ou infraestrutura externa. Não incluir animais, furtos ou pragas nesta entrega.

Preservar o motor de crescimento, rega, pragas, automações, economia, cinco minutos de exclusividade e espera de furto. O alerta não colhe, vende, concede presentes ou resgata recompensas. A sincronização offline usa o motor existente e suas transações; não inventa um cálculo paralelo de maturação.

## Abordagens

- Apenas enviar durante acessos: simples, mas não atende ao aviso com jogo fechado.
- Varredura contínua de snapshots: atende parcialmente, mas desperdiça processamento e amplia contenção.
- Fila persistente de próximos eventos por fazenda: recomendada. Consultar índice de vencimento em lotes limitados; carregar somente fazendas com evento relevante e inscrição elegível.

O usuário já aprovou Java + PostgreSQL e a divisão em três etapas. O usuário confirmou agrupamento por minuto, revalidação e tratamento de atraso na retomada.

## Comportamento proposto

Um aviso genérico: “Há plantas prontas para colher na sua fazenda. Toque para voltar ao jogo.” Sem nome, saldo, cultura, identificador público ou dados de amigos. Agrupar maturações por jogador; no máximo um lote de alerta por minuto, destinado aos dispositivos ativos que optaram por plantas. Cada dispositivo recebe no máximo uma tentativa por lote; aceite não comprova entrega.

Revalidar que existe planta madura na temporada correspondente antes da reserva do envio. Se já colhida, furtada, removida, pausada ou pertencente a outra temporada, cancelar ou reagendar. Uma ação concorrente depois da última validação pode tornar o aviso desatualizado; não manter bloqueio de fazenda durante rede para tentar eliminar essa corrida.

Depois de indisponibilidade da API, processar a fila persistida em lotes, sem rajada de eventos antigos. Enviar somente se ainda houver planta disponível. Não prometer pontualidade enquanto API estiver parada, provedor suspenso ou dispositivo sem conexão. Não enviar retroativamente todas as plantas anteriores à primeira ativação: registrar a situação atual como ponto inicial e acompanhar maturações posteriores. Ao reativar Plantas depois de um período sem inscrições elegíveis, adotar o estado atual como novo ponto inicial: não enviar maturações desse intervalo nem reapresentar temporadas já notificadas. Essa decisão evita avisos históricos.

## Agendamento e persistência

Fila privada aditiva por fazenda com próxima verificação, estado/versão e controle de tomada de trabalho. Índice de vencimento. Atualização da fila dentro da mesma transação que persiste uma mudança relevante da fazenda; contemplar tanto save direto quanto saves diferidos pelo recibo econômico. Assinar/reativar dispositivo também reconcilia o agendamento. No início, reconciliar fazendas já inscritas da etapa 1 sem emitir histórico.

Prever a maturação usando o estado real da planta e regras já existentes. Alterações realizadas por rega, pragas, fertilizantes, temporada ou contratação/validade de automação replanejam a previsão na transação correspondente. Não agendar despertares intermediários somente para sortear pragas. Despertar na previsão de maturação ou retomada por automação, revalidando pelo motor normal. Planta sem primeira rega ou com pausa indefinida aguarda mudança de estado; automação capaz de retomá-la precisa de próximo evento. Não acordar repetidamente para plantas sem progresso possível. Pragas naturais continuam com a semântica lazy atual de PestSystem.update; não criar replay ou sorteio intermediário de pragas fora do replay já implementado das automações. A previsão é uma hipótese de despertar, nunca prova de maturação. Testes devem comparar o processamento com um acesso normal à mesma fazenda no mesmo instante e com a mesma fonte aleatória.

O processador restaura o estado pelo mesmo motor do online, sob os bloqueios existentes, persiste avanço normal e agenda a próxima verificação. Esse caminho não chama carregamento da própria fazenda que concede Natal ou notas de atualização. Não consumir recibo ou requestId econômico. Snapshots continuam no formato atual.

Cursores persistentes de planta/temporada impedem repetição após descarte das notificações internas, reload, renovação de vínculo ou reinício. Usar identidade de terreno e revisão de colheita; não depender apenas da lista limitada a cem avisos. Reserva de envio inclui endpoint, binding e revisão. Revalidar proprietário ativo, inscrição, preferência e versão antes de enviar. Uma transferência de conta invalida destinatário antigo; o worker mantém a checagem local antes/depois da exibição.

Envio fora de transações. Um recibo reservado não é reenviado depois de resultado incerto ou crash: expira para indisponível, preservando semântica de no máximo uma tentativa. Registrar resultado e invalidar 404/410 somente se vínculo/revisão ainda corresponder. Respeitar backoff e limites do transporte da etapa 1. Não reaproveitar a quota manual de teste para alertas automáticos. Documentar retenção dos novos cursores/recibos sem enfraquecer replay.

Um agendador encerrável, habilitado somente com VAPID válido, verifica a fila a cada trinta segundos, em lote pequeno e sem sobreposição local. Tomada transacional com lease/versão ou mecanismo equivalente impede envio duplicado entre instâncias. Erros isolados não encerram o agendador. Não manter conexão de banco enquanto envia. Encerramento da API interrompe o trabalhador antes de fechar pool/transporte.

## Interface e privacidade

Habilitar somente a preferência Plantas em Ajustes. Os demais tipos continuam em breve. Persistir alterações por dispositivo usando prova e revisão existentes; não permitir alterar inscrição alheia ou sobrescrever vínculo concorrente. Exibir que horários são aproximados e entrega depende do navegador.

Worker aceita TEST e CROP_READY com mensagens fixas locais; rejeita tipos desconhecidos e nunca renderiza texto ou URL arbitrários do payload. Clique abre apenas a origem do jogo. Não alterar revogação offline, privacidade ao trocar conta ou pedido de permissão por gesto.

## Aceitação e testes

- Maturação com rega normal, seca, fertilizante, praga, remoção e seis temporadas; replanejamento por automações/expiração.
- Offline, retomada e reinício da API; deduplicação independente dos avisos internos descartados.
- Lote com várias plantas; limite de um lote/minuto; dispositivos distintos e preferência desativada.
- Compra/colheita/furto concorrentes com trabalho, dois processadores concorrentes, rollback e crash após reserva.
- Logout offline, troca de conta, vínculo novo enquanto envio termina, 404/410, falhas e Retry-After.
- Migração idempotente, índices, RLS, ausência de vazamento em visitas/diagnóstico; nenhuma alteração de economia, Natal ou recibos existentes.
- Java, PostgreSQL descartável, capacidade, SpotBugs, web, TypeScript e build. Revisão independente do diff e evidência visual em desktop/celular.

## Publicação futura

Implementar e abrir PR para `codex/online-core-compartilhado`, sem merge, deploy, secrets, migração ou envio em produção. Atualizar notas com o número real do novo PR, sem anunciar conteúdo ainda não publicado. Entrega real em Android/iPhone instalado continua validação separada: testes simulados não a comprovam. Documentar tabelas, habilitação, rollback e custo das consultas limitadas; nenhuma garantia de custo zero ou disponibilidade permanente.

## Revisão do desenho

A revisão independente identificou duas decisões relevantes: efeitos de observações offline sobre o sistema lazy de pragas e política de reativação. Proposta: não adicionar ticks de pragas; atualizar pelo motor existente somente nos eventos previstos de maturação/retomada, como um acesso normal naquele instante. Isso pode observar pragas ou executar cuidados normais em outras plantas da mesma fazenda; não é uma operação puramente de transporte. A execução segue essa política documentada. Reativação usa baseline atual, sem histórico. O usuário confirmou a recomendação de agrupamento, revalidação e retomada antes da implementação.
