# Preparação da API no Fly.io — São Paulo

**Estado: preparação local para a aplicação existente `colheita-feliz-public`, organização `akaza-291`.** A inspeção de 31/10/2026 confirmou a máquina `d8946d7c92d7d8` parada em GRU. Os oito secrets exigidos e o adicional `AUTH_MODE` constam como Deployed, mas seus valores e o funcionamento não foram verificados. A falha antiga `Missing OWNER_USER_ID` antecede o cadastro manual. Esta atualização altera somente arquivos locais; não executa deploy, inicia máquinas, cadastra secrets ou altera recursos remotos. A configuração mantém uma máquina ligada quando aplicada; gastos exigem aprovação posterior. Alertas de orçamento não devem ser tratados como limite rígido de cobrança.

O objetivo é medir o mesmo servidor perto do Supabase em São Paulo. Cloudflare Pages e o banco atual permanecem onde estão. Autenticação, quatro threads HTTP, fila de 32 e pool HikariCP de quatro conexões continuam iguais. Não há garantia de capacidade para 30 jogadores antes do teste de carga.

## Arquivos e limites

- `fly.toml`: região `gru`, uma CPU compartilhada, 512 MB, HTTPS, porta 8080, sem suspensão automática e verificação `/health`.
- `Dockerfile`: reutilizado sem alterações, Java 21 e usuário `10001:10001`.
- CA pública Supabase injetada em `/etc/secrets/root.crt` pelo secret `SUPABASE_CA_CERT`, cujo valor deve ser o PEM codificado em base64.
- Usar a aplicação existente `colheita-feliz-public`; não criar outra aplicação. Capacidade de produção para 10–30 jogadores não está comprovada.
- `min_machines_running = 1` não limita o máximo de máquinas. O deploy futuro deve usar `--ha=false`, seguido da conferência de que há exatamente uma máquina em GRU. A aplicação já tem uma máquina; essa opção não remove máquinas extras existentes. Não usar autoscaling nesta etapa.
- Uma máquina não oferece alta disponibilidade. Reinícios e deploys podem interromper conexões; recibos e o mesmo identificador de ação continuam necessários para retomada.

## Custo estimado do teste econômico

