# Colheita Feliz — Documentação de economia e itens

Este documento reúne os valores e as regras fornecidos para o projeto. As regras de venda abaixo representam a definição de economia do projeto; não constituem uma validação histórica do jogo original.

Esta é a fonte normativa dos valores e das regras desejadas. `docs/regras-de-negocio.md` descreve os fluxos e referencia estas seções, sem manter tabelas concorrentes. Definições documentadas não significam funcionalidades implementadas. Decisões complementares confirmadas em 2026-10-26 estão nas seções 10 a 14.

## 1. Moedas

- **Ouro:** moeda utilizada nos preços e custos indicados como ouro.
- **Moedas verdes:** moeda utilizada em compras premium e alternativas de aquisição.
- Nas tabelas de preços, ouro e moedas verdes representam alternativas de compra, não uma soma. As recompensas da seção 8 concedem ambas as moedas cumulativamente.
- Moedas verdes são obtidas jogando: saldo inicial, recompensas de nível, tarefas diárias e vendas de produtos originados de aquisições em verdes. Não há compra de moeda com dinheiro real, câmbio automático ou integração de pagamentos.

## 2. Sementes e plantas: tabela consolidada

O preço de compra é por semente. A venda é por unidade colhida, na mesma moeda da aquisição; o custo-base é o preço da semente. Todas as culturas rendem uma unidade por terreno em cada temporada. As sementes VIP exigem cartão VIP ativo para compra, plantio e venda dos produtos, conforme as seções 7 e 9.2.

As vendas comuns usam acréscimo de 15% e as VIP, de 25%. Os valores abaixo são para venda de uma unidade, arredondados para cima em uma casa decimal; para várias unidades, arredonda-se apenas o total, conforme a seção 6. O bloqueio de venda sem VIP prevalece para as culturas VIP.

Os tempos são as durações nominais de crescimento do estágio 1 ao 5, sujeitos à rega e às pausas por pragas e insetos. O total de temporadas inclui a primeira colheita. A sequência de XP corresponde às colheitas da primeira até a última temporada, calculada conforme a seção 3.1.

