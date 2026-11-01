# Colheita Feliz — backend Fly.io (São Paulo)

> **Estado em 01/11/2026:** migração do backend **ativa em produção**. O Cloudflare Pages de produção está configurado para `https://colheita-feliz-public.fly.dev`; a aplicação Fly.io respondeu a `/health`, `/v1/catalog` e `/v1/me/farm` e o jogo foi validado com login Google e persistência de ações. O Render permanece disponível como contingência. Este documento descreve o estado observado e procedimentos de operação, **não** autoriza mudanças adicionais. Reconfirmar estados no painel antes de agir.

## Arquitetura e recursos

| Componente | Configuração |
| --- | --- |
| Frontend de produção | `https://colheita-feliz-amigos.pages.dev` (Cloudflare Pages) |
| API de produção | `https://colheita-feliz-public.fly.dev` (Fly.io, região `gru`) |
| API de contingência | `https://colheita-feliz-api.onrender.com` (Render) |
| Banco e autenticação | Supabase existente; **compartilhado** entre Fly.io e Render |
| Aplicação Fly | `colheita-feliz-public` |
| Organização Fly observada na preparação | `akaza-291` (confirmar conta/organização antes de qualquer ação) |
| Máquina observada | `d8946d7c92d7d8`, processo `app`, uma CPU compartilhada, 512 MB |
| Imagem verificada no deploy | `deployment-01M69BY898E7F9PJV8C9GVHKG6` |
| Versão de máquina observada | v8 após atualização do secret de CORS |
| HTTP | Porta interna 8080, HTTPS público obrigatório, GET `/health` a cada 15 segundos |
| Operação | `auto_stop_machines = "off"`, `min_machines_running = 1`; uma máquina sem alta disponibilidade |
| Endereço público | IPv4 compartilhado `66.241.124.160` alocado no teste |

O `fly.toml` é a configuração versionada: uma CPU compartilhada, 512 MB, região `gru`, health check, TLS/HTTPS e arquivo de certificado. O Dockerfile existente usa Java 21 e o usuário `10001:10001`. O serviço HTTP usa quatro threads e fila limitada; o pool PostgreSQL usa até quatro conexões. **Não existe teste de carga representativo para 10–30 jogadores** nem garantia de capacidade nesse nível.

O banco **não foi migrado**: os dois servidores leem e escrevem no mesmo Supabase. Nunca restaure um backup antigo em cima de gravações legítimas durante um rollback de frontend.

## Histórico da implantação e validações

Em 31/10/2026, os deploys iniciais geraram releases que não permaneceram aplicadas à máquina. Os logs antigos mostraram primeiro ausência de `OWNER_USER_ID` e depois uma falha genérica de inicialização PostgreSQL; a configuração de máquina v4 não continha a montagem do certificado. O secret `SUPABASE_CA_CERT` foi novamente preparado a partir do PEM original em Base64, e um deploy posterior aplicou a configuração com `[[files]]`.

A máquina foi iniciada manualmente, aprovou o health check e recebeu IPv4 público **compartilhado**. Em seguida foram validados:

- `GET /health`: `ok: true`, `development: false`, acessível por HTTPS.
- `GET /v1/catalog`: retorno completo do catálogo.
- `GET /v1/me/farm`: HTTP 200 com Bearer válido; login Google também validado pelo Preview no navegador.
- Ações realizadas no Preview foram persistidas e apareceram posteriormente no site de produção, demonstrando gravação e leitura do **mesmo** banco.
- CORS e CSP foram validados no Preview; após o corte, o site principal fez requisições autenticadas com HTTP 200 diretamente ao Fly.io.
- `fly status` após atualização de `ALLOWED_ORIGINS`: máquina **v8 / started / 1 health check passing**.
- Build e testes locais: `.\gradlew.bat :server:test :server:installDist` — `BUILD SUCCESSFUL`; `git diff --check` — sem problemas; `fly config validate` — configuração válida.

Amostras de latência medidas do mesmo computador: `/v1/catalog` com média de **43 ms no Fly.io** e **800 ms no Render** (10 requisições por ambiente, sem teste de carga). Uma requisição autenticada no Fly.io apresentou cerca de **125 ms**, versus amostras anteriores do Render próximas de **1.600 ms**; essas amostras não constituem comparação controlada de p50/p95. Valores variam com rede, aquecimento e carga.

## Secrets, SSL e CORS

As credenciais ficam no Fly.io, **fora do Git**. Nomes usados:

- `OWNER_USER_ID`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`
- `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `ALLOWED_ORIGINS`
- `SUPABASE_CA_CERT` (conteúdo PEM do **Supabase Root 2021 CA**, codificado em Base64)
- `AUTH_MODE=production` vem do `fly.toml` e também pode constar como secret já cadastrado; evite configurações conflitantes.

O `fly.toml` monta `SUPABASE_CA_CERT` em `/etc/secrets/root.crt`. A URL JDBC deve usar `sslmode=verify-full` e apontar `sslrootcert` para esse caminho. **Não** desative verificação de certificado, imprima secrets, registre Bearer tokens nem altere senhas para depurar um erro genérico.

`ALLOWED_ORIGINS` é uma lista de origens HTTPS exatas, separadas por vírgula, **sem wildcard**. No teste de 01/11/2026 foram incluídas:

```text
https://colheita-feliz-amigos.pages.dev
https://codex-preparar-fly-gru.colheita-feliz-amigos.pages.dev
https://48106aea.colheita-feliz-amigos.pages.dev
```

