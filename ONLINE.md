# Online com amigos

O desktop continua disponível. O servidor online cria fazendas novas e não importa saves locais. O piloto admite 20 participantes ativos no total, incluindo o dono, por convite aceito. O limite é definido no backend e exige publicar a API atualizada para mudar em produção; enviar apenas o link comum da página não autoriza uma conta nova. O cliente TypeScript/Canvas usa a mesma economia Java no servidor.

## Testar o servidor

### Ambiente local em um comando

Na raiz do repositório, execute `./dev.sh`. O script inicia o PostgreSQL 17
com `docker compose`, compila e sobe a API e o cliente Web. Abra
`http://127.0.0.1:5173`; a API fica em `http://127.0.0.1:8080`.
Requer Docker Compose, JDK 21, Node 24, npm e curl. A primeira execução instala
as dependências Web e pode baixar dependências Gradle. O banco fica em um volume
Docker local e continua ativo ao encerrar o script com Ctrl+C; use
`docker compose stop postgres` para pará-lo. As contas de desenvolvimento são
Alice (`dev-alice`) e Bob (`dev-bob`).

### Latência e medição

Para comparar ações sem usar fazendas pessoais, `LATENCY_BENCHMARK=once` habilita
um ensaio interno após o startup. Não cria endpoint HTTP. São 24 cenários com
participantes sintéticos e três amostras após um aquecimento por cenário. Cada
amostra roda em uma transação sempre revertida; fixtures e resultados não são
commitados. A limpeza de recibos do ensaio restringe-se ao ator sintético.
O ensaio usa uma conexão enquanto roda e deve ser habilitado apenas durante
diagnóstico controlado; remova a variável após a comparação para não repetir
trabalho nos próximos restarts. Logs `LATENCY_BENCHMARK` incluem apenas rótulos,
tempos e escopo, sem dados dos jogadores.

O tempo desse ensaio mede o serviço/transação após preparar o fixture e antes do
rollback: exclui setup, autenticação, rede do navegador, BEGIN/lock inicial e
commit. Não apresente esses valores como tempo de clique até resposta. O cliente
também registra no console `Colheita API timing` quando a API envia Server-Timing:
rota com UUID substituído, ação e durações. Não registra corpo, token ou saldo.

O servidor reutiliza conexões PostgreSQL com HikariCP 7.0.2: máximo de quatro,
mínimo de uma ociosa, espera por conexão de até cinco segundos e renovação após
dez minutos. O pool é encerrado com a API. TLS `verify-full`, transações, locks,
rollback e validação remota da sessão continuam ativos. O Session pooler do
Supabase não substitui este pool: a aplicação também precisa reaproveitar sua
conexão TCP/TLS até ele.

O servidor inclui `requestLease` com o próximo identificador nas respostas de
fazenda própria e nas respostas persistidas das mutações. O cliente valida UUIDv7
e validade com o cabeçalho `Date` do servidor e reutiliza essa reserva sem outro
GET de emissão no fluxo normal. O replay mantém a resposta completa idêntica,
incluindo a reserva. APIs antigas ou reservas expiradas usam o GET como fallback.
O identificador é consumido uma vez e descartado depois de
30 segundos sem uso. A intenção é gravada antes do POST; retries continuam usando
exatamente o mesmo identificador e corpo. Falha da antecipação não impede a
próxima tentativa. Não existe fila de ações econômicas offline.

Dentro de cada transação, a linha do participante já bloqueada com `FOR SHARE`
é reutilizada, com invalidação após escritas e sem cache entre pedidos. O refresh
pula o UPDATE do nome quando não mudou. Limpeza limitada e contagem de recibos
usam uma consulta; as escritas de fazenda(s) e recibo usam um único comando SQL
com CTE. Locks, limite de recibos, expiração e replay continuam autoritativos.
Uma transação com escritas adiadas sem recibo completo é revertida.

