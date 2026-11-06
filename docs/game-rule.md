# Colheita Feliz — Documentação de economia e itens

Este documento reúne os valores e as regras fornecidos para o projeto. As regras de venda abaixo representam a definição de economia do projeto; não constituem uma validação histórica do jogo original.

Esta é a fonte normativa dos valores e das regras desejadas. `docs/regras-de-negocio.md` descreve os fluxos e referencia estas seções, sem manter tabelas concorrentes. Definições documentadas não significam funcionalidades implementadas. Decisões complementares confirmadas em 2026-10-26 estão nas seções 10 a 14.

## 1. Moedas

- **Ouro:** moeda utilizada nos preços e custos indicados como ouro.
- **Moedas verdes:** moeda utilizada em compras premium e alternativas de aquisição.
- Nas tabelas de preços, ouro e moedas verdes representam alternativas de compra, não uma soma. As recompensas da seção 8 concedem ambas as moedas cumulativamente.
- Moedas verdes são obtidas jogando: saldo inicial, recompensas de nível, tarefas diárias e vendas de produtos originados de aquisições em verdes. Não há compra de moeda com dinheiro real, câmbio automático ou integração de pagamentos.

## 2. Sementes e plantas: tabela consolidada

O preço de compra é por semente. A receita v1 é o retorno do ciclo completo, repartido entre as temporadas na moeda da aquisição. Cada produto preserva o custo da semente e sua própria temporada. Todas as culturas rendem uma unidade por terreno em cada temporada. As sementes VIP exigem cartão VIP ativo para compra, plantio e venda dos produtos, conforme as seções 7 e 9.2.

Desde o balanceamento v1 de 2026-11-05, a coluna de venda mostra os valores exatos por temporada, na ordem das colheitas. Para culturas comuns, VIP ativo na venda acrescenta somente a parcela proporcional de 10% do custo da semente, dividida entre todas as temporadas. Culturas exclusivas VIP exigem VIP ativo na venda e recebem apenas o valor da tabela, sem bônus adicional. As plantas e os produtos anteriores conservam a regra v0, conforme a seção 6.3.

Os tempos são as durações nominais de crescimento do estágio 1 ao 5, sujeitos à rega e às pausas por pragas e insetos. O total de temporadas inclui a primeira colheita. A sequência de XP corresponde às colheitas da primeira até a última temporada, calculada conforme a seção 3.1.

