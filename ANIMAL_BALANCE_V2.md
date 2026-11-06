# Proposta de balanceamento V2 dos animais

Revisão das margens verdes: [comparação A/B/C](ANIMAL_GREEN_CALIBRATION_V2.md). Os preços desta proposta continuam representando a alternativa A; B é recomendação adicional ainda sujeita a aprovação.

**Status: estudo e proposta para aprovação. Nenhuma regra econômica implementada foi alterada.** Branch `codex/balanceamento-animais-v2`, base online `be6e985` (integração da economia V1 de plantas). Data de referência 06/11/2026.

## 1. Diagnóstico do sistema atual

O catálogo real contém seis produtores, quatro animais de crescimento e cinco protetores. Todos aceitam ouro ou verdes e têm limite de um por tipo. O produtor gera uma unidade, é removido na coleta e exige nova compra para repetir o ciclo. O animal de crescimento é vendido uma vez adulto. Não há produção permanente após amortizar uma compra. O sono é visual; não interrompe o crescimento alimentado.

A alimentação inicial vem cheia e está incluída na aquisição. Produtores recebem 8 h por refeição de 120 ouro, animais de crescimento 12 h por 80 ouro. Protetores recebem 6 h por 80 ou 120 ouro. A fome pausa o progresso, não o reinicia. A venda comum aplica 1,15 ao custo de aquisição; a VIP aplica 1,25 na moeda de origem, com teto em décimos. Esse cálculo é independente da ração e do tempo.

Fontes examinadas: `docs/game-rule.md` (seções 4 a 8, 12.1 e 13), `assets/data/animals.json`, `assets/data/crops.json`, `assets/data/progression.json`; `Animal`, `AnimalType`, `Barn`, `Market`, `GameState`, `PlayerProgress`, `OnlineGameService`, `OnlineViews`, `MarketPanel`, `web/src/main.ts` e `presentation.ts`. O core real do servidor é usado pelo estudo; não há segunda implementação de crescimento ou XP.

Não há câmbio no jogo. Na compra em verdes, a ração continua sendo paga em ouro. Portanto o resultado líquido é um vetor `(−ração em ouro, ganho em verdes)`. Uma taxa única de ROI líquido ou um payback conjunto das duas moedas não existe sem atribuir um valor de conversão que o jogo não tem. O CSV informa ROI do principal verde e o gasto em ouro separadamente. O ROI em ouro inclui integralmente as refeições adicionais.

## 2. Problemas encontrados e gravidade

- **P1 econômico — geração recorrente alta de verdes.** A margem percentual aplicada ao principal verde, repetida a cada nova compra, permite rendimentos bem superiores às tarefas diárias. Nos personagens avançados com liquidez e todos os tipos liberados, o estoque é limitado por espécie, mas as dez espécies operam em paralelo. É uma fonte legítima do jogo, não uma falha de pagamento.
- **P2 — dominância e regressões de rentabilidade.** Porco supera burro com a mesma compra e venda, termina antes, custa menos ração e rende mais XP/h. Pato supera pavão por custo, prazo, ROI, lucro/h e XP/h. Galinha supera pombo na rota verde em todas as métricas normalizadas comparadas, incluindo ausência de ração paga. A abelha supera pombo em eficiência de ouro; na rota verde a comparação é um trade-off porque a abelha gasta mais ouro em ração por hora. O limite de um por tipo impede substituir um portfólio inteiro por muitas galinhas; isso reduz, mas não elimina, a fraqueza das opções posteriores.
- **P2 — saltos sem relação com duração ou custo operacional.** O preço de venda ignora tempo e alimentação; produtores tardios podem gerar ouro muito mais rápido que animais de crescimento anteriores. A tabela de dominância normalizada está em `dominated-routes.csv`, separando comparações entre o mesmo tipo de atividade e entre atividades distintas.
- **P2 de compreensão — alimentação mistura moedas.** Mostrar lucro em verdes sem o gasto em ouro, ou tratar a refeição inicial como compra adicional, distorce a decisão. O estudo não converte o gasto para verdes.
- **P2 de experiência — mais acessos não garantem maior rendimento.** Intervalos uniformes de 4 h encaixam refeições de 8/12 h; intervalos de 2,4 h podem desperdiçar tempo depois da fome. Isso é efeito do calendário do jogador, não motivo suficiente para mudar durações nesta etapa.
- **P2 de compatibilidade futura — animais não têm termos de venda versionados.** A origem atual guarda custo/moeda, mas a venda é reconstruída pelo multiplicador vigente. Uma implementação V2 precisa capturar seus preços sem reprecificar animais ou produtos antigos. Os leitores atuais também recusam metadados de plantas em animais, portanto reutilizar `cropSale` seria incorreto.
- **P3 — ração de 120 para protetor é dominada pela de 80.** Ambas concedem as mesmas 6 h. Mantê-las aceitas preserva a mecânica, mas a orientação/seleção de compra deve favorecer a opção de 80 quando possível.

