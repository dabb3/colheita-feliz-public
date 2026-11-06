# Implementação do balanceamento animal V2

Branch: `codex/balanceamento-animais-v2`. Base: `be6e985`, de `codex/online-core-compartilhado`. Aprovação: vendas ouro do estudo e alternativa B para verdes. Não houve merge, deploy ou alteração de provedores/dados de jogadores.

## Resultado e valores

As dez espécies vendáveis passam a capturar preços finais comum/VIP na compra, nas duas moedas. Os cinco protetores continuam sem revenda. Valores em décimos no catálogo, BigDecimal na liquidação; nenhum câmbio entre moedas ou multiplicação da receita V2 por bônus antigo.

| Animal | Ouro comum | Ouro VIP | Verdes comum | Verdes VIP |
|---|---:|---:|---:|---:|
| Galinha | 8.480,0 | 8.600,0 | 9,2 | 9,4 |
| Abelha | 29.200,0 | 29.470,0 | 28,4 | 28,8 |
| Pombo | 40.480,0 | 41.560,0 | 37,4 | 38,6 |
| Coelho | 67.440,0 | 69.240,0 | 61,4 | 62,6 |
| Pato | 73.000,0 | 73.720,0 | 70,0 | 70,6 |
| Pavão | 106.240,0 | 107.710,0 | 101,0 | 102,0 |
| Porco | 164.720,0 | 168.320,0 | 104,8 | 107,2 |
| Burro | 169.840,0 | 174.700,0 | 106,4 | 109,6 |
| Ovelha | 186.240,0 | 187.740,0 | 122,0 | 123,0 |
| Vaca | 228.160,0 | 230.140,0 | 182,6 | 184,0 |

[CSV aprovado versus implementado](analysis/animals-v2/implemented-prices.csv). `node analysis/animals-v2/verify-implementation.mjs` compara os quarenta preços com os CSVs aprovados e a documentação, verifica os demais campos de todas as quinze espécies contra a base e confirma os catálogos de plantas, progressão e fertilizantes intactos.

Compras, níveis, tempos, refeições iniciais e seguintes, XP, limites por espécie, validade/cobertura de protetores, terrenos, missões, recompensas e regras sociais não mudaram. A janela de exclusividade permanece cinco minutos por temporada e o limite permanece um furto bem-sucedido por jogador a cada 24 horas.

## Termos por aquisição e estoque anterior

`AnimalSale` contém versão 2, espécie e receitas finais comum/VIP em décimos. `ItemLot` conserva esses termos junto da moeda/custo de aquisição e marcador externo de versão. A compra resolve a espécie no catálogo autoritativo de GameState, sem confiar no objeto fornecido pelo chamador. A coleta transfere os termos para o produto sem alterá-los; animais de crescimento liquidam diretamente. `withQuantity` conserva os termos em vendas parciais.

Aquisições e produtos históricos sem termos permanecem v0: custo original × 1,15 ou 1,25, com teto histórico por operação/moeda. Um animal antigo alimentado/coletado depois do upgrade continua antigo. Não se aplica V2 ao estoque antigo e não se paga nenhuma diferença na migração. O perfil comum/VIP é determinado na venda, inclusive depois de expirar o cartão.

`CropSale` v1 e `AnimalSale` v2 não podem coexistir no mesmo lote. Sementes e plantações rejeitam termos animais; protetores não recebem termos. Produtos animais V2 não são exclusivos VIP: continuam vendáveis no perfil comum. Vender tudo conserva produtos VIP de plantas quando o cartão está inativo.

## Persistência e autoridade

Leitores de arquivo e PostgreSQL validam o JSON antes da desserialização: campos completos, números de verdade, inteiros exatos, limites numéricos, quantidade e booleano VIP. Depois verificam espécie/produto, moeda, custo e receitas autorizadas. Marcador V2 sem termos, termos cruzados ou corrompidos são recusados sem normalizar ou sobrescrever o save. A restauração direta de GameState também valida os contextos e os termos.

O formato estrutural permanece v20 e o caminho local permanece `save/game-rules-v4.json`. São aceitos `economy-2026-10` e `economy-2026-11-plants-v1`; gravações usam `economy-2026-11-animals-v2`. Os dois caminhos de escrita JDBC já atualizam snapshot e ruleset na mesma transação. Nenhuma migração SQL de estrutura é necessária.

Os metadados não são assinatura criptográfica. Um administrador com escrita no banco pode fabricar um lote indistinguível de legado; a fronteira de segurança é o banco controlado pelo servidor. A API não aceita snapshots/preços do cliente e rejeita campos extras como `unitSale`, `saleQuote`, `animalSale` e `economyVersion`. Uma moeda de comando diferente não muda a origem monetária do produto vendido.