| Semente | Condição | Nível mínimo | Compra por semente | Venda comum por temporada | Venda VIP por temporada | Primeira colheita | Intervalo após próximas coletas | Temporadas | Unidades por colheita | XP por temporada | Cinco estágios em disco |
|---|---|---:|---|---|---|---|---|---:|---:|---|---|
| Nabo | Comum | 1 | 300 ouro | 345,0 ouro | 375,0 ouro | 10 h | Não se aplica | 1 | 1 | 15 | Sim |
| Cenoura | Comum | 1 | 350 ouro | 440,0 ouro | 475,0 ouro | 15 h | Não se aplica | 1 | 1 | 22,5 | Sim |
| Milho | Comum | 2 | 500 ouro | 650,0 ouro | 700,0 ouro | 20 h | Não se aplica | 1 | 1 | 30 | Sim |
| Batata | Comum | 3 | 650 ouro | 848,0 ouro | 913,0 ouro | 22 h | Não se aplica | 1 | 1 | 33 | Sim |
| Berinjela | Comum | 4 | 800 ouro | 1.052,0 ouro | 1.132,0 ouro | 24 h | Não se aplica | 1 | 1 | 36 | Sim |
| Tomate | Comum | 5 | 950 ouro | 679,0 → 679,0 ouro | 726,5 → 726,5 ouro | 26 h | 8 h | 2 | 1 | 39 → 19,5 | Sim |
| Pimenta | Comum | 5 | 1.100 ouro | 799,8 → 799,7 ouro | 854,8 → 854,7 ouro | 28 h | 9 h | 2 | 1 | 42 → 21 | Sim |
| Abóbora | Comum | 7 | 1.250 ouro | 917,5 → 917,5 ouro | 980,0 → 980,0 ouro | 29 h | 10 h | 2 | 1 | 43,5 → 21,75 | Sim |
| Ervilha | Comum | 9 | 1.400 ouro | 1.038,3 → 1.038,2 ouro | 1.108,3 → 1.108,2 ouro | 30 h | 11 h | 2 | 1 | 45 → 22,5 | Sim |
| Morango | Comum | 11 | 1.550 ouro | 846,7 → 846,7 → 846,6 ouro | 898,4 → 898,4 → 898,2 ouro | 31 h | 12 h | 3 | 1 | 46,5 → 23,25 → 11,625 | Sim |
| Maçã | Comum | 13 | 1.800 ouro | 1.055,0 → 1.055,0 → 1.055,0 ouro | 1.115,0 → 1.115,0 → 1.115,0 ouro | 42 h | 14 h | 3 | 1 | 63 → 31,5 → 15,75 | Sim |
| Melancia | Comum | 16 | 1.975 ouro | 1.204,4 → 1.204,3 → 1.204,3 ouro | 1.270,3 → 1.270,1 → 1.270,1 ouro | 46 h | 16 h | 3 | 1 | 69 → 34,5 → 17,25 | Sim |
| Banana | Comum | 19 | 2.150 ouro | 1.361,7 → 1.361,7 → 1.361,6 ouro | 1.433,4 → 1.433,4 → 1.433,2 ouro | 50 h | 18 h | 3 | 1 | 75 → 37,5 → 18,75 | Sim |
| Pêssego | Comum | 22 | 3.075 ouro | 1.777,0 → 1.777,0 → 1.777,0 ouro | 1.879,5 → 1.879,5 → 1.879,5 ouro | 54 h | 20 h | 3 | 1 | 81 → 40,5 → 20,25 | Sim |
| Laranja | Comum | 25 | 3.350 ouro | 2.000,7 → 2.000,7 → 2.000,6 ouro | 2.112,4 → 2.112,4 → 2.112,2 ouro | 60 h | 22 h | 3 | 1 | 90 → 45 → 22,5 | Sim |
| Uva | Comum | 28 | 4.125 ouro | 1.962,8 → 1.962,8 → 1.962,7 → 1.962,7 ouro | 2.066,0 → 2.065,9 → 2.065,8 → 2.065,8 ouro | 66 h | 24 h | 4 | 1 | 99 → 49,5 → 24,75 → 12,375 | Sim |
| Romã | Comum | 31 | 3.875 ouro | 2.158,7 → 2.158,6 → 2.158,6 → 2.158,6 ouro | 2.255,6 → 2.255,5 → 2.255,5 → 2.255,4 ouro | 68 h | 33 h | 4 | 1 | 102 → 51 → 25,5 → 12,75 | Sim |
| Melão | Comum | 35 | 4.600 ouro | 2.462,5 → 2.462,5 → 2.462,5 → 2.462,5 ouro | 2.577,5 → 2.577,5 → 2.577,5 → 2.577,5 ouro | 70 h | 35 h | 4 | 1 | 105 → 52,5 → 26,25 → 13,125 | Sim |
| Cana | Comum | 39 | 4.600 ouro | 2.591,2 → 2.591,1 → 2.591,1 → 2.591,1 ouro | 2.706,2 → 2.706,1 → 2.706,1 → 2.706,1 ouro | 72 h | 37 h | 4 | 1 | 108 → 54 → 27 → 13,5 | Sim |
| Lichia | Comum | 43 | 4.750 ouro | 2.763,3 → 2.763,3 → 2.763,2 → 2.763,2 ouro | 2.882,1 → 2.882,1 → 2.881,9 → 2.881,9 ouro | 74 h | 39 h | 4 | 1 | 111 → 55,5 → 27,75 → 13,875 | Sim |
| Kiwi | Comum | 45 | 5.050 ouro | 2.978,9 → 2.978,9 → 2.978,9 → 2.978,8 ouro | 3.105,2 → 3.105,2 → 3.105,1 → 3.105,0 ouro | 76 h | 41 h | 4 | 1 | 114 → 57 → 28,5 → 14,25 | Sim |
| Abacaxi | Comum | 47 | 5.200 ouro | 3.163,0 → 3.163,0 → 3.163,0 → 3.163,0 ouro | 3.293,0 → 3.293,0 → 3.293,0 → 3.293,0 ouro | 78 h | 43 h | 4 | 1 | 117 → 58,5 → 29,25 → 14,625 | Sim |
| Cereja | Comum | 49 | 5.500 ouro | 3.390,7 → 3.390,6 → 3.390,6 → 3.390,6 ouro | 3.528,2 → 3.528,1 → 3.528,1 → 3.528,1 ouro | 80 h | 45 h | 4 | 1 | 120 → 60 → 30 → 15 | Sim |
| Pitaya | Comum | 51 | 6.180 ouro | 3.719,3 → 3.719,3 → 3.719,2 → 3.719,2 ouro | 3.873,8 → 3.873,8 → 3.873,7 → 3.873,7 ouro | 82 h | 47 h | 4 | 1 | 123 → 61,5 → 30,75 → 15,375 | Sim |
| Manga | Comum | 53 | 6.850 ouro | 4.051,4 → 4.051,4 → 4.051,4 → 4.051,3 ouro | 4.222,7 → 4.222,7 → 4.222,6 → 4.222,5 ouro | 84 h | 49 h | 4 | 1 | 126 → 63 → 31,5 → 15,75 | Sim |
| Cacau | Comum | 55 | 8.500 ouro | 4.634,5 → 4.634,5 → 4.634,5 → 4.634,5 ouro | 4.847,0 → 4.847,0 → 4.847,0 → 4.847,0 ouro | 86 h | 51 h | 4 | 1 | 129 → 64,5 → 32,25 → 16,125 | Sim |
| Goiaba | Comum | 57 | 15.000 ouro | 6.751,5 → 6.751,5 → 6.751,5 → 6.751,5 ouro | 7.126,5 → 7.126,5 → 7.126,5 → 7.126,5 ouro | 96 h | 60 h | 4 | 1 | 144 → 72 → 36 → 18 | Sim |
| Caviar | Comum | 60 | 25.000 ouro | 9.536,0 → 9.536,0 → 9.536,0 → 9.536,0 → 9.536,0 ouro | 10.036,0 → 10.036,0 → 10.036,0 → 10.036,0 → 10.036,0 ouro | 168 h | 84 h | 5 | 1 | 252 → 126 → 63 → 31,5 → 15,75 | Sim |
| Batata-doce | VIP | 10 | 4 verdes | Bloqueada sem VIP | 2,5 → 2,5 verdes | 12 h | 6 h | 2 | 1 | 18 → 9 | Sim |
| Amendoim | VIP | 20 | 8 verdes | Bloqueada sem VIP | 3,6 → 3,6 → 3,6 verdes | 24 h | 12 h | 3 | 1 | 36 → 18 → 9 | Sim |
| Limão | VIP | 30 | 12 verdes | Bloqueada sem VIP | 4,4 → 4,4 → 4,3 → 4,3 verdes | 36 h | 18 h | 4 | 1 | 54 → 27 → 13,5 → 6,75 | Sim |
| Noz | VIP | 40 | 16 verdes | Bloqueada sem VIP | 5,0 → 5,0 → 5,0 → 4,9 → 4,9 verdes | 48 h | 24 h | 5 | 1 | 72 → 36 → 18 → 9 → 4,5 | Sim |
| Coco | VIP | 50 | 20 verdes | Bloqueada sem VIP | 5,5 → 5,5 → 5,5 → 5,5 → 5,5 → 5,5 verdes | 60 h | 30 h | 6 | 1 | 90 → 45 → 22,5 → 11,25 → 5,625 → 2,8125 | Sim |

### 2.1. Receita e lucro por ciclo v1

Os valores de culturas comuns são em ouro e usam perfil comum durante todas as vendas. Os valores de culturas exclusivas VIP são em verdes. O tempo inclui a primeira duração e todos os intervalos seguintes, com rega em dia e sem pragas. Lucro é receita menos uma única semente, antes de outros custos. As taxas são metas de projeto para playtest de 30 dias.