## 3. Valores atuais completos

Valores financeiros estão em unidades da moeda indicada. O custo de ração abaixo é sempre ouro, depois de descontar a refeição inicial incluída. `T` é tempo alimentado nominal, não prazo garantido entre acessos. Os CSVs trazem os números completos com oito casas nos indicadores derivados.

### Produtores — seis animais

| Animal / nível | Compra ouro / verdes | T (h) | Refeições total / pagas | Ração ouro | Venda ouro comum / VIP | Venda verdes comum / VIP | XP / XP por h |
|---|---|---|---|---|---|---|---|
| galinha / 10 | 8.000 / 9 | 8 | 1 / 0 | 0 | 9.200,0 / 10.000,0 | 10,4 / 11,3 | 100 / 12,500 |
| abelha / 14 | 28.000 / 28 | 12 | 2 / 1 | 120 | 32.200,0 / 35.000,0 | 32,2 / 35,0 | 140 / 11,667 |
| pato / 28 | 70.000 / 69 | 16 | 2 / 1 | 120 | 80.500,0 / 87.500,0 | 79,4 / 86,3 | 280 / 17,500 |
| pavao / 32 | 100.000 / 99 | 28 | 4 / 3 | 360 | 115.000,0 / 125.000,0 | 113,9 / 123,8 | 320 / 11,429 |
| ovelha / 44 | 180.000 / 120 | 20 | 3 / 2 | 240 | 207.000,0 / 225.000,0 | 138,0 / 150,0 | 440 / 22,000 |
| vaca / 48 | 220.000 / 180 | 24 | 3 / 2 | 240 | 253.000,0 / 275.000,0 | 207,0 / 225,0 | 480 / 20,000 |

### Crescimento — quatro animais

| Animal / nível | Compra ouro / verdes | T (h) | Refeições total / pagas | Ração ouro | Venda ouro comum / VIP | Venda verdes comum / VIP | XP / XP por h |
|---|---|---|---|---|---|---|---|
| pombo / 18 | 36.000 / 36 | 36 | 3 / 2 | 160 | 41.400,0 / 45.000,0 | 41,4 / 45,0 | 180 / 5,000 |
| coelho / 24 | 60.000 / 59 | 48 | 4 / 3 | 240 | 69.000,0 / 75.000,0 | 67,9 / 73,8 | 240 / 5,000 |
| porco / 36 | 150.000 / 100 | 60 | 5 / 4 | 320 | 172.500,0 / 187.500,0 | 115,0 / 125,0 | 360 / 6,000 |
| burro / 40 | 150.000 / 100 | 72 | 6 / 5 | 400 | 172.500,0 / 187.500,0 | 115,0 / 125,0 | 400 / 5,556 |

### Lucro líquido e ROI na rota ouro

| Animal | Lucro/ciclo comum / VIP | ROI líquido % comum / VIP | Ouro/h comum / VIP |
|---|---|---|---|
| galinha | 1.200,0 / 2.000,0 | 15,000 / 25,000 | 150,000 / 250,000 |
| abelha | 4.080,0 / 6.880,0 | 14,571 / 24,571 | 340,000 / 573,333 |
| pombo | 5.240,0 / 8.840,0 | 14,556 / 24,556 | 145,556 / 245,556 |
| coelho | 8.760,0 / 14.760,0 | 14,600 / 24,600 | 182,500 / 307,500 |
| pato | 10.380,0 / 17.380,0 | 14,829 / 24,829 | 648,750 / 1.086,250 |
| pavao | 14.640,0 / 24.640,0 | 14,640 / 24,640 | 522,857 / 880,000 |
| porco | 22.180,0 / 37.180,0 | 14,787 / 24,787 | 369,667 / 619,667 |
| burro | 22.100,0 / 37.100,0 | 14,733 / 24,733 | 306,944 / 515,278 |
| ovelha | 26.760,0 / 44.760,0 | 14,867 / 24,867 | 1.338,000 / 2.238,000 |
| vaca | 32.760,0 / 54.760,0 | 14,891 / 24,891 | 1.365,000 / 2.281,667 |

### Rota verde — ganho verde e despesa ouro separados

| Animal | Ganho verde/ciclo comum / VIP | ROI do principal verde % comum / VIP | Verdes/h comum / VIP | Ração ouro/ciclo |
|---|---|---|---|---|
| galinha | 1,4 / 2,3 | 15,556 / 25,556 | 0,1750 / 0,2875 | 0 |
| abelha | 4,2 / 7,0 | 15,000 / 25,000 | 0,3500 / 0,5833 | 120 |
| pombo | 5,4 / 9,0 | 15,000 / 25,000 | 0,1500 / 0,2500 | 160 |
| coelho | 8,9 / 14,8 | 15,085 / 25,085 | 0,1854 / 0,3083 | 240 |
| pato | 10,4 / 17,3 | 15,072 / 25,072 | 0,6500 / 1,0813 | 120 |
| pavao | 14,9 / 24,8 | 15,051 / 25,051 | 0,5321 / 0,8857 | 360 |
| porco | 15,0 / 25,0 | 15,000 / 25,000 | 0,2500 / 0,4167 | 320 |
| burro | 15,0 / 25,0 | 15,000 / 25,000 | 0,2083 / 0,3472 | 400 |
| ovelha | 18,0 / 30,0 | 15,000 / 25,000 | 0,9000 / 1,5000 | 240 |
| vaca | 27,0 / 45,0 | 15,000 / 25,000 | 1,1250 / 1,8750 | 240 |

