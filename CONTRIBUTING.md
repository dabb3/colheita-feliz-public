# Como contribuir

Toda mudança na `main` deve entrar por pull request, inclusive mudanças de administradores e agentes.

1. Crie uma branch a partir de `origin/main` atualizada. Agentes usam o prefixo `codex/`.
2. Faça commits pequenos com mensagens claras e simples. Execute `./gradlew build` para mudanças no código Java.
3. Abra um PR explicando o que mudou, por quê e como foi verificado.
4. Obtenha pelo menos uma aprovação válida de outra pessoa. O autor não aprova o próprio PR; o último envio precisa de aprovação de outra pessoa.
5. Resolva as conversas pendentes. Novas alterações invalidam as aprovações anteriores; obtenha nova aprovação antes do merge.

Não envie commits diretamente à `main`, não use force-push nela e não exclua essa branch. Administradores seguem o mesmo fluxo, sem bypass.

## Configuração do GitHub

O ruleset está em [main.json](.github/rulesets/main.json). O arquivo não ativa a proteção sozinho: um administrador deve importá-lo em Settings > Rules > Rulesets, confirmar Active, alvo `main` e lista de bypass vazia. Repositórios públicos permitem essas regras no GitHub Free. Preserve outras proteções existentes e confirme que um PR sem aprovação fica bloqueado, inclusive para administradores.

Preserve as licenças e atribuições dos recursos. Não adicione saves, credenciais, skills, ferramentas pessoais ou material interno. O `.gitignore` não apaga arquivos que já estejam no histórico.