| ID | Custo da semente | Temporadas | Horas nominais totais | Receita total | Lucro do ciclo | Ouro líquido/h ou ROI VIP |
|---|---:|---:|---:|---:|---:|---:|
| nabo | 300,0 | 1 | 10 | 345,0 | 45,0 | 4,5 |
| cenoura | 350,0 | 1 | 15 | 440,0 | 90,0 | 6,0 |
| milho | 500,0 | 1 | 20 | 650,0 | 150,0 | 7,5 |
| batata | 650,0 | 1 | 22 | 848,0 | 198,0 | 9,0 |
| berinjela | 800,0 | 1 | 24 | 1.052,0 | 252,0 | 10,5 |
| tomate | 950,0 | 2 | 34 | 1.358,0 | 408,0 | 12,0 |
| pimenta | 1.100,0 | 2 | 37 | 1.599,5 | 499,5 | 13,5 |
| abobora | 1.250,0 | 2 | 39 | 1.835,0 | 585,0 | 15,0 |
| ervilha | 1.400,0 | 2 | 41 | 2.076,5 | 676,5 | 16,5 |
| morango | 1.550,0 | 3 | 55 | 2.540,0 | 990,0 | 18,0 |
| maca | 1.800,0 | 3 | 70 | 3.165,0 | 1.365,0 | 19,5 |
| melancia | 1.975,0 | 3 | 78 | 3.613,0 | 1.638,0 | 21,0 |
| banana | 2.150,0 | 3 | 86 | 4.085,0 | 1.935,0 | 22,5 |
| pessego | 3.075,0 | 3 | 94 | 5.331,0 | 2.256,0 | 24,0 |
| laranja | 3.350,0 | 3 | 104 | 6.002,0 | 2.652,0 | 25,5 |
| uva | 4.125,0 | 4 | 138 | 7.851,0 | 3.726,0 | 27,0 |
| roma | 3.875,0 | 4 | 167 | 8.634,5 | 4.759,5 | 28,5 |
| melao | 4.600,0 | 4 | 175 | 9.850,0 | 5.250,0 | 30,0 |
| cana | 4.600,0 | 4 | 183 | 10.364,5 | 5.764,5 | 31,5 |
| lichia | 4.750,0 | 4 | 191 | 11.053,0 | 6.303,0 | 33,0 |
| kiwi | 5.050,0 | 4 | 199 | 11.915,5 | 6.865,5 | 34,5 |
| abacaxi | 5.200,0 | 4 | 207 | 12.652,0 | 7.452,0 | 36,0 |
| cereja | 5.500,0 | 4 | 215 | 13.562,5 | 8.062,5 | 37,5 |
| pitaya | 6.180,0 | 4 | 223 | 14.877,0 | 8.697,0 | 39,0 |
| manga | 6.850,0 | 4 | 231 | 16.205,5 | 9.355,5 | 40,5 |
| cacau | 8.500,0 | 4 | 239 | 18.538,0 | 10.038,0 | 42,0 |
| goiaba | 15.000,0 | 4 | 276 | 27.006,0 | 12.006,0 | 43,5 |
| caviar | 25.000,0 | 5 | 504 | 47.680,0 | 22.680,0 | 45,0 |
| batata-doce | 4,0 | 2 | 18 | 5,0 | 1,0 | 25% |
| amendoim | 8,0 | 3 | 48 | 10,8 | 2,8 | 35% |
| limao | 12,0 | 4 | 90 | 17,4 | 5,4 | 45% |
| noz | 16,0 | 5 | 144 | 24,8 | 8,8 | 55% |
| coco | 20,0 | 6 | 210 | 33,0 | 13,0 | 65% |

Culturas comuns seguem lucro nominal de 4,5 ouro/h no nabo, aumentando 1,5 ouro/h por posição. As exclusivas VIP seguem ROI de 25%, 35%, 45%, 55% e 65%. A receita comum com VIP integral aumenta exatamente 10% do custo de uma semente por ciclo.

## 3. Cultivo, temporadas e XP

Plantar consome uma semente e inicia a temporada no estágio 1. A primeira rega é necessária para iniciar o crescimento. As durações da tabela da seção 2 são de crescimento efetivo, não uma garantia de tempo corrido independentemente dos cuidados.

Nas plantas com várias temporadas, o intervalo da próxima temporada começa após a coleta da atual, repetindo a progressão do estágio 1 ao 5. Cada temporada mantém o comportamento de uma planta de temporada única: rega continua necessária, e pragas e insetos continuam funcionando normalmente. Após a última colheita da última temporada, a planta morre.

O intervalo úmido é `max(5 min, duração nominal da temporada atual / 3)`. Terra seca cresce a 50% depois da primeira rega; uma temporada ainda não regada não cresce. Cada nova temporada zera crescimento, rega, pragas, insetos e uso de fertilizante. A fórmula de XP continua usando a primeira duração, não o intervalo úmido nem a duração posterior.

Pragas e insetos têm a mesma penalidade: pausam o crescimento, preservando o progresso, até sua remoção. Insetos não reduzem a quantidade colhida. As ferramentas de remoção continuam correspondendo ao tipo: herbicida para as pragas tratadas com herbicida e pesticida para insetos. Se ambas as condições estiverem presentes, o crescimento só retoma após remover ambas.

### 3.1. XP de colheita

O XP da primeira colheita é a duração nominal da primeira colheita em horas multiplicada por 1,5. Cada temporada seguinte concede metade do XP da anterior, sem recalcular a base usando o intervalo das próximas colheitas.

```text
xp_primeira_colheita = horas_primeira_colheita × 1,5
xp_temporada_n = xp_primeira_colheita ÷ 2^(n − 1)
```

O nabo, com primeira colheita de 10 h, concede 15 XP. O morango, com 31 h e três temporadas, concede 46,5 XP, 23,25 XP e 11,625 XP. A aplicação de fertilizante não altera a duração nominal usada como base de XP.

O XP preserva integralmente os valores fracionários resultantes da fórmula, sem arredondamento ou truncamento, tanto na concessão por colheita quanto no saldo acumulado. Por exemplo, 11,625 XP e 2,8125 XP são valores válidos. A regra de arredondamento de moedas da seção 9.1 não se aplica ao XP.


## 4. Animais

### 4.1. Tabela consolidada

Nos preços, a ordem é **ouro / moedas verdes**. A compra usa uma das moedas e a venda devolve a moeda usada na aquisição. Para produtores, o preço de venda corresponde a **uma unidade do produto**; para animais de crescimento, ao **animal adulto**. Os valores de venda foram calculados com os acréscimos da seção 6 e arredondados para cima em uma casa decimal. Em vendas de várias unidades, o arredondamento é aplicado somente ao total, conforme a seção 9.1.

Na coluna de crescimento, os tempos representam bebê → jovem e jovem → adulto, seguidos do total. Na produção, a duração começa na compra e existe apenas um ciclo por animal; não há produções seguintes. Os tempos dependem da alimentação. O limite de quantidade é por tipo de animal.

