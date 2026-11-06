import fs from 'node:fs';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
const root='analysis/animals-v2/', out=root+'green-calibration/';
function read(path){const lines=fs.readFileSync(path,'utf8').trim().split(/\r?\n/),keys=lines.shift().split(',');return lines.map(l=>{const v=l.split(',');assert.equal(v.length,keys.length);return Object.fromEntries(keys.map((k,i)=>[k,v[i]]));});}
const alternatives=['A','B','C'];
const sims=alternatives.flatMap(alternative=>read(out+alternative+'/portfolio-simulations.csv').map(r=>({...r,alternative})));
const metrics=alternatives.flatMap(alternative=>read(out+alternative+'/animal-metrics.csv').filter(r=>r.model==='PROPOSED').map(r=>({...r,alternative})));
const old=read(root+'results/portfolio-simulations.csv').filter(r=>r.model==='PROPOSED');
const key=r=>['funding','start_level','logins','vip_intent','animal_currency','strategy','days'].map(k=>r[k]).join(':');
const oldMap=new Map(old.map(r=>[key(r),r])), aMap=new Map(sims.filter(r=>r.alternative==='A').map(r=>[key(r),r]));
assert.equal(sims.length,3888); assert.equal(metrics.length,120);
for(const r of sims){
 const a=aMap.get(key(r)); assert.ok(a);
 if(r.alternative==='A')for(const f of Object.keys(oldMap.get(key(r))))assert.equal(r[f],oldMap.get(key(r))[f],f);
 if(r.animal_currency==='GOLD'||r.strategy==='PLANTS')for(const f of Object.keys(a))if(f!=='alternative')assert.equal(r[f],a[f],f);
}
for(const r of metrics){
 const a=metrics.find(a=>a.alternative==='A'&&a.animal===r.animal&&a.currency===r.currency&&a.vip===r.vip);
 if(r.currency==='GOLD')for(const f of Object.keys(a))if(f!=='alternative')assert.equal(r[f],a[f],f);
 else {const tenths=s=>Math.round(Number(s)*10);assert.equal(tenths(r.delta_green),tenths(a.delta_green)*(alternatives.indexOf(r.alternative)+1));assert.ok(Number.isInteger(tenths(r.sale)));}
}
const manifest=JSON.parse(fs.readFileSync(root+'results/source-manifest.json','utf8'));
for(const [file,hash] of Object.entries(manifest.files))assert.equal(crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex'),hash,file);
function csv(path,rows,fields=Object.keys(rows[0])){fs.writeFileSync(path,fields.join(',')+'\n'+rows.map(r=>fields.map(k=>typeof r[k]==='number'?Number(r[k].toFixed(8)):r[k]).join(',')).join('\n')+'\n');}
csv(out+'metrics.csv',metrics.filter(r=>r.currency==='GREEN'));
csv(out+'simulations.csv',sims);
const n=(v,d=2)=>Number(v).toLocaleString('pt-BR',{minimumFractionDigits:d,maximumFractionDigits:d});
const md=(headers,rows)=>'| '+headers.join(' | ')+' |\n|'+headers.map(()=> '---').join('|')+'|\n'+rows.map(r=>'| '+r.join(' | ')+' |').join('\n');
const met=(alt,id,vip=false,currency='GREEN')=>metrics.find(r=>r.alternative===alt&&r.animal===id&&r.vip===String(vip)&&r.currency===currency);
const sum=(alt,vip,level=60)=>metrics.filter(r=>r.alternative===alt&&r.currency==='GREEN'&&r.vip===String(vip)&&Number(r.level)<=level).reduce((s,r)=>s+Number(r.green_per_hour),0)*24;
const select=(alt,level,logins,vip,days,strategy='ANIMALS',route='GREEN',funding='REAL_OR_RETAINED_REWARDS')=>sims.find(r=>r.alternative===alt&&+r.start_level===level&&+r.logins===logins&&r.vip_intent===String(vip)&&+r.days===days&&r.strategy===strategy&&r.animal_currency===route&&r.funding===funding);
const ids=metrics.filter(r=>r.alternative==='A'&&r.currency==='GREEN'&&r.vip==='false').map(r=>r.animal);
const prices=ids.map(id=>({animal:id,buy:met('A',id).buy,...Object.fromEntries(alternatives.flatMap(alt=>[[alt+'_common',met(alt,id).sale],[alt+'_vip',met(alt,id,true).sale]]))}));
csv(out+'prices.csv',prices);
const daily=alternatives.flatMap(alt=>[10,20,30,40,50,60].map(level=>({alternative:alt,level,common_green_day:sum(alt,false,level),vip_green_day:sum(alt,true,level),task_max_green_day:2,common_days_for_plot:50/sum(alt,false,level),vip_days_for_plot:50/sum(alt,true,level),vip_total_gain_days_for_card:100/sum(alt,true,level),vip_increment_days_for_card:100/(sum(alt,true,level)-sum(alt,false,level))})));
csv(out+'daily-capacity.csv',daily);
const summary=[];
for(const level of [10,20,30,40,50,60])for(const visits of [3,6,10])for(const days of [7,15,30]){
 const r={start_level:level,visits,days};
 for(const alt of alternatives)for(const vip of [false,true])r[alt+(vip?'_vip':'_common')]=select(alt,level,visits,vip,days).animal_realized_green;
 summary.push(r);
}
csv(out+'realized-comparison.csv',summary);
const vipComparison=[];
for(const alt of alternatives)for(const funding of PortfolioFunding())for(const level of [10,20,30,40,50,60])for(const visits of [3,6,10])for(const days of [7,15,30]){
 const common=select(alt,level,visits,false,days,'ANIMALS','GREEN',funding),vip=select(alt,level,visits,true,days,'ANIMALS','GREEN',funding);
 const tenths=s=>Math.round(Number(s)*10),v=tenths(vip.animal_realized_green),c=tenths(common.animal_realized_green),fee=tenths(vip.vip_green);
 vipComparison.push({alternative:alt,funding,start_level:level,visits,days,common_gain:common.animal_realized_green,vip_gain:vip.animal_realized_green,vip_spent:vip.vip_green,vip_hours:vip.vip_active_hours,incremental_animal_green_after_card:((v-c-fee)/10).toFixed(1),common_plot_equivalents:(c/500).toFixed(3),vip_plot_equivalents_after_card:((v-fee)/500).toFixed(3)});
}
function PortfolioFunding(){return ['REAL_OR_RETAINED_REWARDS','CONTROLLED_LIQUIDITY'];}
csv(out+'vip-and-land.csv',vipComparison);
const sections=[
'# Recalibração das vendas verdes — alternativas A/B/C',
'',
'**Proposta para aprovação, 06/11/2026.** Complementa ANIMAL_BALANCE_V2.md; A permanece a candidata original. B e C são alternativas, não decisões implementadas. Apenas margens verdes variam. Nenhum preço em ouro, compra, nível, duração, refeição, XP, protetor, planta, recompensa, terreno ou regra social foi alterado.',
'',
'## Critério de cálculo',
'',
'B = aquisição verde + 2 × margem verde de A. C = aquisição verde + 3 × margem verde de A. Aplicado separadamente ao comum e ao VIP: preserva exatamente o desenho do bônus de A. As margens originais já estão em décimos; multiplicá-las por inteiros resulta em preços finais exatos em décimos, sem arredondamento extra. Não se multiplica o principal nem a receita bruta. Não se recalcula o bônus VIP com uma fórmula diferente da comparação solicitada.',
'',
'Ganho verde por ciclo = venda − compra. ROI verde = ganho/compra; não inclui um câmbio fictício da ração. Resultado completo é um vetor: +verdes e −ouro de ração. As refeições e o custo ouro por ciclo permanecem nos CSVs. Verdes/h nominais = ganho/duração alimentada; as visitas reais podem alongar o ciclo. Primeiro retorno do principal ocorre na venda; acumular lucro suficiente para um cartão ou terreno é outra métrica.',
'',
'## Preços finais dos dez animais',
'',
md(['Animal','Compra','A comum','A VIP','B comum','B VIP','C comum','C VIP'],prices.map(r=>[r.animal,n(r.buy,1),...alternatives.flatMap(a=>[n(r[a+'_common'],1),n(r[a+'_vip'],1)])])),
'',
'## Ganho por ciclo, ROI e verdes/h',
'',
'Cada célula comum/VIP mostra os dois perfis. Ração em ouro é uma despesa adicional igual nas três alternativas.',
'',
md(['Animal','Alt.','Ganho comum/VIP','ROI % comum/VIP','Verdes/h comum/VIP','Ração ouro/ciclo'],ids.flatMap(id=>alternatives.map(alt=>{const c=met(alt,id),v=met(alt,id,true);return [id,alt,n(c.delta_green,1)+' / '+n(v.delta_green,1),n(c.green_principal_roi_pct)+' / '+n(v.green_principal_roi_pct),n(c.green_per_hour,5)+' / '+n(v.green_per_hour,5),n(c.feed_gold,0)];}))),
'',
'## Capacidade diária, tarefas e plantas V1',
'',
'Limite nominal de todas as espécies liberadas até o nível, com capital suficiente, coleta/recompra imediatas e alimentação pontual. Não é previsão de conta nova nem inclui subidas de nível durante o período. As tarefas dão até 2 verdes/dia: 14, 30 e 60 em 7, 15 e 30 dias, exigindo concluí-las. Não há comparação entre lucro ouro e verde.',
'',
md(['Nível','Alt.','Comum/dia','VIP/dia','Comum ÷ tarefas','VIP ÷ tarefas'],daily.map(r=>[r.level,r.alternative,n(r.common_green_day,3),n(r.vip_green_day,3),n(r.common_green_day/2),n(r.vip_green_day/2)])),
'',
'Referência das plantas VIP V1: coco rende 13 verdes líquidos por semente de 20, em 210 h totais nominais, antes do custo compartilhado do cartão. Uma terra gera 1,486 verdes/dia; cinco, 7,429; trinta, 44,571. Exige nível/desbloqueio e água em dia; temporadas, latência de visitas e furtos podem reduzir o realizado. Não equivale a uma conta com trinta terras gratuitas: as simulações começam com cinco e compram as demais por 50.',
'',
md(['Alt.','Animais comuns/dia','Animais VIP/dia','VIP animal ÷ 30 cocos VIP','Capital animal verde','Capital sementes 30 cocos'],alternatives.map(a=>[a,n(sum(a,false),3),n(sum(a,true),3),n(sum(a,true)/(30*13/210*24)),800,600])),
'',
'Os dez slots animais são adicionais às terras: mesmo uma taxa inferior à plantação pode aumentar a emissão agregada. O lucro das sementes é por ciclo completo com várias temporadas, não ROI diário. A alimentação animal em ouro impede tratar essas alternativas como investimento sem despesas na outra moeda.',
'',
'## Simulações determinísticas de 7, 15 e 30 dias',
'',
'1.296 trajetórias, 3.888 observações: três alternativas × dois regimes de patrimônio × seis níveis iniciais × duas moedas de compra × três cadências × dois perfis VIP × três estratégias (somente animais, somente plantas, misto). Usa o mesmo GameState e relógio real de domínio do estudo original; troca somente a liquidação das vendas dos animais por um ajuste offline. Cada saldo foi conciliado com compras, alimentação, vendas, recompensas, ajuda e capital ativo.',
'',
'O regime principal conserva as premiações históricas até o nível inicial (limite superior de reservas, não saldo presumido de usuários reais), com cinco terras. O controle de liquidez começa com 2 milhões de ouro e 2 mil verdes, explicitamente capital já acumulado, também com cinco terras. VIP precisa ser comprado por 100 verdes e pode começar tarde. Nível inicial não fica congelado: XP e novas liberações são reais. A alimentação inicial é incluída; refeições seguintes só são compradas se houver ouro. Não há compra além de um animal por tipo.',
'',
'A política que pretende VIP reserva 100 verdes antes de comprar animais. Nos níveis iniciais 10 e 20, a rota somente animais do regime principal fica sem agir porque ainda não pode pagar o cartão; por isso mostra zero. Isso é limitação da política de poupança, não prova de que animais VIP não funcionem nesses níveis. O controle de liquidez fornece a comparação com VIP ativo desde o início em todos os níveis; os cenários mistos permitem progredir pelo cultivo. Não se concedeu cartão grátis para esconder esse bloqueio.',
'',
'Tabela: lucro animal verde realizado, comum/VIP, antes de descontar o cartão. Regime principal, somente animais. O CSV completo também mostra caixa nas duas moedas, custo de comida, principal ativo, terrenos, capital de plantas, recompensas, XP, ciclos e horas VIP. Não somar principal ativo ao lucro nem atribuir premiações ao rendimento animal.',
'',
md(['Nível inicial','Visitas/dia','Dias','A comum/VIP','B comum/VIP','C comum/VIP'],summary.map(r=>[r.start_level,r.visits,r.days,...alternatives.map(a=>n(r[a+'_common'],1)+' / '+n(r[a+'_vip'],1))])),
'',
'As estratégias de cultivo V1 e as rotas animais em ouro são idênticas entre A/B/C em todos os campos verificados. Nas rotas mistas verdes, mais lucro permite expansão, compra de sementes, XP e desbloqueios diferentes: os resultados não são simplesmente multiplicados por 2 ou 3. As políticas não otimizam carteiras: animais são priorizados antes de terras e sementes, o que pode reduzir a liquidez para plantas VIP. Preferem sementes VIP na rota verde quando o cartão está ativo e há dinheiro; senão usam as elegíveis comuns.',
'',
'## Cartão VIP e terrenos',
'',
'Para avaliar se o cartão se paga, considerar o ganho adicional VIP sobre comum, não a receita VIP inteira. Os dias abaixo são equivalentes de lucro nominal em regime, não uma promessa de desbloqueio ou disponibilidade. Cartão com duração limitada: acima de 30 dias, o bônus animal isolado não recupera o custo no mês. O capital inicial e ração continuam necessários.',
'',
md(['Nível','Alt.','Dias/terreno comum','Dias/terreno VIP','Dias/cartão pelo bônus VIP'],daily.map(r=>[r.level,r.alternative,n(r.common_days_for_plot),n(r.vip_days_for_plot),n(r.vip_increment_days_for_card)])),
'',
'Realizado em 30 dias, nível inicial 60, somente animais, regime principal. Terrenos equivalentes são lucro dividido por 50: não são compras executadas nessa estratégia. A quantidade efetiva de terras nas estratégias mistas consta do CSV. Valores negativos após o cartão indicam que o bônus animal não o pagou, sem negar benefícios do VIP nas plantas.',
'',
md(['Alt.','Visitas','Ganho comum','Ganho VIP','Cartão pago','Bônus adicional após cartão','Terrenos pelo comum','Terrenos pelo VIP após cartão'],vipComparison.filter(r=>r.funding==='REAL_OR_RETAINED_REWARDS'&&r.start_level===60&&r.days===30).map(r=>[r.alternative,r.visits,n(r.common_gain,1),n(r.vip_gain,1),n(r.vip_spent,0),n(r.incremental_animal_green_after_card,1),n(r.common_plot_equivalents),n(r.vip_plot_equivalents_after_card)])),
'',
'## Comprar em verdes ou ouro?',
'',
'Não existe vencedor absoluto sem preferências sobre as duas moedas. Com compra em ouro, principal e ração são ouro e a venda recupera ouro; gera zero verde animal. Com compra em verdes, principal/venda são verdes e a ração continua ouro. Cada tipo só pode ocupar um slot: escolher uma rota exclui a outra naquele ciclo. B/C melhoram a atratividade verde sem alterar nenhum resultado da rota ouro. O CSV analítico da proposta original continua valendo para ouro.',
'',
md(['Animal','Compra ouro','Compra verde','Lucro ouro comum/VIP','Despesa ouro na rota verde','Ganho verde B comum/VIP'],ids.map(id=>{const g=met('A',id,false,'GOLD'),v=met('A',id,true,'GOLD'),b=met('B',id);return [id,n(g.buy,0),n(b.buy,1),n(g.delta_gold,0)+' / '+n(v.delta_gold,0),n(b.feed_gold,0),n(b.delta_green,1)+' / '+n(met('B',id,true).delta_green,1)];})),
'',
'Comprar em verdes faz sentido quando o jogador consegue manter a ração em ouro, tem capital verde que não precisa imediatamente para terrenos/cartão e quer aumentar suas fontes verdes. Ouro pode ser a escolha adequada para quem poupa verdes ou precisa recompor ouro. A galinha não exige refeição paga durante um ciclo ideal; isso não transforma aquisição em verde em renda de ouro nem cria conversão entre moedas. Não há arbitragem entre rotas: moeda de venda acompanha aquisição; cada nova produção exige recomprar.',
'',
'## Diagnóstico e recomendação',
'',
'**Recomendo B para aprovação.** A é a opção de menor emissão, mas o ganho marginal de vários ciclos é pouco perceptível: vaca dá 1,3 verde por 24 h alimentadas e exige 180 de principal e 240 ouro de ração. B sobe a 2,6 comum / 4,0 VIP por ciclo, preservando o custo de oportunidade e dando uma remuneração visível. C sobe a 3,9 / 6,0: não há evidência de inflação explosiva animal isolada nesta matriz, mas seu impacto agregado com plantas VIP e tarefas pede mais cautela.',
'',
'A emissão nominal madura comum/VIP fica aproximadamente 7,90/12,49 verdes por dia em A, 15,80/24,98 em B e 23,70/37,48 em C. B ainda permanece abaixo dos 44,571 verdes/dia de trinta cocos VIP, embora some aos slots de cultivo. C se aproxima dessa fonte com capital animal de 800 verdes e despesa ouro contínua; acelera a amortização do cartão e dos terrenos. Os terrenos são finitos: depois da expansão, parte dessa emissão deixa de encontrar esse sumidouro. Não há estatística de população/consumo que permita declarar uma taxa universalmente segura de inflação.',
'',
'Não se classificou nenhuma rota como estritamente dominada por animal anterior considerando simultaneamente compra, duração, ROI verde, verdes/h, XP/h e despesa ouro/h. Como B/C multiplicam todas as margens igualmente, preservam a ordenação de A. Isso não significa que cada métrica sobe com o nível: ROI por principal pode cair, e animais de crescimento demoram mais a realizar caixa. Limite de um por tipo torna o animal avançado uma opção adicional, não substituição ilimitada de um antigo.',
'',
'O principal risco de desinteresse de A continua sendo a baixa margem absoluta e o longo prazo para financiar objetivos apenas com animais iniciais. B alivia; C alivia mais e eleva emissão recorrente. Nenhuma alternativa elimina falta de liquidez, alimentação em ouro ou progressão por nível. Não proponho modificar compras, comida, plantas, proteção ou recompensas para compensar.',
'',
'Contra os 3.142,7 verdes do sistema atual no mesmo cenário comum de nível 60/seis visitas/30 dias, A realiza 225,7 (redução de 92,82%), B realiza 451,4 (85,64%) e C realiza 677,1 (78,45%). Isso é redução do ganho animal realizado desse cenário, não da emissão global do jogo. A soma nominal de A foi corrigida textualmente de 7,8 para 7,90 verdes/dia, sem mudar qualquer preço ou simulação original.',
'',
'## Evidências, limitações e reprodução',
'',
'Verificado: A reproduz exatamente o subconjunto correspondente da simulação anterior; B/C multiplicam apenas a margem verde; todas as rotas em ouro e todos os cenários somente plantas ficaram idênticos; os quatro arquivos econômicos conservam os hashes anteriores. Assertions do harness conferem carteiras e limite por tipo. Este estudo não é teste HTTP, concorrência, migração ou implementação de preços.',
'',
'Revisão independente somente leitura não encontrou problemas confirmados nas fórmulas, separação monetária e comparação incremental do VIP. Não executou o harness; a execução e integração foram verificadas pelo agente principal. A preferência por B é julgamento de calibração, não resultado de teste de percepção dos jogadores.',
'',
'Visitas uniformes de 8 h, 4 h e 2,4 h, inclusive ao longo da noite, não reproduzem sono humano. Mais visitas não garante ciclos melhores devido ao alinhamento da fome/coleta. Sem furtos, pragas ou rede, a comparação isola a calibração; as regras sociais permanecem intactas. As recompensas históricas são reservas hipotéticas, as missões exigem ações e o VIP pode estar inativo em parte da simulação. Médias não são garantia para jogadores reais.',
'',
'Execução: `./analysis/animals-v2/run.ps1 -GreenCalibration` com JAVA_HOME apontando para o JDK 21 existente; depois `node analysis/animals-v2/compare-green.mjs`. Nenhuma dependência nova. Tabelas completas e resultados estão em `analysis/animals-v2/green-calibration/`: prices.csv, metrics.csv, daily-capacity.csv, simulations.csv, realized-comparison.csv e vip-and-land.csv. Nenhuma alteração de produção, PR, merge ou deploy.',
];
// Strict normalized economic dominance, retaining separate gold feeding cost and XP.
for(const alt of alternatives)for(const vip of [false,true])for(const id of ids){
 const weak=met(alt,id,vip);
 for(const other of ids){const strong=met(alt,other,vip);if(+strong.level>=+weak.level)continue;
  assert.ok(!(+strong.buy<=+weak.buy&&+strong.nominal_hours<=+weak.nominal_hours&&+strong.green_per_hour>=+weak.green_per_hour&&+strong.green_principal_roi_pct>=+weak.green_principal_roi_pct&&+strong.gold_per_hour>=+weak.gold_per_hour&&+strong.xp_per_hour>=+weak.xp_per_hour),'Dominated '+alt+id);
 }
}
fs.writeFileSync('ANIMAL_GREEN_CALIBRATION_V2.md',sections.join('\n')+'\n');
fs.writeFileSync(out+'source-manifest.json',JSON.stringify({...manifest,scope:'offline GREEN margin sensitivity only',alternatives:{A:1,B:2,C:3},trajectories:1296,observations:3888},null,2)+'\n');
console.log('Verified 3888 observations, exact A reproduction, unchanged GOLD/plants, scaling, no normalized dominance, and source hashes.');
for(const alt of alternatives) console.log(alt,JSON.stringify({commonDay:sum(alt,false),vipDay:sum(alt,true),level60six30common:select(alt,60,6,false,30).animal_realized_green,level60six30vip:select(alt,60,6,true,30).animal_realized_green}));
