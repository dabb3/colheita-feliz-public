# Colheita Feliz — Desktop e Web

Reimplementação independente e **não oficial** de um jogo de fazenda inspirado no Colheita Feliz clássico. O projeto oferece o jogo **Desktop em Java 21/libGDX**, um **cliente Web em TypeScript/Canvas** e uma **API online em Java 21**, que reutilizam o módulo de regras econômicas `core`.

## Visão geral

| Componente | Tecnologia | Situação |
| --- | --- | --- |
| Web | TypeScript, Canvas 2D, HTML/CSS, esbuild | Publicado no Cloudflare Pages |
| API | Java 21, módulo Gradle `server`, regras `core` | Hospedada no Fly.io, São Paulo (`gru`) |
| Contas e dados | Google/Supabase Auth e PostgreSQL Supabase | Fazendas online persistidas no servidor |
| Contingência da API | Render | Endpoint alternativo para rollback, sujeito a disponibilidade |

A branch padrão e branch configurada para publicação Web é `codex/online-core-compartilhado`. O Cloudflare Pages pode iniciar uma publicação após merges nessa branch. O Web usa fazendas próprias no banco Supabase e **não importa** saves Desktop. A versão online oferece login Google, convites, amigos, visitas e ações validadas pela API; A infraestrutura atual não possui alta disponibilidade e não há benchmark representativo para 10–30 jogadores simultâneos.

## Cliente Web: executar e testar

É necessário Node.js/npm. A partir da raiz do repositório, no Windows:

```powershell
npm.cmd ci --prefix web
npm.cmd test --prefix web
npm.cmd run typecheck --prefix web
npm.cmd run build:local --prefix web
```

O build local configura a API em `http://127.0.0.1:8080`. Para iniciar uma API local com contas de desenvolvimento e PostgreSQL descartável, consulte [ONLINE.md](ONLINE.md).

O cliente inclui um modo de revisão visual com dados sintéticos. Os menus de Loja, Depósito, Missões, Amigos e Ajustes são acessíveis por botões; a seleção de avatar é uma preferência local por conta e dispositivo, não uma atualização do perfil no banco.

| Tecla | Atalho no Web |
| --- | --- |
| `1`–`8` | Ferramentas da própria fazenda |
| `B`, `V`, `Q` | Loja, Depósito, Missões |
| `M`, `N` | Música e efeitos |
| `Esc` | Fechar janelas |

Os cursores acompanham a ferramenta selecionada; durante visitas, as ferramentas disponíveis mudam. Em telas pequenas, o cenário é reduzido para preservar a fazenda inteira, o que pode tornar os terrenos menores.

## Jogar

A fazenda começa vazia, no nível 1, com 500 ouro, 5 verdes e cinco terrenos em grama. Compre uma semente de nabo na loja e siga a orientação para arar, plantar, regar, esperar a maturação, colher e vender. O tutorial não concede bônus; cada ação mantém seu XP normal.

Duas tarefas fixas já contam durante o tutorial: cuidar da fazenda e realizar uma compra ou venda válida. Cada uma paga 1 verde por dia UTC, sem ouro, itens ou XP extra. Reabrir o jogo ou retroceder o relógio não reabre pagamentos antigos. Terrenos extras custam 50 verdes, mantendo a ordem e os requisitos de nível.

A curva exige `25 + 3 × (nível − 1)²` XP para o próximo nível e preserva todo excedente decimal. Níveis tabelados concedem apenas ouro e verdes. Um ensaio sem pragas, com nabo, animais e reinvestimento, chegou ao nível 30 em 31,333 dias com três visitas diárias; uma visita levou 64 dias e só plantas, 74,667 dias. São rotinas controladas sobre o domínio Java, sem VIP ou fertilizantes, não uma garantia de prazo para qualquer jogador.

A primeira rega inicia o crescimento. Terra seca reduz a velocidade; ervas daninhas e insetos pausam o crescimento até serem removidos (com os dois, só retoma após remover ambos) e não reduzem a colheita. Use herbicida e pesticida para removê-los. Cada temporada rende uma unidade; nas culturas com várias temporadas, a planta recomeça no estágio 1 após a coleta e precisa de nova rega. Retire a planta morta com a pá após a última colheita. Venda a produção no depósito. As listas da loja rolam pela roda do mouse ou pela barra lateral arrastável. Sementes e colheitas têm filtros Comuns e VIP. Os fertilizantes (comum, rápido, super e do amor) ficam no botão **Fertilizantes** da loja e são escolhidos pela bandeja da ferramenta 8. Cada planta aceita um por temporada, qualquer que seja o tipo; ele reduz o tempo restante da temporada atual (o excedente se perde) e só vale em planta regada, úmida e sem pragas. O do amor é só para a plantação de amigos.

