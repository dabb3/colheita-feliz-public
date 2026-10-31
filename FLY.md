# Preparação da API no Fly.io — São Paulo

**Estado: arquivos preparados; nenhum recurso Fly criado ou publicado.** O responsável autorizou somente preparação, sem gastos. A configuração mantém uma máquina ligada e terá cobrança quando implantada. Aprovar preço regional, tráfego, eventual build remoto e teto de gasto antes de executar os comandos de criação/deploy abaixo. Alertas de orçamento não devem ser tratados como limite rígido de cobrança.

O objetivo é medir o mesmo servidor perto do Supabase em São Paulo. Cloudflare Pages e o banco atual permanecem onde estão. Autenticação, quatro threads HTTP, fila de 32 e pool HikariCP de quatro conexões continuam iguais. Não há garantia de capacidade para 30 jogadores antes do teste de carga.

## Arquivos e limites

- `fly.toml`: região `gru`, duas CPUs compartilhadas, 1 GB, HTTPS, porta 8080, sem suspensão automática e verificação `/health`.
- `Dockerfile`: reutilizado sem alterações, Java 21 e usuário `10001:10001`.
- CA pública Supabase injetada em `/etc/secrets/root.crt` pelo secret `SUPABASE_CA_CERT`, cujo valor deve ser o PEM codificado em base64.
- Nome `colheita-feliz-api` é uma proposta; disponibilidade e capacidade em GRU ainda não verificadas.
- `min_machines_running = 1` não limita o máximo de máquinas. O primeiro deploy deve usar `--ha=false`, seguido da conferência de que há exatamente uma máquina em GRU. Não usar autoscaling nesta etapa.
- Uma máquina não oferece alta disponibilidade. Reinícios e deploys podem interromper conexões; recibos e o mesmo identificador de ação continuam necessários para retomada.

## Antes de criar recursos

1. Aprovar orçamento e confirmar preços de GRU no painel/tabela oficial. Não usar a tarifa de Virginia como estimativa definitiva para São Paulo.
2. Criar/acessar a conta Fly pelo responsável, aceitar eventuais termos e configurar cobrança somente após essa decisão. A CLI não foi instalada nesta preparação.
3. Conferir o commit aprovado e passar pelo fluxo de PR. O arquivo não dispara deploy automático; não foi criado workflow ou token de CI.
4. Guardar configuração atual do Render e `PUBLIC_API_URL` do Pages sem imprimir senhas/tokens. Validar backup e restauração antes de liberar escrita no novo host.
5. Revalidar comportamento com o PostgreSQL descartável do CI e build normal. Não apontar a suíte de integração ou testes de carga ao banco dos jogadores.

## Provisionamento futuro, após aprovação

Instalar `flyctl` apenas da fonte oficial, autenticar e verificar a organização correta. Confirmar nome disponível; se necessário, ajustar `app` no arquivo antes da criação. Não executar `fly launch` aceitando valores padrão que criem banco, volumes ou máquinas extras.

```powershell
fly auth whoami
fly config validate --config fly.toml
fly apps create colheita-feliz-api --org <organizacao-aprovada>
```

Esses comandos são um roteiro, não evidência de execução. A organização, a conta e a disponibilidade do nome continuam pendentes. A criação da conta/recursos exige aprovação posterior; a validação TOML local não substitui a validação do Fly.

### Configuração e certificado

Definir no destino, fora do Git: `OWNER_USER_ID`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `ALLOWED_ORIGINS` e `SUPABASE_CA_CERT`. `AUTH_MODE`, `HOST` e `PORT` já estão no TOML. Não copiar credenciais para a conversa, argumentos de shell ou arquivos rastreados. Usar o painel ou `fly secrets import --stage` recebendo os valores por entrada padrão segura, sem registrar a entrada em logs.

O certificado é público; converter o arquivo PEM baixado do projeto em base64 para o secret de arquivo. Credenciais SQL permanecem em variáveis separadas. A URL JDBC deve manter `sslmode=verify-full` e `sslrootcert=/etc/secrets/root.crt`. Não usar `require`, trust-all ou desativar SSL.

Começar com o mesmo Session pooler de São Paulo usado pelo Render: muda-se apenas a hospedagem da API. Conexão direta por IPv6 pode ser um ensaio posterior; medir conectividade e latência antes de alterar esse segundo fator.

`ALLOWED_ORIGINS` deve conter a origem exata `https://colheita-feliz-amigos.pages.dev`. O endereço da API `*.fly.dev` não precisa virar uma origem CORS. Não criar wildcard.

