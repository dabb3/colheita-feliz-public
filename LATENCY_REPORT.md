# Latência do piloto web — 31/10/2026

As operações internas de cultivo, venda e animais ficaram cerca de 32% mais rápidas neste ensaio. A latência percebida continua dependendo da autenticação, da rede e do banco remoto. Não houve troca de plano, região ou provedor.

## Comparação das 24 ações

Mediana de três amostras por ação, após uma execução de aquecimento por cenário. Antes: commit 845f857; depois: 7332284. Ambos executados no mesmo serviço Render e banco Supabase. As duas versões já incluem o pool de conexões do PR #42; portanto esta tabela mede apenas o ganho adicional.

O relógio mede o corpo do serviço após preparar as fixtures e antes do rollback. Exclui preparação, autenticação, rede do navegador, obtenção da conexão, BEGIN, lock inicial do ator e commit. Inclui consultas SQL e comunicação entre API e banco dentro do corpo do serviço. Estes números não representam o tempo completo de um clique.

| Ação | Antes (ms) | Depois (ms) | Redução |
|---|---:|---:|---:|
| Comprar semente (BUY_SEED) | 1150.9 | 1002.7 | 12.9% |
| Comprar animal (BUY_ANIMAL) | 1254.8 | 796.3 | 36.5% |
| Comprar ração (BUY_FEED) | 1122.5 | 763.1 | 32.0% |
| Comprar fertilizante (BUY_FERTILIZER) | 1121.6 | 761.3 | 32.1% |
| Comprar VIP (BUY_VIP) | 1120.3 | 760.3 | 32.1% |
| Comprar terra (BUY_PLOT) | 1120.2 | 760.1 | 32.1% |
| Arar (PLOW) | 1117.9 | 758.8 | 32.1% |
| Plantar (PLANT) | 1121.0 | 759.8 | 32.2% |
| Regar (WATER) | 1117.9 | 761.5 | 31.9% |
| Remover ervas (WEED) | 1117.5 | 763.5 | 31.7% |
| Aplicar pesticida (PESTICIDE) | 1118.5 | 759.0 | 32.1% |
| Adubar (FERTILIZE) | 1117.7 | 758.9 | 32.1% |
| Colher (HARVEST) | 1118.5 | 757.2 | 32.3% |
| Vender unidade (SELL_ITEM) | 1119.1 | 758.5 | 32.2% |
| Vender lote do item (SELL_ITEM_ALL) | 1119.5 | 757.7 | 32.3% |
| Vender tudo (SELL_ALL) | 1123.3 | 757.4 | 32.6% |
| Alimentar animal (TEND_ANIMAL_FEED) | 1120.8 | 757.3 | 32.4% |
| Coletar produção animal (TEND_ANIMAL_COLLECT) | 1120.2 | 757.4 | 32.4% |
| Vender animal (SELL_ANIMAL) | 1116.2 | 759.0 | 32.0% |
| Carregar fazenda (FARM) | 744.7 | 505.1 | 32.2% |
| Listar amigos (FRIENDS) | 272.6 | 251.4 | 7.8% |
| Visitar fazenda (VISIT) | 880.4 | 756.0 | 14.1% |
| Furtar (STEAL) | 1862.6 | 1261.9 | 32.3% |
| Ajudar (LOVE) | 1863.9 | 1262.9 | 32.2% |

Cada amostra usa jogadores e fazendas sintéticos novos, com transação sempre revertida. Nenhuma compra, colheita ou furto foi executado sobre fazendas reais. As fixtures têm progressão e itens preparados para cada ação; não simulam uma fazenda grande ou carga simultânea de muitos usuários. Uma amostra pequena não permite afirmar p95 nem garantia de desempenho. Comprar semente apresentou oscilação maior, preservada nos dados.

## Tempo completo no navegador

Três consultas reais por rota, com API acordada, na mesma sessão e mesma amizade. Inclui obtenção de token, rede, autenticação, transação e leitura da resposta. Fazenda usa as três atualizações mais recentes de cada fase; a primeira conexão fria foi excluída. Amigos e visita usam três interações consecutivas. Valores negativos indicam piora nesta rodada.

| Consulta HTTP | Antes (ms) | Depois (ms) | Redução |
|---|---:|---:|---:|
| Fazenda | 1830.6 | 1600.5 | 12.6% |
| Amigos | 1335.6 | 1351.4 | -1.2% |
| Visita | 1716.5 | 1912.5 | -11.4% |

Amigos e visitas ainda apresentam variação de autenticação e rede. O ensaio não demonstra melhora consistente de ponta a ponta nessas duas telas. Não somar o ganho interno ao ganho HTTP: são escopos diferentes. O componente de autenticação variou aproximadamente entre 150 e 590 ms nas consultas capturadas. Cada ida ao banco ainda custa aproximadamente 120 ms, mesmo com conexão reaproveitada. O tempo db_connect mede a obtenção da conexão do pool; esse valor isolado não comprova abertura de um socket novo por chamada.

## Alterações entregues

- Jogador carregado uma vez por transação; atualização do nome ocorre somente quando necessário.
- Limpeza de recibos expirados e contagem de recibos ativos em uma consulta.
- Persistência das fazendas e recibo da ação em uma instrução SQL, mantendo atomicidade.
- Identificador da próxima ação vem na resposta autenticada da fazenda ou ação. O fluxo normal dispensa GET /v1/request-id, com fallback para respostas antigas ou lease inválido/expirado. Replays preservam a mesma resposta e o mesmo lease. Não há cache de autorização.
- Medição opt-in das 24 ações com fixtures revertidas e diagnóstico HTTP sem tokens, corpos ou identificadores de jogadores.

Os testes do cliente cobrem duas mutações consecutivas sem GET de emissão. Na janela HTTP posterior também não houve GET /v1/request-id. Não foi medido um clique real de compra para evitar alterar o saldo do jogador; as mutações foram medidas pelo ensaio sintético acima.

## Validação e publicação

[PR #43](https://github.com/dabb3/colheita-feliz-public/pull/43) e [PR #44](https://github.com/dabb3/colheita-feliz-public/pull/44) integrados em codex/online-core-compartilhado. Main preservada. Build Java/SpotBugs, 76 testes web, typecheck e build web passaram. O [CI da otimização](https://github.com/dabb3/colheita-feliz-public/actions/runs/41166522816) também passou com PostgreSQL descartável, incluindo concorrência, replay, autorização e rollback. Revisão independente do código sem achados confirmados.

API final: commit 7332284, deploy dep-dbimipc9v7es7381gdq0, confirmado Live. Cliente publicado no Cloudflare e conferido no navegador. A flag LATENCY_BENCHMARK foi removida após recolher as 24 medições; o último deploy roda sem o ensaio. As demais variáveis e o certificado do banco foram preservados.

Dados brutos: [antes interno](benchmarks/latency/2026-10-31/baseline.json), [depois interno](benchmarks/latency/2026-10-31/after.json), [HTTP antes](benchmarks/latency/2026-10-31/http-before.json), [HTTP depois](benchmarks/latency/2026-10-31/http-after.json). As capturas HTTP incluem contexto anterior às três amostras; as regras de seleção estão descritas acima.

## Limites e próximo gargalo

O plano gratuito continua sujeito ao despertar da API após inatividade. Este trabalho reduz viagens SQL e uma chamada auxiliar; não elimina a distância entre navegador, API, autenticação e banco. Não foi feito teste de carga, alteração de segurança, migração de fazendas nem contratação de serviço. Para a próxima redução, medir regiões/proximidade dos serviços e avaliar a validação de tokens conforme os requisitos de revogação, antes de mudar a autenticação.
