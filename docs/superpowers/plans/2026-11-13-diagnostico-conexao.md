# Diagnóstico de conexão

**Objetivo:** executar o item #11 do plano aprovado: Ajustes → Testar conexão, com relatório seguro e copiável.

**Arquitetura:** GET `/v1/diagnostics` passa pela autenticação, origem permitida e limites existentes, mas não acessa fazendas, banco, recibos ou reservas de comandos. O navegador testa `/health` e essa rota com prazo limitado e gera resultado parcial. Não utiliza o fluxo de ações.

**Restrições:** preservar a ação pendente e seus identificadores; nunca reenviar, limpar armazenamento, criar operações econômicas ou copiar respostas brutas, tokens, nomes, IDs de jogadores ou URLs com parâmetros. Não implementar Push, sprites ou mudanças em produção.

## Aceitação e execução

- [x] Testar a rota sem serviço/banco: autenticação, origem, horário, preflight, método e ausência de dados privados.
- [x] Implementar sondas independentes com timeout e classificação limitada de erros; autenticação sem alterar leases.
- [x] Testar indisponibilidade, sessão inválida, timeout, relógio, relatório sanitizado e preservação exata da pendência.
- [x] Adicionar painel acessível, relatório selecionável e cópia com fallback; impedir duplicação e atualizações após fechar/trocar conta.
- [x] Executar testes Java afetados, frontend, TypeScript, build local e SpotBugs; revisar o diff.
- [x] Documentar limitações: fetch não diferencia sozinho DNS, TLS, CORS ou bloqueio; não verifica banco; nenhuma conclusão automática sobre a causa do Chrome.

Comandos: `node --test --test-isolation=none web/test/diagnostics.test.ts`; `npm --prefix web test`; `npm --prefix web run build:local`; `.\gradlew.bat :server:test :server:spotbugsMain :server:spotbugsTest`.
