# Timers sociais e cooldown de 3h

> Execução: backend isolado por arquivos, frontend e integração no agente principal. Seguir executing-plans, testes antes da implementação e revisão independente final.

**Objetivo:** três limites independentes de 3h, privados e autoritativos, consultáveis na barra sem enviar tentativas.

**Base:** `b1718a1`, após #108. Brief fornecido pelo usuário: timers-cooldowns-3h-revisado.

**Arquitetura:** persistir o último uso existente, calcular `socialCooldowns` na transação própria. Frontend usa `serverTime` e `performance.now()`, preserva botões e foco, bloqueia entradas reais e mantém pedidos incertos indisponíveis.

## Contratos e aceitação

- `Farm.socialCooldowns?: {stealNextAt:string|null; insectsNextAt:string|null; weedsNextAt:string|null}`; omitido em snapshots alheios.
- 3h exatas para furto, insetos e ervas; presentes 24h, prioridade do dono 5min e consumo de tentativas preservados.
- Não migrar dados nem alterar autenticação, imagens ou apresentação Canvas do #108.
- API primeiro, frontend depois; API antiga ou relógio inválido implica “Verificando...”.
- Revisões antigas, troca de conta, suspensão e pedidos pendentes não podem liberar ação por suposição.

## Tarefas

- [x] Backend: testes de limite exato, independência, privacidade, histórico, consumo, idempotência e concorrência. Alterar constantes, mensagens e snapshot na mesma transação.
- [x] Frontend: helpers de estado/tempo; testes puros e DOM. Barra com badge, aria-disabled consultável, bloqueio em seleção/envio/inspeção/atalhos, um agendador e limpeza de sessão.
- [x] Integração: testes Web, TypeScript/build e Java/PostgreSQL. Revisão independente do diff e correção de achados.
- [x] Evidências: navegador real com renderer/ícones do produto, desktop e mobile, disponível/espera/liberação exata e independência.
- [ ] Entrega: documentar contrato e publicação API→Web; criar PR, acompanhar CI, não fazer merge.

## Comandos

`npm.cmd test --prefix web`; `npm.cmd run build:local --prefix web`;
`.\gradlew.bat test :server:test :server:integrationTest build` com JDK21 e PostgreSQL descartável conforme ambiente disponível; `git diff --check`.

## Riscos de revisão

Snapshots fora de ordem; resposta protegida com cooldown consumido; pedido em trânsito/incerto;
foco de teclado durante ticks; conta nova herdando dados ou intervalos antigos.
