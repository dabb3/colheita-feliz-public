# Serviços VIP e publicação de notas

## Contratos de dados

O formato de save permanece v20. O ruleset `game-rules-2026-11-vip-services-v1` impede que um escritor antigo descarte silenciosamente as assinaturas. Os cinco identificadores anteriores continuam aceitos; não há reprecificação de plantas, animais, produtos ou saldos.

O snapshot contém o campo opcional `automations`:

- `cursor`: último instante processado, em UTC/ISO-8601.
- `expiresAt`: mapa de vencimentos dos serviços `water`, `pests` e `animals`.
- `weedsSince` e `insectsSince`: mapas de horários de surgimento, por índice decimal de terreno entre 0 e 29. Os dois tipos têm espera independente.

Ausência completa identifica save anterior, sem serviço adquirido. Campos presentes precisam ter a estrutura e os tipos exatos. Serviços desconhecidos, datas inválidas, índices inválidos e horários inconsistentes de pragas são recusados. Horários autoritativos futuros não são aceitos. Dados históricos sem horário de surgimento usam a primeira observação como início conservador da espera, nunca uma data presumida que antecipe a limpeza.

O catálogo público inclui `automationServices`; a visão própria inclui a situação e o prazo de cada serviço. A visão pública inclui apenas `vipActive`, sem validade do cartão nem termos das automações. O comando próprio `BUY_AUTOMATION` usa `itemId`, os recibos existentes e a revisão da fazenda; o cliente não envia preço, duração ou timestamps.

## Processamento offline e recursos

O processamento ocorre sob o lock e a transação PostgreSQL da fazenda, antes de ler/agir e antes da persistência. Ele reconstitui o estado anterior sem aplicar primeiro um refresh em “agora”, para não perder a ordem dos eventos. Um relógio de replay avança até a próxima rega, limiar de crescimento, verificação de praga, limpeza de 15 minutos, alimentação, produção, expiração ou o instante final solicitado.

Não há loop por segundo de ausência, tarefa permanente, cron externo nem navegador do dono mantendo a automação viva. Sem consultas, não há processamento: no próximo acesso próprio ou de um amigo, o estado é calculado como se os cuidados tivessem ocorrido nos instantes devidos. O custo é proporcional aos eventos necessários. Fazendas sem serviços mantêm o comportamento anterior de atualização.

O replay usa as ações existentes: consumo real de ração, capacidade por produto, receitas/lotes capturados, XP e tarefas UTC normais. Não chama venda de adultos, colheita de plantas, compra de ração ou ações sociais. Vencimentos são exclusivos: no exato instante de expiração, aquele serviço não pode mais agir. O VIP suspende o uso sem congelar os prazos.

Cursor e resultados são gravados na mesma transação. Rollback refaz o processamento a partir do estado persistido, sem duplicar pagamentos, ração ou produtos. Repetição de comandos é resolvida pelos recibos existentes. Uma cópia normalizada apenas para comparar revisões ignora a mudança isolada do cursor; o snapshot gravado sempre conserva o cursor verdadeiro. Isso evita que uma visita sem evento cause conflito falso ao dono.

## Notas diárias

`assets/data/update-notes.json` guarda a versão, o número real de PR e os textos. No deploy de produção, `.github/scripts/deploy-fly.sh` valida esse manifesto e passa automaticamente os dois metadados abaixo por `flyctl deploy --env`, junto com a imagem do mesmo commit:

```text
PUBLISHED_UPDATE_NOTES_VERSION=vip-benefits-v1
PUBLISHED_UPDATE_NOTES_PR=74
```

Não é necessário cadastrar ou atualizar essas chaves como secrets. Para uma atualização com novas notas, editar somente o manifesto: versão inédita, número real do PR e novidades que ele entrega. PRs sem novas notas conservam o manifesto. Os testes não fixam o número 74; validam os valores presentes no arquivo. O número não é inferido do PR em revisão.

O pipeline só implanta pushes da branch online após os checks, e recusa commits substituídos por um mais recente. Execuções de PR, outras branches e tags não ativam notas. Dados inválidos ou manifesto ausente interrompem o deploy antes de chamar o Fly, mantendo a versão em execução. Um rollback pelo mesmo pipeline deve usar o manifesto da versão restaurada. A execução padrão fora desse pipeline continua sem notas, a menos que os dois metadados sejam configurados explicitamente e correspondam ao manifesto. Ambientes locais de QA podem simular essa configuração com dados sintéticos; configuração parcial/incompatível no servidor desativa apenas as notas, mantendo o jogo disponível.

A migração aditiva de inicialização cria a tabela privada `update_note_views(actor, version, seen_day)`, com referência ao jogador, RLS e revogação de acesso público/anon/authenticated, seguindo o esquema da API. Não cria usuários, serviços ou privilégios novos.

No GET próprio, o servidor reivindica atomicamente a versão/dia usando seu relógio em `America/Sao_Paulo`. Só uma resposta concorrente inclui `updateNotes`; o estado fica compartilhado entre dispositivos. A reivindicação pertence à mesma transação da fazenda e um rollback não a consome. Visitas, lista de amigos e comandos não reivindicam notas alheias. Um retrocesso de relógio não repete a mesma versão em um dia anterior.

A política é de no máximo uma entrega por dia/versão: uma resposta perdida após o commit pode adiar a leitura até a próxima versão/dia. Esse limite evita duplicar popups entre dispositivos sem depender de armazenamento local. A interface enfileira notas recebidas se outro diálogo estiver aberto e elimina a fila ao trocar de conta.

## Implantação e restauração posteriores

1. Fazer backup verificável do banco e dos snapshots. A entrega não executa migração em produção.
2. Publicar apenas servidor e clientes compatíveis com o novo ruleset. Não manter escritores anteriores e novos simultaneamente.
3. A inicialização cria `update_note_views` no esquema privado já pertencente à API; o usuário SQL precisa manter as permissões de DDL usadas nas migrações existentes.
4. Conferir saúde, compras confirmadas e um caso de cuidado offline com conta de teste. Não ampliar recursos Fly.io nem adicionar agendador para isso.
5. O deploy automático extrai versão/PR do manifesto e ativa as notas com a mesma implantação, sem etapa manual de secrets. Conferir o popup com conta de teste após a API e o cliente estarem publicados. Uma execução manual fora do pipeline deve passar os dois metadados explicitamente; sem eles, os benefícios funcionam normalmente, mas não há popup.
6. Para rollback, preferir binário que preserve os novos metadados ou restaurar o backup de forma coordenada. Não usar um leitor antigo sobre dados atualizados. Conservar as linhas de visualização ao restaurar dados de jogador para evitar repetição involuntária das notas.

Não há pagamento real, provedor novo ou custo de infraestrutura contratado pelo código. O consumo de CPU/SQL ocorre nas consultas já atendidas pelo servidor; não é prometido custo de hospedagem zero.
