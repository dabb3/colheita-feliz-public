# Colheita Feliz — Desktop e Web

Reimplementação independente e **não oficial** de um jogo de fazenda inspirado no Colheita Feliz clássico. O projeto oferece o jogo **Desktop em Java 21/libGDX**, um **cliente Web em TypeScript/Canvas** e uma **API online em Java 21**, que reutilizam o módulo de regras econômicas `core`.

**[Jogar online](https://colheita-feliz-amigos.pages.dev)** · [Online e testes](ONLINE.md) · [Infraestrutura Fly.io](FLY.md) · [Regras do jogo](docs/game-rule.md) · [Contribuição](CONTRIBUTING.md)

## Visão geral

| Componente | Tecnologia | Situação |
| --- | --- | --- |
| Desktop | Java 21, libGDX, save local | Disponível; sem sincronização com contas Web |
| Web | TypeScript, Canvas 2D, HTML/CSS, esbuild | Publicado no Cloudflare Pages |
| API | Java 21, módulo Gradle `server`, regras `core` | Hospedada no Fly.io, São Paulo (`gru`) |
| Contas e dados | Google/Supabase Auth e PostgreSQL Supabase | Fazendas online persistidas no servidor |
| Contingência da API | Render | Endpoint alternativo para rollback, sujeito a disponibilidade |

**Endereços:** [Web em produção](https://colheita-feliz-amigos.pages.dev) · [API de produção](https://colheita-feliz-public.fly.dev/health). O frontend de produção usa a variável de build `PUBLIC_API_URL=https://colheita-feliz-public.fly.dev`.

A branch padrão e branch configurada para publicação Web é `codex/online-core-compartilhado`. O Cloudflare Pages pode iniciar uma publicação após merges nessa branch. O Desktop usa arquivos de save locais; o Web usa fazendas próprias no banco Supabase e **não importa** saves Desktop. A versão online oferece login Google, convites, amigos, visitas e ações validadas pela API; a paridade integral com o Desktop continua em evolução. A infraestrutura atual não possui alta disponibilidade e não há benchmark representativo para 10–30 jogadores simultâneos.

## Cliente Web: executar e testar

É necessário Node.js/npm. A partir da raiz do repositório, no Windows:

```powershell
npm.cmd ci --prefix web
npm.cmd test --prefix web
npm.cmd run typecheck --prefix web
npm.cmd run build:local --prefix web
```

O build local configura a API em `http://127.0.0.1:8080`. Para iniciar uma API local com contas de desenvolvimento e PostgreSQL descartável, consulte [ONLINE.md](ONLINE.md). **Nunca use dados dos jogadores nos testes de integração ou carga.** O build de produção utiliza `PUBLIC_API_URL`, `PUBLIC_SUPABASE_URL` e `PUBLIC_SUPABASE_PUBLISHABLE_KEY`, fornecidas pelo ambiente de publicação. Não coloque credenciais no Git.

O cliente inclui um modo de revisão visual com dados sintéticos. Os menus de Loja, Depósito, Missões, Amigos e Ajustes são acessíveis por botões; a seleção de avatar é uma preferência local por conta e dispositivo, não uma atualização do perfil no banco.

| Tecla | Atalho no Web |
| --- | --- |
| `1`–`8` | Ferramentas da própria fazenda |
| `B`, `V`, `Q` | Loja, Depósito, Missões |
| `T` | Abrir a lista de terras |
| `M`, `N` | Música e efeitos |
| `Esc` | Fechar janelas |

Os cursores acompanham a ferramenta selecionada; durante visitas, as ferramentas disponíveis mudam. Em telas pequenas, o cenário é reduzido para preservar a fazenda inteira, o que pode tornar os terrenos menores. A lista de terras pode ser aberta com `T` na implementação atual.

## Desktop: executar e compilar

Instale JDK 21 e configure `JAVA_HOME`. O Gradle Wrapper está incluído; a primeira execução baixa o Gradle e as dependências Maven.

```powershell
.\gradlew.bat run
.\gradlew.bat test
.\gradlew.bat build
.\gradlew.bat installDist
```

Em Linux/macOS use `./gradlew`. No macOS o launcher configura `-XstartOnFirstThread`. A janela abre em 1280 × 720.

`build` compila, executa os testes e gera distribuições em `build/distributions/`. `installDist` prepara o launcher em `build/install/colheita-feliz-java/bin/`. Os assets e dados necessários já estão incluídos; Python e ferramentas de geração de arte não são necessários.

## Jogar

A fazenda começa vazia, no nível 1, com 500 ouro, 5 verdes e cinco terrenos em grama. Compre uma semente de nabo na loja e siga a orientação para arar, plantar, regar, esperar a maturação, colher e vender. O tutorial não concede bônus; cada ação mantém seu XP normal.

Duas tarefas fixas já contam durante o tutorial: cuidar da fazenda e realizar uma compra ou venda válida. Cada uma paga 1 verde por dia UTC, sem ouro, itens ou XP extra. Reabrir o jogo ou retroceder o relógio não reabre pagamentos antigos. Terrenos extras custam 50 verdes, mantendo a ordem e os requisitos de nível.

A curva exige `25 + 3 × (nível − 1)²` XP para o próximo nível e preserva todo excedente decimal. Níveis tabelados concedem apenas ouro e verdes. Um ensaio sem pragas, com nabo, animais e reinvestimento, chegou ao nível 30 em 31,333 dias com três visitas diárias; uma visita levou 64 dias e só plantas, 74,667 dias. São rotinas controladas sobre o domínio Java, sem VIP ou fertilizantes, não uma garantia de prazo para qualquer jogador.

| Tecla | Ação |
| --- | --- |
| `1`–`8` | Cursor, pá, sementes, regador, herbicida, pesticida, luvinha, fertilizante |
| `B` / `V` | Loja / depósito |
| `Q` | Missões diárias |
| `Esc` | Fechar janelas |
| `M` / `N` | Alternar música / efeitos |
| `S` | Salvar |
| `T` | Alternar tempo 1× / 60× para desenvolvimento |
| `F3` | Overlay técnico |

A primeira rega inicia o crescimento. Terra seca reduz a velocidade; ervas daninhas e insetos pausam o crescimento até serem removidos (com os dois, só retoma após remover ambos) e não reduzem a colheita. Use herbicida e pesticida para removê-los. Cada temporada rende uma unidade; nas culturas com várias temporadas, a planta recomeça no estágio 1 após a coleta e precisa de nova rega. Retire a planta morta com a pá após a última colheita. Venda a produção no depósito. As listas da loja rolam pela roda do mouse ou pela barra lateral arrastável. Sementes e colheitas têm filtros Comuns e VIP. Os fertilizantes (comum, rápido, super e do amor) ficam no botão **Fertilizantes** da loja e são escolhidos pela bandeja da ferramenta 8. Cada planta aceita um por temporada, qualquer que seja o tipo; ele reduz o tempo restante da temporada atual (o excedente se perde) e só vale em planta regada, úmida e sem pragas. O do amor é só para a plantação de amigos.

A aba **Animais** rola uma lista de dez animais econômicos e cinco protetores. Cada tipo tem limite próprio de um animal e compra alternativa em ouro ou verdes, liberada por nível. Produtores chegam adultos e alimentados por 8 h; após uma coleta única, desaparecem e deixam uma unidade no depósito. Animais de crescimento chegam bebês, alimentados por 12 h; clique no adulto para vendê-lo. A loja também vende o adulto e os produtos na mesma aba.

A ração é genérica: crescimento custa 80 ouro e dura 12 h; produtor custa 120 ouro e dura 8 h. Clique no animal com fome para alimentar; uma refeição antecipada é recusada e não acumula tempo. A fome pausa o progresso, inclusive offline, sem reiniciá-lo. Dormir só muda a aparência. Protetores aceitam ambas as rações por 6 h; o clique usa primeiro a de crescimento, se disponível. Eles duram 30 dias reais desde a compra, sem revenda ou XP, e a fome não estende esse prazo. O Desktop calcula e persiste localmente a cobertura das terras. Amigos e visitas online são implementados separadamente no cliente Web e no servidor.

Os preços, tempos e rendimentos ficam em [crops.json](assets/data/crops.json); a progressão fica em [progression.json](assets/data/progression.json) e os animais em [animals.json](assets/data/animals.json). O jogo funciona sem dispositivo de áudio.

## Save

O save local atual é `save/game-rules-v4.json`, relativo ao diretório de execução, no formato v20 (`economy-2026-10`). Ele guarda progressão, tarefas, lotes, culturas, animais, preferências, revisão monotônica dos terrenos e seleção de proteção. Um save v19 abre com metadados sociais novos, sem converter a economia; a próxima gravação o atualiza para v20. O executável v19 recusa esse arquivo atualizado.

Faça backup antes de trocar de versão.

A curva, o tutorial e as tarefas mudaram de significado. Saves v16, v17 e v18, assim como formatos anteriores, futuros ou inválidos, são recusados sem conversão nem sobrescrita, mesmo quando selecionados por `COLHEITA_SAVE_PATH`. Os arquivos `game-rules-v1.json`, `game-rules-v2.json`, `game-rules-v3.json` e `game.json` permanecem em disco.

Se houver erro de carga, o launcher mostra o caminho e permite cancelar ou escolher um arquivo inexistente para outra fazenda; um destino existente é recusado. O caminho escolhido vale para essa execução: para reabrir essa fazenda depois, informe o mesmo caminho em `COLHEITA_SAVE_PATH`. Nenhum save é restaurado ou convertido automaticamente.

Há autosave e progresso offline; saves não são versionados. Cada instância desktop mantém um bloqueio cooperativo por destino; outra instância no mesmo arquivo é recusada. O arquivo `.lock` pode permanecer vazio após encerrar e não precisa ser apagado. A escrita usa temporários exclusivos; arquivos temporários antigos não são carregados como save.

Uma fazenda nova começa com 500 moedas de ouro e 5 verdes. Saldos usam uma casa decimal; XP preserva frações. Produtos mantêm a moeda e o custo de aquisição da semente. A venda soma os lotes elegíveis por moeda, aplica lucro de 15% e arredonda o total para cima em uma casa decimal. Na aba **VIP**, 100 verdes compram 30 dias reais, acumuláveis; enquanto ativo, o lucro é 25%. Produtos exclusivos ficam bloqueados sem VIP e são ignorados em **Vender tudo**.

Produtos animais usam o custo e a moeda reais da aquisição do produtor. A ração comprada depois não muda essa base. A venda adulta usa a origem do próprio animal. Coleta e venda adulta concedem o XP da espécie uma única vez.

As etapas econômicas e o núcleo social local do Desktop estão implementados neste checkout. A API interna de ensaio transfere uma temporada pronta e desprotegida, preservando origem e impedindo consumo duplicado por revisão. Protetores cobrem terrenos distintos com seleção persistida; amor usa o estoque do visitante e o limite compartilhado da temporada, sem recompensas. São operações sobre dois estados locais autorizados pelo chamador controlado. As contas, a lista de amigos, as visitas e a comunicação de rede estão disponíveis na implementação Web/API, sem sincronização com o save Desktop. As regras completas estão em [game-rule.md](docs/game-rule.md).

O fundo acompanha o horário real local do computador: dia das 06:00 às 16:59; pôr do sol das 17:00 às 18:30; noite das 18:31 às 05:59. Os animais dormem durante esse mesmo período noturno e acordam às 06:00. À noite, os passeios e a exibição da cauda do pavão param; as abelhas recolhem-se na colmeia. As poses de sono usam os sprites disponíveis no acervo. A troca é automática com o jogo aberto e não depende da aceleração por `T`, nem altera crescimento, recompensas ou o save.

## Desenvolvimento e distribuição

Código em `src/main/java`, testes em `src/test/java` e recursos em `assets/`. O módulo Gradle `core` compila domínio, regras, persistência e catálogos sem libGDX; o aplicativo desktop depende desse módulo. A classe `FarmGame` pertence ao pacote `desktop`. `./gradlew build` verifica ambos; `./gradlew :core:test` verifica o motor sem o framework gráfico no classpath. Para capturas descartáveis, execute `./gradlew visualReview`; as imagens ficam em `build/visual-review/` e o save do jogador não é usado.

O GitHub Actions executa build, testes, SpotBugs e JaCoCo em pushes e PRs. O SpotBugs roda como parte do `build` com `ignoreFailures = false`; achados novos quebram o build local e no CI. Warnings pré-existentes estão listados em `config/spotbugs/exclude.xml`, escopados por classe e método para não mascarar código novo. Os resultados são publicados como annotations no PR via SARIF. O JaCoCo gera relatório de cobertura em `build/reports/jacoco/test/`. Tags `vMAJOR.MINOR.PATCH` geram um Release com o ZIP e checksum SHA-256. A branch padrão atual é `codex/online-core-compartilhado`. Alterações de produção devem passar por Pull Request e revisão independente, conforme as regras ativas do GitHub. O [CONTRIBUTING.md](CONTRIBUTING.md) ainda descreve o fluxo histórico da `main` e precisa ser alinhado à nova branch.

Skills, configurações pessoais da IDE, ferramentas de arte e material interno ficam apenas no ambiente local, ignorados pelo Git.

## Referência, atribuição e licença

Os fundos `background-sunset.png` e `background-night.png`, em `assets/classic/background/`, foram criados com Imagegen em 29/10/2026 como variações de iluminação de `background.png`, preservando a composição e as dimensões de 1478 × 1064.

Os sprites `assets/classic/terrain/planta-morta.png` e `placa-terreno.png` foram fornecidos pelo responsável pelo projeto em 29/10/2026. São usados no terreno após a colheita e no próximo lote disponível. Os animais só aparecem após a compra; burro, coelho e cão não são acrescentados como decoração gratuita.

O atlas `assets/classic/animals/abelha/colmeia-abelha-atlas.png` foi separado com Imagegen a partir da arte existente: colmeia na metade esquerda, abelha na direita, fundo transparente. A colmeia comprada fica fixa no canto inferior direito; somente as abelhas fazem voos curtos ao redor dela. Alimentação e coleta continuam pelo clique na colmeia.

Dentro do cercado ficam apenas porco, ovelha, vaca, galinha e pato. Pombo fica acima da colmeia; burro, cabra e cão robô ficam próximos das casas; pavão e avestruz acima da cerca; coelho, cavalo e Mastiff ficam nas bordas inferiores. As posições dependem do tipo, preservadas ao comprar ou remover outros animais. Não há cão comum decorativo.

Cada pose usa sua própria base visível para apoiar o animal no chão, inclusive dormindo e ao virar. Sombras, áreas de clique e dicas acompanham esse apoio, sem considerar as margens transparentes como corpo do animal. As escalas são definidas por tipo; filhotes continuam menores que adultos.

Os terrenos usam uma grade isométrica uniforme de 170 × 72 unidades, elevada para deixar a última fileira acima da barra de ferramentas. O desenho compensa as margens transparentes dos sprites com sobreposição entre os tiles; plantas, destaque e cliques acompanham a mesma projeção.

Pombo e colmeia não passeiam. O pavão permanece no lugar e alterna a cauda fechada e aberta a cada quatro segundos. Os outros animais viram conforme a orientação original da arte e alternam os passos pela distância percorrida; o cavalo faz percursos mais longos para não parecer andar parado.

Os assets derivados do projeto de referência estão sujeitos à licença **Source Available — Non-Commercial**, reproduzida em [assets/REFERENCE_LICENSE](assets/REFERENCE_LICENSE). Preserve a atribuição. Uso comercial desses assets exige autorização do titular. A proveniência das imagens não foi verificada além da declaração do projeto original de que foram geradas com Google Gemini e ajustadas manualmente.

O ícone Web `web/public/assets/classic/icons/missoes.png` é uma exportação de `HudIcons.clipboard()` do Desktop, em 128 × 128, preservando as formas, cores e contorno do gerador Java. Não é uma nova ilustração nem uma variação gerada por IA.

O responsável pelo projeto forneceu `amigos.png` e cinco novos avatares em `assets/classic/icons/` em 01/11/2026. Com o avatar masculino original, a Web oferece três opções masculinas, três femininas e, quando disponível, a foto Google da sessão. `web/scripts/WebAssets.java` gera cópias otimizadas desses ícones, dos cursores de ferramenta e do cursor original `cursor-action.png`, preservando os arquivos de origem.

As fontes **M PLUS Rounded 1c**, **Nunito** e **Lato** estão sob SIL Open Font License 1.1. Suas licenças estão em [OFL-MPLUSRounded1c.txt](assets/fonts/OFL-MPLUSRounded1c.txt), [OFL-Nunito.txt](assets/fonts/OFL-Nunito.txt) e [OFL-Lato.txt](assets/fonts/OFL-Lato.txt).

Os avisos acima documentam os materiais de terceiros; não concedem uma licença geral ao código Java deste repositório.