### Prazo de retorno

O principal é devolvido na venda única, juntamente com a margem, ao concluir o ciclo. O prazo até esse primeiro recebimento é diferente de acumular lucro equivalente a outro principal. Para a rota ouro, o segundo indicador é `compra / lucro líquido por hora`. Para verdes, é apenas um retorno equivalente de lucro verde, sem recuperar o ouro gasto em ração. A assinatura VIP não é atribuída inteira a um único animal; seu custo compartilhado aparece nas simulações do portfólio.

| Animal | Primeira venda nominal h | Primeira venda com 3 / 6 / 10 logins (h) | Lucro ouro equivalente ao principal: comum / VIP (h) |
|---|---|---|---|
| galinha | 8 | 8 / 8 / 9.6 | 53,33 / 32,00 |
| abelha | 12 | 16 / 12 / 14.4 | 82,35 / 48,84 |
| pombo | 36 | 48 / 36 / 36 | 247,33 / 146,61 |
| coelho | 48 | 64 / 48 / 48 | 328,77 / 195,12 |
| pato | 16 | 16 / 16 / 19.2 | 107,90 / 64,44 |
| pavao | 28 | 32 / 28 / 33.6 | 191,26 / 113,64 |
| porco | 60 | 80 / 60 / 60 | 405,77 / 242,07 |
| burro | 72 | 96 / 72 / 72 | 488,69 / 291,11 |
| ovelha | 20 | 24 / 20 / 24 | 134,53 / 80,43 |
| vaca | 24 | 24 / 24 / 28.8 | 161,17 / 96,42 |

## 4. Tabela proposta para os 15 animais

A recomendação altera somente preços de venda dos dez vendáveis. Compras nas duas moedas, tipos, níveis, limite por espécie, durações, ração, XP e preços dos cinco protetores ficam iguais. Os valores abaixo são candidatos para aprovação, não novos preços implementados.

| Animal | Compra ouro / verdes atual = proposta | Venda ouro comum atual → proposta | Venda ouro VIP atual → proposta | Venda verdes comum atual → proposta | Venda verdes VIP atual → proposta |
|---|---|---|---|---|---|
| galinha | 8.000 / 9 | 9.200,0 → 8.480,0 | 10.000,0 → 8.600,0 | 10,4 → 9,1 | 11,3 → 9,2 |
| abelha | 28.000 / 28 | 32.200,0 → 29.200,0 | 35.000,0 → 29.470,0 | 32,2 → 28,2 | 35,0 → 28,4 |
| pombo | 36.000 / 36 | 41.400,0 → 40.480,0 | 45.000,0 → 41.560,0 | 41,4 → 36,7 | 45,0 → 37,3 |
| coelho | 60.000 / 59 | 69.000,0 → 67.440,0 | 75.000,0 → 69.240,0 | 67,9 → 60,2 | 73,8 → 60,8 |
| pato | 70.000 / 69 | 80.500,0 → 73.000,0 | 87.500,0 → 73.720,0 | 79,4 → 69,5 | 86,3 → 69,8 |
| pavao | 100.000 / 99 | 115.000,0 → 106.240,0 | 125.000,0 → 107.710,0 | 113,9 → 100,0 | 123,8 → 100,5 |
| porco | 150.000 / 100 | 172.500,0 → 164.720,0 | 187.500,0 → 168.320,0 | 115,0 → 102,4 | 125,0 → 103,6 |
| burro | 150.000 / 100 | 172.500,0 → 169.840,0 | 187.500,0 → 174.700,0 | 115,0 → 103,2 | 125,0 → 104,8 |
| ovelha | 180.000 / 120 | 207.000,0 → 186.240,0 | 225.000,0 → 187.740,0 | 138,0 → 121,0 | 150,0 → 121,5 |
| vaca | 220.000 / 180 | 253.000,0 → 228.160,0 | 275.000,0 → 230.140,0 | 207,0 → 181,3 | 225,0 → 182,0 |
| cachorro-robo | 20.000 / 15 | Não vendável | Não vendável | Não vendável | Não vendável |
| cachorro-mastiff | 50.000 / 50 | Não vendável | Não vendável | Não vendável | Não vendável |
| avestruz | 80.000 / 75 | Não vendável | Não vendável | Não vendável | Não vendável |
| cabra | 120.000 / 100 | Não vendável | Não vendável | Não vendável | Não vendável |
| cavalo | 150.000 / 120 | Não vendável | Não vendável | Não vendável | Não vendável |

