# Recalibração das vendas verdes — alternativas A/B/C

**Proposta para aprovação, 06/11/2026.** Complementa ANIMAL_BALANCE_V2.md; A permanece a candidata original. B e C são alternativas, não decisões implementadas. Apenas margens verdes variam. Nenhum preço em ouro, compra, nível, duração, refeição, XP, protetor, planta, recompensa, terreno ou regra social foi alterado.

## Critério de cálculo

B = aquisição verde + 2 × margem verde de A. C = aquisição verde + 3 × margem verde de A. Aplicado separadamente ao comum e ao VIP: preserva exatamente o desenho do bônus de A. As margens originais já estão em décimos; multiplicá-las por inteiros resulta em preços finais exatos em décimos, sem arredondamento extra. Não se multiplica o principal nem a receita bruta. Não se recalcula o bônus VIP com uma fórmula diferente da comparação solicitada.

Ganho verde por ciclo = venda − compra. ROI verde = ganho/compra; não inclui um câmbio fictício da ração. Resultado completo é um vetor: +verdes e −ouro de ração. As refeições e o custo ouro por ciclo permanecem nos CSVs. Verdes/h nominais = ganho/duração alimentada; as visitas reais podem alongar o ciclo. Primeiro retorno do principal ocorre na venda; acumular lucro suficiente para um cartão ou terreno é outra métrica.

## Preços finais dos dez animais

| Animal | Compra | A comum | A VIP | B comum | B VIP | C comum | C VIP |
|---|---|---|---|---|---|---|---|
| galinha | 9,0 | 9,1 | 9,2 | 9,2 | 9,4 | 9,3 | 9,6 |
| abelha | 28,0 | 28,2 | 28,4 | 28,4 | 28,8 | 28,6 | 29,2 |
| pombo | 36,0 | 36,7 | 37,3 | 37,4 | 38,6 | 38,1 | 39,9 |
| coelho | 59,0 | 60,2 | 60,8 | 61,4 | 62,6 | 62,6 | 64,4 |
| pato | 69,0 | 69,5 | 69,8 | 70,0 | 70,6 | 70,5 | 71,4 |
| pavao | 99,0 | 100,0 | 100,5 | 101,0 | 102,0 | 102,0 | 103,5 |
| porco | 100,0 | 102,4 | 103,6 | 104,8 | 107,2 | 107,2 | 110,8 |
| burro | 100,0 | 103,2 | 104,8 | 106,4 | 109,6 | 109,6 | 114,4 |
| ovelha | 120,0 | 121,0 | 121,5 | 122,0 | 123,0 | 123,0 | 124,5 |
| vaca | 180,0 | 181,3 | 182,0 | 182,6 | 184,0 | 183,9 | 186,0 |

## Ganho por ciclo, ROI e verdes/h

Cada célula comum/VIP mostra os dois perfis. Ração em ouro é uma despesa adicional igual nas três alternativas.

