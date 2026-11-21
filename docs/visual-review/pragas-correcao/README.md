# Correção visual das pragas do PR #107

Base: `98c07a348a97e722acfaad3ea88d074a698b231e`, branch de desenvolvimento
`codex/online-core-compartilhado`. Revisão em 21/11/2026.

## Aceitação e execução

- Erva com largura 27; minhocas com largura 23, em unidades do mundo 1600 × 900.
- Erva gira 2,5° pela base fixa, lateral e recuada em relação à cultura.
- Minhocas se deslocam até 3 na horizontal e 1 na vertical, com rotação de até 3,16°.
- O centro visível da minhoca compensa o apoio assimétrico do PNG recortado.
- Entrada dura 300 ms. Movimento reduzido mantém sprites estáticos, sem escala de entrada.
- Sprites, ícones das ferramentas e regras permanecem preservados.

Comandos: `npm test --prefix web`, `npm run build:local --prefix web` e
`npm run review --prefix web`. O build de produção exige as três variáveis
`PUBLIC_API_URL`, `PUBLIC_SUPABASE_URL` e `PUBLIC_SUPABASE_PUBLISHABLE_KEY`.

## Evidências reais

Capturas do Edge headless, Canvas `Scene` e PNGs reais. Sem mockup, API, conta
ou save. A comparação anterior compila `scene.ts` e `presentation.ts` da base
acima, dentro do mesmo fixture. A câmera móvel usa `cameraLayout` do produto,
zoom 1,4 e enquadramentos esquerdo/direito; o fixture ocupa a tela para inspeção
das plantações. Não representa o HUD completo nem uma execução em aparelho físico.

Cada coluna mostra, respectivamente, erva, uma, duas, três minhocas e combinação.
Cada linha mostra um estágio de crescimento de 1 a 5. A câmera esquerda cobre as
culturas iniciais; a direita permite avaliar a combinação com erva e três minhocas.
Tempos fixos de 2, 2,75 e 3,5 segundos permitem comparar o movimento determinístico.
Não são início/fim de um ciclo fechado: as fases variam por terreno.

| Cena | Antes | Depois: início | Meio | Fim do intervalo |
|---|---|---|---|---|
| Desktop 1280 × 720 | [Antes](desktop-antes-2000.png) | [2 s](desktop-depois-2000.png) | [2,75 s](desktop-depois-2750.png) | [3,5 s](desktop-depois-3500.png) |
| Mobile 390 × 844, esquerda | [Antes](mobile-antes-2000.png) | [2 s](mobile-depois-2000.png) | [2,75 s](mobile-depois-2750.png) | [3,5 s](mobile-depois-3500.png) |
| Mobile 390 × 844, direita | [Antes](mobile-direita-antes-2000.png) | [2 s](mobile-direita-depois-2000.png) | [2,75 s](mobile-direita-depois-2750.png) | [3,5 s](mobile-direita-depois-3500.png) |

Para inspeção manual: abra `http://127.0.0.1:5174/?pests`, ou acrescente
`&camera=left` / `&camera=right` em viewport vertical. Sem esses parâmetros,
a revisão anterior permanece disponível.

## Revisão e limites

Revisão independente somente leitura encontrou colisão entre erva e primeira
minhoca e a assimetria do apoio no PNG. A correção centraliza a minhoca e recua
a erva. Os testes cobrem escala, âncora, limites do terreno, separação, entrada,
remoção, movimento reduzido e hashes dos quatro ícones aprovados.

O movimento da minhoca é uma oscilação rígida curta; não há contorção por frames.
As capturas não validam Safari/iOS, toque real, desempenho de GPU móvel ou produção.
Revisão humana das imagens e aprovação válida de outra pessoa continuam necessárias.
Não fazer merge automático.