Os níveis e demais parâmetros dos 15 tipos estão em `current-proposed.csv`. Nenhum protetor passa a produzir, ser revendido ou exigir moeda real. O limite de um por tipo é mantido; não há evidência que justifique aumentar a oferta de slots nesta proposta.

## 5. Justificativa matemática de cada alteração

Para cada vendável na ordem do catálogo, índice i de zero a nove, C é a aquisição em ouro, H é o total de horas alimentadas e F é a ração paga. A margem comum candidata é `H × (60 + 30i)` ouro. A venda é `C + F + margem`; com VIP, só a margem aumenta 25%. Isso recompõe a alimentação na rota ouro e torna o lucro absoluto/h estritamente crescente sem alterar XP, duração ou compra.

| Animal | Custo ração recomposto em ouro | Margem/h atual → proposta comum | Margem/h VIP proposta | Motivo específico |
|---|---|---|---|---|
| galinha | 0 | 150,000 → 60 | 75,00 | Reduz o piso de geração sem cobrar a refeição inicial |
| abelha | 120 | 340,000 → 90 | 112,50 | Sobe o retorno/h sobre galinha, sem depender de principal maior |
| pombo | 160 | 145,556 → 120 | 150,00 | Remove a baixa eficiência de ouro frente à abelha |
| coelho | 240 | 182,500 → 150 | 187,50 | Mantém crescimento mais rentável/h que pombo |
| pato | 120 | 648,750 → 180 | 225,00 | Corrige o salto de margem do produtor no nível 28 |
| pavao | 360 | 522,857 → 210 | 262,50 | Supera pato em retorno/h em vez de ser dominado |
| porco | 320 | 369,667 → 240 | 300,00 | Evita salto por manter 15% de um principal elevado |
| burro | 400 | 306,944 → 270 | 337,50 | Dá ganho/h maior que porco apesar do ciclo mais longo |
| ovelha | 240 | 1.338,000 → 300 | 375,00 | Reduz o salto de emissão no nível 44 |
| vaca | 240 | 1.365,000 → 330 | 412,50 | Mantém maior renda absoluta/h entre os vendáveis |

As metas de 60 e incremento de 30 ouro/h são uma escolha de projeto, não uma estimativa histórica: juntas geram 1.950 ouro/h no portfólio completo comum. Isso aproxima a eficiência de capital do cultivo V1 em vez de escalar só com o preço de aquisição. Animais avançados continuam gerando mais ouro/h e verdes/h, mas não lideram necessariamente ROI por principal ou XP/h. A compra crescente, os tempos e o XP preservados tornam impossível igualar todas essas métricas sem mudar outros parâmetros. O limite de um por espécie dá utilidade ao investimento marginal nas espécies seguintes.

Para verdes, a meta comum é `(0,010 + 0,005i) verdes/h`, convertida em ganho por ciclo e arredondada uma vez ao décimo mais próximo (empate para cima). A meta VIP parte de 1,5 vez esse ganho e usa o menor décimo que também mantenha retorno/h maior que o VIP anterior. Formalmente, com n em décimos e v anterior: `n_i = round_half_up(10 × H_i × meta_i)` e `v_i = max(round_half_up(1,5 × n_i), floor(v_anterior × H_i/H_anterior) + 1)`. A venda verde é principal + ganho. Ração em ouro não é convertida nem embutida em verdes.

O piso de décimos produz ganhos VIP percentuais maiores nos primeiros tipos: galinha passa de 0,1 para 0,2 por ciclo, e abelha de 0,2 para 0,4. Esse aumento é na pequena margem, não no principal, e evita platô horário nos primeiros níveis. O vetor autorizado para discussão está no harness e nas tabelas; uma eventual implementação deverá persistir o preço unitário final, sem aplicar outro multiplicador.

Todos os preços de compra ficam preservados. A relação entre os dois preços de um mesmo animal pode ser descrita como ouro evitado ao optar por verdes, mas não é uma taxa de câmbio. Porco/burro oferecem a mesma compra, enquanto a vaca pede mais verdes; o alvo de ganho por hora corrige a emissão desigual sem reescrever o patrimônio necessário para adquirir os animais.

### Protetores e manutenção

Todos duram 30 dias reais e protegem 20% das terras liberadas quando ativos, com teto de cinco e arredondamento da quantidade de terras para cima. Com menos de cinco, o visitante pode escolher uma terra descoberta; cobertura não é uma probabilidade de 20% de bloquear qualquer tentativa. O quinto ativo elimina o furto enquanto todos estão alimentados. Essa complementaridade, o limite de um por tipo e a disponibilidade por nível sustentam manter os preços de compra atuais nesta etapa.

