# Evento de domingo — notas para revisão

Desde 22/11/2026, colher uma cultura comum na própria fazenda no domingo concede 2× XP, no horário de São Paulo. Vale mesmo se a planta ficou pronta no sábado. Colher na segunda concede XP normal.

Todas as culturas comuns da loja participam, sem rotação. Culturas VIP e Bronze, Prata, Ouro e Diamante ficam excluídas. Nabo concede 15/30 XP e Cenoura 22,5/45 XP. Temporadas mantêm XP fracionário. Moedas, quantidades, vendas e outras ações não mudam.

O menu Eventos informa a janela e culturas; loja e informações da planta destacam culturas beneficiadas. Falha de carregamento não bloqueia o jogo.

Validação local: build Java completo, oito testes específicos do evento, projeção do servidor, 334 testes web e build:local. Fixtures móveis/desktop de evento futuro e ativo em 320, 390 e 1366 px. Revisão independente corrigiu projeção antiga no domingo seguinte. Integração PostgreSQL deve ser confirmada na CI; Docker local indisponível.

Estado: PR para revisão humana, sem merge ou deploy.
