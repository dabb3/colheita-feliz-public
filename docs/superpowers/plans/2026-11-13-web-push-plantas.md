# Web Push Plantas Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox syntax for tracking.

**Goal:** Alertar plantas prontas offline com fila persistente, revalidação e até um lote por minuto.
**Architecture:** Reutilizar motor do online e transporte Web Push. Fila privada aditiva e cursores separados do histórico interno; despachos reservados antes da rede. Um trabalhador encerrável consulta trabalhos vencidos a cada trinta segundos, somente com VAPID.
**Tech Stack:** Java21, JDBC/PostgreSQL17, JUnit5, TypeScript, Service Worker; nenhuma dependência nova.
**Spec:** docs/superpowers/specs/2026-11-13-web-push-plantas-design.md

## Global Constraints
- Base 7d3abed; branch codex/web-push-plantas. PR para codex/online-core-compartilhado; sem merge/deploy/produção.
- Snapshot atual preservado; nenhuma economia, Natal, furtos ou novas regras de crescimento.
- Aviso fixo CROP_READY, por vínculo privado, sem nome/saldo/cultura. Até um lote por minuto, no máximo uma tentativa por dispositivo/lote.
- Baseline sem histórico em ativação/reativação sem dispositivo elegível; cursores preservados ao apagar avisos internos.
- Falha incerta não reenvia. Crescimento revalidado pelo motor; nenhuma rede em transação.

## Review Focus
- Saves diferidos por recibos devem reagendar atomicamente também; rollback não deixa trabalho fantasma.
- Assinar/desativar/trocar conta durante envio não reativa vínculo antigo nem debita recursos.
- Plantas pausadas com automação, secas ou nunca regadas não entram em loop de varredura.
- Dois trabalhadores/reinício não reenviam o mesmo lote; endpoint/revisão desativados não são alterados por resposta antiga.
- Atualização de preferência preserva chave/prova e não sobrescreve alteração concorrente.

## Task 1 — Agendamento e envio (Java, escritor exclusivo)
Files: novos CropPushService/CropPushStore e testes correspondentes; adaptar JdbcOnlineStore, OnlineGameService, ServerMain, PushService/PushStore quando necessário.
- [x] Escrever regressões vermelhas para fila offline, baseline e deduplicação de temporada.
- [x] Implementar fila privada indexada, reconciliar save direto/diferido e inscrição. Cursores limitados por terreno/estado corrente, recibo por despacho; preservar replay sem limpeza arbitrária.
- [x] Implementar tick() testável com clock/sender injetados e start/close limitados. Lote de dez fazendas, dois envios concorrentes no transporte já existente, sem agendador quando desconfigurado.
- [x] Revalidar estado real e destinatários em transações curtas, reservar antes da rede e nunca reenviar reserva incerta. Encerrar pendências após sessenta segundos; responder tarde não muda vínculo novo.
- [x] Testar maturação seca/regada, fertilizante, pausa, seis temporadas, automações, rollback, concorrência, transferência e reinício. Provar ausência de presentes/notas/ações econômicas automáticas e índices/RLS.
- [x] Rodar Java unitário e PostgreSQL afetados; informar red/green e contratos finais.

Interface ao frontend: GET /v1/push/config retorna plantEventsEnabled=true somente quando transporte configurado; automaticEventsEnabled continua false para tipos ainda não entregues, ou documentar a semântica final coerente. Preferências continuam no subscribe existente; para alterar preferência de vínculo ativo, adicionar POST /v1/push/preferences autenticado com endpoint,deviceToken,bindingId,deviceRevision e plants:boolean (somente plantas), mantendo revisão monotônica e identidade vinculada. Retornar estado/preferências próprias sem dados de terceiros. Ajustar tipos concretos com o frontend antes de mudar contrato.

## Task 2 — Preferência e worker (Web, escritor exclusivo)
Files: web/src/push.ts,push-state.ts,push-worker.ts,web/test/push.test.ts e testes adicionais/estilos necessários.
- [x] Testes vermelhos: CROP_READY aceito apenas com vínculo ativo; textos fixos por tipo; tipo desconhecido rejeitado, logout offline mantém supressão.
- [x] Habilitar Plantas com persistência por dispositivo, inclusive ao voltar aos Ajustes; outros tipos em breve. Não permitir clique duplo nem atualização de outra conta/revisão.
- [x] Reusar incremento de revisão/prova; revalidar estado após respostas. Mostrar indisponibilidade e erro recuperável sem limpar intenção econômica.
- [x] Worker exibe texto fixo, URL própria e revalida vínculo antes/depois. Sem fetch/cache ou dados arbitrários de payload.
- [x] Rodar npm --prefix web test, typecheck, build:local; registrar limites de mocks/iPhone.

## Task 3 — Integração, documentação, revisão e entrega
- [x] Coordenador confirma diferenças, contratos e reexecuta suíte Java/PostgreSQL completa, capacidade, SpotBugs, installDist, web/TypeScript/build e diff --check.
- [x] Revisão independente do diff completo; corrigir achados confirmados e repetir somente checks pertinentes.
- [x] Documentar migração/worker/retomada/limites e evidência visual isolada sem VAPID real.
- [ ] Commit/push e PR com notas ligadas ao número real, testes, limitações. Conferir CI; sem merge ou deploy.