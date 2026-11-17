# Melhorias urgentes — plano de implementação

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Entregar fertilizantes, correções móveis e evento dominical em dois PRs sem deploy.
**Architecture:** Catálogo compartilhado e motor existentes; evento determinístico com relógio autoritativo. Interface reutiliza projeções e componentes atuais.
**Tech Stack:** Java 21, libGDX, Gson, JUnit 5, TypeScript, Node.
**Spec:** docs/superpowers/specs/2026-11-17-melhorias-urgentes-design.md

## Global Constraints
Preservar save v20 e estoques existentes. Sem dependências ou migração. Não usar dados pessoais. Dois PRs para codex/online-core-compartilhado; sem merge/deploy.

## Review Focus
- Compras concorrentes e replay não duplicam débitos ou estoque.
- Redução maior que tempo restante não avança temporada.
- Zoom, viewport visual e ferramentas não ocultam popup.
- Elegibilidade depende da colheita, nunca do plantio ou maturação.
- Falha na informação de eventos não bloqueia ações econômicas.

### Task 1: Catálogo, compra e persistência
Files: assets/data/fertilizers.json; src/main/java/br/com/colheitafeliz/render/GameAssets.java; src/test/java/br/com/colheitafeliz/game/FertilizerTest.java; web/test/assets.test.ts; assets/classic/icons/adubo-{ultra,mega}.png; testes de save e integração existentes.
Interfaces: FertilizerCatalogLoader.load(), GameState.buyFertilizer(), estoque por ID; manter assinaturas.
- [ ] Atualizar testes: Comum 1000 ouro; Rápido 1 verde sem ouro; Super 2 verdes nível 30; Ultra 3 verdes nível 40/32400 segundos; Mega 5 verdes nível 50/50400 segundos; Amor 2000 ouro. Assistir falha.
- [ ] Testar compra abaixo/no nível, saldo insuficiente, consumo único e excesso perdido; roundtrip de todos os estoques e replay/concorrência via serviço.
- [ ] Alterar catálogo; registrar texturas e novos ícones/fallbacks. Reexecutar testes delimitados até PASS.

### Task 2: Amigos e popup
Files: web/style.css; web/src/main.ts; web/test/viewport.test.ts; web/test/ui-panels.test.ts.
Interfaces: positionHealth(panel, game, point), friend-card; preservar callers.
- [ ] Reproduzir transbordamento com nomes longos e dois botões; popup atrás de ferramentas. Adicionar regressões e assistir falha.
- [ ] Adaptar cartões à largura disponível; limitar popup à área visível acima de ferramentas, com rolagem quando necessário.
- [ ] Verificar resize/zoom e desktop/celular; npm test e npm run build devem passar.
- [ ] Atualizar ONLINE.md/notas, build Java e integração descartável; revisão independente do diff; commit e abrir PR 1.

### Task 3: Evento autoritativo
Files: novo src/main/java/br/com/colheitafeliz/game/SundayHarvestEvent.java; GameState.java; OnlineViews.java; testes Java de evento/colheita/integração.
Interfaces: SundayHarvestEvent.multiplier(CropType, Instant); SundayHarvestEvent projection com início/fim/estado e próximo evento. GameState usa apenas no HAND bem-sucedido; OnlineViews expõe projeção do servidor.
- [ ] Testar início 22/11/2026, fronteiras locais/UTC, ano, reinício, culturas especiais, Nabo 15/30, Cenoura 22,5/45, temporadas e outras ações.
- [ ] Incluir planta pronta sábado/colhida domingo com bônus; pronta domingo/colhida segunda sem bônus. Assistir falhas.
- [ ] Implementar função determinística e aplicação BigDecimal; comprovar recibos, concorrência, furto e VIP sem bônus adicional.

### Task 4: Interface de eventos e entrega
Files: web/src/types.ts; web/src/main.ts; web/src/ui-panels.ts; web/src/ui-presentation.ts; web/style.css; testes web; ONLINE.md; notas.
Interfaces: projeção opcional SundayHarvestEvent nas respostas de fazenda; contagem pelo tempo recebido do servidor.
- [ ] Adicionar testes futuro/ativo/próximo, contagem, falha de carga e exclusão das especiais; assistir falhas.
- [ ] Adicionar entrada Eventos, selo/valores, destaques discretos em loja/fazenda; verificar viewport estreito.
- [ ] Executar build Java, integração descartável, npm test/build, revisão visual e git diff --check. Esperado: PASS; registrar limitações.
- [ ] Revisão independente do diff; corrigir achados confirmados; commit e abrir PR 2. Nenhum merge/deploy.
