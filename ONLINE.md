# Online com amigos

O desktop continua disponível. O servidor online cria fazendas novas e não importa saves locais. O piloto admite cinco participantes ativos, por convite aceito. O cliente TypeScript/Canvas usa a mesma economia Java no servidor.

## Testar o servidor

Requer JDK 21, Gradle Wrapper e PostgreSQL 17 descartável. Nunca aponte os testes para dados pessoais. A integração exige banco `colheita_test` em localhost e cria schemas exclusivos por caso.

```powershell
docker run --detach --rm --name colheita-online-qa --publish 127.0.0.1:55439:5432 --env POSTGRES_HOST_AUTH_METHOD=trust --env POSTGRES_DB=colheita_test postgres:17-alpine
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:55439/colheita_test'
.\gradlew.bat build :server:integrationTest
```

O modo sem senha acima é exclusivo do banco descartável, exposto apenas em localhost.

```powershell
$env:AUTH_MODE='development'
$env:HOST='127.0.0.1'
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:55439/colheita_test'
$env:DATABASE_USER='postgres'
$env:ALLOWED_ORIGINS='http://127.0.0.1:5173'
.\gradlew.bat :server:run
```

No desenvolvimento, `Bearer dev-alice` e `Bearer dev-bob` representam duas contas fixas. Apenas Alice começa autorizada; Bob precisa aceitar seu convite. O servidor recusa bind público nesse modo. Nenhuma rota concede saldo, XP ou altera o relógio.

## Configuração de produção

Construir com `docker build -t colheita-server .`. O runtime inclui somente Java, core, catálogo e servidor. Configurar no provedor, nunca no repositório:

- `AUTH_MODE`: `production` (padrão).
- `OWNER_USER_ID`: UUID da conta Google previamente criada no Supabase Auth.
- `DATABASE_URL`: conexão JDBC PostgreSQL com `sslmode=verify-full` e CA confiável.
- `DATABASE_USER` e `DATABASE_PASSWORD`: credenciais exclusivas do servidor.
- `SUPABASE_URL` e `SUPABASE_PUBLISHABLE_KEY`: projeto Auth e chave pública.
- `ALLOWED_ORIGINS`: origens HTTPS exatas, separadas por vírgula.
- `PORT`: porta fornecida pelo host; `HOST` usa `0.0.0.0` em produção.

O servidor valida cada sessão em `/auth/v1/user`, antes de consultar recibos ou dados. Schema `colheita` tem RLS sem políticas para clientes e privilégios públicos revogados. Não exponha credenciais SQL ou chave de serviço ao navegador. Use uma conta SQL exclusiva capaz de criar o schema privado, sem acesso aos demais schemas.

Convites são de uso único e expiram em sete dias. Amizade exige aceite. A revogação administrativa desativa o participante em `colheita.players` e também bloqueia reenvio de pedidos anteriores. Não há painel público de administração.

Ao abrir a própria fazenda, o servidor atualiza o nome público pelo perfil autenticado no Supabase. Isso substitui o nome inicial do dono e acompanha mudanças de perfil. O nome é limitado a 40 caracteres, sem controles; participantes desativados continuam bloqueados.

## Consistência e operação

Comandos carregam UUID único e revisão decimal em string. A API registra recibo e mudanças na mesma transação. Repetir o mesmo pedido retorna o resultado gravado; trocar conteúdo com a mesma chave retorna 409. Visitas alteram as duas fazendas atomicamente, com locks ordenados. IDs de ação vêm de `GET /v1/request-id` autenticado e contêm o horário do servidor (UUIDv7). Repetições são aceitas por 24 horas; IDs antigos, futuros ou expirados retornam 410 sem executar, inclusive após a remoção do recibo. O cliente deve atualizar a fazenda para conferir o resultado antes de iniciar outra ação. Não gere um novo ID automaticamente para uma intenção cujo resultado continua desconhecido.

Os recibos recebem `expires_at` e são limpos em lotes de até 1.000, com locks ignorados por outros limpadores. Cada participante pode manter até 1.000 recibos válidos na janela de 24 horas; o limite recusa novas ações antes de alterar o jogo, mas permite repetir recibos existentes. A implantação atualiza somente a estrutura da tabela de recibos; não muda snapshots nem saves locais. IDs UUIDv4 usados no protótipo anterior deixam de ser aceitos; consulte a fazenda para confirmar seus efeitos e não os converta em IDs novos.

Tempo, saldos, XP, proteção e sorteios pertencem ao servidor. Visitantes recebem apenas a projeção pública. O relógio local controla somente a aparência dia/noite.

Antes de publicar: validar duas contas, configurar Google OAuth e redirects, testar restauração de backup PostgreSQL e conferir limites gratuitos vigentes. Proposta: Cloudflare Pages para arquivos, Render para API e Supabase para PostgreSQL/Auth. Nenhum deploy realizado. Serviços gratuitos podem suspender por inatividade ou atingir quotas.

Backup usa `pg_dump` do schema `colheita`, guardado fora de artefatos públicos. Restaurar primeiro em banco isolado e conferir participantes, fazendas e recibos. Para rollback de código, guardar a imagem anterior e preservar compatibilidade do schema. Mudanças futuras de schema exigem migração explícita e backup.


## Cliente web

Requer Node 24. As dependências são locais e fixadas no lockfile.