| Cobertura instantânea | Nível mínimo | Compra ouro / verdes | Ração ideal 30 dias em ouro | Total rota ouro | Rota verde: verdes + ouro de ração |
|---|---|---|---|---|---|
| 20% | 10 | 20.000 / 15 | 9.520 | 29.520 | 15 verdes + 9.520 ouro |
| 40% | 20 | 70.000 / 65 | 19.040 | 89.040 | 65 verdes + 19.040 ouro |
| 60% | 30 | 150.000 / 140 | 28.560 | 178.560 | 140 verdes + 28.560 ouro |
| 80% | 40 | 270.000 / 240 | 38.080 | 308.080 | 240 verdes + 38.080 ouro |
| 100% | 50 | 420.000 / 360 | 47.600 | 467.600 | 360 verdes + 47.600 ouro |

Na cobertura contínua, cada protetor usa 120 refeições em 30 dias, das quais 119 são pagas. A refeição inicial já está na compra. Para cinco, são 47.600 ouro em ração de 80, ou 71.400 usando a de 120, sem ganho de duração. A ração de 120 permanece aceita, mas não é a escolha eficiente.

| Logins uniformes/dia | Pagamentos de ração por protetor em 30 dias | Horas ativas em 720 h | Tempo com a cobertura escolhida ativa | Ração ouro para cinco |
|---|---|---|---|---|
| 3 | 89 | 540,0 | 75,00% | 35.600 |
| 6 | 89 | 540,0 | 75,00% | 35.600 |
| 10 | 99 | 600,0 | 83,33% | 39.600 |
| 4 | 119 | 720,0 | 100,00% | 47.600 |

Quatro logins ajustados a cada seis horas são a referência ideal, não um cenário humano garantido. Nos perfis solicitados, três e seis logins uniformes reabastecem efetivamente a cada oito horas e sustentam 75% do tempo; dez logins a cada 2,4 h reabastecem a cada 7,2 h e sustentam 83,33%. Quando os cinco são alimentados juntos, a proteção alterna entre integral e ausência, em vez de ser uma cobertura parcial estática. Horários humanos adaptativos ou oito horas de sono podem mudar esses resultados.

Com apenas as premiações até o nível, preservadas integralmente, os saldos de referência são 54.500 ouro/11 verdes no nível 10, 486.500/59 no 20, 2.145.500/240 no 30, 6.195.500/690 no 40, 12.555.500/1.385 no 50 e 17.955.500/1.880 no 60. Isso é um teto de patrimônio retido, não uma promessa de liquidez: jogadores já terão gasto parte desses recursos.

Na rota ouro, o cão de 20.000 com manutenção contínua de 9.520 cabe no saldo retido do nível 10, e o conjunto integral de 467.600 cabe no saldo retido do nível 50. Na rota verde, o novato de nível 10 não tem os 15 verdes do cão só com essas premiações; no nível 20, os 59 verdes tampouco compram os dois primeiros por 65. Ouro é uma alternativa real, sem exigir dinheiro de fora. A ajuda de 100 ouro/dia, isoladamente, não sustenta nem um protetor continuamente (317,33 ouro/dia); cultivo ou reservas são necessários.

Não recomendo alterar mecânica, duração, alimentação ou compras dos protetores. A simulação de fazenda protegida paga aquisição e ração com os recursos do perfil, mede cobertura temporal real e não atribui uma receita fictícia de furtos evitados. O valor defensivo exige um cenário social com visitantes e safras, fora desta comparação de emissão monetária.

## 6. Simulações comparativas antes/depois

São 1.248 trajetórias com observações aos 7, 15 e 30 dias, totalizando 3.744 linhas e 1.872 pares antes/depois. Usam 3/6/10 logins uniformes, compras em ouro/verdes, intenção comum/VIP, níveis iniciais 1/10/20/30/40/50/60 e estratégias de só plantas, só vendáveis, combinação ou fazenda com protetores. Níveis e saldos evoluem, o limite por tipo é verificado a cada login e a substituição é comprada após a venda.

O nível 1 usa exatamente 500 ouro, cinco verdes e cinco terras. Nos níveis seguintes há dois cenários de capital: (a) saldos iniciais mais todas as recompensas históricas até aquele nível, ainda retidas; (b) liquidez controlada de 2.000.000 ouro e 2.000 verdes, presumida como patrimônio obtido anteriormente jogando. Esse segundo caso isola capacidade e VIP, sem alegar que uma conta nova possua essas reservas. Não há compra de moeda real nem conversão entre moedas. Em ambos, as terras extras são compradas por 50 verdes e o cartão VIP custa 100, sem concessão gratuita.

No core real, GameState processa compra, limite, fome, alimentação, produção, coleta, remoção, XP, nível, recompensas, missões, plantio V1 e expansão. A ajuda online de 100 ouro/dia UTC é emulada explicitamente conforme OnlineGameService, pois não pertence ao GameState desktop. Não são exercitados HTTP, autenticação, recibos ou concorrência nessa emulação.

O modelo proposto usa um overlay isolado na liquidação: executa a ação atual, preserva XP/missões/remoção, e substitui em memória apenas o crédito de venda do animal pelo preço candidato. O ajuste é registrado na contabilidade. Esse é um contrafactual de preço, não uma implementação V2 no jogo. Os arquivos de produção e seus hashes permanecem iguais.