Respostas bem-sucedidas da API incluem `Server-Timing`, em milissegundos:
`auth` (Supabase Auth), `db_connect` (aquisição no pool), `db` (transação) e
`app` (processamento até enviar a resposta, incluindo as etapas anteriores).
`db` inclui esperas por locks e viagens SQL; não significa apenas CPU do banco.
Esses números não incluem a viagem de rede do navegador. As métricas não levam
tokens, SQL nem dados do jogador. Compare no painel Network do navegador após
aquecer a API; `/health` não mede autenticação nem banco.

Em 31/10/2026, antes desta correção, Amigos levou 2.335 ms até mostrar a lista
na sessão real, por observação da interface. O health acordado havia respondido
em 173–203 ms. Esses valores são uma amostra, não percentis nem uma promessa de
ganho. A melhora em produção depende de publicar cliente e API e repetir o
ensaio. Hospedagem e regiões não foram alteradas.

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

Preparação alternativa da API em São Paulo, sem deploy ou gastos autorizados: [Fly.io GRU](FLY.md). O piloto continua no Render até concluir medições e aprovar a troca.

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

Na visita a amigos, a toolbar oferece **Lançar inseto** e **Lançar erva**, cada qual limitado a um sucesso global por jogador a cada 24 horas reais. Selecione a ferramenta e depois uma planta em crescimento já regada. A [regra de pragas entre visitantes](docs/game-rule.md#131-pragas-lançadas-por-visitantes) define elegibilidade, coexistência e limpeza. As ferramentas normais do dono continuam removendo as pragas. A recusa informa o tempo restante quando o limite estiver ativo. Não há custo, estoque consumível ou recompensa por lançar pragas, nem compartilhamento de limite com o furto.

As ações `SPREAD_INSECTS` e `SPREAD_WEEDS` são exclusivas de `POST /v1/farms/{owner}/visit-actions`. Exigem amizade ativa, revisão da fazenda e da temporada; não são aceitas na rota própria. A inicialização cria de forma aditiva e idempotente a tabela privada `colheita.pest_cooldowns`, com chave `(actor, kind)` e `last_applied_at`, RLS e revogação de privilégios como nas demais tabelas. A aplicação e o prazo são gravados na mesma transação do recibo e das fazendas, sob os locks existentes. Rollback desfaz ambos; replay não renova a janela. O horário não vem do cliente e a expiração de recibos não remove os limites.

Incluir `pest_cooldowns` nos backups/restaurações do schema. Jogadores sem registro começam com as duas aplicações disponíveis. Não há mudança no snapshot ou save desktop v20: as pragas usam o estado já persistido. Publicar a API atualizada antes do frontend; clientes antigos continuam funcionando, mas não mostram os novos botões. Não executar versões antigas e novas da API simultaneamente durante a troca. A tabela não deve ser apagada num rollback; código anterior deixa de aceitar as novas ações e deve ser acompanhado pelo rollback do frontend.

O furto online segue o limite global da [regra de furto](docs/game-rule.md#13-furto-de-temporadas): um sucesso por jogador a cada 24 horas reais, independentemente da fazenda. O servidor calcula o prazo a partir do último sucesso; recusas e ajuda com amor não gastam a vez. A recusa mostra horas, minutos e segundos restantes pela mensagem já exibida no cliente.

Antes disso, cada temporada tem uma janela exclusiva de cinco minutos para o dono após amadurecer. O servidor deriva o instante efetivo dos dados de crescimento já persistidos, considerando água, solo seco, pausas e fertilizantes; não conta desde o primeiro acesso à fazenda. O dono pode colher na hora. O visitante recebe mensagem com o tempo restante, sem consumir seu limite diário. Ao completar cinco minutos, ainda precisa cumprir as demais condições de furto. A temporada seguinte ganha outra janela. Não há campo novo no save nem migração de snapshots; plantas existentes usam seu histórico de crescimento e não recebem cinco minutos extras por causa do deploy.

A inicialização cria, de forma aditiva e idempotente, `colheita.theft_cooldowns` (`actor` e `last_stolen_at`), com RLS e privilégios públicos revogados como nas demais tabelas privadas. Não altera snapshots ou o save desktop v20. Jogadores sem registro começam com furto disponível; não há reconstrução retroativa de furtos anteriores à implantação. Incluir essa tabela no backup/restauração do schema. Não apagar registros para limpar recibos: expiração do recibo não libera o limite.

O lock transacional por ator serializa a leitura do prazo, a transferência, a gravação do horário e o recibo. Um rollback desfaz todos esses efeitos; replay retorna a resposta original sem renovar o prazo. Logout e reinício não removem o registro. Publicar a API atualizada é necessário para ativar a regra; versões antigas não a aplicam, portanto não manter versões antigas e novas atendendo simultaneamente durante a troca. Rollback para código anterior também remove a fiscalização, mesmo preservando a tabela.

Comandos carregam UUID único e revisão decimal em string. A API registra recibo e mudanças na mesma transação. Repetir o mesmo pedido retorna o resultado gravado; trocar conteúdo com a mesma chave retorna 409. Visitas alteram as duas fazendas atomicamente, com locks ordenados. IDs de ação vêm de `GET /v1/request-id` autenticado e contêm o horário do servidor (UUIDv7). Repetições são aceitas por 24 horas; IDs antigos, futuros ou expirados retornam 410 sem executar, inclusive após a remoção do recibo. O cliente deve atualizar a fazenda para conferir o resultado antes de iniciar outra ação. Não gere um novo ID automaticamente para uma intenção cujo resultado continua desconhecido.

Os recibos recebem `expires_at` e são limpos em lotes de até 1.000, com locks ignorados por outros limpadores. Cada participante pode manter até 1.000 recibos válidos na janela de 24 horas; o limite recusa novas ações antes de alterar o jogo, mas permite repetir recibos existentes. A inicialização mantém a atualização idempotente da tabela de recibos e cria a tabela de limite de furto descrita acima; não muda snapshots nem saves locais. IDs UUIDv4 usados no protótipo anterior deixam de ser aceitos; consulte a fazenda para confirmar seus efeitos e não os converta em IDs novos.

Tempo, saldos, XP, proteção e sorteios pertencem ao servidor. Visitantes recebem apenas a projeção pública. O relógio local controla a aparência dia/noite e a trilha ambiente, sem alterar a economia.

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

O build Web copia os assets de `assets/classic`, `assets/sounds` e `web/public`, nessa ordem; as cópias otimizadas em `web/public` substituem somente os arquivos publicados. O MP3 diurno original permanece em `assets/sounds/farm_loop.mp3`; a cópia Web estéreo em `web/public/assets/sounds/farm_loop.mp3` usa 128 kb/s e preserva a duração inteira. Para regenerá-la com FFmpeg: `ffmpeg -i assets/sounds/farm_loop.mp3 -map 0:a:0 -vn -map_metadata -1 -c:a libmp3lame -b:a 128k web/public/assets/sounds/farm_loop.mp3`. FFmpeg não é necessário na CI. O build valida o pacote final e recusa qualquer arquivo acima de 25 MiB, limite por asset do Cloudflare Pages, indicando seu caminho e tamanho. As imagens carregam conforme o cenário ou a loja precisa. A pasta completa de arte vai ao host estático; não é baixada inteira no primeiro acesso. Dia/noite segue o relógio local; crescimento e tarefas usam tempo UTC do servidor. A previsão sem nova rega pode exceder a duração nominal porque solo seco cresce mais devagar.

### Enquadramento e interface principal

O navegador ajusta o mundo lógico de 1600 × 900 inteiro à área disponível, preservando as proporções da casa, dos terrenos e dos animais. Espaços externos usam o fundo do período como decoração; não repetem as faixas verdes da janela Desktop. Cliques, hover e dicas usam a mesma transformação após redimensionar. Clicar fora do mundo não envia ação.

Em janelas de até 1100 px de largura, o cabeçalho e a barra de ferramentas reservam espaço para a cena. O tutorial acompanha a altura do perfil. O atalho **T** abre a lista acessível de terras em todos os tamanhos; a HUD e os Ajustes não têm botão ou lista duplicada. Em telas estreitas, os terrenos ficam menores para manter a fazenda inteira visível; use essa lista para selecionar cada um com alvos maiores. As oito ferramentas mantêm botões de 53 × 53 CSS px e ícones de 38 px (cerca de 20% maiores); sementes e fertilizantes mostram o estoque selecionado.

O perfil usa os ícones originais de ouro/verdes e mantém os valores decimais recebidos. Durante visitas, mostra o nome visitado e os saldos do próprio jogador. A barra de XP usa o renderizador compartilhado da interface; preços, recompensas e elegibilidade continuam autoritativos no servidor.

O perfil usa branco com opacidade de 65%, o tutorial mantém 82%, e a barra inferior usa vidro escuro translúcido com ferramentas sem blocos opacos. Ouro e verdes ficam lado a lado. **Missões** usa a prancheta exportada do mesmo gerador do Desktop. Os sprites de terreno têm margens transparentes diferentes; o cliente normaliza seu corpo opaco para o losango clicável e desenha todos os terrenos antes das plantas. A planta morta usa o centro da arte recortada, largura de 86 unidades e base 10 unidades abaixo do centro do terreno, como no Desktop; a faixa inferior assimétrica das folhas não desloca sua âncora.

Os avisos de ação ficam acima da altura real da barra de ferramentas; a bandeja de sementes/adubos reserva espaço acima do aviso. Textos longos têm altura limitada e rolagem, sem cobrir as ferramentas. Falhas de conexão antes do login continuam visíveis.

Clique no avatar ou em **Ajustes → Trocar avatar** para escolher entre três avatares masculinos, três femininos e a foto da conta Google, quando disponível na sessão. A escolha é salva por conta neste navegador, sem sincronização com outros dispositivos ou com a lista de amigos. Não há upload nem gravação no Auth/banco. Imagem ausente ou com erro volta ao original; a política de imagens permite somente o host Google `https://lh3.googleusercontent.com` além dos assets locais. Autenticação, scripts e origens de conexão permanecem iguais.

O menu usa `amigos.png` para Amigos e permite duas linhas em Minha fazenda. O cursor padrão usa uma cópia recortada de `cursor-action.png`, reduzida a 32×32; o ícone Selecionar da barra continua `hud/cursor-ui.png`. Sobre o cenário, o ponteiro acompanha a ferramenta selecionada, incluindo o tipo de fertilizante e as ferramentas de visita, com hotspot central e assets de 32×32. Selecionar restaura a seta; menus mantêm a seta padrão. Avisos comuns e de nível usam fundo com opacidade de 78%; todos os diálogos usam fundo de 65% e cabeçalho de 40%, incluindo Missões, Depósito, Loja, Amigos e Ajustes. Cartões e painéis internos também são translúcidos. Somente os fundos são translúcidos, preservando texto e controles legíveis. `WebAssets.java` gera as cópias otimizadas dos ícones e avatares a partir dos originais.

Atualizações da Loja e do Depósito preservam a posição de rolagem e o controle focado. Se o item desaparecer ou ficar indisponível, o foco vai para Fechar. Trocar de categoria começa no topo. Uma venda confirmada depois de fechar o Depósito não reabre a janela.

Ao receber respostas fora de ordem, o cliente mantém a revisão econômica mais recente. Apenas no empate da revisão e do dono, compara `serverTime` para não voltar a uma projeção temporal anterior. Horários ausentes ou inválidos conservam o comportamento de aceitar a resposta recebida no empate; uma revisão maior sempre vence. UUID, corpo de retry e autoridade do servidor permanecem iguais.

Verifique com `npm.cmd test --prefix web`, `npm.cmd run typecheck --prefix web` e `npm.cmd run build:local --prefix web`. Os testes de viewport cobrem 390 × 844, 1024 × 600, 1280 × 1024, 1366 × 768 e 2560 × 1080. A revisão manual deve conferir tooltip, nome/saldos longos, bandejas, teclado, visita e modal nessas dimensões. Testes sintéticos não comprovam paridade funcional integral nem alterações já publicadas.

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

### Revisão visual isolada do cenário web

Execute `npm.cmd run review --prefix web` e abra `http://127.0.0.1:5174/`.
A cena usa dados sintéticos, sem autenticação, API, save ou alteração de saldo.
Os links exibem dia, pôr do sol, noite, filhotes e jovens. Inclui plantas em cinco
estágios, seca, pragas, morte, produtos prontos e todos os animais.
O processo atende apenas localhost; encerre com Ctrl+C. O bundle fica em
`.tools/web-visual-review` e não integra o build publicado.

A apresentação usa altura por estágio das plantas, escala de crescimento dos
animais, sprites de pragas e produtos somente quando prontos. A colmeia permanece
fixa, com abelhas próximas. Em painéis estreitos, HUD e ferramentas ficam fora
do canvas para preservar a área das terras. Esta revisão não comprova animações
de caminhada, fluxo econômico ou concorrência; esses ensaios pertencem às etapas
seguintes da paridade web.

A amostra de HUD e loja usa os mesmos renderizadores HTML do cliente real:
carteira com avatar genérico, progresso de XP, saldos preservados como texto e
loja com dez itens por página. Bloqueios por nível, VIP e animal já comprado
ficam explícitos. O seletor de semente/adubo mantém teclado e ganha preview.
Na revisão isolada, os botões de compra exibem apenas mensagem de demonstração.
Paginação mantém foco e Escape fecha o dialog. Não confundir essa fixture com
teste multiplayer.

### Depósito e painéis contextuais

O depósito tem abas de produtos, sementes e insumos. Produtos indicam quantidades
por moeda de aquisição; vender um produto usa a operação existente para o estoque
daquele item. Não há venda unitária nem preço estimado no cliente. Lotes VIP
bloqueados permanecem no estoque e não impedem a operação Vender tudo.

O popup da planta informa temporada, estágio, condições e dois tempos com rótulos
distintos: com rega contínua e sem nova rega. A posição acompanha a terra; em
painéis estreitos, a informação aparece fora do canvas. A atualização recebida da
API renova o popup mesmo com o cursor parado.

Clique em animal abre o alvo escolhido. Ações de alimentação, coleta ou venda
seguem espécie, estágio e estado projetados pelo servidor. Animais da fazenda
visitada são somente leitura; Loja → Meus animais continua mostrando os próprios,
inclusive durante visitas. O painel aberto acompanha as atualizações periódicas,
preservando o foco quando a ação ainda existe. Percentual de fome não é exibido,
pois esse dado não integra a projeção atual.

Tarefas usam cartões de estado e amigos usam nomes e botões de visita. Listagem,
criação e aceite de convite não preenchem nem fecham uma janela diferente após
resposta tardia. A revisão isolada apresenta esses painéis com dados sintéticos e
callbacks de demonstração. Validar economia e duas pessoas simultâneas continua
sendo parte dos ensaios de integração, com PostgreSQL descartável.

### Animações e áudio

- [ ] Abrir a ficha do animal ao clicar com o cursor no desktop. Deixar alimentação e coleta como ações explícitas na ficha.

Com o cursor selecionado, manter o mouse sobre um animal por 500 ms toca seu efeito uma vez. No celular, tocar no animal abre a inspeção e toca o efeito. Cada som novo interrompe o anterior. Os animais ficam silenciosos à noite e quando os efeitos estão desligados.

Animais móveis percorrem trajetos locais curtos, com repouso, orientação e quadros
de caminhada vinculados ao deslocamento. Pombo e colmeia permanecem fixos; pavão
alterna penas a cada quatro segundos. Pés, sombras e seleção usam a mesma posição
visual; produtos prontos continuam nas âncoras de produção. A cerca frontal separa
os cinco animais internos dos animais externos.

O relógio visual usa tempo decorrido, com atualização a30fps enquanto a aba está
visível e delta máximo de100ms. Não substitui o relógio autoritativo da API.
Noite local interrompe deslocamento e abelhas; retomar a aba não compensa tempo
visual oculto. Indicadores de rega, plantio, colheita e cuidados nas terras só
aparecem após sucesso confirmado, sem animar outra fazenda após sair da visita.

Ajustes oferece controles separados de música, efeitos e volumes, guardados na
chave local `colheita:audio`. Ambos começam desligados sem preferência anterior;
reprodução depende de interação do usuário. O toggle da toolbar controla efeitos.
Logout e aba oculta pausam música, ambiente, sons de animais e efeitos em andamento. Desligar Efeitos interrompe os sons desse canal imediatamente. A música usa `farm_loop.mp3` de dia e no pôr do sol e `noite.mp3` à noite. O controle Efeitos também regula a ambientação, com vento e pássaros de dia e grilos à noite. Os efeitos de ferramenta, furto e resgate tocam após a confirmação da API. Uma recusa do jogo toca o aviso de ação bloqueada. Quando a API confirma que uma planta própria passou de crescendo para pronta, toca um aviso por atualização, mesmo se várias plantas amadurecerem juntas.
Falha de storage, decoder ou autoplay não interrompe o jogo.

Na revisão isolada, Ajustes usa o player real e os botões Regar/Plantar/Colher
apresentam efeitos demonstrativos sem comandar a API. Para ouvir, habilite Efeitos
nos Ajustes antes de usar os botões. Audição subjetiva,
autoplay em Firefox/Safari e medição de fluidez em diferentes máquinas ainda
precisam de ensaio; testes automatizados não comprovam esses resultados.

## Tempos exibidos no cultivo

A loja identifica o tempo nominal como duração com rega em dia e sem pragas. Solo seco cresce à metade da velocidade; ervas e minhocas pausam o crescimento. O popup separa o restante com rega em dia do restante sem nova rega. Cada cenário mostra sua própria previsão com data e hora locais, calculadas a partir do horário retornado pela API: uma mantendo a rega em dia e sem pragas; outra sem nova rega e sem novas pragas. O contador atualiza a previsão ideal conforme o crescimento real: ela permanece estável com solo úmido e acompanha o atraso enquanto a planta continuar seca. Pragas e plantas sem a primeira rega não exibem previsão de conclusão. Os textos usam os dados existentes da API; não alteram regras, preços, duração, relógio autoritativo ou saves.

## Ajuda diária de 100 ouro

Missões oferece resgate manual de 100 ouro por dia UTC, sem acumular valores de dias ausentes e sem depender de saldo, nível ou VIP. O botão só aparece quando a API informa dailyAllowance; após o resgate fica bloqueado e mostra a próxima virada em data/hora local. Não há pagamento por GET, login ou tutorial, nem XP, verdes, itens ou progresso de tarefas. A regra fonte é a seção 12.1 de docs/game-rule.md.

CLAIM_DAILY_ALLOWANCE usa POST /v1/me/actions e os identificadores/revisões existentes. O backend define o valor; campos extras como amount são recusados. A tabela privada colheita.daily_allowances guarda somente actor e last_claimed_day, com RLS/revogação como as demais tabelas, criada aditivamente na inicialização. Crédito, último dia e recibo são confirmados na mesma transação, sob lock por ator e fazenda; replay não paga novamente e rollback desfaz tudo. A data usa UTC explícito e retrocessos não reabrem dias já pagos.

A consulta existente de fazenda usa LEFT JOIN para carregar o último dia, sem novo roundtrip SQL por ação. dailyAllowance (amount, available, nextResetAt) aparece somente na projeção própria. Não muda snapshot, save desktop v20 ou credenciais. Incluir daily_allowances e fazendas no mesmo backup/restauração; não apagar os registros ao limpar recibos ou fazer rollback. Jogadores sem registro começam com o resgate do dia disponível, sem pagamentos retroativos. Publicar API antes do frontend e evitar versões divergentes atendendo simultaneamente; a implantação cria estrutura, mas não distribui ouro.

## Inspeção de plantas com o cursor

Na própria fazenda e nas visitas, toque ou clique numa planta com o cursor selecionado para fixar o popup de crescimento.
Clique ou toque fora da planta para fechar o popup. Selecionar outra planta troca a inspeção.

A visita começa com o cursor Inspecionar plantas. Tocar numa planta abre e fixa o popup de crescimento, inclusive em celular; clicar com o cursor também inspeciona. A inspeção não envia ações sociais, mesmo em plantas prontas. Furto, amor e pragas exigem selecionar a ferramenta correspondente. As restrições continuam verificadas pelo servidor.