| Animal | Alt. | Ganho comum/VIP | ROI % comum/VIP | Verdes/h comum/VIP | Ração ouro/ciclo |
|---|---|---|---|---|---|
| galinha | A | 0,1 / 0,2 | 1,11 / 2,22 | 0,01250 / 0,02500 | 0 |
| galinha | B | 0,2 / 0,4 | 2,22 / 4,44 | 0,02500 / 0,05000 | 0 |
| galinha | C | 0,3 / 0,6 | 3,33 / 6,67 | 0,03750 / 0,07500 | 0 |
| abelha | A | 0,2 / 0,4 | 0,71 / 1,43 | 0,01667 / 0,03333 | 120 |
| abelha | B | 0,4 / 0,8 | 1,43 / 2,86 | 0,03333 / 0,06667 | 120 |
| abelha | C | 0,6 / 1,2 | 2,14 / 4,29 | 0,05000 / 0,10000 | 120 |
| pombo | A | 0,7 / 1,3 | 1,94 / 3,61 | 0,01944 / 0,03611 | 160 |
| pombo | B | 1,4 / 2,6 | 3,89 / 7,22 | 0,03889 / 0,07222 | 160 |
| pombo | C | 2,1 / 3,9 | 5,83 / 10,83 | 0,05833 / 0,10833 | 160 |
| coelho | A | 1,2 / 1,8 | 2,03 / 3,05 | 0,02500 / 0,03750 | 240 |
| coelho | B | 2,4 / 3,6 | 4,07 / 6,10 | 0,05000 / 0,07500 | 240 |
| coelho | C | 3,6 / 5,4 | 6,10 / 9,15 | 0,07500 / 0,11250 | 240 |
| pato | A | 0,5 / 0,8 | 0,72 / 1,16 | 0,03125 / 0,05000 | 120 |
| pato | B | 1,0 / 1,6 | 1,45 / 2,32 | 0,06250 / 0,10000 | 120 |
| pato | C | 1,5 / 2,4 | 2,17 / 3,48 | 0,09375 / 0,15000 | 120 |
| pavao | A | 1,0 / 1,5 | 1,01 / 1,52 | 0,03571 / 0,05357 | 360 |
| pavao | B | 2,0 / 3,0 | 2,02 / 3,03 | 0,07143 / 0,10714 | 360 |
| pavao | C | 3,0 / 4,5 | 3,03 / 4,55 | 0,10714 / 0,16071 | 360 |
| porco | A | 2,4 / 3,6 | 2,40 / 3,60 | 0,04000 / 0,06000 | 320 |
| porco | B | 4,8 / 7,2 | 4,80 / 7,20 | 0,08000 / 0,12000 | 320 |
| porco | C | 7,2 / 10,8 | 7,20 / 10,80 | 0,12000 / 0,18000 | 320 |
| burro | A | 3,2 / 4,8 | 3,20 / 4,80 | 0,04444 / 0,06667 | 400 |
| burro | B | 6,4 / 9,6 | 6,40 / 9,60 | 0,08889 / 0,13333 | 400 |
| burro | C | 9,6 / 14,4 | 9,60 / 14,40 | 0,13333 / 0,20000 | 400 |
| ovelha | A | 1,0 / 1,5 | 0,83 / 1,25 | 0,05000 / 0,07500 | 240 |
| ovelha | B | 2,0 / 3,0 | 1,67 / 2,50 | 0,10000 / 0,15000 | 240 |
| ovelha | C | 3,0 / 4,5 | 2,50 / 3,75 | 0,15000 / 0,22500 | 240 |
| vaca | A | 1,3 / 2,0 | 0,72 / 1,11 | 0,05417 / 0,08333 | 240 |
| vaca | B | 2,6 / 4,0 | 1,44 / 2,22 | 0,10833 / 0,16667 | 240 |
| vaca | C | 3,9 / 6,0 | 2,17 / 3,33 | 0,16250 / 0,25000 | 240 |

## Capacidade diária, tarefas e plantas V1

Limite nominal de todas as espécies liberadas até o nível, com capital suficiente, coleta/recompra imediatas e alimentação pontual. Não é previsão de conta nova nem inclui subidas de nível durante o período. As tarefas dão até 2 verdes/dia: 14, 30 e 60 em 7, 15 e 30 dias, exigindo concluí-las. Não há comparação entre lucro ouro e verde.

| Nível | Alt. | Comum/dia | VIP/dia | Comum ÷ tarefas | VIP ÷ tarefas |
|---|---|---|---|---|---|
| 10 | A | 0,300 | 0,600 | 0,15 | 0,30 |
| 20 | A | 1,167 | 2,267 | 0,58 | 1,13 |
| 30 | A | 2,517 | 4,367 | 1,26 | 2,18 |
| 40 | A | 5,400 | 8,692 | 2,70 | 4,35 |
| 50 | A | 7,900 | 12,492 | 3,95 | 6,25 |
| 60 | A | 7,900 | 12,492 | 3,95 | 6,25 |
| 10 | B | 0,600 | 1,200 | 0,30 | 0,60 |
| 20 | B | 2,333 | 4,533 | 1,17 | 2,27 |
| 30 | B | 5,033 | 8,733 | 2,52 | 4,37 |
| 40 | B | 10,801 | 17,385 | 5,40 | 8,69 |
| 50 | B | 15,801 | 24,985 | 7,90 | 12,49 |
| 60 | B | 15,801 | 24,985 | 7,90 | 12,49 |
| 10 | C | 0,900 | 1,800 | 0,45 | 0,90 |
| 20 | C | 3,500 | 6,800 | 1,75 | 3,40 |
| 30 | C | 7,550 | 13,100 | 3,77 | 6,55 |
| 40 | C | 16,201 | 26,077 | 8,10 | 13,04 |
| 50 | C | 23,701 | 37,477 | 11,85 | 18,74 |
| 60 | C | 23,701 | 37,477 | 11,85 | 18,74 |