| Semente | Condição | Nível mínimo | Compra por semente | Venda comum por unidade | Venda VIP por unidade | Primeira colheita | Intervalo após próximas coletas | Temporadas | Unidades por colheita | XP por temporada | Cinco estágios em disco |
|---|---|---:|---|---|---|---|---|---:|---:|---|---|
| Nabo | Comum | 1 | 300 ouro | 345 ouro | 375 ouro | 10 h | Não se aplica | 1 | 1 | 15 | Sim |
| Cenoura | Comum | 1 | 350 ouro | 402,5 ouro | 437,5 ouro | 15 h | Não se aplica | 1 | 1 | 22,5 | Sim |
| Milho | Comum | 2 | 500 ouro | 575 ouro | 625 ouro | 20 h | Não se aplica | 1 | 1 | 30 | Sim |
| Batata | Comum | 3 | 650 ouro | 747,5 ouro | 812,5 ouro | 22 h | Não se aplica | 1 | 1 | 33 | Sim |
| Berinjela | Comum | 4 | 800 ouro | 920 ouro | 1.000 ouro | 24 h | Não se aplica | 1 | 1 | 36 | Sim |
| Tomate | Comum | 5 | 950 ouro | 1.092,5 ouro | 1.187,5 ouro | 26 h | 8 h | 2 | 1 | 39 → 19,5 | Sim |
| Pimenta | Comum | 5 | 1.100 ouro | 1.265 ouro | 1.375 ouro | 28 h | 9 h | 2 | 1 | 42 → 21 | Sim |
| Abóbora | Comum | 7 | 1.250 ouro | 1.437,5 ouro | 1.562,5 ouro | 29 h | 10 h | 2 | 1 | 43,5 → 21,75 | Sim |
| Ervilha | Comum | 9 | 1.400 ouro | 1.610 ouro | 1.750 ouro | 30 h | 11 h | 2 | 1 | 45 → 22,5 | Sim |
| Morango | Comum | 11 | 1.550 ouro | 1.782,5 ouro | 1.937,5 ouro | 31 h | 12 h | 3 | 1 | 46,5 → 23,25 → 11,625 | Sim |
| Maçã | Comum | 13 | 1.800 ouro | 2.070 ouro | 2.250 ouro | 42 h | 14 h | 3 | 1 | 63 → 31,5 → 15,75 | Sim |
| Melancia | Comum | 16 | 1.975 ouro | 2.271,3 ouro | 2.468,8 ouro | 46 h | 16 h | 3 | 1 | 69 → 34,5 → 17,25 | Sim |
| Banana | Comum | 19 | 2.150 ouro | 2.472,5 ouro | 2.687,5 ouro | 50 h | 18 h | 3 | 1 | 75 → 37,5 → 18,75 | Sim |
| Pêssego | Comum | 22 | 3.075 ouro | 3.536,3 ouro | 3.843,8 ouro | 54 h | 20 h | 3 | 1 | 81 → 40,5 → 20,25 | Sim |
| Laranja | Comum | 25 | 3.350 ouro | 3.852,5 ouro | 4.187,5 ouro | 60 h | 22 h | 3 | 1 | 90 → 45 → 22,5 | Sim |
| Uva | Comum | 28 | 4.125 ouro | 4.743,8 ouro | 5.156,3 ouro | 66 h | 24 h | 4 | 1 | 99 → 49,5 → 24,75 → 12,375 | Sim |
| Romã | Comum | 31 | 3.875 ouro | 4.456,3 ouro | 4.843,8 ouro | 68 h | 33 h | 4 | 1 | 102 → 51 → 25,5 → 12,75 | Sim |
| Melão | Comum | 35 | 4.600 ouro | 5.290 ouro | 5.750 ouro | 70 h | 35 h | 4 | 1 | 105 → 52,5 → 26,25 → 13,125 | Sim |
| Cana | Comum | 39 | 4.600 ouro | 5.290 ouro | 5.750 ouro | 72 h | 37 h | 4 | 1 | 108 → 54 → 27 → 13,5 | Sim |
| Lichia | Comum | 43 | 4.750 ouro | 5.462,5 ouro | 5.937,5 ouro | 74 h | 39 h | 4 | 1 | 111 → 55,5 → 27,75 → 13,875 | Sim |
| Kiwi | Comum | 45 | 5.050 ouro | 5.807,5 ouro | 6.312,5 ouro | 76 h | 41 h | 4 | 1 | 114 → 57 → 28,5 → 14,25 | Sim |
| Abacaxi | Comum | 47 | 5.200 ouro | 5.980 ouro | 6.500 ouro | 78 h | 43 h | 4 | 1 | 117 → 58,5 → 29,25 → 14,625 | Sim |
| Cereja | Comum | 49 | 5.500 ouro | 6.325 ouro | 6.875 ouro | 80 h | 45 h | 4 | 1 | 120 → 60 → 30 → 15 | Sim |
| Pitaya | Comum | 51 | 6.180 ouro | 7.107 ouro | 7.725 ouro | 82 h | 47 h | 4 | 1 | 123 → 61,5 → 30,75 → 15,375 | Sim |
| Manga | Comum | 53 | 6.850 ouro | 7.877,5 ouro | 8.562,5 ouro | 84 h | 49 h | 4 | 1 | 126 → 63 → 31,5 → 15,75 | Sim |
| Cacau | Comum | 55 | 8.500 ouro | 9.775 ouro | 10.625 ouro | 86 h | 51 h | 4 | 1 | 129 → 64,5 → 32,25 → 16,125 | Sim |
| Goiaba | Comum | 57 | 15.000 ouro | 17.250 ouro | 18.750 ouro | 96 h | 60 h | 4 | 1 | 144 → 72 → 36 → 18 | Sim |
| Caviar | Comum | 60 | 25.000 ouro | 28.750 ouro | 31.250 ouro | 168 h | 84 h | 5 | 1 | 252 → 126 → 63 → 31,5 → 15,75 | Sim |
| Batata-doce | VIP | 10 | 4 verdes | Bloqueada sem VIP | 5 verdes | 12 h | 6 h | 2 | 1 | 18 → 9 | Sim |
| Amendoim | VIP | 20 | 8 verdes | Bloqueada sem VIP | 10 verdes | 24 h | 12 h | 3 | 1 | 36 → 18 → 9 | Sim |
| Limão | VIP | 30 | 12 verdes | Bloqueada sem VIP | 15 verdes | 36 h | 18 h | 4 | 1 | 54 → 27 → 13,5 → 6,75 | Sim |
| Noz | VIP | 40 | 16 verdes | Bloqueada sem VIP | 20 verdes | 48 h | 24 h | 5 | 1 | 72 → 36 → 18 → 9 → 4,5 | Sim |
| Coco | VIP | 50 | 20 verdes | Bloqueada sem VIP | 25 verdes | 60 h | 30 h | 6 | 1 | 90 → 45 → 22,5 → 11,25 → 5,625 → 2,8125 | Sim |

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

Os protetores não concedem XP por nenhuma ação. Sua única função é bloquear ações de outros jogadores na fazenda, incluindo roubos de plantações, em uma futura implementação multiplayer. Essa definição não indica que o roubo ou o multiplayer já estejam implementados.

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

Todo item vendível segue um acréscimo sobre seu custo-base:

| Perfil do jogador | Acréscimo sobre o custo-base | Multiplicador |
|---|---:|---:|
| Comum | 15% | 1,15 |
| VIP | 25% | 1,25 |

