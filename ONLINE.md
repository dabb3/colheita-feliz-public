# Online com amigos

O desktop continua disponível. O servidor online cria fazendas novas e não importa saves locais. O piloto admite cinco participantes ativos, por convite aceito. O cliente web está em implementação.

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

## Consistência e operação

Comandos carregam UUID único e revisão decimal em string. A API registra recibo e mudanças na mesma transação. Repetir o mesmo pedido retorna o resultado gravado; trocar conteúdo com a mesma chave retorna 409. Visitas alteram as duas fazendas atomicamente, com locks ordenados. IDs de ação vêm de `GET /v1/request-id` autenticado e contêm o horário do servidor (UUIDv7). Repetições são aceitas por 24 horas; IDs antigos, futuros ou expirados retornam 410 sem executar, inclusive após a remoção do recibo. O cliente deve atualizar a fazenda para conferir o resultado antes de iniciar outra ação. Não gere um novo ID automaticamente para uma intenção cujo resultado continua desconhecido.

Os recibos recebem `expires_at` e são limpos em lotes de até 1.000, com locks ignorados por outros limpadores. Cada participante pode manter até 1.000 recibos válidos na janela de 24 horas; o limite recusa novas ações antes de alterar o jogo, mas permite repetir recibos existentes. A implantação atualiza somente a estrutura da tabela de recibos; não muda snapshots nem saves locais. IDs UUIDv4 usados no protótipo anterior deixam de ser aceitos; consulte a fazenda para confirmar seus efeitos e não os converta em IDs novos.

Tempo, saldos, XP, proteção e sorteios pertencem ao servidor. Visitantes recebem apenas a projeção pública. O relógio local controla somente a aparência dia/noite.

Antes de publicar: validar duas contas, configurar Google OAuth e redirects, testar restauração de backup PostgreSQL e conferir limites gratuitos vigentes. Proposta: Cloudflare Pages para arquivos, Render para API e Supabase para PostgreSQL/Auth. Nenhum deploy realizado. Serviços gratuitos podem suspender por inatividade ou atingir quotas.

Backup usa `pg_dump` do schema `colheita`, guardado fora de artefatos públicos. Restaurar primeiro em banco isolado e conferir participantes, fazendas e recibos. Para rollback de código, guardar a imagem anterior e preservar compatibilidade do schema. Mudanças futuras de schema exigem migração explícita e backup.


### Validação do banco em produção

A URL JDBC deve ter `sslmode=verify-full`; `require` não autentica o servidor e é recusado. Forneça a CA do banco em um arquivo legível pelo processo, por exemplo `sslrootcert=/etc/secrets/root.crt`, usando o certificado do projeto no painel Supabase. A URL aceita somente `sslmode` e `sslrootcert`, sem parâmetros duplicados ou factories SSL customizadas. Usuário e senha ficam em variáveis próprias. Não desative a validação para contornar erros de certificado ou hostname.

Após aceitar uma visita sem mudança, as revisões das fazendas permanecem estáveis; relógio e atualização real de estado ainda podem mudar a revisão. Cada ação bem-sucedida continua sendo atômica.