Referência das plantas VIP V1: coco rende 13 verdes líquidos por semente de 20, em 210 h totais nominais, antes do custo compartilhado do cartão. Uma terra gera 1,486 verdes/dia; cinco, 7,429; trinta, 44,571. Exige nível/desbloqueio e água em dia; temporadas, latência de visitas e furtos podem reduzir o realizado. Não equivale a uma conta com trinta terras gratuitas: as simulações começam com cinco e compram as demais por 50.

| Alt. | Animais comuns/dia | Animais VIP/dia | VIP animal ÷ 30 cocos VIP | Capital animal verde | Capital sementes 30 cocos |
|---|---|---|---|---|---|
| A | 7,900 | 12,492 | 0,28 | 800 | 600 |
| B | 15,801 | 24,985 | 0,56 | 800 | 600 |
| C | 23,701 | 37,477 | 0,84 | 800 | 600 |

Os dez slots animais são adicionais às terras: mesmo uma taxa inferior à plantação pode aumentar a emissão agregada. O lucro das sementes é por ciclo completo com várias temporadas, não ROI diário. A alimentação animal em ouro impede tratar essas alternativas como investimento sem despesas na outra moeda.

## Simulações determinísticas de 7, 15 e 30 dias

1.296 trajetórias, 3.888 observações: três alternativas × dois regimes de patrimônio × seis níveis iniciais × duas moedas de compra × três cadências × dois perfis VIP × três estratégias (somente animais, somente plantas, misto). Usa o mesmo GameState e relógio real de domínio do estudo original; troca somente a liquidação das vendas dos animais por um ajuste offline. Cada saldo foi conciliado com compras, alimentação, vendas, recompensas, ajuda e capital ativo.

O regime principal conserva as premiações históricas até o nível inicial (limite superior de reservas, não saldo presumido de usuários reais), com cinco terras. O controle de liquidez começa com 2 milhões de ouro e 2 mil verdes, explicitamente capital já acumulado, também com cinco terras. VIP precisa ser comprado por 100 verdes e pode começar tarde. Nível inicial não fica congelado: XP e novas liberações são reais. A alimentação inicial é incluída; refeições seguintes só são compradas se houver ouro. Não há compra além de um animal por tipo.

A política que pretende VIP reserva 100 verdes antes de comprar animais. Nos níveis iniciais 10 e 20, a rota somente animais do regime principal fica sem agir porque ainda não pode pagar o cartão; por isso mostra zero. Isso é limitação da política de poupança, não prova de que animais VIP não funcionem nesses níveis. O controle de liquidez fornece a comparação com VIP ativo desde o início em todos os níveis; os cenários mistos permitem progredir pelo cultivo. Não se concedeu cartão grátis para esconder esse bloqueio.

Tabela: lucro animal verde realizado, comum/VIP, antes de descontar o cartão. Regime principal, somente animais. O CSV completo também mostra caixa nas duas moedas, custo de comida, principal ativo, terrenos, capital de plantas, recompensas, XP, ciclos e horas VIP. Não somar principal ativo ao lucro nem atribuir premiações ao rendimento animal.