O endereço do servidor `*.fly.dev` não é uma *origem do frontend* e não precisa ser incluído nessa lista. Previews novos têm endereços próprios: se for necessário testá-los, autorize **somente** as origens exatas necessárias, depois remova as temporárias quando não forem mais usadas. O Preview atual no histórico possuía redirecionamentos OAuth autorizados no Supabase; não altere o campo **Site URL** de produção para testes.

## Operação e diagnóstico (somente leitura)

Executar com CLI autenticada e organização correta:

```powershell
fly status --app colheita-feliz-public
fly machine status d8946d7c92d7d8 --app colheita-feliz-public --display-config
fly secrets list --app colheita-feliz-public
fly ips list --app colheita-feliz-public
fly logs --app colheita-feliz-public
Invoke-RestMethod "https://colheita-feliz-public.fly.dev/health"
```

Esperado: uma máquina em GRU, `started`, health check passando, imagem aprovada, `files` com `/etc/secrets/root.crt`, sem secrets pendentes e HTTPS acessível. `/health` apenas confirma que a API responde; para comprovar leitura do banco, validar uma rota autenticada apropriada sem exibir o token.

O diagnóstico `DB_DIAGNOSTIC` introduzido em `JdbcOnlineStore.java` imprime SQLSTATE e classes de exceção **sem** mensagem do driver ou credenciais. Em caso de falha, correlacione o log ao horário atual: `fly logs` também pode retornar mensagens de tentativas antigas. Falha de `/health` ou `stopped` não deve desencadear repetição automática de deploys.

**Mudanças que exigem aprovação:** `fly deploy`, `fly secrets deploy`, `fly machine start/stop`, alocação de IP, mudanças de plano/recursos e qualquer ação que afete a produção. A atualização de secrets por `--stage` não os aplica até um deploy/update. Para mudanças futuras do código, revisar branch, configuração, testes e orçamento; `fly deploy --config fly.toml --ha=false --app colheita-feliz-public` é um exemplo do comando já utilizado, não uma instrução para executá-lo sem revisão. Não criar aplicações, máquinas ou volumes adicionais por padrão.

## Cloudflare Pages: produção e Preview

O build `web/scripts/build.mjs` gera `web/dist/config.js` usando a variável de build **`PUBLIC_API_URL`**; também gera `_headers` com a CSP. Uma alteração dessa variável só entra em vigor após **nova compilação/publicação**. Não modificar `config.js` publicado manualmente.

- **Production:** `PUBLIC_API_URL=https://colheita-feliz-public.fly.dev`, conforme a publicação validada.
- **Preview da branch `codex/preparar-fly-gru`:** configurado para Fly.io durante os testes.
- Preservar `PUBLIC_SUPABASE_URL` e `PUBLIC_SUPABASE_PUBLISHABLE_KEY`.

No momento do corte, a branch de publicação do Cloudflare Pages era `codex/online-core-compartilhado`; a branch de preparação do Fly.io é `codex/preparar-fly-gru`. O merge do PR de consolidação deve ser revisado independentemente da mudança da variável em Cloudflare. Verifique se a CSP gerada no build **de produção** autoriza a URL atual da API: `${config.api}` já inclui Fly.io quando `PUBLIC_API_URL` é Fly.io.

## Rollback: retornar o frontend ao Render

Se a API Fly apresentar problema grave, manter o Supabase compartilhado intacto e executar uma reversão **controlada**:

1. Confirmar disponibilidade e compatibilidade do Render (saúde, autenticação e versão do protocolo). A alternativa só funciona enquanto o serviço Render permanecer operacional.
2. No Cloudflare Pages, ambiente **Production**, restaurar `PUBLIC_API_URL=https://colheita-feliz-api.onrender.com`.
3. Publicar **um novo build de produção** com esse valor e confirmar `window.COLHEITA_CONFIG.api` no navegador.
4. Verificar `GET /health`, login Google, `/v1/me/farm` HTTP 200 e uma consulta de leitura da quinta. Monitore erros de escrita antes de liberar uso normal.
5. Informar jogadores sobre eventual atualização da página: abas já abertas podem manter a configuração anterior. Para POST com resposta incerta, preservar o mesmo identificador de ação e consultar o estado antes de repetir.

Não restaurar bancos a partir de snapshots antigos nem executar ações de escrita duplicadas por trocar de host. **Não parar o Fly automaticamente durante o rollback**: faça isso apenas depois de confirmar que não há clientes/requisições pendentes e com aprovação específica.

## Custo e limitações

Na calculadora Fly.io consultada em 31/10/2026, uma VM `shared/1 CPU/512 MB` em GRU por 730 horas foi estimada em **US$ 5,97** para computação/memória, **não** como teto de cobrança. Podem existir custos de tráfego, rootfs, impostos e outros serviços; confirmar os valores atuais no painel e na [documentação oficial de preços](https://docs.fly.io/about/pricing). O IPv4 usado é compartilhado, não dedicado.

Como `auto_stop_machines = "off"`, a única máquina fica configurada para operar continuamente, mesmo sem jogadores. Sem redundância, deploys ou falhas podem causar indisponibilidade. Ainda é necessário observar CPU, RAM, filas, erros e latência com uso real, além de planejar teste de carga **em ambiente isolado** com 1, 10 e 30 usuários virtuais antes de afirmar capacidade.

## Referências

- [Configuração do Fly.io](https://docs.fly.io/reference/configuration)
- [Deploy](https://docs.fly.io/launch/deploy)
- [Secrets](https://docs.fly.io/apps/secrets)
- [Preços](https://docs.fly.io/about/pricing)
- [CLI e opções de deploy](https://docs.fly.io/flyctl/deploy/)

Este documento registra evidências obtidas até **01/11/2026**. Para operações futuras, prevalecem os estados efetivos no Cloudflare Pages, Fly.io, Supabase e Render.
