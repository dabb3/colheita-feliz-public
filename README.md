# Colheita Feliz em Java

Jogo desktop de fazenda single-player em Java 21 e libGDX, inspirado em [Fazenda dos Sonhos / Colheita Feliz em C++](https://github.com/davidcreator/colheita-feliz-cpp), de Harlley Bastos. Reimplementação independente e não oficial.

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
| `1`–`6` | Mão, enxada, regador, sementes, pesticida, herbicida |
| `B` / `V` | Loja / depósito |
| `Q` | Missões diárias |
| `Esc` | Fechar janelas |
| `M` / `N` | Alternar música / efeitos |
| `S` | Salvar |
| `T` | Alternar tempo 1× / 60× para desenvolvimento |
| `F3` | Overlay técnico |

A primeira rega inicia o crescimento. Terra seca reduz a velocidade; ervas daninhas pausam o crescimento e insetos reduzem a colheita. Use herbicida e pesticida para removê-los. Retire a planta morta com a enxada após colher. Venda a produção no depósito.

Os preços, tempos e rendimentos ficam em [crops.json](assets/data/crops.json); a progressão fica em [progression.json](assets/data/progression.json). O jogo funciona sem dispositivo de áudio.

## Save

O save é local: `save/game.json`, relativo ao diretório de execução. Defina `COLHEITA_SAVE_PATH` para usar outro caminho. Há autosave, progresso offline e migração dos formatos antigos para v12. Faça backup do save antes de trocar de versão. Saves não são versionados.

## Desenvolvimento e distribuição

Código em `src/main/java`, testes em `src/test/java` e recursos em `assets/`. Para capturas descartáveis, execute `./gradlew visualReview`; as imagens ficam em `build/visual-review/` e o save do jogador não é usado.

O GitHub Actions executa build e testes em pushes e PRs. Tags `vMAJOR.MINOR.PATCH` geram um Release com o ZIP e checksum SHA-256. Mudanças na `main` exigem PR e pelo menos uma aprovação de outra pessoa, inclusive para administradores; consulte [CONTRIBUTING.md](CONTRIBUTING.md).

Skills, configurações pessoais da IDE, ferramentas de arte e material interno ficam apenas no ambiente local, ignorados pelo Git.

## Referência, atribuição e licença

Os assets derivados do projeto de referência estão sujeitos à licença **Source Available — Non-Commercial**, reproduzida em [assets/REFERENCE_LICENSE](assets/REFERENCE_LICENSE). Preserve a atribuição. Uso comercial desses assets exige autorização do titular. A proveniência das imagens não foi verificada além da declaração do projeto original de que foram geradas com Google Gemini e ajustadas manualmente.

As fontes **M PLUS Rounded 1c**, **Nunito** e **Lato** estão sob SIL Open Font License 1.1. Suas licenças estão em [OFL-MPLUSRounded1c.txt](assets/fonts/OFL-MPLUSRounded1c.txt), [OFL-Nunito.txt](assets/fonts/OFL-Nunito.txt) e [OFL-Lato.txt](assets/fonts/OFL-Lato.txt).

Os avisos acima documentam os materiais de terceiros; não concedem uma licença geral ao código Java deste repositório.