| Nível inicial | Visitas/dia | Dias | A comum/VIP | B comum/VIP | C comum/VIP |
|---|---|---|---|---|---|
| 10 | 3 | 7 | 2,2 / 0,0 | 4,8 / 0,0 | 7,2 / 0,0 |
| 10 | 3 | 15 | 7,7 / 0,0 | 17,2 / 0,0 | 25,8 / 0,0 |
| 10 | 3 | 30 | 24,6 / 0,0 | 50,0 / 0,0 | 75,0 / 0,0 |
| 10 | 6 | 7 | 2,2 / 0,0 | 5,2 / 0,0 | 7,8 / 0,0 |
| 10 | 6 | 15 | 9,2 / 0,0 | 20,6 / 0,0 | 30,9 / 0,0 |
| 10 | 6 | 30 | 31,5 / 0,0 | 66,2 / 0,0 | 99,3 / 0,0 |
| 10 | 10 | 7 | 1,9 / 0,0 | 3,8 / 0,0 | 5,7 / 0,0 |
| 10 | 10 | 15 | 7,2 / 0,0 | 15,8 / 0,0 | 23,7 / 0,0 |
| 10 | 10 | 30 | 26,5 / 0,0 | 54,4 / 0,0 | 81,6 / 0,0 |
| 20 | 3 | 7 | 5,4 / 0,0 | 10,8 / 0,0 | 16,2 / 0,0 |
| 20 | 3 | 15 | 14,2 / 0,0 | 30,8 / 0,0 | 46,2 / 0,0 |
| 20 | 3 | 30 | 42,2 / 0,0 | 84,4 / 0,0 | 126,6 / 0,0 |
| 20 | 6 | 7 | 6,7 / 0,0 | 13,4 / 0,0 | 20,1 / 0,0 |
| 20 | 6 | 15 | 18,2 / 0,0 | 38,8 / 0,0 | 58,2 / 0,0 |
| 20 | 6 | 30 | 56,1 / 0,0 | 117,6 / 0,0 | 176,4 / 0,0 |
| 20 | 10 | 7 | 6,0 / 0,0 | 12,0 / 0,0 | 18,0 / 0,0 |
| 20 | 10 | 15 | 15,3 / 0,0 | 33,0 / 0,0 | 49,5 / 0,0 |
| 20 | 10 | 30 | 46,6 / 0,0 | 95,6 / 0,0 | 143,4 / 0,0 |
| 30 | 3 | 7 | 14,5 / 18,7 | 29,0 / 37,4 | 43,5 / 56,1 |
| 30 | 3 | 15 | 37,7 / 54,5 | 75,4 / 109,0 | 113,1 / 168,0 |
| 30 | 3 | 30 | 90,3 / 137,1 | 180,6 / 274,2 | 270,9 / 426,6 |
| 30 | 6 | 7 | 17,0 / 23,8 | 34,0 / 47,6 | 51,0 / 71,4 |
| 30 | 6 | 15 | 43,9 / 65,8 | 87,8 / 131,6 | 131,7 / 201,9 |
| 30 | 6 | 30 | 114,6 / 175,9 | 229,2 / 351,8 | 343,8 / 532,2 |
| 30 | 10 | 7 | 14,3 / 20,8 | 28,6 / 41,6 | 42,9 / 62,4 |
| 30 | 10 | 15 | 38,2 / 56,2 | 76,4 / 112,4 | 114,6 / 173,1 |
| 30 | 10 | 30 | 98,0 / 148,9 | 196,0 / 297,8 | 294,0 / 451,2 |
| 40 | 3 | 7 | 26,5 / 43,0 | 53,0 / 86,0 | 79,5 / 129,0 |
| 40 | 3 | 15 | 61,9 / 100,1 | 123,8 / 200,2 | 185,7 / 300,3 |
| 40 | 3 | 30 | 147,5 / 236,0 | 295,0 / 472,0 | 442,5 / 708,0 |
| 40 | 6 | 7 | 32,2 / 52,1 | 64,4 / 104,2 | 96,6 / 156,3 |
| 40 | 6 | 15 | 75,7 / 122,0 | 151,4 / 244,0 | 227,1 / 366,0 |
| 40 | 6 | 30 | 183,1 / 292,3 | 366,2 / 584,6 | 549,3 / 876,9 |
| 40 | 10 | 7 | 29,5 / 47,6 | 59,0 / 95,2 | 88,5 / 142,8 |
| 40 | 10 | 15 | 68,0 / 109,4 | 136,0 / 218,8 | 204,0 / 328,2 |
| 40 | 10 | 30 | 161,9 / 258,2 | 323,8 / 516,4 | 485,7 / 774,6 |
| 50 | 3 | 7 | 40,3 / 64,0 | 80,6 / 128,0 | 120,9 / 192,0 |
| 50 | 3 | 15 | 93,1 / 147,6 | 186,2 / 295,2 | 279,3 / 442,8 |
| 50 | 3 | 30 | 193,0 / 305,5 | 386,0 / 611,0 | 579,0 / 916,5 |
| 50 | 6 | 7 | 48,0 / 76,1 | 96,0 / 152,2 | 144,0 / 228,3 |
| 50 | 6 | 15 | 107,9 / 171,0 | 215,8 / 342,0 | 323,7 / 513,0 |
| 50 | 6 | 30 | 225,7 / 357,3 | 451,4 / 714,6 | 677,1 / 1.071,9 |
| 50 | 10 | 7 | 42,0 / 66,6 | 84,0 / 133,2 | 126,0 / 199,8 |
| 50 | 10 | 15 | 96,6 / 152,9 | 193,2 / 305,8 | 289,8 / 458,7 |
| 50 | 10 | 30 | 202,2 / 319,7 | 404,4 / 639,4 | 606,6 / 959,1 |
| 60 | 3 | 7 | 40,3 / 64,0 | 80,6 / 128,0 | 120,9 / 192,0 |
| 60 | 3 | 15 | 93,1 / 147,6 | 186,2 / 295,2 | 279,3 / 442,8 |
| 60 | 3 | 30 | 193,0 / 305,5 | 386,0 / 611,0 | 579,0 / 916,5 |
| 60 | 6 | 7 | 48,0 / 76,1 | 96,0 / 152,2 | 144,0 / 228,3 |
| 60 | 6 | 15 | 107,9 / 171,0 | 215,8 / 342,0 | 323,7 / 513,0 |
| 60 | 6 | 30 | 225,7 / 357,3 | 451,4 / 714,6 | 677,1 / 1.071,9 |
| 60 | 10 | 7 | 42,0 / 66,6 | 84,0 / 133,2 | 126,0 / 199,8 |
| 60 | 10 | 15 | 96,6 / 152,9 | 193,2 / 305,8 | 289,8 / 458,7 |
| 60 | 10 | 30 | 202,2 / 319,7 | 404,4 / 639,4 | 606,6 / 959,1 |

