import test from 'node:test';
import assert from 'node:assert/strict';
import {xpPercent,pageItems,purchaseLock,durationLabel,animalCare,seedGrowthLabel,cropTimingText,dailyAllowanceText} from '../src/ui-presentation.ts';

test('daily allowance explains manual claim, no accumulation and the UTC reset in local time',()=>{
 const status={amount:'100',available:true,nextResetAt:'2026-11-04T00:00:00Z'};
 const view=dailyAllowanceText(status);assert.equal(view.button,'Resgatar 100 ouro');assert.equal(view.disabled,false);
 assert.match(view.description,/não acumulam/);assert.match(view.reset,/UTC/);
 assert.ok(view.reset.includes(new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(Date.parse(status.nextResetAt))));
 const claimed=dailyAllowanceText({...status,available:false});assert.equal(claimed.button,'Já resgatado');assert.equal(claimed.disabled,true);
 assert.equal(dailyAllowanceText(undefined),undefined);assert.ok(!dailyAllowanceText({...status,nextResetAt:'invalid'}).reset.includes('Invalid'));
});

test('seed duration states the conditions behind nominal growth',()=>{
 assert.equal(seedGrowthLabel({growthSeconds:36000,seasons:1}),'10 h com rega em dia · 1 temporada(s)');
});

test('nominal remaining time includes its own date and time forecast',()=>{
 const tile={state:'GROWING',watered:true,dry:true,paused:false,weeds:false,insects:0,nominalRemainingSeconds:36000,remainingSeconds:60000};
 const nominalAt=Date.parse('2026-11-03T23:00:00Z'),withoutWaterAt=Date.parse('2026-11-04T05:40:00Z');
 const format=value=>new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(value);
 const view=cropTimingText(tile,withoutWaterAt,nominalAt);
 assert.equal(view.nominal,`Com rega em dia e sem pragas: 10 h restantes. Previsão: ${format(nominalAt)}.`);
 assert.ok(view.estimate.includes(format(withoutWaterAt)));
 for(const patch of [{paused:true},{watered:false}])assert.ok(!cropTimingText({...tile,...patch},withoutWaterAt,nominalAt).nominal.includes('Previsão:'));
 assert.ok(!cropTimingText(tile,NaN,NaN).nominal.includes('Previsão:'));
});

test('crop timing separates ideal remaining growth from the forecast without further watering',()=>{
 const tile={state:'GROWING',watered:true,dry:true,paused:false,weeds:false,insects:0,nominalRemainingSeconds:36000,remainingSeconds:60000};
 const forecast=Date.parse('2026-11-04T03:25:00Z'),view=cropTimingText(tile,forecast);
 assert.equal(view.nominal,'Com rega em dia e sem pragas: 10 h restantes');
 assert.equal(view.condition,'Solo seco: crescimento à metade da velocidade. Regue para acelerar.');
 assert.ok(view.estimate.startsWith('Sem nova rega e sem pragas: 16 h 40 min restantes. Previsão: '));
 assert.ok(view.estimate.includes(new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(forecast)));
 assert.equal(cropTimingText({...tile,dry:false},forecast).condition,'Mantenha a rega em dia para evitar atrasos.');
 assert.ok(!cropTimingText(tile,NaN).estimate.includes('Previsão:'));
});

test('paused and never-watered plants cannot promise a harvest timestamp',()=>{
 const tile={state:'GROWING',watered:true,dry:false,paused:true,weeds:true,insects:1,nominalRemainingSeconds:3600,remainingSeconds:7200};
 const paused=cropTimingText(tile,Date.now());
 assert.equal(paused.estimate,'');assert.match(paused.condition,/Crescimento pausado/);assert.match(paused.nominal,/Após remover as pragas/);
 assert.equal(cropTimingText({...tile,paused:false},Date.now()).estimate,'');
 const unwatered=cropTimingText({...tile,paused:false,weeds:false,insects:0,watered:false,dry:true},Date.now());
 assert.equal(unwatered.estimate,'');assert.equal(unwatered.condition,'Regue para iniciar o crescimento.');
 for(const state of ['READY','DEAD'])assert.deepEqual(cropTimingText({...tile,state},Date.now()),{nominal:'',condition:'',estimate:''});
});

test('XP progress keeps integer precision beyond the safe Number range',()=>{
 assert.equal(xpPercent('900719925474099300','1801439850948198600'),50);
 assert.equal(xpPercent('0.5','25'),2);
 assert.equal(xpPercent('0','25'),0);
 assert.equal(xpPercent('25','0'),100);
 assert.equal(xpPercent(undefined,undefined),0);
});

test('market pagination bounds first, last and empty pages without losing entries',()=>{
 const items=Array.from({length:23},(_,i)=>i);
 assert.deepEqual(pageItems(items,1).items,[10,11,12,13,14,15,16,17,18,19]);
 assert.equal(pageItems(items,99).page,2);
 assert.deepEqual(pageItems(items,99).items,[20,21,22]);
 assert.equal(pageItems(items,-1).page,0);
 assert.deepEqual(pageItems([],0),{items:[],page:0,pages:1,total:0});
});

test('purchase locks explain ownership, level and VIP without changing prices',()=>{
 assert.equal(purchaseLock(2,3,false,false,false),'Nível 3');
 assert.equal(purchaseLock(3,3,true,false,false),'Exige VIP');
 assert.equal(purchaseLock(3,3,true,true,false),undefined);
 assert.equal(purchaseLock(3,3,false,false,true),'Já comprado');
});

test('remaining duration distinguishes nominal 20h from 32h without watering',()=>{
 assert.equal(durationLabel(72000),'20 h');assert.equal(durationLabel(115200),'1 d 8 h');
 assert.equal(durationLabel(3599),'1 h');assert.equal(durationLabel(1),'1 min');assert.equal(durationLabel(0),'Pronto');
});

test('animal care follows server priority without collecting a protector',()=>{
 assert.deepEqual(animalCare({stage:'ADULT',status:'HUNGRY'},{kind:'GROWING'}),{label:'Vender adulto',action:'SELL_ANIMAL'});
 assert.deepEqual(animalCare({stage:'ADULT',status:'READY'},{kind:'PRODUCER'}),{label:'Recolher produto',action:'TEND_ANIMAL'});
 assert.equal(animalCare({stage:'ADULT',status:'PRODUCING'},{kind:'PROTECTOR'}),undefined);
 assert.equal(animalCare({stage:'YOUNG',status:'PRODUCING'},{kind:'GROWING'}),undefined);
 assert.deepEqual(animalCare({stage:'YOUNG',status:'HUNGRY'},{kind:'GROWING'}),{label:'Alimentar',action:'TEND_ANIMAL'});
});

test('fertilizer duration uses Portuguese hours and minutes',async()=>{
 const {fertilizerDuration}=await import('../src/ui-presentation.ts');assert.equal(fertilizerDuration(3600),'1 hora');assert.equal(fertilizerDuration(3660),'1 hora e 1 minuto');assert.equal(fertilizerDuration(7260),'2 horas e 1 minuto');assert.equal(fertilizerDuration(120),'2 minutos');
});