Pagamento e consumo de estoque usam os locks, revisão e recibos idempotentes já existentes na mesma transação PostgreSQL. A mudança não cria outro caminho de pagamento. Vendas V2 em décimos não ganham moedas ao fragmentar ou agrupar; frações v0 continuam com o comportamento histórico deliberadamente preservado. Testes provocam concorrência, repetição de UUID, reabertura da conexão e rollback.

## Interfaces

O servidor projeta a cotação do animal existente usando seus termos e o perfil atual. Esses campos privados não são adicionados à projeção pública de visita. Produtos no depósito continuam usando `Market` para preço unitário e cotações por item/total. Loja web mostra a oferta do catálogo para novas compras; animais próprios e popup usam a cotação recebida da API. Desktop usa o mesmo Market para tooltip, adulto existente e depósito; oferece a cotação de nova compra na loja. Textos genéricos que prometiam 25% a todo produto foram substituídos por preços VIP conforme cada item.

## Verificação

- Antes da implementação, os dez testes de preço falharam contra as vendas antigas. Depois passaram nas quarenta combinações de moeda/perfil, com alimentação real até maturidade e remoção/consumo após venda.
- Java: 253 testes desktop/domínio/persistência, um teste core e 40 testes unitários server; zero falhas/skips. Quatro testes de capacidade também passaram.
- PostgreSQL 17 descartável, somente `127.0.0.1:55439/colheita_test`: suíte completa com 65 testes passou; após acrescentar o teste HTTP de preço forjado, os 15 testes da suíte animal delimitada passaram novamente, junto de `:server:build` e `:server:spotbugsTest`.
- Web: 135 testes aprovados, TypeScript e `npm run build:local` aprovados.
- `./gradlew.bat build :server:integrationTest spotbugsMain spotbugsTest :core:spotbugsMain :core:spotbugsTest :server:spotbugsMain :server:spotbugsTest`: BUILD SUCCESSFUL. Distribuições desktop/API geradas. Os seis XMLs de SpotBugs não contêm achados.
- `verify-implementation.mjs`: quarenta preços iguais às referências, regras e campos preservados. `git diff --check` aprovado.
- Reexecução de 1.296 trajetórias/3.888 observações A/B/C usando o core implementado: todos os CSVs originais foram reproduzidos byte a byte após a correção do harness. O ajuste usa a cotação efetivamente paga, sem manter dependência de um multiplicador legado.

Os testes novos cobrem produtos antigos e novos, adultos antigos, alimentação sem mudança, ida/volta de save, metadados ausentes/fracionários/string/overflow/adulterados, sementes/plantas com termos indevidos, VIP expirado, venda parcial/agrupada/mista v0/v1/V2 e VIP bloqueado. A integração cobre as quarenta rotas, replay, concorrência, rollback monetário/estoque/recibo, leitura anterior e recusa de corrupção sem regravação. Os testes preexistentes conferem os dois caminhos JDBC de atualização do ruleset.

## Estudos e revisão

[Estudo inicial](ANIMAL_BALANCE_V2.md), [comparação verde A/B/C](ANIMAL_GREEN_CALIBRATION_V2.md) e seus CSVs são registros históricos da aprovação, baseados em `be6e985`. Suas afirmações de proposta não implementada se referem à etapa anterior. Os manifests preservam os hashes das fontes analisadas; verificadores históricos de hash/report devem ser executados naquela base, sem regenerar a evidência histórica sobre os arquivos agora modificados. O verificador desta implementação é `verify-implementation.mjs`.

A revisão independente somente leitura não confirmou defeito na implementação de produção. Encontrou uma dependência do harness ao pagamento legado: corrigida para descontar a receita efetivamente liquidada por Market antes de aplicar a alternativa simulada. O simulador continua fora do jogo. A revisão não substitui a aprovação externa do PR.

## Implantação futura e limites

Antes de qualquer implantação aprovada separadamente: backup verificável do banco, retirada dos escritores antigos, atualização coordenada da API e clientes, smoke de compra/coleta/venda comum/VIP e observação dos erros de carga/recibos. Não executar binário antigo sobre dados V2. Um rollback exige versão que entenda V2 ou restauração coordenada do backup, com decisão explícita sobre progresso posterior ao backup. Não apagar metadados para viabilizar downgrade.

Uma futura V3 deve manter o resolvedor/validação histórica V2 e criar nova versão para novas aquisições, em vez de editar os termos existentes. Não houve teste manual visual em aparelhos reais nem certificação de recuperação de desastre em produção. As simulações são determinísticas e não comprovam estabilidade macroeconômica de uma população real. Os testes não acessaram saves ou contas dos jogadores.

Alterações locais preexistentes em CONTRIBUTING.md, LATENCY_REPORT.md e o ícone de cadeado ficam fora do commit/PR. Documentação de arquitetura local ignorada também foi alinhada, sem publicar os arquivos locais de setup.
