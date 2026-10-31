# Desktop/Web: matriz de paridade

Baseline: branch codex/online-core-compartilhado, commit 3cce613 (30/10/2026). Comparação estática entre desktop LibGDX e cliente web TypeScript. Cada diferença só passa a migrada após verificação visual e funcional.

| Área | Desktop atual | Navegador atual | Trabalho para paridade | Estado |
|---|---|---|---|---|
| Cenário e fundo | FitViewport, fundo com escala proporcional e camadas ordenadas | Canvas 1600×900 com fundo proporcional alinhado ao topo; HTML sobreposto | Validar enquadramento e ordem em várias janelas; conferir alinhamento das construções/cercas e toque | Parcial |
| Terras | Losango lógico 170×72, arte 188×100, destaque pulsante | Centros e área 188×100 alinhados; contorno simples | Igualar espaçamento, sobreposição, estados, destaque e hit testing | Parcial |
| HUD e viewport | HUD desenhado dentro da viewport | Cabeçalho, tutorial e barra em HTML; Canvas ajusta à altura disponível | Evitar cortes/sobreposições; suportar janela estreita e toque | Parcial |
| Ferramentas | Oito: cursor, pá, sementes, regador, herbicida, pesticida, luvinha e fertilizante; atalhos 1–8, B/V/Q, M/N | Ordem e atalhos desktop; repetir B/V/Q fecha o painel aberto; M/N alternam música/efeitos; bandejas de sementes/adubos com seleção e estoque | Validar ciclo real de cada ferramenta no servidor e equivalência dos efeitos/retornos | Parcial |
| Plantas e canteiros | Estágios, planta seca/morta, maturação pulsante, ervas em quadros, insetos animados | Estágios proporcionais, planta seca/morta, brilho de maturação, ervas/insetos animados; transições mostram partículas; proteção ainda como glifo | Migrar indicador de proteção e comparar feedback visual de rega/colheita | Parcial |
| Popup de planta | Tooltip junto ao canteiro, com contexto e estado | Tooltip ancorado no canteiro; barras de temporada/crescimento e chips de pragas, seca e proteção | Ajustar ícones e validar posicionamento sem cobrir HUD/canteiro | Parcial |
| Animais | Estágios, passeio com pausas e destinos próximos, quadros de movimento/sono, direção, sombra, fome e camadas | Destinos limitados com pausas; sprites recortados preservam escala e apoio no chão; sombra usa largura visível do animal; poses, orientação, sono, fome e hitbox | Validar visualmente as espécies e posições no cercado | Parcial |
| Produção e colmeia | Pontos de produto quando prontos; colmeia e quatro abelhas com órbitas próprias | Produto pronto aparece nas espécies com marcador; colmeia/abelha usam atlas desktop e voo segue a mesma órbita; clique coleta pela ação contextual | Validar visualmente produto pronto e colmeia; revisar diferenças de coleta por espécie | Parcial |
| Dia/noite | Horário local: dia 06:00–16:59, pôr do sol 17:00–18:30, noite desde 18:31; animais dormem | Mesmos limites; imagens alternadas por período | Preservar limites e migrar composição, transição e sono | Parcial |
| Loja e depósito | Painéis próprios, abas, filtros, níveis, preços, estoque, compra e venda | Sementes comuns/VIP em blocos; depósito mostra totais por moeda e permite vender 1, tudo por produto ou tudo elegível | Reproduzir visual da loja, estoque/preço por item e mensagens | Parcial |
| Tarefas | Cartões, progresso atual/meta, barra, recompensa e tutorial integrado | Cartões, progresso/meta, barra, recompensa com ícone e tempo UTC; tutorial integrado com etapa e barra | Validar estados e recompensa em ciclos completos | Parcial |
| Amigos | Sem tela equivalente no desktop local | Convites, aceite, lista em cartões e visita; ferramentas de furto e amor na fazenda visitada | Validar troca entre contas e estados de amizade/autorização | Parcial |
| Popup de animal | Clique no animal executa ação contextual: alimentar, recolher ou vender | Clique próprio executa a ação contextual; “Meus animais” mantém ações explícitas e visita abre detalhe somente leitura | Comparar estados e condições exatas de cada ação; sons novos seguem pendentes | Parcial |
| Mensagens e recompensas | Toast com ícone/tipo, recompensas flutuantes e banner de nível | Toast de ação com ícone e ganhos de XP/moedas; banner de nível com culturas liberadas | Animar ganhos junto à cena e validar todas as mensagens/estados | Parcial |
| Áudio | Música e efeitos de arar, plantar, regar, colher, comprar, vender, ervas e nível | Música em loop com controle próprio; efeitos desktop para ferramentas, compras, vendas, colheita/visita e nível | Validar audibilidade em dispositivos; sons novos de animais seguem pendentes | Parcial |
| Regras online | API autoritativa, revisão, recibos, convites, proteção e transações | Cliente usa rotas e preserva requisições pendentes | Completar apresentação e validação sem duplicar economia no cliente | Parcial |
| Concorrência | Integração local passou: dono colhendo e visitante furtando ao mesmo tempo consomem só uma temporada; duplicidade também coberta | Duas sessões web independentes entraram como Alice/Bob e Bob visitou Alice; ações simultâneas no navegador ainda não verificadas | Exercitar conflito web e confirmar atualização sem duplicar recompensa ou perder estado | Parcial |

## Critérios de aceite

- Cena, ferramentas e telas reconhecíveis como desktop, sem perder regras autoritativas do servidor.
- Plantio, crescimento, rega, pragas, colheita, produção animal, economia e temporadas têm feedback correspondente.
- Loja, depósito, tarefas, amigos e popups permitem concluir ações sem estados ambíguos.
- Duas contas locais concluem amizade e ciclo de fazenda; concorrência não duplica recompensa nem perde estado.
- Sons novos de animais seguem explicitamente adiados.