As estratégias de cultivo V1 e as rotas animais em ouro são idênticas entre A/B/C em todos os campos verificados. Nas rotas mistas verdes, mais lucro permite expansão, compra de sementes, XP e desbloqueios diferentes: os resultados não são simplesmente multiplicados por 2 ou 3. As políticas não otimizam carteiras: animais são priorizados antes de terras e sementes, o que pode reduzir a liquidez para plantas VIP. Preferem sementes VIP na rota verde quando o cartão está ativo e há dinheiro; senão usam as elegíveis comuns.

## Cartão VIP e terrenos

Para avaliar se o cartão se paga, considerar o ganho adicional VIP sobre comum, não a receita VIP inteira. Os dias abaixo são equivalentes de lucro nominal em regime, não uma promessa de desbloqueio ou disponibilidade. Cartão com duração limitada: acima de 30 dias, o bônus animal isolado não recupera o custo no mês. O capital inicial e ração continuam necessários.

| Nível | Alt. | Dias/terreno comum | Dias/terreno VIP | Dias/cartão pelo bônus VIP |
|---|---|---|---|---|
| 10 | A | 166,67 | 83,33 | 333,33 |
| 20 | A | 42,86 | 22,06 | 90,91 |
| 30 | A | 19,87 | 11,45 | 54,05 |
| 40 | A | 9,26 | 5,75 | 30,38 |
| 50 | A | 6,33 | 4,00 | 21,78 |
| 60 | A | 6,33 | 4,00 | 21,78 |
| 10 | B | 83,33 | 41,67 | 166,67 |
| 20 | B | 21,43 | 11,03 | 45,45 |
| 30 | B | 9,93 | 5,73 | 27,03 |
| 40 | B | 4,63 | 2,88 | 15,19 |
| 50 | B | 3,16 | 2,00 | 10,89 |
| 60 | B | 3,16 | 2,00 | 10,89 |
| 10 | C | 55,56 | 27,78 | 111,11 |
| 20 | C | 14,29 | 7,35 | 30,30 |
| 30 | C | 6,62 | 3,82 | 18,02 |
| 40 | C | 3,09 | 1,92 | 10,13 |
| 50 | C | 2,11 | 1,33 | 7,26 |
| 60 | C | 2,11 | 1,33 | 7,26 |