Na [calculadora oficial](https://fly.io/calculator/), consultada em 31/10/2026, uma máquina GRU com uma CPU compartilhada e 512 MB ligada por 730 horas apresentou estimativa de **US$ 5,97**, mais encargos aplicáveis. É uma estimativa de computação e memória, não um teto de cobrança nem uma franquia gratuita. O custo varia com tempo de execução, preços e recursos utilizados.

Pelas [regras de cobrança](https://docs.fly.io/about/pricing), podem incidir tráfego de saída na América do Sul (US$ 0,04/GB), rootfs enquanto a máquina estiver parada (US$ 0,15/GB-mês) e eventuais IPs dedicados, volumes, impostos ou outros recursos contratados. Não há volume previsto e não é necessário IPv4 dedicado. Aprovar orçamento antes de iniciar o teste; não contratar recursos adicionais automaticamente.

## Antes de aplicar a configuração

1. Aprovar orçamento e confirmar preços de GRU no painel/tabela oficial. Não usar a tarifa de Virginia como estimativa definitiva para São Paulo.
2. Confirmar a organização `akaza-291` e a elegibilidade de cobrança pelo responsável. A CLI oficial v0.5.1 está instalada e autenticada; login MCP não substitui a autenticação da CLI.
3. Conferir o commit aprovado e passar pelo fluxo de PR. O arquivo não dispara deploy automático; não foi criado workflow ou token de CI.
4. Guardar configuração atual do Render e `PUBLIC_API_URL` do Pages sem imprimir senhas/tokens. Validar backup e restauração antes de liberar escrita no novo host.
5. Revalidar comportamento com o PostgreSQL descartável do CI e build normal. Não apontar a suíte de integração ou testes de carga ao banco dos jogadores.

## Aplicação futura, após aprovação

Se necessário, instalar `flyctl` apenas da fonte oficial, autenticar e verificar a organização correta. Usar o app já existente, sem executar `fly apps create` ou `fly launch` com valores padrão que criem banco, volumes ou máquinas extras.

```powershell
fly auth whoami
fly config validate --config fly.toml
fly status --app colheita-feliz-public
```

Esses comandos são um roteiro, não evidência de execução nesta atualização. A aplicação existente foi inspecionada em modo somente leitura. A aplicação de mudanças exige aprovação posterior; a validação TOML local não substitui a validação do Fly.

### Configuração e certificado

Definir no destino, fora do Git: `OWNER_USER_ID`, `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD`, `SUPABASE_URL`, `SUPABASE_PUBLISHABLE_KEY`, `ALLOWED_ORIGINS` e `SUPABASE_CA_CERT`. `AUTH_MODE`, `HOST` e `PORT` já estão no TOML. Não copiar credenciais para a conversa, argumentos de shell ou arquivos rastreados. Usar o painel ou `fly secrets import --stage` recebendo os valores por entrada padrão segura, sem registrar a entrada em logs.

O certificado é público; converter o arquivo PEM baixado do projeto em base64 para o secret de arquivo. Credenciais SQL permanecem em variáveis separadas. A URL JDBC deve manter `sslmode=verify-full` e `sslrootcert=/etc/secrets/root.crt`. Não usar `require`, trust-all ou desativar SSL.

Começar com o mesmo Session pooler de São Paulo usado pelo Render: muda-se apenas a hospedagem da API. Conexão direta por IPv6 pode ser um ensaio posterior; medir conectividade e latência antes de alterar esse segundo fator.

`ALLOWED_ORIGINS` deve conter a origem exata `https://colheita-feliz-amigos.pages.dev`. O endereço da API `*.fly.dev` não precisa virar uma origem CORS. Não criar wildcard.

```powershell
fly deploy --app colheita-feliz-public --config fly.toml --ha=false --update-only --only-machines d8946d7c92d7d8 --strategy immediate --no-public-ips --local-only
fly machine list --app colheita-feliz-public
fly status --app colheita-feliz-public
fly checks list --app colheita-feliz-public
```

Conferir exatamente uma máquina, região GRU, uma CPU compartilhada e 512 MB. Usar somente `memory = "512mb"`, sem o campo conflitante `memory_mb` da configuração remota anterior. Confirmar health check `/health` e configuração HTTPS; conectividade pública fica para outra aprovação, pois a inspeção anterior não encontrou IPs públicos atribuídos. Não alocar IPs nesta preparação. No runtime, confirmar que o usuário 10001 lê o certificado. Falha de leitura ou conexão impede a migração; não alterar permissões de autenticação/SSL para contorná-la. Não há volume: fazendas continuam persistidas no PostgreSQL.

## Primeira implantação controlada: aprovação separada

O TOML já está correto; não é necessário alterá-lo para corrigir o estado remoto. A leitura da configuração efetiva mostrou: shared/1 CPU/512 MB e porta 8080 corretos, mas `files` ausente, checks ausentes, autostop ligado e mínimo zero. Editar ou validar o TOML não aplica essas diferenças. A imagem atual é `registry.fly.io/colheita-feliz-public:deployment-6d448d2763593dcbc5764db590d87e56`; sua correspondência com o código local não foi comprovada.

### Condições antes de autorizar

- Aprovar explicitamente publicação da imagem, atualização e início da máquina existente, custo e acesso inicial ao banco real. `store.initialize(owner)` ocorre antes de abrir HTTP e pode executar DDL idempotente; uma primeira inicialização não é um teste somente leitura do banco. Conferir backup recuperável antes disso.
- Iniciar o Docker Desktop local e obter sucesso em `docker info`; não substituir silenciosamente `--local-only` por builder remoto. Revisar o commit e o contexto `.dockerignore`, que exclui arquivos locais e credenciais. Não incluir secrets no build.
- No checkout `codex/preparar-fly-gru`, conferir `git status --short --branch`, `fly auth whoami`, `fly status --app colheita-feliz-public`, `fly machine list --app colheita-feliz-public`, `fly secrets list --app colheita-feliz-public` e `fly config validate --config fly.toml`. Confirmar organização akaza-291, exatamente o ID esperado parado em GRU, oito nomes presentes, nenhum volume/IP e nenhum comando de release ou machine check no TOML. Interromper diante de divergência. Não copiar valores para evidências.

### Comando de implantação proposto, ainda não executado

Executar da raiz do repositório somente após autorização, usando o comando completo apresentado na seção anterior. `--ha=false` impede a criação de reservas de alta disponibilidade, mas sozinho não significa uma máquina máxima, nem preserva o estado parado. `--update-only` impede criar máquinas de novos grupos; `--only-machines` restringe os IDs alvo. `--strategy immediate` evita estratégia canary/bluegreen e aceita interrupção. `--no-public-ips` evita alocação automática de novos IPs. `--local-only` evita criar builder remoto; ainda haverá build local, push para o registry e nova release/configuração remota.

Tratar esse deploy como **capaz de iniciar automaticamente a máquina parada**, aplicar todos os secrets existentes à nova versão e começar cobrança. Não usá-lo como simples edição de configuração. As opções não constituem um limite financeiro nem autorização para criar máquina substituta: se o ID desaparecer ou a ferramenta solicitar recursos adicionais, interromper e revisar. Não há release command, volume ou machine check temporário previsto.

### Certificado e usuário 10001

`[[files]]` materializa o PEM decodificado do secret em `/etc/secrets/root.crt`; não é um volume. O secret precisa conter Base64 do PEM original Supabase Root 2021 CA. A presença do nome não comprova conteúdo correto. O Dockerfile executa como `10001:10001`; o TOML não oferece aqui uma declaração de dono/modo do arquivo. A documentação consultada não comprovou as permissões efetivas dessa máquina. Não mudar o processo para root nem enfraquecer `sslmode=verify-full` para contornar falha.

Depois do deploy aprovado, verificar a leitura como o próprio UID, sem imprimir o certificado:

```powershell
fly ssh console --app colheita-feliz-public --machine d8946d7c92d7d8 --user 10001 --command "sh -c 'id -u; test -s /etc/secrets/root.crt && test -r /etc/secrets/root.crt'"
```

Exigir UID 10001 e saída zero. Se a seleção numérica de usuário não for aceita, interromper essa verificação; uma leitura como root não a substitui. Conexão PostgreSQL bem-sucedida com verify-full é outra evidência necessária. Não executar `env`, `printenv`, `cat` do certificado ou consultas de credenciais. Se o processo morrer antes de permitir SSH, registrar a falha sem repetir inicializações automaticamente.

### IPv6 e teste interno sem IP público

`HOST=0.0.0.0` cobre IPv4. O HttpServer usa ServerSocketChannel; o OpenJDK 21 em Linux normalmente usa socket IPv6 dual stack quando IPv6 está disponível, mas isso depende do runtime e de não forçar `java.net.preferIPv4Stack=true`. A rede 6PN e `fly proxy` usam IPv6: não considerar o literal IPv4 uma garantia de acesso. Manter HOST nesta preparação e comprovar no contêiner; Docker indisponível impede essa comprovação agora.

Após aprovação de início e do túnel autenticado (que pode provisionar um peer WireGuard), selecionar explicitamente a máquina já ligada:

```powershell
fly proxy 18080:8080 --app colheita-feliz-public --bind-addr 127.0.0.1 --select
# Em outro terminal, após selecionar d8946d7c92d7d8:
Invoke-RestMethod http://127.0.0.1:18080/health
```

Não abrir o túnel enquanto ela estiver parada. Alternativa se o bind IPv6 falhar: verificar HTTP pelo loopback IPv4 dentro da própria máquina, via SSH autenticado, sem atribuir IP:

```powershell
fly ssh console --app colheita-feliz-public --machine d8946d7c92d7d8 --user 10001
```

Na sessão remota, executar apenas esta consulta HTTP local:

```bash
bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080; printf "GET /health HTTP/1.0\r\nHost: localhost\r\n\r\n" >&3; head -n 1 <&3'
exit
```

Aguardar resposta HTTP 200; bash precisa estar disponível na imagem. Este comando testa a API, não a conectividade 6PN do serviço. Se necessário ajustar HOST para `::`, isso exige mudança local revisada, teste Linux dual stack e novo deploy aprovado; não alterar automaticamente a máquina. SSH/túnel também dependem de acesso de rede e permissões da conta.

### Aceitação e interrupção

Conferir novamente um único ID em GRU, shared/1/512 MB, `files` com o caminho correto, GET `/health` saudável, autostop desligado, mínimo um e HTTPS. `/health` não consulta o banco. Conferir certificado legível e inicialização SQL sem falhas; sem isso, não trocar Pages nem executar ações dos jogadores. Limitar o primeiro ensaio a saúde/configuração, sem benchmark ou escrita sintética.

Uma máquina não oferece redundância. CPU compartilhada, memória limitada, falhas de certificado/SQL e bind IPv6 podem impedir o teste. Separar a autorização de deploy da autorização de encerramento: combinar previamente parada controlada em caso de erro ou ao fim da janela, sem executá-la nesta preparação. Uma falha de deploy não garante que a máquina ficou parada nem desfaz escrita SQL. Restaurar uma imagem anterior também exige autorização; não reaplicar a configuração antiga sem certificado. Manter Render e Pages intactos.

Estimativa para a janela: US$ 5,97 / 730 horas, aproximadamente US$ 0,00818 por hora de execução, mais encargos. Auto-stop desligado mantém cobrança contínua. Rootfs parado e tráfego podem continuar cobrados conforme a tabela acima. Confirmar preços e duração antes da autorização; não há teto automático.

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

Sintaxe TOML e contratos locais de nome, região, CPU/RAM, porta, Dockerfile, usuário, certificado e configuração conferidos com parser padrão. A CLI oficial v0.5.1 executou `fly config validate --config fly.toml` com resultado válido. Nesta atualização não houve criação de conta/app/máquina, deploy, inicialização, cadastro de secrets ou troca de endpoint. A máquina permanece parada, com a mesma revisão. Permissões reais do arquivo e desempenho em GRU permanecem pendentes. O daemon Docker local está indisponível; nenhum build Linux foi executado.

Fontes oficiais consultadas em 31/10/2026: [configuração](https://docs.fly.io/reference/configuration), [deploy e redundância padrão](https://docs.fly.io/launch/deploy), [secrets](https://docs.fly.io/apps/secrets), [preços](https://docs.fly.io/about/pricing). Context7 não foi usado para este provedor; configuração conferida na documentação oficial.

Referências para o primeiro teste: [opções de deploy](https://docs.fly.io/flyctl/deploy/), [rede privada IPv6](https://docs.fly.io/networking/private-networking/), [implementação OpenJDK 21 do socket dual stack](https://github.com/openjdk/jdk21u/blob/master/src/java.base/unix/native/libnio/ch/Net.c). Os comandos de implantação e teste remoto são propostas, não foram executados.