```powershell
npm.cmd ci --ignore-scripts --prefix web
npm.cmd test --prefix web
npm.cmd run build:local --prefix web
npm.cmd run serve --prefix web
```

Abra `http://127.0.0.1:5173`. Entre como Alice, crie um convite em Amigos e abra o link em outra aba como Bob. Bob só recebe fazenda depois de aceitar. Loja, ferramentas, depósito, animais, alimentação, fertilizantes, VIP, tarefas, expansão e visitas usam a API. Há lista de terras acessível pelo teclado. Atalhos B/V abrem loja/depósito e 2–8 selecionam as ferramentas do tutorial. T/F3 não existem no online.

Uma compra cujo resultado ficou desconhecido bloqueia novas ações até confirmar a mesma requisição. O navegador guarda somente essa intenção por conta e aba, além da sessão Auth; não guarda um save econômico. Não limpe o armazenamento da aba enquanto houver ação pendente. Faça login novamente com a mesma conta se a sessão expirar. Sem conexão, novas ações ficam indisponíveis.

As imagens carregam conforme o cenário ou a loja precisa. A pasta completa de arte vai ao host estático; não é baixada inteira no primeiro acesso. Dia/noite segue o relógio local; crescimento e tarefas usam tempo UTC do servidor. A previsão sem nova rega pode exceder a duração nominal porque solo seco cresce mais devagar.

O piloto foi desenhado para Chrome/Edge em desktop, a partir de 1280×720. Não há promessa de suporte móvel ou reprodução visual idêntica ao desktop. Sons de ferramentas existentes são opcionais; novos sons de animais não foram gerados.

## Publicação gratuita proposta

Os artefatos estão preparados, mas contas, OAuth e deploy externo ainda precisam ser configurados pelo responsável. Não ative planos pagos, cartão obrigatório ou pings para manter instâncias acordadas. Confira as quotas dos provedores no dia da publicação.

1. Criar projeto Supabase Free. Ativar Google em Auth e configurar cliente OAuth no Google Cloud, com callback do projeto Supabase. Configurar a origem final do site na lista de redirects do Supabase. Não colocar client secret do Google no código.
2. Preparar serviço Render **Free**, usando o Dockerfile da raiz. Anotar sua origem HTTPS. O primeiro deploy só estará pronto depois das variáveis de produção e do UUID do dono.
3. Preparar Cloudflare Pages Free com este repositório: comando `cd web && npm ci --ignore-scripts && npm run build`, saída `web/dist`, Node 24. Informar `PUBLIC_API_URL` (origem Render), `PUBLIC_SUPABASE_URL` e `PUBLIC_SUPABASE_PUBLISHABLE_KEY`. Essas três configurações são públicas; nenhuma senha SQL/chave de serviço pertence ao build.
4. Publicar o cliente e entrar com Google. O botão de login permanece disponível mesmo antes de a API estar pronta. Conferir no Supabase Auth o UUID criado; esse será `OWNER_USER_ID` no Render. Não usar UUID fictício para bootstrap.
5. Configurar o servidor com a conexão PostgreSQL do pooler Supabase adequado ao host (IPv4 quando necessário), SSL, usuário/senha, projeto Auth e `ALLOWED_ORIGINS` exata do Pages. Publicar o servidor. Nenhum volume local guarda progresso online.
6. Validar login, compra, convite, aceite, visita e retomada após suspensão com duas contas reais. Salvar backup e testar restauração em banco isolado antes de compartilhar com amigos.

Não execute build:local no site publicado: ele só usa contas de teste e localhost. O build de produção recusa endpoints sem HTTPS e exige as três variáveis públicas. Render pode dormir e atrasar o primeiro acesso; Supabase Free também tem limites e regras de inatividade. O piloto usa requisições HTTP normais, sem websocket permanente.

### Arte e tamanho inicial

As variantes pequenas de ícones e casas em `web/public` são derivadas dos assets existentes. Para regenerar, com JDK 21 na raiz: `java web/scripts/WebAssets.java`. O build usa esses arquivos prontos e não exige Java no Cloudflare. O teste de payload confere todos os períodos do dia e reserva espaço para scripts/API; não mede latência em uma conexão real de 10 Mbps. A licença e atribuição do acervo original permanecem aplicáveis.


Fontes de hospedagem conferidas em 29/10/2026: [limites do Cloudflare Pages](https://developers.cloudflare.com/pages/platform/limits/), [instâncias gratuitas Render](https://render.com/docs/free) e [Supabase Free](https://supabase.com/pricing). Render suspende a API após 15 minutos sem tráfego e pode levar cerca de um minuto para acordar. Supabase Free oferece 500 MB de banco e pausa projetos após uma semana de inatividade. Esses planos servem ao piloto e não garantem disponibilidade contínua.


### Validação do banco em produção

A URL JDBC deve ter `sslmode=verify-full`; `require` não autentica o servidor e é recusado. Forneça a CA do banco em um arquivo legível pelo processo, por exemplo `sslrootcert=/etc/secrets/root.crt`, usando o certificado do projeto no painel Supabase. A URL aceita somente `sslmode` e `sslrootcert`, sem parâmetros duplicados ou factories SSL customizadas. Usuário e senha ficam em variáveis próprias. Não desative a validação para contornar erros de certificado ou hostname.

Após aceitar uma visita sem mudança, as revisões das fazendas permanecem estáveis; relógio e atualização real de estado ainda podem mudar a revisão. Cada ação bem-sucedida continua sendo atômica.