Realizado em 30 dias, nível inicial 60, somente animais, regime principal. Terrenos equivalentes são lucro dividido por 50: não são compras executadas nessa estratégia. A quantidade efetiva de terras nas estratégias mistas consta do CSV. Valores negativos após o cartão indicam que o bônus animal não o pagou, sem negar benefícios do VIP nas plantas.

| Alt. | Visitas | Ganho comum | Ganho VIP | Cartão pago | Bônus adicional após cartão | Terrenos pelo comum | Terrenos pelo VIP após cartão |
|---|---|---|---|---|---|---|---|
| A | 3 | 193,0 | 305,5 | 100 | 12,5 | 3,86 | 4,11 |
| A | 6 | 225,7 | 357,3 | 100 | 31,6 | 4,51 | 5,15 |
| A | 10 | 202,2 | 319,7 | 100 | 17,5 | 4,04 | 4,39 |
| B | 3 | 386,0 | 611,0 | 100 | 125,0 | 7,72 | 10,22 |
| B | 6 | 451,4 | 714,6 | 100 | 163,2 | 9,03 | 12,29 |
| B | 10 | 404,4 | 639,4 | 100 | 135,0 | 8,09 | 10,79 |
| C | 3 | 579,0 | 916,5 | 100 | 237,5 | 11,58 | 16,33 |
| C | 6 | 677,1 | 1.071,9 | 100 | 294,8 | 13,54 | 19,44 |
| C | 10 | 606,6 | 959,1 | 100 | 252,5 | 12,13 | 17,18 |

## Comprar em verdes ou ouro?

Não existe vencedor absoluto sem preferências sobre as duas moedas. Com compra em ouro, principal e ração são ouro e a venda recupera ouro; gera zero verde animal. Com compra em verdes, principal/venda são verdes e a ração continua ouro. Cada tipo só pode ocupar um slot: escolher uma rota exclui a outra naquele ciclo. B/C melhoram a atratividade verde sem alterar nenhum resultado da rota ouro. O CSV analítico da proposta original continua valendo para ouro.

| Animal | Compra ouro | Compra verde | Lucro ouro comum/VIP | Despesa ouro na rota verde | Ganho verde B comum/VIP |
|---|---|---|---|---|---|
| galinha | 8.000 | 9,0 | 480 / 600 | 0 | 0,2 / 0,4 |
| abelha | 28.000 | 28,0 | 1.080 / 1.350 | 120 | 0,4 / 0,8 |
| pombo | 36.000 | 36,0 | 4.320 / 5.400 | 160 | 1,4 / 2,6 |
| coelho | 60.000 | 59,0 | 7.200 / 9.000 | 240 | 2,4 / 3,6 |
| pato | 70.000 | 69,0 | 2.880 / 3.600 | 120 | 1,0 / 1,6 |
| pavao | 100.000 | 99,0 | 5.880 / 7.350 | 360 | 2,0 / 3,0 |
| porco | 150.000 | 100,0 | 14.400 / 18.000 | 320 | 4,8 / 7,2 |
| burro | 150.000 | 100,0 | 19.440 / 24.300 | 400 | 6,4 / 9,6 |
| ovelha | 180.000 | 120,0 | 6.000 / 7.500 | 240 | 2,0 / 3,0 |
| vaca | 220.000 | 180,0 | 7.920 / 9.900 | 240 | 2,6 / 4,0 |

Comprar em verdes faz sentido quando o jogador consegue manter a ração em ouro, tem capital verde que não precisa imediatamente para terrenos/cartão e quer aumentar suas fontes verdes. Ouro pode ser a escolha adequada para quem poupa verdes ou precisa recompor ouro. A galinha não exige refeição paga durante um ciclo ideal; isso não transforma aquisição em verde em renda de ouro nem cria conversão entre moedas. Não há arbitragem entre rotas: moeda de venda acompanha aquisição; cada nova produção exige recomprar.