A aba **Animais** rola uma lista de dez animais econômicos e cinco protetores. Cada tipo tem limite próprio de um animal e compra alternativa em ouro ou verdes, liberada por nível. Produtores chegam adultos e alimentados por 8 h; após uma coleta única, desaparecem e deixam uma unidade no depósito. Animais de crescimento chegam bebês, alimentados por 12 h; clique no adulto para vendê-lo. A loja também vende o adulto e os produtos na mesma aba.

A ração é genérica: crescimento custa 80 ouro e dura 12 h; produtor custa 120 ouro e dura 8 h. Clique no animal com fome para alimentar; uma refeição antecipada é recusada e não acumula tempo. A fome pausa o progresso, inclusive offline, sem reiniciá-lo. Dormir só muda a aparência. Protetores aceitam ambas as rações por 6 h; o clique usa primeiro a de crescimento, se disponível. Eles duram 30 dias reais desde a compra, sem revenda ou XP, e a fome não estende esse prazo. O Desktop calcula e persiste localmente a cobertura das terras. Amigos e visitas online são implementados separadamente no cliente Web e no servidor.

Os preços, tempos e rendimentos ficam em [crops.json](assets/data/crops.json); a progressão fica em [progression.json](assets/data/progression.json) e os animais em [animals.json](assets/data/animals.json). O jogo funciona sem dispositivo de áudio.

Uma fazenda nova começa com 500 moedas de ouro e 5 verdes. Saldos usam uma casa decimal; XP preserva frações. Produtos mantêm a moeda e o custo de aquisição da semente. A venda soma os lotes elegíveis por moeda, aplica lucro de 15% e arredonda o total para cima em uma casa decimal. Na aba **VIP**, 100 verdes compram 30 dias reais, acumuláveis; enquanto ativo, o lucro é 25%. Produtos exclusivos ficam bloqueados sem VIP e são ignorados em **Vender tudo**.

Produtos animais usam o custo e a moeda reais da aquisição do produtor. A ração comprada depois não muda essa base. A venda adulta usa a origem do próprio animal. Coleta e venda adulta concedem o XP da espécie uma única vez.

O fundo acompanha o horário real local do computador: dia das 06:00 às 16:59; pôr do sol das 17:00 às 18:30; noite das 18:31 às 05:59. Os animais dormem durante esse mesmo período noturno e acordam às 06:00. À noite, os passeios e a exibição da cauda do pavão param; as abelhas recolhem-se na colmeia. As poses de sono usam os sprites disponíveis no acervo. A troca é automática com o jogo aberto e não depende da aceleração por `T`, nem altera crescimento, recompensas ou o save.

O GitHub Actions executa build, testes, SpotBugs e JaCoCo em pushes e PRs. O SpotBugs roda como parte do `build` com `ignoreFailures = false`; achados novos quebram o build local e no CI. Warnings pré-existentes estão listados em `config/spotbugs/exclude.xml`, escopados por classe e método para não mascarar código novo. Os resultados são publicados como annotations no PR via SARIF. O JaCoCo gera relatório de cobertura em `build/reports/jacoco/test/`. Tags `vMAJOR.MINOR.PATCH` geram um Release com o ZIP e checksum SHA-256. A branch padrão atual é `codex/online-core-compartilhado`. Alterações de produção devem passar por Pull Request e revisão independente, conforme as regras ativas do GitHub.

## Licença

Todas as imagens desse projeto foram criados com Imagegen.

Os terrenos usam uma grade isométrica uniforme de 170 × 72 unidades, elevada para deixar a última fileira acima da barra de ferramentas. O desenho compensa as margens transparentes dos sprites com sobreposição entre os tiles; plantas, destaque e cliques acompanham a mesma projeção.

APENAS PARA USO NÃO COMERCIAL - Esse projeto tem por objetivo levatar um jogo do orkut por simples nostalgia de jogar Colheita Feliz novamente.