| Animal | Tipo / produto vendido | Nível / limite | Compra: ouro / verdes | Venda comum: ouro / verdes | Venda VIP: ouro / verdes | Crescimento: bebê → jovem → adulto | Produção: primeira / seguintes | Unidades por ciclo / total de ciclos | Alimentação: custo e duração | XP | Encerramento |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Galinha | Produção / ovo | 10 / 1 | 8.000 / 9 | 9.200 / 10,4 | 10.000 / 11,3 | Comprado adulto | 8 h / Não se aplica | 1 unidade / 1 ciclo; venda única | 120 ouro / 8 h | 100 por coleta | Após coleta única |
| Abelha | Produção / mel | 14 / 1 | 28.000 / 28 | 32.200 / 32,2 | 35.000 / 35 | Comprado adulto | 12 h / Não se aplica | 1 unidade / 1 ciclo; venda única | 120 ouro / 8 h | 140 por coleta | Após coleta única |
| Pombo | Crescimento / adulto | 18 / 1 | 36.000 / 36 | 41.400 / 41,4 | 45.000 / 45 | 12 h + 24 h = 36 h | Não se aplica | Venda única | 80 ouro / 12 h | 180 por venda | Após venda adulta |
| Coelho | Crescimento / adulto | 24 / 1 | 60.000 / 59 | 69.000 / 67,9 | 75.000 / 73,8 | 16 h + 32 h = 48 h | Não se aplica | Venda única | 80 ouro / 12 h | 240 por venda | Após venda adulta |
| Pato | Produção / ovo de pato | 28 / 1 | 70.000 / 69 | 80.500 / 79,4 | 87.500 / 86,3 | Comprado adulto | 16 h / Não se aplica | 1 unidade / 1 ciclo; venda única | 120 ouro / 8 h | 280 por coleta | Após coleta única |
| Pavão | Produção / pena | 32 / 1 | 100.000 / 99 | 115.000 / 113,9 | 125.000 / 123,8 | Comprado adulto | 28 h / Não se aplica | 1 unidade / 1 ciclo; venda única | 120 ouro / 8 h | 320 por coleta | Após coleta única |
| Porco | Crescimento / adulto | 36 / 1 | 150.000 / 100 | 172.500 / 115 | 187.500 / 125 | 20 h + 40 h = 60 h | Não se aplica | Venda única | 80 ouro / 12 h | 360 por venda | Após venda adulta |
| Burro | Crescimento / adulto | 40 / 1 | 150.000 / 100 | 172.500 / 115 | 187.500 / 125 | 24 h + 48 h = 72 h | Não se aplica | Venda única | 80 ouro / 12 h | 400 por venda | Após venda adulta |
| Ovelha | Produção / lã | 44 / 1 | 180.000 / 120 | 207.000 / 138 | 225.000 / 150 | Comprado adulto | 20 h / Não se aplica | 1 unidade / 1 ciclo; venda única | 120 ouro / 8 h | 440 por coleta | Após coleta única |
| Vaca leiteira | Produção / leite | 48 / 1 | 220.000 / 180 | 253.000 / 207 | 275.000 / 225 | Comprado adulto | 24 h / Não se aplica | 1 unidade / 1 ciclo; venda única | 120 ouro / 8 h | 480 por coleta | Após coleta única |
| Cão Robô | Proteção de plantações | 10 / 1 | 20.000 / 15 | Não pode vender | Não pode vender | Comprado adulto | Não produz | Não se aplica | 80 ou 120 ouro / 6 h | 0 | Removido após 30 dias |
| Cão Mastiff | Proteção de plantações | 20 / 1 | 50.000 / 50 | Não pode vender | Não pode vender | Comprado adulto | Não produz | Não se aplica | 80 ou 120 ouro / 6 h | 0 | Removido após 30 dias |
| Avestruz | Proteção de plantações | 30 / 1 | 80.000 / 75 | Não pode vender | Não pode vender | Comprado adulto | Não produz | Não se aplica | 80 ou 120 ouro / 6 h | 0 | Removido após 30 dias |
| Cabra | Proteção de plantações | 40 / 1 | 120.000 / 100 | Não pode vender | Não pode vender | Comprado adulto | Não produz | Não se aplica | 80 ou 120 ouro / 6 h | 0 | Removido após 30 dias |
| Cavalo | Proteção de plantações | 50 / 1 | 150.000 / 120 | Não pode vender | Não pode vender | Comprado adulto | Não produz | Não se aplica | 80 ou 120 ouro / 6 h | 0 | Removido após 30 dias |

### 4.2. Regras de produção, crescimento e alimentação

- Cada unidade de produto animal tem como custo-base o preço de compra do animal que a produziu, na moeda usada na aquisição. Uma galinha de 8.000 ouro produz ovos com custo-base de 8.000 ouro cada.
- Animais de crescimento só podem ser vendidos na fase adulta. Seu custo-base de venda é o preço de compra do próprio animal, na moeda usada na aquisição. Após a venda, o animal é removido.
- O perfil comum ou VIP é determinado no momento da venda.
- Todo animal começa alimentado a 100%, sem compra adicional de ração: 8 h para produtores, 12 h para animais de crescimento e 6 h para protetores. Essa alimentação faz parte da aquisição; não é item gratuito no inventário.
- Cada alimentação atende um único animal, sem acumular duração. Só é permitido alimentar quando o animal estiver com fome; enquanto houver tempo de alimentação restante, uma nova alimentação é bloqueada. Isso também vale para protetores. Animais de crescimento usam ração genérica de crescimento; produtores usam ração genérica de produtor.
- A falta de alimento pausa os tempos de crescimento e produção, preservando o progresso. Ao alimentar novamente, a etapa retoma somente o tempo restante, sem reiniciar.
- Produtores são comprados adultos e produzem uma única unidade em um único ciclo. O produto pronto espera a coleta, sem acumular produção. Após a coleta única, o animal desaparece e o produto permanece no armazém para venda conforme a seção 6. Para produzir novamente, é necessário comprar outro animal.
- Cada animal de crescimento permite uma única venda na fase adulta. Cada produtor permite a venda de seu único produto, sem novos ciclos. Os protetores continuam sem possibilidade de revenda.
- Com o jogo fechado, o tempo avança normalmente, a 1×, respeitando a alimentação e a espera pela coleta.
- O sono é apenas visual: não pausa nem altera produção, crescimento ou proteção. As restrições por falta de alimento continuam valendo.

### 4.3. Regras de proteção

Todos os protetores são comprados adultos e duram 30 dias. Após esse prazo, o animal é removido e o jogador precisa comprá-lo novamente. A falta de alimento pausa a proteção, sem estender a duração de 30 dias. Protetores não podem ser revendidos.

Os protetores não concedem XP por nenhuma ação. Sua função é bloquear ações de visitantes nas terras protegidas, incluindo furto, ajuda com amor e lançamento de pragas. O estado de entrega online é registrado em `ONLINE.md`.

Existem exatamente cinco tipos de protetores: Cão Robô, Cão Mastiff, Avestruz, Cabra e Cavalo, com os preços, níveis e limites da tabela 4.1. O cão normal não integra o catálogo jogável. Cada protetor alimentado e dentro dos 30 dias de validade protege 20% das terras plantáveis do dono. Cinco protetores ativos dão 100% de proteção e impedem qualquer furto. Animais com fome ou expirados não contribuem. A cobertura máxima é 100%; não há mordida, multa ou transferência de ouro como regra substituta.

