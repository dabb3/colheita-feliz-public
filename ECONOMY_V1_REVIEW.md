# Balanceamento de plantas v1

Esta entrega reúne as duas rodadas de implementação e validação. A fonte normativa é [docs/game-rule.md](docs/game-rule.md). O PR parte de `codex/balanceamento-plantas-v1` para a base online `codex/online-core-compartilhado`.

## Economia das 33 culturas

As 28 culturas comuns e cinco exclusivas VIP usam a receita total aprovada por ciclo. O investimento na semente ocorre uma vez. Níveis, preços de sementes, temporadas, tempos, XP, animais e recompensas mantêm os valores anteriores.

Receita e bônus são repartidos em décimos inteiros. Para total T e N temporadas, q é a divisão inteira T/N e r é o resto. A temporada i recebe q+1 quando i <= r e q nas demais. O bônus comum VIP total corresponde a 10% do custo da semente e usa a mesma repartição. Só a temporada vendida com VIP ativo recebe seu bônus. Culturas exclusivas VIP exigem VIP na venda e recebem apenas a receita aprovada, sem outro multiplicador.

- Tomate custa 950 ouro e retorna duas safras de 679,0. Com VIP nas duas vendas, soma 95,0 de bônus, ou 47,5 por temporada.
- Amendoim retorna três safras de 3,6 verdes, total 10,8.
- Coco custa 20 verdes e retorna seis safras de 5,5, total 33,0 e lucro de 13,0 por ciclo.

O catálogo guarda os totais e os testes conferem as 33 linhas, lucro, horas nominais e repartição. A progressão nominal comum começa em 4,5 ouro/h por terra e aumenta 1,5 por posição. O ROI das exclusivas VIP é 25%, 35%, 45%, 55% e 65%.

## Persistência, venda e migração

Cada lote preserva ID, moeda, custo, quantidade e restrição VIP. A versão v1 acrescenta o marcador externo economyVersion e os termos cropSale, com versão, índice da temporada, número de temporadas e totais de receita/bônus. O índice zero identifica a planta; produtos usam 1 a N. Colheita e furto preservam esses termos.

Produtos armazenados e plantas existentes sem termos v1 conservam a economia v0, inclusive as temporadas futuras da mesma planta. Sementes já compradas passam a v1 quando plantadas, se moeda, custo e restrição correspondem ao catálogo. Aquisições históricas diferentes mantêm v0, evitando conversão silenciosa.

O save estrutural permanece v20 no caminho atual. O novo ruleset é economy-2026-11-plants-v1; o leitor também aceita economy-2026-10 para v19/v20. No PostgreSQL, ambos os caminhos de gravação atualizam o ruleset junto do snapshot.

Os leitores validam campos obrigatórios, tipos e inteiros exatos antes do Gson. A validação contextual confere cultura, moeda, custo, temporadas, receita, bônus e VIP. Versões desconhecidas, índices inválidos, overflow, frações e metadados inconsistentes são recusados. O marcador externo impede fallback v0 quando cropSale é perdido isoladamente. Rascunhos v1 completos sem esse marcador e saves v0 válidos continuam aceitos. A recusa preserva o arquivo ou JSON armazenado.

O servidor calcula os valores e a elegibilidade VIP. O cliente recebe cotações para uma unidade, lote e venda total. Estoque, crédito e recibo idempotente são confirmados na mesma transação sob lock. Replay e concorrência não vendem a mesma unidade duas vezes. Vender tudo mantém os produtos VIP bloqueados no depósito.

## Decisões finais aprovadas

- Preços v1 permanecem exatamente como aprovados.
- Furto mantém cinco minutos de exclusividade por temporada e um sucesso a cada 24 horas por jogador, somando os destinos.
- Não criar imunidade para iniciantes.
- Preservar o arredondamento histórico v0.

Essas decisões encerram as propostas de mudança levantadas na segunda rodada. Os efeitos abaixo ficam registrados como limitações aceitas para acompanhamento.

## Testes e simulações

A validação local com JDK 21 e PostgreSQL 17 descartável passou com `./gradlew.bat build :server:integrationTest --max-workers=2`. O último teste adicional de compatibilidade e preservação de arquivo foi conferido com `./gradlew.bat :test :spotbugsTest --max-workers=2`.

