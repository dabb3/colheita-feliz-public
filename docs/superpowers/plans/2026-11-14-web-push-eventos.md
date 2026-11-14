# Plano — Web Push 3/3: animais e eventos sociais

## Escopo

Adicionar avisos opcionais para animal pronto, furto bem-sucedido e praga aplicada, usando o vínculo por dispositivo e a fila persistente existentes. A etapa não altera economia, regras de furto, proteção, XP ou snapshots.

## Contratos

- `animals`, `thefts` e `pests` exigem opt-in explícito independente de `plants`, com marcadores `animalsConfirmed`, `theftsConfirmed` e `pestsConfirmed`.
- Inscrições antigas e preferências ausentes permanecem desativadas.
- Mensagens não incluem saldo, identificadores de outros jogadores, cultura, quantidade econômica ou endpoint.
- Cada proprietário recebe no máximo um lote por minuto compartilhado entre plantas, animais e eventos sociais; cada dispositivo recebe uma tentativa por lote.
- Furto e praga são registrados na mesma transação da ação bem-sucedida; bloqueios, falhas e replays não criam eventos.
- Animal pronto usa agendamento temporal e revalidação do estado, inclusive após cuidador automático recolher o produto.
- O servidor valida proprietário, vínculo, revisão e evento antes de reservar; replay não cria nova entrega.
- Logout e troca de conta suprimem mensagens pendentes da conta anterior.

## Implementação em ordem

1. Generalizar a fila privada para tipos de evento, cursores por tipo e lotes compartilhados.
2. Expor preferências independentes no servidor e em Ajustes, mantendo consentimento explícito.
3. Emitir eventos nos fluxos já transacionais de coleta, furto e pragas.
4. Adaptar o Service Worker para mensagens fixas por tipo e validação de consentimento.
5. Cobrir concorrência, reentrada, replay, troca de conta, logout offline e isolamento entre jogadores.
6. Validar a atualização do Service Worker legado, troca de conta e logout offline.
7. Executar testes Java, PostgreSQL, web, TypeScript, build e SpotBugs.

## Aceitação

- Uma ação social gera no máximo um aviso por proprietário/dispositivo/lote.
- Nenhum evento é enviado quando a preferência correspondente está ausente ou falsa.
- Um furto bloqueado ou uma praga não aplicada não gera evento.
- VAPID ausente mantém a API e o jogo operacionais sem envio.
- O evento continua privado e não concede recompensa nem executa ação no jogo.
- A etapa 2 de plantas permanece sem regressão.
