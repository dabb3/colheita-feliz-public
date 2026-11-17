# Melhorias urgentes do Online

## Decisões confirmadas

Entregar dois PRs independentes, sem merge ou deploy. Base: `origin/codex/online-core-compartilhado`, revisão observada `d258b86023f2356aef9ece45b1f90d1f61ecf7cf`, já com calendário diário e sementes especiais. Revalidar antes da implementação. Preservar as três imagens locais não versionadas.

## PR 1 — Fertilizantes e interface móvel

Reutilizar catálogo e caminhos existentes de compra, aplicação, estoque e persistência. Comum: 1.000 ouro; Rápido: somente 1 verde; Super: 2 verdes, nível 30; Ultra: 3 verdes, nível 40, redução nominal de 9 horas; Mega: 5 verdes, nível 50, redução nominal de 14 horas; Amor: 2.000 ouro. Manter níveis e efeitos não alterados pelo pedido. Criar ícones Ultra/Mega coerentes com os existentes e incluir resolução de assets e fallbacks web.

Corrigir a origem do transbordamento dos cartões de Amigos em telas estreitas, mantendo todos os botões acessíveis e rolagem vertical. Corrigir o posicionamento do popup da planta considerando viewport visível, painel de ferramentas, terreno selecionado e zoom; permitir acesso a todas as informações. Preservar comportamento desktop e de toque.

Aceitação: preços e níveis iguais no servidor e clientes; Rápido indisponível por ouro; estoque anterior intacto; Ultra/Mega consumidos uma vez, sem crescimento negativo ou colheita extra. Cobrir saldo insuficiente, limite de nível, restauração, concorrência e replay. Verificar interface e ícones em desktop e celular.

## PR 2 — Domingos com XP dobrado

A partir de 22/11/2026, todo domingo, de 00h até segunda-feira 00h exclusiva em `America/Sao_Paulo`, todas as culturas comuns recebem 2× XP exclusivamente na colheita própria válida. Bronze, Prata, Ouro e Diamante ficam excluídas. Esta decisão substitui a rotação Nabo/Cenoura do documento original; não há cultura selecionada nem rotação.

Calcular janela e elegibilidade deterministicamente usando relógio autoritativo do servidor. Multiplicar o XP base da temporada com `BigDecimal`, somente após sucesso. Não alterar `CropType.harvestXp()`, ouro, produtos, vendas ou XP de outras ações. Não acumular outro multiplicador por VIP. Preservar locks, recibos e replay.

Expor projeção informativa com identificador, multiplicador, culturas elegíveis, estado, início/fim e próximo evento em ISO 8601. A interface Eventos mostra próximo início ou término, selo 2× XP e XP normal/dobrado; destaca culturas elegíveis discretamente na loja e fazenda. O navegador usa tempo do servidor como referência para contagens. Falha de carregamento não bloqueia jogo.

Aceitação: fronteiras sábado/domingo/segunda, UTC, virada de ano, início em 22/11, reinício, temporadas e culturas especiais. Nabo: 15/30 XP; Cenoura: 22,5/45 XP. Sem bônus em furto ou duplicação por concorrência/replay. Verificar evento futuro/ativo/encerrado, telas estreitas e erro de carregamento.

Regressões temporais obrigatórias: uma planta pronta no sábado e colhida no domingo recebe 2× XP; uma planta pronta no domingo e colhida na segunda-feira recebe XP normal. A elegibilidade considera exclusivamente o horário autoritativo da colheita bem-sucedida, independentemente do plantio ou do momento em que a planta ficou pronta. Usar relógio controlado e plantas comuns elegíveis, verificando o delta exato de XP em cada caso.

## Abordagem e limites

Escolha proposta: estender os catálogos, motor e componentes atuais, sem novas dependências ou migração de save. Alternativas descartadas: motor econômico paralelo duplicaria regras; jobs agendados adicionariam estado desnecessário; reescrever os painéis ampliaria o risco sem necessidade.

Não mudar demais preços, culturas, regras econômicas ou formato de save. Não usar dados de jogadores em testes. Atualizar os documentos responsáveis e notas junto com cada comportamento. Criar branches próprias; não enviar alterações diretamente à base que dispara deploy.

## Verificação prevista

Executar testes de regressão antes e depois da correção, `./gradlew.bat build`, integração com PostgreSQL descartável, `npm test` e `npm run build` em `web`, revisão visual móvel/desktop e `git diff --check`. Registrar comandos e limitações reais nos PRs. Asserções de DOM não substituem inspeção visual.
