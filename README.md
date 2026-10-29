# Colheita Feliz em Java

Jogo desktop de fazenda single-player (por enquanto) em Java 21 e libGDX, inspirado no jogo [Colheita Feliz] original do Orkut. Reimplementação independente e não oficial.

## Executar e compilar

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

A primeira rega inicia o crescimento. Terra seca reduz a velocidade; ervas daninhas e insetos pausam o crescimento até serem removidos (com os dois, só retoma após remover ambos) e não reduzem a colheita. Use herbicida e pesticida para removê-los. Cada temporada rende uma unidade; nas culturas com várias temporadas, a planta recomeça no estágio 1 após a coleta e precisa de nova rega. Retire a planta morta com a pá após a última colheita. Venda a produção no depósito. Os fertilizantes (comum, rápido, super e do amor) ficam no botão **Fertilizantes** da loja e são escolhidos pela bandeja da ferramenta 8. Cada planta aceita um por temporada, qualquer que seja o tipo; ele reduz o tempo restante da temporada atual (o excedente se perde) e só vale em planta regada, úmida e sem pragas. O do amor é só para a plantação de amigos.

A aba **Animais** tem quatro páginas com dez animais econômicos e cinco protetores. Cada tipo tem limite próprio de um animal e compra alternativa em ouro ou verdes, liberada por nível. Produtores chegam adultos e alimentados por 8 h; após uma coleta única, desaparecem e deixam uma unidade no depósito. Animais de crescimento chegam bebês, alimentados por 12 h; clique no adulto para vendê-lo. A loja também vende o adulto e os produtos na mesma aba.

A ração é genérica: crescimento custa 80 ouro e dura 12 h; produtor custa 120 ouro e dura 8 h. Clique no animal com fome para alimentar; uma refeição antecipada é recusada e não acumula tempo. A fome pausa o progresso, inclusive offline, sem reiniciá-lo. Dormir só muda a aparência. Protetores aceitam ambas as rações por 6 h; o clique usa primeiro a de crescimento, se disponível. Eles duram 30 dias reais desde a compra, sem revenda ou XP, e a fome não estende esse prazo. O núcleo local da etapa 6 calcula e persiste a cobertura das terras; multiplayer e interface de visitas permanecem planejados.

Os preços, tempos e rendimentos ficam em [crops.json](assets/data/crops.json); a progressão fica em [progression.json](assets/data/progression.json) e os animais em [animals.json](assets/data/animals.json). O jogo funciona sem dispositivo de áudio.

## Save

O save local atual é `save/game-rules-v4.json`, relativo ao diretório de execução, no formato v20 (`economy-2026-10`). Ele guarda progressão, tarefas, lotes, culturas, animais, preferências, revisão monotônica dos terrenos e seleção de proteção. Um save v19 abre com metadados sociais novos, sem converter a economia; a próxima gravação o atualiza para v20. O executável v19 recusa esse arquivo atualizado. Faça backup antes de trocar de versão.

A curva, o tutorial e as tarefas mudaram de significado. Saves v16, v17 e v18, assim como formatos anteriores, futuros ou inválidos, são recusados sem conversão nem sobrescrita, mesmo quando selecionados por `COLHEITA_SAVE_PATH`. Os arquivos `game-rules-v1.json`, `game-rules-v2.json`, `game-rules-v3.json` e `game.json` permanecem em disco. Para começar outra fazenda após uma recusa, remova `COLHEITA_SAVE_PATH` da sessão ou aponte para um arquivo inexistente, mantendo o original. Faça backup antes de trocar de versão. Há autosave e progresso offline; saves não são versionados.

Uma fazenda nova começa com 500 moedas de ouro e 5 verdes. Saldos usam uma casa decimal; XP preserva frações. Produtos mantêm a moeda e o custo de aquisição da semente. A venda soma os lotes elegíveis por moeda, aplica lucro de 15% e arredonda o total para cima em uma casa decimal. Na aba **VIP**, 100 verdes compram 30 dias reais, acumuláveis; enquanto ativo, o lucro é 25%. Produtos exclusivos ficam bloqueados sem VIP e são ignorados em **Vender tudo**.

Produtos animais usam o custo e a moeda reais da aquisição do produtor. A ração comprada depois não muda essa base. A venda adulta usa a origem do próprio animal. Coleta e venda adulta concedem o XP da espécie uma única vez.

As etapas econômicas 1–5 e o núcleo social local da etapa 6 estão implementados neste checkout. A API interna de ensaio transfere uma temporada pronta e desprotegida, preservando origem e impedindo consumo duplicado por revisão. Protetores cobrem terrenos distintos com seleção persistida; amor usa o estoque do visitante e o limite compartilhado da temporada, sem recompensas. São operações sobre dois estados locais autorizados pelo chamador controlado. Contas, amigos, visitas na interface e rede dependem de desenho online próprio. As regras completas estão em [game-rule.md](docs/game-rule.md).

## Desenvolvimento e distribuição

Código em `src/main/java`, testes em `src/test/java` e recursos em `assets/`. Para capturas descartáveis, execute `./gradlew visualReview`; as imagens ficam em `build/visual-review/` e o save do jogador não é usado.

O GitHub Actions executa build, testes, SpotBugs e JaCoCo em pushes e PRs. O SpotBugs roda como parte do `build` com `ignoreFailures = false`; achados novos quebram o build local e no CI. Warnings pré-existentes estão listados em `config/spotbugs/exclude.xml`, escopados por classe e método para não mascarar código novo. Os resultados são publicados como annotations no PR via SARIF. O JaCoCo gera relatório de cobertura em `build/reports/jacoco/test/`. Tags `vMAJOR.MINOR.PATCH` geram um Release com o ZIP e checksum SHA-256. Mudanças na `main` exigem PR e pelo menos uma aprovação de outra pessoa, inclusive para administradores; consulte [CONTRIBUTING.md](CONTRIBUTING.md).

Skills, configurações pessoais da IDE, ferramentas de arte e material interno ficam apenas no ambiente local, ignorados pelo Git.

## Referência, atribuição e licença

Os sprites `assets/classic/terrain/planta-morta.png` e `placa-terreno.png` foram fornecidos pelo responsável pelo projeto em 29/10/2026. São usados no terreno após a colheita e no próximo lote disponível. Os animais só aparecem após a compra; burro, coelho e cão não são acrescentados como decoração gratuita.

Os assets derivados do projeto de referência estão sujeitos à licença **Source Available — Non-Commercial**, reproduzida em [assets/REFERENCE_LICENSE](assets/REFERENCE_LICENSE). Preserve a atribuição. Uso comercial desses assets exige autorização do titular. A proveniência das imagens não foi verificada além da declaração do projeto original de que foram geradas com Google Gemini e ajustadas manualmente.

As fontes **M PLUS Rounded 1c**, **Nunito** e **Lato** estão sob SIL Open Font License 1.1. Suas licenças estão em [OFL-MPLUSRounded1c.txt](assets/fonts/OFL-MPLUSRounded1c.txt), [OFL-Nunito.txt](assets/fonts/OFL-Nunito.txt) e [OFL-Lato.txt](assets/fonts/OFL-Lato.txt).

Os avisos acima documentam os materiais de terceiros; não concedem uma licença geral ao código Java deste repositório.