## Diagnóstico e recomendação

**Recomendo B para aprovação.** A é a opção de menor emissão, mas o ganho marginal de vários ciclos é pouco perceptível: vaca dá 1,3 verde por 24 h alimentadas e exige 180 de principal e 240 ouro de ração. B sobe a 2,6 comum / 4,0 VIP por ciclo, preservando o custo de oportunidade e dando uma remuneração visível. C sobe a 3,9 / 6,0: não há evidência de inflação explosiva animal isolada nesta matriz, mas seu impacto agregado com plantas VIP e tarefas pede mais cautela.

A emissão nominal madura comum/VIP fica aproximadamente 7,90/12,49 verdes por dia em A, 15,80/24,98 em B e 23,70/37,48 em C. B ainda permanece abaixo dos 44,571 verdes/dia de trinta cocos VIP, embora some aos slots de cultivo. C se aproxima dessa fonte com capital animal de 800 verdes e despesa ouro contínua; acelera a amortização do cartão e dos terrenos. Os terrenos são finitos: depois da expansão, parte dessa emissão deixa de encontrar esse sumidouro. Não há estatística de população/consumo que permita declarar uma taxa universalmente segura de inflação.

Não se classificou nenhuma rota como estritamente dominada por animal anterior considerando simultaneamente compra, duração, ROI verde, verdes/h, XP/h e despesa ouro/h. Como B/C multiplicam todas as margens igualmente, preservam a ordenação de A. Isso não significa que cada métrica sobe com o nível: ROI por principal pode cair, e animais de crescimento demoram mais a realizar caixa. Limite de um por tipo torna o animal avançado uma opção adicional, não substituição ilimitada de um antigo.

O principal risco de desinteresse de A continua sendo a baixa margem absoluta e o longo prazo para financiar objetivos apenas com animais iniciais. B alivia; C alivia mais e eleva emissão recorrente. Nenhuma alternativa elimina falta de liquidez, alimentação em ouro ou progressão por nível. Não proponho modificar compras, comida, plantas, proteção ou recompensas para compensar.

Contra os 3.142,7 verdes do sistema atual no mesmo cenário comum de nível 60/seis visitas/30 dias, A realiza 225,7 (redução de 92,82%), B realiza 451,4 (85,64%) e C realiza 677,1 (78,45%). Isso é redução do ganho animal realizado desse cenário, não da emissão global do jogo. A soma nominal de A foi corrigida textualmente de 7,8 para 7,90 verdes/dia, sem mudar qualquer preço ou simulação original.

## Evidências, limitações e reprodução

Verificado: A reproduz exatamente o subconjunto correspondente da simulação anterior; B/C multiplicam apenas a margem verde; todas as rotas em ouro e todos os cenários somente plantas ficaram idênticos; os quatro arquivos econômicos conservam os hashes anteriores. Assertions do harness conferem carteiras e limite por tipo. Este estudo não é teste HTTP, concorrência, migração ou implementação de preços.

Revisão independente somente leitura não encontrou problemas confirmados nas fórmulas, separação monetária e comparação incremental do VIP. Não executou o harness; a execução e integração foram verificadas pelo agente principal. A preferência por B é julgamento de calibração, não resultado de teste de percepção dos jogadores.

Visitas uniformes de 8 h, 4 h e 2,4 h, inclusive ao longo da noite, não reproduzem sono humano. Mais visitas não garante ciclos melhores devido ao alinhamento da fome/coleta. Sem furtos, pragas ou rede, a comparação isola a calibração; as regras sociais permanecem intactas. As recompensas históricas são reservas hipotéticas, as missões exigem ações e o VIP pode estar inativo em parte da simulação. Médias não são garantia para jogadores reais.

Execução: `./analysis/animals-v2/run.ps1 -GreenCalibration` com JAVA_HOME apontando para o JDK 21 existente; depois `node analysis/animals-v2/compare-green.mjs`. Nenhuma dependência nova. Tabelas completas e resultados estão em `analysis/animals-v2/green-calibration/`: prices.csv, metrics.csv, daily-capacity.csv, simulations.csv, realized-comparison.csv e vip-and-land.csv. Nenhuma alteração de produção, PR, merge ou deploy.
