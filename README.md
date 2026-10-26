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

Comece colhendo os dois nabos prontos. Siga as tarefas de primeiros passos para aprender a vender, comprar sementes, arar, plantar e regar. Depois, cumpra as missões diárias e amplie a fazenda.

| Tecla | Ação |
| --- | --- |
| `1`–`8` | Cursor, pá, sementes, regador, herbicida, pesticida, luvinha, adubo |
| `B` / `V` | Loja / depósito |
| `Q` | Missões diárias |
| `Esc` | Fechar janelas |
| `M` / `N` | Alternar música / efeitos |
| `S` | Salvar |
| `T` | Alternar tempo 1× / 60× para desenvolvimento |
| `F3` | Overlay técnico |

A primeira rega inicia o crescimento. Terra seca reduz a velocidade; ervas daninhas e insetos pausam o crescimento até serem removidos (com os dois, só retoma após remover ambos) e não reduzem a colheita. Use herbicida e pesticida para removê-los. Cada temporada rende uma unidade; nas culturas com várias temporadas, a planta recomeça no estágio 1 após a coleta e precisa de nova rega. Retire a planta morta com a pá após a última colheita. Venda a produção no depósito. O adubo, comprado na loja, adianta um quarto do ciclo de uma planta regada.

A aba **Animais** da loja vende galinha, pato, ovelha e vaca, liberados por nível, e a ração de cada um. Clique no animal no cercado para dar ração ou coletar o produto pronto; os produtos são vendidos na mesma aba ou no depósito.

Os preços, tempos e rendimentos ficam em [crops.json](assets/data/crops.json); a progressão fica em [progression.json](assets/data/progression.json) e os animais em [animals.json](assets/data/animals.json). O jogo funciona sem dispositivo de áudio.

## Save

O save da nova economia é local: `save/game-rules-v2.json`, relativo ao diretório de execução, no formato v16. O `save/game-rules-v1.json` (v15) da primeira etapa fica preservado e não é lido; apontar `COLHEITA_SAVE_PATH` para ele é recusado. Defina `COLHEITA_SAVE_PATH` para usar outro caminho. Há autosave e progresso offline. Saves anteriores não são importados nem convertidos; o arquivo antigo permanece preservado. Um destino explícito com versão antiga, futura ou conteúdo inválido impede a carga e não é sobrescrito. Para iniciar uma fazenda nova após uma recusa, remova `COLHEITA_SAVE_PATH` da sessão ou aponte para um caminho inexistente; preserve o arquivo original. Faça backup antes de trocar de versão. Saves não são versionados.

Uma fazenda nova começa com 500 moedas de ouro e 5 verdes. Saldos usam uma casa decimal; XP preserva frações. Produtos mantêm a moeda e o custo de aquisição da semente. A venda soma os lotes elegíveis por moeda, aplica lucro de 15% e arredonda o total para cima em uma casa decimal. Na aba **VIP**, 100 verdes compram 30 dias reais, acumuláveis; enquanto ativo, o lucro é 25%. Produtos exclusivos ficam bloqueados sem VIP e são ignorados em **Vender tudo**.

Nesta etapa intermediária, os animais do catálogo atual continuam repetindo ciclos. Seus produtos usam `productPrice` como base unitária, antes do lucro de 15%/25%; o preço de compra do animal não é aplicado a cada produto. Na etapa de animais, essa exceção será retirada junto com os ciclos repetidos: o produtor terá uma única coleta e o produto usará o custo e a moeda reais da aquisição. Lotes já salvos mantêm sua origem e seu custo, sem reprecificação automática.

A substituição do catálogo, dos ciclos e da progressão restante está dividida nas próximas etapas. Baús ao arar, presentes iniciais e recompensas do tutorial serão retirados na etapa de progressão (5/6). As regras finais estão em [game-rule.md](docs/game-rule.md); nem todas estão implementadas nesta etapa econômica.

## Desenvolvimento e distribuição

Código em `src/main/java`, testes em `src/test/java` e recursos em `assets/`. Para capturas descartáveis, execute `./gradlew visualReview`; as imagens ficam em `build/visual-review/` e o save do jogador não é usado.

O GitHub Actions executa build, testes, SpotBugs e JaCoCo em pushes e PRs. O SpotBugs roda como parte do `build` com `ignoreFailures = false`; achados novos quebram o build local e no CI. Warnings pré-existentes estão listados em `config/spotbugs/exclude.xml`, escopados por classe e método para não mascarar código novo. Os resultados são publicados como annotations no PR via SARIF. O JaCoCo gera relatório de cobertura em `build/reports/jacoco/test/`. Tags `vMAJOR.MINOR.PATCH` geram um Release com o ZIP e checksum SHA-256. Mudanças na `main` exigem PR e pelo menos uma aprovação de outra pessoa, inclusive para administradores; consulte [CONTRIBUTING.md](CONTRIBUTING.md).

Skills, configurações pessoais da IDE, ferramentas de arte e material interno ficam apenas no ambiente local, ignorados pelo Git.

## Referência, atribuição e licença

Os assets derivados do projeto de referência estão sujeitos à licença **Source Available — Non-Commercial**, reproduzida em [assets/REFERENCE_LICENSE](assets/REFERENCE_LICENSE). Preserve a atribuição. Uso comercial desses assets exige autorização do titular. A proveniência das imagens não foi verificada além da declaração do projeto original de que foram geradas com Google Gemini e ajustadas manualmente.

As fontes **M PLUS Rounded 1c**, **Nunito** e **Lato** estão sob SIL Open Font License 1.1. Suas licenças estão em [OFL-MPLUSRounded1c.txt](assets/fonts/OFL-MPLUSRounded1c.txt), [OFL-Nunito.txt](assets/fonts/OFL-Nunito.txt) e [OFL-Lato.txt](assets/fonts/OFL-Lato.txt).

Os avisos acima documentam os materiais de terceiros; não concedem uma licença geral ao código Java deste repositório.