A regra abrange os itens vendíveis de cultivo, animais de produção e animais de crescimento.

### 6.1. Fórmulas

```text
valor_unitario_sem_arredondar = custo_base × multiplicador_do_perfil
total_sem_arredondar = valor_unitario_sem_arredondar × quantidade_vendida
valor_total_venda = teto(total_sem_arredondar × 10) ÷ 10
```

O multiplicador é 1,15 para o perfil comum e 1,25 para VIP. A função teto arredonda para o inteiro imediatamente superior, preservando valores já inteiros. O valor unitário não é arredondado antes da multiplicação pela quantidade. Por exemplo, dois ovos de uma galinha adquirida por 9 moedas verdes rendem 20,7 moedas verdes no perfil comum (9 × 1,15 × 2), embora uma venda isolada de um ovo renda 10,4 moedas verdes.

O benefício VIP acrescenta **10 pontos percentuais** ao acréscimo padrão: 15% + 10% = 25%.

No comando **Vender tudo**, produtos VIP bloqueados são ignorados e permanecem no armazém; os demais são vendidos normalmente. Para cada moeda, somam-se os valores exatos de todos os itens/lotes elegíveis e aplica-se o teto de uma casa decimal uma única vez ao total dessa moeda. Ouro e verdes nunca são somados nem convertidos. Esta unidade de arredondamento por operação e moeda é a decisão técnica adotada para concretizar a regra de arredondar o total.

### 6.2. Exemplo: nabo

Uma semente de nabo custa 300 ouro. Conforme a regra definida, cada unidade de nabo colhida usa esse valor como custo-base.

| Perfil | Cálculo do acréscimo | Acréscimo | Venda por unidade |
|---|---|---:|---:|
| Comum | 300 × 15% | 45 ouro | 345 ouro |
| VIP | 300 × 25% | 75 ouro | 375 ouro |

O percentual representa um acréscimo sobre o custo-base. O lucro líquido da atividade depende também de alimentação, fertilizantes, quantidade produzida e outros custos aplicáveis.

## 7. Cartão VIP

- **Preço:** 100 moedas verdes.
- **Duração:** 30 dias por compra, com ativação imediata no ato da compra.
- **Renovação:** com VIP ativo, cada compra soma 30 dias ao prazo restante; com VIP expirado, inicia 30 dias a partir da compra. Dez compras consecutivas, uma de cada vez, acumulam 300 dias de duração concedida.
- O cartão não fica armazenado no depósito nem aguarda uso manual: a compra aplica o benefício imediatamente.
- **Acesso exclusivo:** sementes VIP indisponíveis para jogadores comuns.
- **Venda:** acréscimo de 25% sobre o custo-base de todos os itens vendíveis enquanto o benefício estiver ativo.
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

Com a precisão registrada, 2,25 resulta em 2,3 e 2,20 permanece 2,2. O arredondamento é aplicado ao total da venda, após multiplicar pela quantidade.

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

## 13. Furto de temporadas

Uma visita só pode furtar planta cujo ciclo da temporada atual esteja completo e cujo terreno não esteja protegido. Não existe piso de produção garantido ao dono nem janela exclusiva inicial de colheita.

Um furto bem-sucedido transfere a única unidade daquela temporada para o visitante e consome a temporada do dono. O dono não recebe produto nem XP por essa perda; o visitante recebe o produto sem XP. O produto preserva cultura, moeda e custo-base da semente que o originou; o VIP aplicado à venda é o do vendedor no momento da venda.

Se restarem temporadas, a planta volta ao estágio 1 da temporada seguinte, com relógio zerado, primeira rega pendente e limite de fertilizante renovado. Se era a última temporada, vira planta morta. O furto não remove o terreno nem as próximas temporadas. Exemplo: uma planta de três temporadas furtada uma vez deixa apenas duas temporadas possíveis ao dono, se não houver novos furtos.

Só uma coleta ou furto pode consumir cada temporada. A implementação social deverá resolver concorrência e duplicação de pedidos na autoridade responsável pelo estado, sem confiar em pedidos do cliente. Proteção segue 4.3. Não estender automaticamente essa regra aos produtos de animais: o escopo confirmado aqui é a plantação.

## 14. Assets e estado de entrega

Reutilizar os assets existentes, com mapeamento explícito de nomes do catálogo para nomes de arquivos. A presença no disco deve ser conferida para os cinco estágios e a semente de cada cultura, fases e produtos animais e ícones de itens. A implementação deve conferir o carregamento e o resultado visual no jogo; inventário de caminhos não substitui revisão visual.

O alinhamento destes textos e a existência dos planos não significam que a economia nova, o VIP ou o social estejam implementados. O acompanhamento da entrega fica em `regras-de-negocio.md` e nos planos vinculados.