- 222 testes do aplicativo, um de fronteira do core, 40 unitários do servidor, 51 de integração e quatro de capacidade, totalizando 318 Java.
- 133 testes web, typecheck e `npm run build:local` passaram.
- Regressões cobrem as 33 culturas, todas as combinações de VIP comum, ordem de vendas, agrupamento v1, saldos fracionários, expiração/renovação, replay, concorrência, rollback e temporada furtada sem XP ou segundo pagamento.
- Os testes negativos incluem 26 corrupções de produtos v1, bônus comum divergente, compatibilidade v0/rascunho v1, recusa de sobrescrita e corrupção no PostgreSQL.
- O depósito web foi verificado com estoque misto v0/v1 e mudança de VIP. A loja desktop usa a mesma cotação de domínio. A conferência foi por código e testes, sem revisão visual manual nesta rodada.

Os ensaios simplificados executam 1.647 estratégias fixas e 54 cenários que assumem patrimônio anterior. Uma comparação adicional usa capital igual em 84 estratégias. O ensaio fiel executa 27 cenários pelo serviço e PostgreSQL reais, começando com cinco terrenos, 500 ouro, cinco verdes e nível 1. Expansões, VIP, protetores e alimentação são pagos com recursos obtidos no próprio ensaio. A ajuda diária passa pelo comando real.

Os perfis usam 1/3/6 logins diários, 0/1/3 atacantes e políticas de expansão, reserva para VIP ou compra de proteção. Atacantes procuram safras a cada hora, sujeitos às regras reais. O dono age primeiro quando os horários coincidem. Sem ataques, a política de expansão termina com seis ou sete terrenos. No cenário direcionado de um login e um atacante, foram furtadas as 11 safras produzidas; o dono terminou no nível 4, sem colher. Isso não é previsão de comportamento dos amigos, mas registra a exposição de novatos aprovada nesta decisão.

- [Resultados da progressão pelo serviço](docs/economy-v1/faithful-progression-30-days.csv)
- [Caixa e patrimônio com capital inicial igual](docs/economy-v1/equal-capital-cashflow.csv)

O teste fiel regenera o primeiro CSV em `server/build/reports/economy-v1/`. Os testes de domínio regeneram o segundo e os ensaios simplificados em `build/reports/economy-v1/`. A integração exige TEST_DATABASE_URL num PostgreSQL descartável em loopback, banco colheita_test; não usar banco de jogadores.

## Limitações e cuidados operacionais

- Estoques v0 conservam a diferença histórica de arredondamento. Duas melancias v0 mais um ciclo v1 rendem 8.155,6 ouro em vendas individuais e 8.155,5 em venda conjunta sem VIP. A diferença aprovada é 0,1. Produtos exclusivamente v1 têm invariância exata.
- Uma planta de coco v0 ainda pode retornar seis vezes 25 verdes com VIP, conforme sua regra original. Essa receita transitória não foi reprecificada.
- Lucro/h nominal, caixa e custo investido medem coisas diferentes. Com cinco terras, capital igual e três logins, romã realiza 73.604 ouro de caixa e caviar 36.080, mas mantêm respectivamente 9.687,5 e 100.000 de custo alocado às temporadas pendentes. Esse custo não é moeda disponível nem preço de revenda da planta.
- Culturas curtas podem se ajustar melhor à agenda. Nabo amadurece em 16h40 sem nova rega; cenoura leva 25h. Num login a cada 24h, isso produziu 29 e 14 colheitas por terra no intervalo observado, apesar do lucro nominal/h maior da cenoura.
- As agendas são determinísticas, com pragas aleatórias desativadas e sem fertilizantes ou animais de produção/crescimento. Os atacantes concentram suas cotas num único dono. O ensaio não mede escolhas humanas, navegador, latência ou reconexão.
- A tabela v1 é imutável. Uma versão futura deve manter a autoridade v1 para liquidar os estoques existentes. As validações detectam inconsistência, mas não autenticam um item completamente forjado por alguém com acesso ao banco.
- Uma publicação futura exige backup coerente de fazendas e recibos e API atualizada antes do frontend. Não misturar servidores antigos e novos nem fazer rollback para leitores que descartem cropSale. Uma reversão deve preservar a capacidade de ler e liquidar v1.

O commit usa o prefixo [CF-Pages-Skip] para omitir o deploy automático do Pages durante esta revisão, mantendo os checks GitHub Actions. A API só tem deploy automático por push na base online; este PR é preparado para revisão externa.