```powershell
fly deploy --config fly.toml --ha=false
fly machine list --app colheita-feliz-api
fly status --app colheita-feliz-api
fly checks list --app colheita-feliz-api
```

Conferir exatamente uma máquina, região GRU, 2 CPUs compartilhadas e 1 GB. No runtime, confirmar que o usuário 10001 lê o certificado. Falha de leitura ou conexão impede a migração; não alterar permissões de autenticação/SSL para contorná-la. Não há volume: fazendas continuam persistidas no PostgreSQL.

## Comparação antes de trocar o frontend

1. Confirmar `/health` e `/v1/catalog` em Ohio e GRU, com API acordada. `/health` não consulta banco e não comprova melhora das ações.
2. Executar as mesmas 24 ações sintéticas com rollback, uma passagem de aquecimento e três amostras, apenas para comparação preliminar. Ativar `LATENCY_BENCHMARK=once` temporariamente e removê-lo após coleta. Essa configuração não é padrão do TOML. Não fazer teste de carga no Supabase dos jogadores.
3. Para carga com 1, 10 e 30 usuários virtuais, criar ambiente e contas descartáveis separados, sem dados reais, orçamento aprovado e mesma configuração de CPU/threads/pool. Cada usuário precisa de sessão válida própria; cada intenção usa identificador emitido pelo servidor e revisão atual. Repetições de uma intenção mantêm o mesmo ID. Não reduzir controles para viabilizar a carga.
4. Usar a mesma mistura de ações, taxa por jogador e duração nas duas hospedagens. Registrar número de amostras, p50/p95 do HTTP completo, erros, 429/409, fila, CPU, memória e `Server-Timing` (`auth`, `db_connect`, `db`, `app`). Não somar `app` aos componentes que ele já engloba. Dados sintéticos internos excluem autenticação/rede e não substituem HTTP real.
5. Validar compra, plantio, rega, colheita, venda, alimentação, produção, temporadas, proteção, furto simultâneo e replay. Confirmar rollback sem fixture persistida e ausência de alterações em fazendas reais durante ensaios internos.

O harness de carga 1/10/30 ainda não foi implementado ou executado; este documento define sua aceitação. Aprovar a troca somente com redução de p50 e p95 na carga equivalente, sem regressão de consistência/erros, e custo dentro do teto. Não prometer números antes da medição.

## Troca e rollback

A URL é variável de build **`PUBLIC_API_URL`** no Cloudflare Pages, usada por `web/scripts/build.mjs` para gerar `config.js` e a política CSP. Não editar o JavaScript publicado manualmente.

Após os testes e autorização para publicação, atualizar essa variável para a origem HTTPS exata do Fly, sem barra final, e reconstruir o Pages. Manter `PUBLIC_SUPABASE_URL` e `PUBLIC_SUPABASE_PUBLISHABLE_KEY`. Não é necessário proxy laranja nem mudança de callback Google/Supabase, pois o endereço do frontend continua igual.

Manter Ohio como contingência durante a validação. Conferir login, CORS, fazendas e pedidos pendentes. Clientes já abertos podem continuar no endpoint antigo; confirmar versões compatíveis antes da troca. Antes de retomar um POST de resultado desconhecido, consultar a fazenda e preservar o ID original. Não criar outra intenção automaticamente por trocar de host.

Para rollback, restaurar `PUBLIC_API_URL=https://colheita-feliz-api.onrender.com`, reconstruir o Pages e conferir o commit publicado. Como o banco e o protocolo permanecem iguais, não restaurar um backup antigo sobre gravações válidas. Desativar a API Fly apenas após resolver pedidos pendentes e confirmar que os clientes utilizam Ohio. A máquina ligada continua cobrada enquanto existir execução; recursos parados também podem ter custo residual. Conferir faturamento e recursos ao encerrar o teste.

## Verificado nesta preparação

Sintaxe TOML e contratos locais de porta, Dockerfile, usuário, certificado e configuração conferidos com parser padrão. Não houve instalação da CLI, criação de conta/app/máquina, deploy, teste de carga ou troca de endpoint. `fly config validate`, permissões reais do arquivo e desempenho em GRU permanecem pendentes.

Fontes oficiais consultadas em 31/10/2026: [configuração](https://docs.fly.io/reference/configuration), [deploy e redundância padrão](https://docs.fly.io/launch/deploy), [secrets](https://docs.fly.io/apps/secrets), [preços](https://docs.fly.io/about/pricing). Context7 não foi usado para este provedor; configuração conferida na documentação oficial.
