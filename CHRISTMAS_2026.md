# Evento de Natal de 2026

Implementação na branch `codex/evento-natal-2026`, sobre `d3eeed0`. Não requer novas dependências. Este relatório descreve alterações locais; não autoriza implantação.

## Regras aprovadas

- Mini-Bis permanente, VIP, nível 55, semente de 30 verdes.
- Primeira colheita em 72 horas nominais, com rega em dia e sem pragas; cinco seguintes de 36 horas.
- Seis unidades no ciclo, cada uma vendida por 8,5 verdes. Receita total de 51, lucro de 21 e ROI líquido de 70% sobre a semente comprada, sem bônus VIP adicional.
- XP pela fórmula existente: 108; 54; 27; 13,5; 6,75; 3,375. Não foi criada fórmula nova.
- Tema natalino de 06/11/2026 às 00:00 até 01/01/2027 às 00:00, fim exclusivo, em America/Sao_Paulo.
- Presente único de uma semente VIP para jogadores ativos cadastrados até 24/12/2026 inclusive. Entrega ao carregar a fazenda a partir de 24/12, inclusive após o evento. Cadastro a partir de 25/12 não qualifica.

As regras normativas estão em `docs/game-rule.md`, seções 2, 6 e 14.1. Os preços, tempos, níveis e fórmulas das 33 plantas anteriores permanecem intactos. Animais, terrenos, tarefas e regras sociais não mudam.

## Implementação

### Planta e vendas

O catálogo compartilhado inclui Mini-Bis com receita de 510 décimos repartida nas seis temporadas. A API continua calculando compras, colheitas e vendas; nenhum preço enviado pelo cliente é aceito. O mecanismo V1 existente preserva moeda, termos por temporada, restrição VIP e precisão decimal. A venda repetida não encontra estoque para um segundo pagamento.

Desktop e web usam os cinco sprites fornecidos e o caminho específico `semente-mini-bis.png`. A compra, bandeja e ficha utilizam os componentes existentes. A planta continua comprável depois de janeiro.

### Calendário, backgrounds e músicas

Os três backgrounds natalinos e as duas músicas fornecidas substituem os padrões somente durante o evento. Dia e pôr do sol compartilham uma música; noite usa outra. A virada do calendário também invalida a seleção de mídia, mesmo sem mudança do período do dia. No desktop, a atualização ocorre no render; na web, no ciclo da cena e na sincronização de áudio.

O ciclo diário continua pelo relógio real local: dia 06:00–16:59, pôr do sol 17:00–18:30, noite 18:31–05:59. Sono e efeitos sonoros não foram alterados. Preferências de volume e silêncio são preservadas. A API usa seu próprio relógio para decidir a entrega financeira do presente; mudar a data no navegador não concede sementes.

### Presente e persistência

`players.registered_at` registra o cadastro online. Uma migração aditiva reconhece as contas anteriores como preexistentes, usando a data sentinela 1970-01-01; cadastros posteriores usam o relógio do servidor. Reiniciar o servidor não redefine datas. Não se infere cadastro a partir da primeira visita à fazenda.

O snapshot mantém `christmas2026.registeredAt` e `giftGranted`. A notificação `CHRISTMAS_GIFT` conserva um cursor permanente mesmo após leitura ou retenção de 100 entradas. O recibo e o cursor precisam concordar. Tipos JSON incorretos, timestamps inválidos e divergências são recusados antes de qualquer sobrescrita. Saves históricos válidos sem os dois metadados continuam aceitos.

Semente, recibo e notificação são persistidos na transação já protegida pelo lock da fazenda. Consultas simultâneas entregam uma unidade; rollback permite uma nova tentativa sem perder nem duplicar o presente. Não há novo endpoint de resgate. Estoque cheio adia a concessão.