As terras protegidas são escolhidas aleatoriamente, sem repetição, entre os terrenos plantáveis liberados do jogador. A quantidade é `min(terrenos_liberados, teto(terrenos_liberados × protetores_ativos ÷ 5))`. Portanto, com seis terrenos e um protetor ativo, dois terrenos ficam protegidos. Com zero protetores, nenhum; com cinco, todos. O percentual representa cobertura de terras, não uma chance independente de 20% em cada tentativa.

Para concretizar o sorteio, a implementação mantém a seleção enquanto os terrenos liberados e o conjunto de protetores ativos não mudarem. Uma alteração nesses conjuntos refaz a seleção com aleatoriedade injetável. A seleção é persistida: repetir tentativa de furto, abrir uma janela ou reabrir o jogo não sorteia outra vez. Essa estabilidade é decisão técnica para impedir novas tentativas até obter uma cobertura favorável.

Cada protetor aceita qualquer ração genérica: a de crescimento (80 ouro) ou a de produtor (120 ouro), conforme a seção 5. Em ambos os casos, cada compra alimenta um único protetor por 6 h, sem acumular duração. Sem alimento, sua proteção fica pausada até ser alimentado novamente.

Os dados desta seção representam regras documentadas, não uma confirmação de implementação no jogo.


## 5. Custos para manter a fazenda

| Item | Preço | Efeito registrado | Nível mínimo |
|---|---:|---|---:|
| Alimento genérico: animais que crescem | 80 ouro | Alimentava por 12 h | 1 |
| Alimento genérico: animais que produzem | 120 ouro | Alimentava por 8 h | 1 |
| Fertilizante comum | 50 ouro | Reduzia 1 h | 10 |
| Fertilizante rápido | 350 ouro ou 1 verde | Reduzia 2 h 30 min | 20 |
| Superfertilizante | 1.000 ouro ou 2 verdes | Reduzia 5 h 30 min | 35 |
| Fertilizante do amor | 1 verde | Reduzia 1 h na plantação de amigos | 15 |
| Cartão VIP | 100 verdes | Acesso VIP por 30 dias | 1 |

Cada compra de ração alimenta apenas um animal. Animais de crescimento usam ração genérica de crescimento; animais produtores usam ração genérica de produtor. Não é permitido acumular duração de alimentação. Os protetores seguem o intervalo específico de 6 h da seção 4.3.

### 5.1. Aplicação de fertilizantes

É permitido aplicar **um fertilizante por planta em cada temporada**, considerando todos os tipos e todos os jogadores em conjunto. Trocar de tipo ou de jogador não permite uma segunda aplicação na mesma temporada. O fertilizante do amor segue esse mesmo limite compartilhado. A temporada seguinte permite uma nova aplicação.

A redução afeta o **tempo restante até a colheita da temporada atual**, abrangendo a progressão do estágio 1 ao 5, e não apenas o estágio visual atual. Se ela alcançar ou superar esse tempo, o restante fica em zero e a planta fica pronta para colher. O excedente é perdido, sem passar para a próxima temporada. O fertilizante não realiza a coleta: a próxima temporada só começa após o jogador colher a atual; na última temporada, a coleta faz a planta morrer.

O morango leva 31 h para ir do estágio 1 ao 5 na primeira temporada e 12 h em cada uma das duas seguintes. Cada temporada permite uma aplicação de qualquer fertilizante para reduzir seu tempo restante. Se faltam 4 h para colher e o fertilizante reduz 5 h, a planta fica pronta imediatamente e a hora excedente é perdida. Após a coleta, se ainda houver temporada, começa uma nova contagem de 12 h, sujeita aos cuidados normais.

Nenhum fertilizante pode ser aplicado a uma planta não saudável: seca, com praga ou com insetos. É necessário regar e remover as pragas e os insetos presentes antes da aplicação.

## 6. Regra geral de venda

Para plantas v1, a receita de autoridade é a tabela da seção 2.1, repartida conforme a seção 6.3. Animais e produtos de plantas v0 mantêm os acréscimos sobre seu custo-base abaixo.

| Perfil do jogador | Acréscimo sobre o custo-base | Multiplicador |
|---|---:|---:|
| Comum | 15% | 1,15 |
| VIP | 25% | 1,25 |

Os multiplicadores acima abrangem animais de produção, animais de crescimento e somente as plantas anteriores à v1.

### 6.1. Fórmulas

```text
valor_unitario_sem_arredondar = custo_base × multiplicador_do_perfil
total_sem_arredondar = valor_unitario_sem_arredondar × quantidade_vendida
valor_total_venda = teto(total_sem_arredondar × 10) ÷ 10
```

Para animais e plantas v0, o multiplicador é 1,15 para o perfil comum e 1,25 para VIP. A função teto arredonda para o inteiro imediatamente superior, preservando valores já inteiros. O valor unitário não é arredondado antes da multiplicação pela quantidade. Por exemplo, dois ovos de uma galinha adquirida por 9 moedas verdes rendem 20,7 moedas verdes no perfil comum (9 × 1,15 × 2), embora uma venda isolada de um ovo renda 10,4 moedas verdes.

Para animais e plantas v0, o benefício VIP acrescenta **10 pontos percentuais** ao acréscimo padrão: 15% + 10% = 25%. Para plantas v1, vale apenas a repartição da seção 6.3.

No comando **Vender tudo**, produtos VIP bloqueados são ignorados e permanecem no armazém; os demais são vendidos normalmente. Para cada moeda, somam-se os valores exatos de todos os itens/lotes elegíveis e aplica-se o teto de uma casa decimal uma única vez ao total dessa moeda. Ouro e verdes nunca são somados nem convertidos. Esta unidade de arredondamento por operação e moeda é a decisão técnica adotada para concretizar a regra de arredondar o total.

### 6.2. Exemplo: nabo

Uma semente de nabo custa 300 ouro e tem uma única temporada. A receita v1 comum é 345 ouro. Com VIP na venda, recebe também 30 ouro, totalizando 375 ouro.

| Perfil | Cálculo do acréscimo | Acréscimo | Venda por unidade |
|---|---|---:|---:|
| Comum | 300 × 15% | 45 ouro | 345 ouro |
| VIP | 300 × 25% | 75 ouro | 375 ouro |

O percentual representa um acréscimo sobre o custo-base. O lucro líquido da atividade depende também de alimentação, fertilizantes, quantidade produzida e outros custos aplicáveis.