A política vende imediatamente, alimenta na primeira visita com fome e saldo, recompra um de cada espécie em ordem de catálogo e depois expande as terras. Na combinação, reserva 1.500 ouro antes de comprar um animal com ouro. A intenção VIP reserva 100 verdes até comprar o cartão, podendo adiar outras aquisições; `vip_active_hours` informa o tempo realmente ativo. Plantas preferem comuns na rota ouro e exclusivas verdes quando VIP está ativo na rota verde; sem acesso VIP, usam comuns. É uma estratégia fixa, não um otimizador de lucro.

### Conta nova, compras em ouro, sem VIP, estratégia combinada, 30 dias

| Logins | Nível atual → proposto | Caixa ouro atual → proposto | Caixa verdes atual → proposto | Ciclos animais atual → proposto |
|---|---|---|---|---|
| 3 | 27 → 27 | 1.768.349,7 → 1.621.909,7 | 39,0 → 39,0 | 107 → 107 |
| 6 | 30 → 30 | 2.402.285,4 → 2.169.745,4 | 48,0 → 48,0 | 134 → 134 |
| 10 | 29 → 29 | 2.313.680,4 → 2.137.120,4 | 49,0 → 49,0 | 111 → 111 |

### Nível inicial 60 com premiações retidas, rota verde, somente vendáveis, 30 dias

| Logins | VIP pretendido | Lucro animal verde atual → proposto | Ração ouro atual = proposta | Caixa verde atual → proposto |
|---|---|---|---|---|
| 3 | Não | 2.798,3 → 193,0 | 44.120 | 4.118,3 → 1.513,0 |
| 6 | Não | 3.142,7 → 225,7 | 52.320 | 4.462,7 → 1.545,7 |
| 10 | Não | 2.704,3 → 202,2 | 45.960 | 4.024,3 → 1.522,2 |
| 3 | Sim | 4.658,3 → 305,5 | 44.120 | 5.878,3 → 1.525,5 |
| 6 | Sim | 5.232,1 → 357,3 | 52.320 | 6.452,1 → 1.577,3 |
| 10 | Sim | 4.502,3 → 319,7 | 45.960 | 5.722,3 → 1.539,7 |

Os dois perfis de nível 60 começam com 1.880 verdes. No caso comum de seis logins, a proposta encerra com 1.545,7 em caixa, mas realizou 225,7 de lucro animal verde e ainda mantém 800 verdes de principal em animais vivos. Somar caixa e principal explica a aparente redução. Além dos negócios, esse caso recebe 180 verdes ao cruzar o nível 61 e 60 em missões; eles estão separados no CSV e não são lucro do animal.

### Horizontes 7, 15 e 30 dias — nível inicial 60, seis logins, rota verde, sem VIP

| Dias | Lucro verde atual → proposto | Caixa verde atual → proposto | Principal verde em animais vivos atual / proposto |
|---|---|---|---|
| 7 | 675,4 → 48,0 | 1.949,4 → 1.322,0 | 800,0 / 800,0 |
| 15 | 1.520,9 → 107,9 | 2.810,9 → 1.397,9 | 800,0 / 800,0 |
| 30 | 3.142,7 → 225,7 | 4.462,7 → 1.545,7 | 800,0 / 800,0 |

Os CSVs incluem os níveis iniciais 10, 20, 30, 40 e 50 e os mesmos horizontes/perfis. Nos cenários de patrimônio retido, VIP pode não ser comprado imediatamente nos níveis 10/20. As linhas de liquidez controlada permitem isolar a venda VIP nesses níveis sem fingir acesso gratuito.

### Cultivo V1 e isolamento do experimento

Todos os pares de só plantas e fazenda protegida são idênticos entre CURRENT e PROPOSED em todos os campos financeiros, XP e cobertura. Essa igualdade é uma verificação automática do gerador de relatório. Na estratégia combinada, mudanças de liquidez e reinvestimento podem alterar quanto se planta ou quando se compra uma terra, sem mudar nenhum preço ou tempo de planta.

Caixa é saldo disponível. Capital animal é o custo dos animais vivos, inclusive prontos ainda não vendidos. Custo de ração de ciclos vivos aparece separado. Lucro animal realizado desconta somente principal e ração dos ciclos encerrados. Cultivo separa a fração de custo da semente das temporadas vendidas e mantém o custo pendente nas plantas. Terrenos e a parcela não consumida de VIP/protetores são custo contábil, sem preço garantido de revenda. Recompensas de nível, missões e ajuda diária são fontes separadas. Nenhuma dessas parcelas deve ser somada entre ouro e verdes.

## 7. Impacto na emissão de ouro e verdes

| Portfólio nominal completo | Atual por dia | Proposto por dia |
|---|---|---|
| Lucro ouro comum | 128.862,6 | 46.800,0 |
| Lucro ouro VIP antes do custo do cartão | 215.934,0 | 58.500,0 |
| Ganho verde comum, com ração em ouro à parte | 108,621 | 7,900 |
| Ganho verde VIP antes do cartão e da ração em ouro | 180,840 | 12,492 |