O presente não debita moedas, não gera XP e não cumpre missões ou tutorial. Seu custo-base de 30 verdes identifica os termos de venda V1; não representa gasto do presenteado. O ROI de 70% aplica-se à compra normal. Receber a semente não dispensa VIP nem nível 55 para plantar. Depois de expirar o VIP, cuidados e colheitas seguem as regras atuais e a venda permanece bloqueada até renovar.

## Compatibilidade e implantação posterior

O formato continua v20 e o caminho local continua `save/game-rules-v4.json`. Novas gravações usam `economy-2026-11-mini-bis-v1`. Os três rulesets econômicos anteriores seguem aceitos, preservando seus lotes.

Antes de publicar, fazer backup verificável do banco e dos saves locais usados no teste. Publicar API e clientes compatíveis com o catálogo e o novo ruleset; não manter escritores antigos e novos simultaneamente. Um rollback exige binário que compreenda Mini-Bis e os recibos, ou restauração coordenada do backup. O código antigo recusa o novo ruleset, evitando descarte silencioso.

A migração SQL é executada na inicialização da API e requer que o usuário do banco possa alterar sua tabela `players`. Não foi executada em nenhum provedor nesta tarefa. Nenhuma entrega a jogadores reais foi realizada.

## Verificação e limites

Os testes incluem os limites de calendário, as seis colheitas e vendas, compra após o evento, entrega única, contas elegíveis e tardias, retorno depois de janeiro, migração de cadastro, rollback, concorrência, retenção da notificação e recusa de recibos corrompidos em arquivo e PostgreSQL descartável.

Resultados em 06/11/2026:

- `gradlew.bat build :server:integrationTest spotbugsMain spotbugsTest :core:spotbugsMain :core:spotbugsTest :server:spotbugsMain :server:spotbugsTest visualReview`: aprovado em 5 min 34 s. A execução completa registrou 277 testes raiz, um core, 40 unitários do servidor, quatro de capacidade e 73 PostgreSQL; zero falhas ou ignorados. Os seis relatórios SpotBugs não têm achados.
- Após acrescentar três regressões, `gradlew.bat :test --tests '*Christmas2026Test' --tests '*ChristmasSaveTest' :server:integrationTest --tests '*ChristmasGiftIntegrationTest' :spotbugsTest :server:spotbugsTest`: aprovado em 22 s, com 14 testes delimitados de domínio/save e seis PostgreSQL. São dois testes raiz e um de integração adicionais; os demais casos foram reexecutados.
- `gradlew.bat :test` final: suíte raiz completa aprovada em 8 s, agora com 279 testes.
- `npm test`: 141 testes aprovados. `npm run build:local`: typecheck, bundle e limite de tamanho dos assets aprovados.
- Comparação estruturada dos catálogos: zero alterações nas 33 entradas anteriores; 34 culturas no total.
- `ffprobe`: as duas músicas são MP3 estéreo, 44,1 kHz, com 100,049 s e 70,661 s. Arquivos fornecidos preservados integralmente.
- `git diff --check`: sem erros de whitespace.

O PostgreSQL 17 usado nos ensaios foi um contêiner descartável acessível somente por 127.0.0.1, sem credenciais ou dados reais. O Gradle ainda emite o aviso preexistente de recursos depreciados para Gradle 9; isso não impediu os checks.

O harness gerou 48 cenas. Foram inspecionadas `build/visual-review/46-natal-dia-mini-bis.png`, `47-natal-por-do-sol.png` e `48-natal-noite.png`, sem abrir o save do jogador. Os cinco estágios fornecidos aparecem no cenário de dia. Os backgrounds fornecidos têm composições diferentes entre períodos; foram utilizados sem editar a arte.

O teste automatizado de áudio verifica seleção, pausa, repetição e retorno às faixas padrão. A qualidade sonora e o volume percebido precisam de escuta humana. Não houve teste em aparelho físico nem implantação; datas visuais dependem do relógio do dispositivo, conforme o ciclo já adotado.
