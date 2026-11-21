# Timers sociais de 3h — evidências

Base: `b1718a1`, após o PR #108. Verificação em 21/11/2026.

## Resultado e comandos

- 424 testes Web aprovados: helpers, DOM, recuperação de recibos e regressões existentes.
- 727 testes Java/PostgreSQL aprovados: 356 desktop, 1 core, 105 servidor e 265 integração.
- `npm.cmd run build:local --prefix web`: aprovado, incluindo TypeScript.
- `.\gradlew.bat build --no-daemon --no-problems-report`: aprovado, com análise estática e distribuições.
- `.\gradlew.bat test :server:test :server:integrationTest --no-daemon --no-problems-report`: aprovado, com JDK 21 existente e PostgreSQL descartável local.
- `git diff --check`: aprovado.

Os testes reproduziram antes da correção: intervalo antigo, campo ausente,
valores `null` removidos pelo JSON e descarte prematuro de recibo sem confirmação.
A revisão independente do diff não encontrou pendências confirmadas. Essa revisão
por agente não substitui aprovação humana no GitHub.

## Capturas da interface real

Edge headless com HTML/CSS/main.ts, Canvas, câmera e imagens reais do produto.
As respostas são sintéticas; não há acesso a conta, API de produção ou save.
O navegador emula eventos touch em 390 × 844 e 320 × 844, além de desktop
1280 × 720. Não houve teste em aparelho físico, Safari ou iOS real.

| Viewport | Disponível | Em espera | Antes do limite | Liberado automaticamente | Depois da proteção |
|---|---|---|---|---|---|
| Desktop | [Pronto](desktop-pronto.png) | [Espera](desktop-espera.png) | [1 ms restante](desktop-limite-antes.png) | [Limite exato](desktop-liberado.png) | [Proteção](desktop-protecao.png) |
| Mobile 390px | [Pronto](mobile-pronto.png) | [Espera](mobile-espera.png) | [1 ms restante](mobile-limite-antes.png) | [Limite exato](mobile-liberado.png) | [Proteção](mobile-protecao.png) |
| Mobile 320px | [Pronto](mobile-320-pronto.png) | [Espera](mobile-320-espera.png) | [1 ms restante](mobile-320-limite-antes.png) | [Limite exato](mobile-320-liberado.png) | [Proteção](mobile-320-protecao.png) |

[Verificações geométricas e de interação](browser-checks.json): badge dentro do
botão e abaixo da arte, sem rolagem horizontal; clique/toque e Enter/espaço no
ícone cinza mostram a espera sem tentativa. A erva permanece disponível enquanto
insetos e roubo têm prazos distintos. Um clique real no terreno recebe a resposta
sintética de proteção e atualiza o prazo de roubo imediatamente.

O relógio do fixture começa com 2s de espera. Avançar somente a referência
monotônica em 1999ms mantém bloqueio e exibe `01s`; mais 1ms libera pelo tick
normal da interface, preservando o mesmo botão e foco. `Date.now()` foi
propositalmente incorreto no teste do navegador. O produto não altera o relógio
do aparelho nem persiste contagens em armazenamento local.

## Reprodução

Execute `npm.cmd run review:social --prefix web`. Abra
`http://127.0.0.1:5177/?state=waiting`. Outros estados: `ready`, `boundary` e
`unknown`. O fixture bloqueia comandos econômicos e usa respostas determinísticas
para as três ações sociais. Para avançar o relógio de teste, execute no console:

```js
window.socialReview.advance(1999);
// Aguarde um tick da interface e confira o bloqueio.
window.socialReview.advance(1);
// A liberação ocorre no próximo tick, sem reconstruir a barra.
```

O hook existe apenas no bundle de revisão. Para saber o número de tentativas
sintéticas enviadas, consulte `window.socialReview.requests()`.

## Contrato e publicação

O [contrato online](../../../ONLINE.md#timers-privados-das-ações-sociais) descreve
`socialCooldowns` privado e os três prazos independentes, calculados por último
uso + 3h. Nenhuma migração ou reset de banco. Presentes continuam 24h; janela do
dono continua 5min. Sprites, sprays e apresentação Canvas do #108 não mudaram.

Publicar a API primeiro e validar os campos autenticados antes de promover o Web.
Pages e Fly podem disparar separadamente após merge; a ordem deve ser coordenada
na publicação. Web novo com API antiga fica neutro, sem supor disponibilidade.
Este PR não implanta serviços nem altera a automação de deploy.

Context7 estava exposto, mas recusou a consulta por falta de `link_id`. A
preservação de `null` foi conferida no [guia oficial do Gson](https://google.github.io/gson/UserGuide.html#null-object-support)
e em testes HTTP/recibo, mantendo inalterado o serializador dos hashes.

Limites: respostas do navegador são sintéticas; regras e concorrência foram
verificadas separadamente no PostgreSQL local. Não há alegação de teste em
produção, dispositivo físico, leitor de tela real ou suspensão real do iOS.
Não fazer merge automático.