### 6.3. Repartição, lotes e migração v1

Receita e bônus são inteiros em décimos. Para um total T e N temporadas: q = T / N por divisão inteira e r = T % N. A temporada i (1 a N) recebe q + 1 décimo quando i <= r, e q nos demais casos. Aplicar separadamente à receita e ao bônus total. Somar os décimos das unidades vendidas, sem novo arredondamento. Não aplicar 1,15 ou 1,25 sobre a receita v1.

O tomate retorna 1.358,0 ouro em duas colheitas de 679,0. Com VIP em ambas as vendas, soma 95,0 ouro em bônus, ou 47,5 por temporada. O amendoim retorna 3,6 verdes em cada uma das três temporadas. O coco retorna seis vezes 5,5 verdes, total de 33,0 e lucro de 13,0 após a semente de 20.

Cada lote mantém ID da cultura, moeda, custo da semente, quantidade e restrição VIP. O lote guarda também economyVersion=0 ou 1. Na versão 1, cropSale é obrigatório e contém economyVersion=1, seasonIndex, seasons, cycleRevenueTenths e cycleVipBonusTenths. Os dois marcadores devem concordar. O índice zero identifica a origem da planta e não pode ser vendido; cada unidade colhida usa seu índice de 1 a N. A tabela v1 é imutável: leitores conferem versão, presença dos campos, inteiros exatos, limites de índices e coerência de cultura, custo, moeda, temporadas, receita e restrição VIP. A validação ocorre antes de o parser normalizar campos ausentes ou números fracionários. Guardar os parâmetros evita reprecificação; uma tabela futura exige outra versão e deve conservar a autoridade v1 para liquidar seus estoques. Furto transfere esses mesmos termos e consome a temporada do dono, sem estorno, unidade extra, XP ou segundo pagamento.

Na migração, economyVersion=0 sem cropSale identifica v0; a ausência total dos marcadores em saves históricos também identifica v0. Um lote marcado externamente como v1 não pode perder cropSale nem ser reclassificado como v0. Rascunhos anteriores com cropSale completo e sem marcador externo continuam aceitos como v1. Plantas em andamento, todas as suas temporadas futuras e produtos no depósito mantêm 1,15/1,25 até a venda. Uma semente já comprada, quando plantada após a atualização, entra na v1. Exceção de compatibilidade: sementes históricas cuja moeda, custo ou restrição VIP diferem do catálogo atual conservam v0; não converter sua aquisição para uma tabela em outra moeda ou outro custo. Saldos, XP, animais e demais estoques ficam intactos.

O formato estrutural permanece v20 e o caminho save/game-rules-v4.json. Arquivos v19/v20 com ruleset economy-2026-10 são carregados preservando v0. Novas gravações usam economy-2026-11-plants-v1, recusado pelo leitor desktop anterior. V1–v18, futuras e versões econômicas desconhecidas são recusadas. No online, a transação preserva os novos termos no snapshot JSON. Antes de uma implantação futura, criar backup e publicar somente servidores que conheçam ambos os preços; não misturar binários anteriores e novos nem fazer rollback para um leitor que descarte cropSale.

A API calcula elegibilidade, preço unitário e totais e credita o saldo sob lock da fazenda e recibo idempotente, na mesma transação do estoque. Campos de preço enviados pelo cliente não são aceitos. Vender tudo ignora os produtos VIP bloqueados. Por decisão final de 2026-11-05, o arredondamento histórico de v0 fica preservado, inclusive a diferença possível entre vendas separadas e agrupadas. Misturas com v0/animais continuam usando o teto único por moeda apenas para suas frações antigas. Para produtos exclusivamente v1, venda individual, por lote e total produzem exatamente a mesma soma.

## 7. Cartão VIP

- **Preço:** 100 moedas verdes.
- **Duração:** 30 dias por compra, com ativação imediata no ato da compra.
- **Renovação:** com VIP ativo, cada compra soma 30 dias ao prazo restante; com VIP expirado, inicia 30 dias a partir da compra. Dez compras consecutivas, uma de cada vez, acumulam 300 dias de duração concedida.
- O cartão não fica armazenado no depósito nem aguarda uso manual: a compra aplica o benefício imediatamente.
- **Acesso exclusivo:** sementes VIP indisponíveis para jogadores comuns.
- **Venda:** aplica a repartição v1 das plantas comuns da seção 6.3 e libera produtos exclusivos VIP. Animais e plantas v0 mantêm acréscimo de 25% sobre seu custo-base.
- As sementes identificadas como VIP estão listadas na tabela consolidada da seção 2.
- Não existem animais exclusivos VIP.
- Após o término, sementes VIP armazenadas não podem ser plantadas. Plantações VIP existentes continuam permitindo cuidados e colheitas, mas seus produtos armazenados ficam bloqueados para uso e venda até a renovação. As regras detalhadas estão na seção 9.2.

## 8. Recompensas ao subir de nível

Esta tabela substitui as recompensas de nível anteriores, sem somá-las. Cada nível listado concede automaticamente ouro e moedas verdes cumulativamente, atualizando o saldo ao subir de nível. Níveis não listados não concedem recompensas de nível. O planejamento de recompensas abrange até o nível 61, conforme a tabela atualizada. Recompensas posteriores ao nível 61 serão definidas no futuro.

| Nível | Ouro | Moedas verdes |
|---|---:|---:|
| 5 | 9.000 | 1 |
| 7 | 18.000 | 2 |
| 9 | 27.000 | 3 |
| 11 | 45.000 | 5 |
| 13 | 63.000 | 7 |
| 15 | 81.000 | 9 |
| 17 | 108.000 | 12 |
| 19 | 135.000 | 15 |
| 21 | 162.000 | 18 |
| 23 | 207.000 | 23 |
| 25 | 300.000 | 30 |
| 27 | 450.000 | 50 |
| 29 | 540.000 | 60 |
| 31 | 630.000 | 70 |
| 33 | 720.000 | 80 |
| 35 | 810.000 | 90 |
| 37 | 900.000 | 100 |
| 39 | 990.000 | 110 |
| 41 | 1.080.000 | 120 |
| 43 | 1.170.000 | 130 |
| 45 | 1.260.000 | 140 |
| 47 | 1.350.000 | 150 |
| 50 | 1.500.000 | 155 |
| 53 | 1.650.000 | 160 |
| 56 | 1.800.000 | 165 |
| 58 | 1.950.000 | 170 |
| 61 | 2.100.000 | 180 |



## 9. Regras complementares para implementação

As tabelas abaixo complementam as regras das seções anteriores. Os valores são definições para implementação e não representam funcionalidades já implementadas.