Esses são limites de regime com alimentação pontual e recompra imediata, não o caixa dos cenários de visitas. O portfólio exige 1.002.000 ouro ou 800 verdes de principal, mais ração e liquidez. Na compra em verdes, a ração ideal de todos os ciclos consome ouro continuamente. A proposta reduz fortemente a emissão verde sem eliminá-la e mantém fontes obtidas jogando.

Como referência separada, 30 terras de caviar comum V1 produzem lucro nominal de 1.350 ouro/h sobre 750.000 de sementes. O portfólio animal candidato produz 1.950 ouro/h sobre 1.002.000 de aquisições, já descontando ração. Em verdes, 30 cocos VIP V1 produzem lucro nominal de 44,571 verdes/dia sobre 600 de sementes, antes do custo compartilhado do VIP. Os animais propostos geram cerca de 7,90 verdes/dia comuns ou 12,5 VIP no limite nominal, usam outros slots e gastam ração em ouro. Essas são comparações na mesma moeda, sem converter a despesa ouro em verdes.

No portfólio completo de nível 60, o bônus verde proposto de VIP recupera a taxa de 100 no mês com pequena sobra: 12,5 / 31,6 / 17,5 verdes a mais em caixa nos perfis de 3/6/10 logins que no comum. Isso não garante que VIP se pague só com animais nos níveis menores. A parcela de lucro em ouro adicional também é uma vantagem distinta, sem taxa de câmbio. O cartão e as recompensas não são alterados.

Renovar os cinco protetores custa 360 verdes a cada 30 dias, além da ração em ouro. A emissão animal nominal proposta soma aproximadamente 237 verdes comuns ou 375 VIP por mês; somar até 60 verdes de tarefas ainda não sustenta, isoladamente, 360 de renovação mais 100 do cartão VIP. No cenário efetivo VIP de seis visitas, são 357,3 + 60 = 417,3, abaixo dos 460. A alternativa é comprar protetores em ouro ou combinar receitas do cultivo V1. Premiações de nível são finitas e não devem justificar sustentabilidade recorrente.

As rotas de compra são fixadas por cenário, sem otimização de uma carteira híbrida. A prioridade animais, depois expansão, depois plantio pode consumir a liquidez destinada às sementes verdes VIP. Resultados dessa política não representam o melhor rendimento possível do jogo.

## 8. Exploração, limites e compatibilidade

A redução proposta não depende de caixa real, nem cria checkout, câmbio ou exclusividade. Todas as espécies mantêm compra nas duas moedas. Receita verde recorrente não é um bug por si só; precisa de um orçamento de emissão adequado às outras fontes. O retorno por hora crescente evita dominância normalizada anterior, mas ROI por capital e XP/h ainda têm trade-offs por causa de preços/tempos preservados.

O sistema atual arredonda o total por operação; vender separadamente produtos com meias décimas pode somar um décimo a mais que a venda conjunta. Não há multiplicação infinita de um mesmo item por replay, mas a escolha de agrupamento altera a fração paga. A implementação candidata deve guardar preços finais em décimos por unidade, como na V1 de plantas, para que o preço V2 não dependa do agrupamento. O estoque legado conserva a regra aprovada até ser vendido.

Não reprecificar animais existentes, adultos prontos ou produtos já coletados. Todos devem conservar origem, moeda, custo e a regra antiga até a venda. Protetores mantêm aquisição, prazo, comida e seleção de terras atuais. Uma compra depois da futura ativação V2 receberá metadados próprios de animal, sem reutilizar cropSale, com comum/VIP na moeda de aquisição e versão fixada. Ausência de metadados novos identifica legado, preservando seu preço. Perda isolada dos termos de um lote marcado V2 deve causar recusa, não fallback.

A venda deve continuar autorizada pelo servidor, sob lock e recibo na mesma transação do estoque. Não aceitar preços, tempo, XP ou VIP enviados pelo cliente. Vender produto não pode conceder novamente XP de coleta. A última ração de um adulto/produto pronto não pode ser cobrada. O tempo real do servidor continua governando VIP e expiração dos protetores; sono não vira atalho econômico.

## 9. Estratégia recomendada de implementação e migração

1. Revisar e aprovar ou ajustar as duas curvas de margem propostas. Manter compras, tempos, comida, XP e proteção nesta primeira implementação, pois não se demonstrou necessidade de alterá-los para corrigir emissão e dominância.
2. Acrescentar uma versão de termos animal separada dos metadados de plantas. Capturar custo, moeda e preços comum/VIP exatos na compra; transportar os termos ao produto na coleta. Guardar a autoridade de cada versão para não reconstruir um item antigo com um catálogo novo.
3. Unificar cotação e liquidação no domínio para adulto e produto, usando os mesmos valores em API, depósito e loja desktop/web. Aplicar o perfil VIP no instante da venda. Os dois caminhos atuais de venda precisam ser atualizados juntos.
4. Carregar saves válidos antigos explicitamente e manter seus preços. Recusar corrupção sem sobrescrever. Atualizar o identificador do ruleset online junto do snapshot nos dois caminhos de escrita. Uma migração não deve alterar saldo, fome, crescimento, prazo de proteção ou XP de um animal já adquirido.
5. Fazer backup coerente de fazendas e recibos, validar migração descartável e planejar API antes do cliente. Evitar versões de servidor antigas e novas atendendo simultaneamente. Rollback deve continuar lendo/liquidando termos V2; não usar leitor que descarte esses campos.
6. Depois da aprovação, abrir um PR de implementação separado, executar CI e playtest em ambiente isolado. Não há PR de implementação nem publicação nesta entrega de estudo.

## 10. Verificações executadas e testes para aprovação

Executado com JDK 21, core real, Gson existente e assertions ativas. Foram geradas 80 rotas analíticas, 15 linhas atuais/propostas, 120 combinações de proteção e 3.744 observações de portfólio. Cada saldo foi conciliado com compras, vendas, ração, ajuda diária, missões e recompensas. O máximo de dois verdes/dia de missões e a ausência de subsídios não registrados foram verificados. A contagem de refeições real coincide com a fórmula para cada animal nas três cadências. Os 1.872 pares têm chaves válidas e nenhuma dominância normalizada de nível inferior permanece na proposta. Os arquivos econômicos ficaram com os mesmos hashes.

Para reproduzir no PowerShell, a partir do repositório com JAVA_HOME apontando para o JDK 21 existente:

```powershell
.\analysis\animals-v2\run.ps1
node analysis/animals-v2/verify-and-report.mjs
```

Antes de aprovar uma implementação:

- Tabela de todos os dez vendáveis por moeda/perfil, ração inicial incluída, refeições pagas, limites por espécie, tipo e nível; cinco protetores inalterados.
- Fluxos completos reais de produtor e crescimento, fome/retomada/offline, último alimento, adulto pronto, remoção após venda e recompra. Não produzir ciclos extras de uma única aquisição.
- VIP comprado, expirado, renovado e mudando entre coleta/venda, inclusive produto antigo e compra em cada moeda. Nenhuma venda deve alterar a moeda de origem.
- Venda unitária, por tipo e total, estoque misto de animais antigos/V2 e plantas v0/v1, somas exatas em décimos, quotes e interfaces concordando com o servidor.
- Replay, transações concorrentes e rollback com PostgreSQL; revogação e autorização, preço forjado no pedido e reconexão com confirmação desconhecida.
- Persistência negativa com versões/campos/índices corrompidos, perda dos termos, custo/moeda/perfil inconsistentes, inteiros fracionários e overflow. Saves antigos válidos e bytes corrompidos preservados.
- Proteção com 0–5 ativos, fome exata, alimentação/expiração, ceil de terras, mesma seleção persistente e janela de cinco minutos/cooldown de furto preservados.
- Repetir os cenários deste estudo com preços implementados em vez do overlay, mais horários humanos com sono e visitas irregulares, riscos sociais e pragas. Fazer playtest antes de tratar as curvas como definitivas.

O estudo é determinístico, sem furtos/pragas aleatórias, fertilizantes ou rede. A proteção mede custo/cobertura, não moeda obtida por perdas evitadas. A simulação usa pedidos lógicos do GameState e emula a ajuda online; não testa HTTP ou a atomicidade do futuro preço V2. Carteiras históricas retidas e liquidez controlada são cenários declarados, não retratos de saldos reais. O lucro realizado não inclui principal ativo, dinheiro de nível ou receitas futuras. O cenário uniforme não garante que dez visitas humanas tenham o mesmo desempenho observado.

A revisão independente do código/CSV não encontrou bloqueios matemáticos; confirmou a refeição inicial, separação monetária, overlay, ledger, XP e integração temporal de proteção. Ela não executou a matriz nem revisou este texto final. Este relatório e os CSVs são proposta para aprovação e não alteram regras do jogo.

### Arquivos entregues

- [Tabela atual e proposta dos 15 animais](analysis/animals-v2/results/current-proposed.csv)
- [80 rotas analíticas completas](analysis/animals-v2/results/animal-metrics.csv)
- [Proteção, custo e tempo ativo](analysis/animals-v2/results/protection-costs.csv)
- [3.744 observações de portfólios](analysis/animals-v2/results/portfolio-simulations.csv)
- [1.872 comparações antes/depois](analysis/animals-v2/results/simulation-comparisons.csv)
- [Diagnóstico de dominância](analysis/animals-v2/results/dominated-routes.csv)

Os CSVs usam vírgula como separador, ponto decimal, GOLD/GREEN e NA para indicadores que exigiriam câmbio. O índice de proteção é temporal/territorial, não probabilidade de bloquear cada furto. Fontes e hashes estão em `analysis/animals-v2/results/source-manifest.json`.