### 9.1. Arredondamento de preços

| Regra | Definição |
|---|---|
| Casas decimais permitidas para ouro | 1 |
| Casas decimais permitidas para moedas verdes | 1 |
| Método de arredondamento (para baixo, para cima ou mais próximo) | Sempre para cima, na precisão de uma casa decimal registrada acima. |
| Tratamento de empate no arredondamento | Não se aplica: qualquer fração além da precisão permitida é arredondada para cima. |
| Momento do arredondamento (por unidade ou após multiplicar pela quantidade) | após multiplicar pela quantidade |

Para animais e plantas v0, 2,25 resulta em 2,3 e 2,20 permanece 2,2, com teto no total da venda. Plantas v1 já têm valores em décimos inteiros por temporada e não passam por arredondamento adicional.

### 9.2. Exclusividade e término do VIP

| Regra | Definição |
|---|---|
| Sementes VIP no inventário após o término | Plantio e uso bloqueados até a renovação do cartão VIP; as sementes permanecem no inventário. |
| Plantações VIP em andamento após o término | Continuam normalmente, permitindo rega, demais cuidados e colheitas. Os produtos coletados ficam sujeitos ao bloqueio de uso e venda enquanto o VIP estiver expirado. |
| Novas colheitas de plantações VIP após o término | Permitidas; os produtos entram no armazém com uso e venda bloqueados até a renovação do cartão VIP. |
| Produtos VIP armazenados após o término | Uso e venda bloqueados até a renovação do cartão VIP, inclusive para produtos coletados antes do vencimento. Com VIP ativo, podem ser vendidos normalmente. |
| Perfil aplicado à venda após o término (no momento da venda ou outra regra) | no momento da venda |

### 9.3. Revenda de itens

| Categoria | Revenda permitida? | Custo-base ou fórmula de revenda | Moeda recebida | Aplicar acréscimo comum/VIP da seção 6? | Restrições |
|---|---|---|---|---|---|
| Sementes não plantadas | Não | Não se aplica | Não se aplica | Não se aplica | Revenda proibida |
| Alimentos | Não | Não se aplica | Não se aplica | Não se aplica | Revenda proibida |
| Fertilizantes | Não | Não se aplica | Não se aplica | Não se aplica | Revenda proibida |
| Cartão VIP | Não | Não se aplica | Não se aplica | Não se aplica | Benefício aplicado na compra; não há cartão não utilizado no depósito |

### 9.4. Recompensas de nível

| Regra | Definição |
|---|---|
| Natureza dos valores da seção 8 (recompensas ou custos de expansão) | recompensas ao subir de nivel |
| Relação entre ouro e moedas verdes (alternativas ou cumulativos) | cumulativos |
| O que cada nível libera ou concede | recompensas listadas na secao 8 |
| Liberação automática ou ação de compra/resgate | automatica, recompensas em moedas devem ser transferidas ao jogador imediatamente e o saldo atualizado |
| Confirmar ou corrigir 207.000 ouro no nível 25 | corrigido |

## 10. Início, tutorial e remoção do legado

- Uma fazenda nova começa no nível 1, com zero XP, **500 ouro e 5 verdes**, sem sementes, produtos, animais, ração, fertilizantes ou plantas prontas.
- Mantém a matriz de 6 colunas por 5 linhas e os cinco terrenos iniciais da primeira coluna. Os demais terrenos custam **50 verdes cada**, na ordem sequencial existente. Os requisitos de nível por linha continuam 2, 3, 4 e 5; o preço antigo em ouro deixa de existir.
- Arroz, repolho e girassol não integram o catálogo novo. Não oferecer seus itens, plantios ou compras. A presença dos PNGs no disco não os torna conteúdo jogável.
- Não converter saldo, itens, culturas em andamento, animais, adubo ou progresso dos saves legados para a economia nova. O jogo novo começa vazio, com apenas os saldos iniciais definidos acima.
- A implementação deve separar o save da nova economia dos saves legados, preservando os arquivos antigos em disco. Não apagar, sobrescrever ou reinicializar silenciosamente um save existente, inclusive quando houver caminho explícito em `COLHEITA_SAVE_PATH`.
- O tutorial somente orienta. A sequência será comprar, arar, plantar, regar, aguardar a maturação, colher e vender. Não concede moedas, sementes, itens nem XP extra; ações reais continuam concedendo o XP normal da seção 11.
- Não há presentes em sementes, itens grátis, nabos iniciais prontos ou recompensas de conclusão do tutorial. A tabela de nível concede apenas as moedas da seção 8. As fontes financeiras do núcleo novo são as descritas neste documento; o bônus legado de baú ao arar não será mantido.

## 11. Curva de nível e XP das ações

A intenção é uma progressão lenta, que recompense persistência. A referência de balanceamento é aproximadamente 30 dias para alcançar o nível 30 em uma rotina ativa; não é prazo garantido para qualquer rotina.

Curva inicial calculada para implementação, com `N` igual ao nível atual:

```text
xp_para_proximo_nivel(N) = 25 + 3 × (N − 1)²
xp_acumulado_para_nivel(L) = 25 × (L − 1) + (L − 2) × (L − 1) × (2 × L − 3) ÷ 2
```

O nível 2 exige 25 XP acumulados; o nível 5 exige 142; o nível 10 exige 837; o nível 30 exige 23.867. Passar do nível 29 ao 30 exige 2.377 XP. XP excedente é preservado e um ganho pode atravessar vários níveis. A fórmula não estabelece nível máximo; recompensas além do nível 61 continuam sem definição.

| Ação válida | XP |
| --- | ---: |
| Plantar uma semente | 5 |
| Regar terra seca | 3 |
| Remover ervas | 5 |
| Remover insetos | 5 |
| Retirar planta morta | 2 |
| Arar grama, comprar, vender produtos já coletados, alimentar e furtar | 0 |

Colheitas usam a seção 3.1. Animais usam a tabela 4.1, inclusive XP pela venda do animal de crescimento adulto. Venda de produto animal já coletado não concede novamente o XP da coleta. Ação recusada não concede XP. Tutorial e tarefas não acrescentam XP às ações.

A calibração local de 2026-10-26 registra hipóteses e limites. O coeficiente da curva é escolha técnica calculada, não resultado de teste jogável. Ajustes futuros exigem repetir a calibração e atualizar esta fonte.

## 12. Tarefas diárias

O limite de recompensa de **tarefas e missões diárias, somadas, é 2 verdes por dia UTC**. Esse limite não bloqueia recompensas de nível nem receitas de venda em verdes.

Para concretizar o limite sem criar presentes ou outra fonte de XP, o planejamento adota duas tarefas fixas, cada uma paga uma vez:

- **Cuidar da fazenda:** realizar uma ação válida de plantar, regar, remover praga/insetos, colher, retirar planta morta, alimentar ou coletar um animal: 1 verde.
- **Movimentar a fazenda:** realizar uma compra ou venda válida de semente, produto, animal, ração, fertilizante, VIP ou terreno: 1 verde.

Não concedem ouro, XP adicional, sementes ou itens. A primeira ação elegível conclui a respectiva tarefa. Uma operação pode concluir ambas quando satisfizer ambas as condições. Eventos recusados, passagem do tempo e login sem ação não contam. Alimentação incluída na aquisição não é uma ação manual de alimentar.

As tarefas já contam durante o tutorial, não exigem gastar mais que o saldo inicial e não dependem de colher diariamente culturas longas. Renovam à meia-noite UTC, persistindo dia, progresso e pagamentos para impedir recompensa duplicada. A troca de dia não gera recompensa automática por dias ausentes.

### 12.1. Ajuda diária em ouro

No online, cada jogador pode resgatar manualmente **100 moedas de ouro por dia UTC**, na tela de Missões. A disponibilidade renova à meia-noite UTC, no mesmo calendário das tarefas; não é um prazo móvel de 24 horas. A interface mostra a próxima virada no horário local. O valor não depende de saldo, nível ou VIP.

Abrir o jogo, visitar, concluir tutorial ou consultar a fazenda não paga a ajuda. Dias não resgatados não acumulam: após qualquer ausência, somente 100 ouro ficam disponíveis para o dia atual. O resgate não concede moedas verdes, XP, itens, progresso de tutorial ou tarefas e não consome os limites de furto ou pragas. É uma fonte adicional explícita de ouro; não altera o saldo inicial de 500 ouro e 5 verdes.

O servidor persiste o último dia pago junto com o crédito, sem confiar no relógio ou em valores enviados pelo cliente. Repetição, pedidos simultâneos, logout e reinício não pagam novamente. Retrocesso do relógio não reabre dia já resgatado. Uma transação revertida preserva o saldo e a disponibilidade.

## 13. Furto de temporadas

Decisão final de 2026-11-05: manter cinco minutos de exclusividade por temporada e um furto bem-sucedido por jogador a cada 24 horas. Não há imunidade para iniciantes.

Cada jogador pode realizar um único furto bem-sucedido a cada 24 horas, somando todas as fazendas visitadas. A janela começa no último furto bem-sucedido, pelo relógio real do servidor; não renova à meia-noite. Tentativas recusadas não consomem o limite. O horário persiste após logout e reinício da API. Pedidos simultâneos só podem produzir um sucesso, e repetir o mesmo pedido não transfere outro produto nem reinicia a janela. Ao recusar pelo limite, informar o tempo restante. Ajuda com amor não consome esse limite.

Uma visita só pode furtar planta cujo ciclo da temporada atual esteja completo e cujo terreno não esteja protegido. Ao atingir 100%, cada temporada fica reservada ao dono por cinco minutos reais. O prazo começa no instante efetivo da maturação, inclusive offline e quando um fertilizante completa o ciclo; login, visita, recarga ou reinício da API não renovam a janela. O dono pode colher imediatamente. Ao completar exatamente cinco minutos, o furto fica disponível se as demais regras permitirem, incluindo protetores e o limite global de 24 horas do visitante. Tentativas durante a exclusividade não transferem produtos nem consomem esse limite. Cada temporada seguinte recebe sua própria janela. Não existe piso de produção garantido ao dono depois desse prazo.

Um furto bem-sucedido transfere a única unidade daquela temporada para o visitante e consome a temporada do dono. O dono não recebe produto nem XP por essa perda; o visitante recebe o produto sem XP. O produto preserva cultura, moeda e custo-base da semente que o originou; o VIP aplicado à venda é o do vendedor no momento da venda.

Se restarem temporadas, a planta volta ao estágio 1 da temporada seguinte, com relógio zerado, primeira rega pendente e limite de fertilizante renovado. Se era a última temporada, vira planta morta. O furto não remove o terreno nem as próximas temporadas. Exemplo: uma planta de três temporadas furtada uma vez deixa apenas duas temporadas possíveis ao dono, se não houver novos furtos.

Só uma coleta ou furto pode consumir cada temporada. A implementação social deverá resolver concorrência e duplicação de pedidos na autoridade responsável pelo estado, sem confiar em pedidos do cliente. Proteção segue 4.3. Não estender automaticamente essa regra aos produtos de animais: o escopo confirmado aqui é a plantação.

### 13.1. Pragas lançadas por visitantes

Durante a visita a um amigo, o jogador pode escolher uma planta e lançar uma minhoca ou uma erva daninha. Cada tipo possui um limite independente de uma aplicação bem-sucedida por jogador a cada 24 horas reais, somando todas as fazendas visitadas. O prazo começa no sucesso daquele tipo, pelo relógio do servidor, e persiste após logout e reinício. Recusas não gastam a vez; repetir um pedido não aplica outra praga nem renova o prazo. Pedidos simultâneos são resolvidos pela autoridade online.

A planta precisa estar em crescimento, já ter recebido a primeira rega e ocupar terreno sem proteção. Plantas prontas, mortas, terras vazias, fazenda própria e terrenos protegidos recusam a ação. Cinco protetores ativos bloqueiam todas as aplicações, conforme 4.3. Solo seco não impede a aplicação após o início do crescimento. Erva e minhoca podem coexistir; não se pode reaplicar um tipo que a planta já tem. Uma aplicação de inseto começa com uma minhoca, sujeita à multiplicação normal até o limite existente.

As pragas pausam o crescimento sem apagar o progresso. O dono usa herbicida para remover ervas e pesticida para remover insetos; se os dois tipos estiverem presentes, só retoma após remover ambos. A limpeza não renova o limite do visitante. Lançar praga não custa moedas ou itens, não concede XP, moedas, tarefas ou produtos e não consome o limite de furto. A janela de cinco minutos da colheita permanece: planta pronta não recebe pragas.

## 14. Assets e estado de entrega

Reutilizar os assets existentes, com mapeamento explícito de nomes do catálogo para nomes de arquivos. A presença no disco deve ser conferida para os cinco estágios e a semente de cada cultura, fases e produtos animais e ícones de itens. A implementação deve conferir o carregamento e o resultado visual no jogo; inventário de caminhos não substitui revisão visual.

O alinhamento destes textos e a existência dos planos não significam que a economia nova, o VIP ou o social estejam implementados. O acompanhamento da entrega fica em `regras-de-negocio.md` e nos planos vinculados.
