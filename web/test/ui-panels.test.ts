import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as values from '../src/ui-presentation.ts';
import {animalPath,productPath} from '../src/presentation.ts';
import {avatarSource} from '../src/avatar.ts';

function panels(){
 const make=tag=>({tag,children:[],attributes:{},textContent:'',style:{setProperty(){}},append(...nodes){this.children.push(...nodes);},replaceChildren(){this.children=[];},setAttribute(k,v){this.attributes[k]=v;}});
 const ctx={...values,animalPath,productPath,avatarSource,document:{createElement:make}};
 const load=name=>readFileSync(new URL('../src/'+name,import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(load('ui.ts')+'\n'+load('ui-panels.ts'))+'\nglobalThis.panels={renderDeposit,renderPlantHealth,renderAnimal,renderMissions,renderFriends,renderGifts,giftLimit,renderAudioSettings:typeof renderAudioSettings==="undefined"?undefined:renderAudioSettings};',ctx);
 return{...ctx.panels,root:make('div')};
}
function nodes(root){return[root,...root.children.flatMap(child=>typeof child==='object'?nodes(child):[])];}
const catalog={crops:[{id:'milho',name:'Milho',spriteStem:'milho',vipOnly:false},{id:'vip',name:'Especial',spriteStem:'nabo',vipOnly:true}],animals:[{id:'vaca',name:'Vaca',kind:'PRODUCER',productId:'leite',productName:'Leite'}],fertilizers:[],feedPrices:{producer:10,growth:20}};

test('deposit preserves mixed currency quantities and skips VIP without blocking sell all',()=>{
 const {root,renderDeposit}=panels(),commands=[];
 renderDeposit(root,catalog,{vipActive:false,depositTotals:{GOLD:100,GREEN:5},produceLots:[{itemId:'milho',quantity:2,currency:'GOLD'},{itemId:'milho',quantity:1,currency:'GREEN'},{itemId:'vip',quantity:1,currency:'GREEN',vipOnly:true}]},'products',()=>{},c=>commands.push(c));
 const all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,2);
 assert.ok(all.some(n=>n.textContent==='2 em ouro · 1 em verdes'));
 const sellAll=all.find(n=>n.textContent.includes('Vender tudo'));assert.ok(!sellAll.disabled);sellAll.onclick();
 const buttons=all.filter(n=>n.textContent==='Vender 1');assert.equal(buttons[1].disabled,true);buttons[0].onclick();
 assert.equal(JSON.stringify(commands),JSON.stringify([{action:'SELL_ALL'},{action:'SELL_ITEM',itemId:'milho'}]));
});

test('plant health separates nominal and unwatered times and hides stale content',()=>{
 const {root,renderPlantHealth}=panels();
 renderPlantHealth(root,catalog,{cropId:'milho',state:'GROWING',watered:true,dry:true,stage:1,season:1,seasons:1,nominalRemainingSeconds:72000,remainingSeconds:115200},'2026-11-03T01:00:00Z');
 let all=nodes(root);assert.ok(all.some(n=>n.textContent.startsWith('Com rega em dia e sem pragas: 20 h restantes. Previsão:')));
 assert.ok(all.some(n=>n.textContent.startsWith('Sem nova rega e sem pragas: 1 d 8 h restantes. Previsão:')));
 assert.ok(all.some(n=>n.textContent.includes('Solo seco: crescimento à metade')));
 renderPlantHealth(root,catalog,{cropId:'milho',state:'GROWING',watered:true,paused:true,weeds:true,nominalRemainingSeconds:3600,remainingSeconds:7200},'2026-11-03T01:00:00Z');
 all=nodes(root);assert.ok(all.some(n=>n.textContent.includes('Crescimento pausado')));assert.ok(!all.some(n=>n.textContent.includes('Previsão:')));
 renderPlantHealth(root,catalog,{cropId:'milho',state:'DEAD'});all=nodes(root);assert.ok(all.some(n=>n.textContent==='Planta morta'));assert.ok(!all.some(n=>n.textContent.startsWith('Com rega')));
 renderPlantHealth(root,catalog,null);assert.equal(root.hidden,true);assert.equal(root.children.length,0);
});

test('individual animal uses selected id and visitors get no economic actions',()=>{
 const {root,renderAnimal}=panels(),commands=[];
 const farm={animals:[{id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0}],feed:{producer:1}};
 renderAnimal(root,catalog,farm,'vaca',false,c=>commands.push(c));assert.equal(nodes(root).filter(n=>n.tag==='button').length,0);
 renderAnimal(root,catalog,farm,'vaca',true,c=>commands.push(c));
 nodes(root).find(n=>n.textContent==='Recolher produto').onclick();assert.equal(commands[0].itemId,'vaca');assert.equal(commands[0].action,'TEND_ANIMAL');
 renderAnimal(root,catalog,{animals:[]},'vaca',true,()=>{});assert.ok(nodes(root).some(n=>n.textContent==='Este animal não está mais nesta fazenda.'));
});

test('missions and friends use text nodes and retain target identity',()=>{
 const {root,renderMissions,renderFriends}=panels();renderMissions(root,{missions:[{id:'m',title:'Colher',green:1,done:true}]});
 assert.ok(nodes(root).some(n=>n.textContent==='Concluída'));
 const visits=[];renderFriends(root,[{id:'bob',name:'<Carlos>'}],id=>visits.push(id));
});
test('missions show partial progress against the target',()=>{
 const {root,renderMissions}=panels();renderMissions(root,{missions:[{id:'nature',title:'Amigo da Natureza',green:0,progress:1,target:2,done:false},{id:'animals',title:'Amigo dos Animais',green:0,progress:0,target:1,done:false}]});
 assert.ok(nodes(root).some(n=>n.textContent==='1/2'));assert.ok(nodes(root).some(n=>n.textContent==='0/1'));assert.ok(nodes(root).some(n=>n.textContent==='Em andamento'));
});

 test('seed gifts bound quantity, confirm before sending and list received gifts',()=>{
  const {root,renderGifts,giftLimit}=panels(),sent=[],now=Date.parse('2026-11-10T12:00:00Z');
  assert.deepEqual([giftLimit(0),giftLimit(3),giftLimit(9)],[0,3,5]);
  const catalog={crops:[{id:'nabo',name:'Nabo',vipOnly:false},{id:'batata-doce',name:'Batata-doce',vipOnly:true}]};
  const gifts={received:[{id:'g',senderName:'<Ana>',cropId:'nabo',quantity:2,createdAt:'2026-11-09T12:00:00Z',state:'DELIVERED'}],
   sent:[{id:'s',recipientId:'eva',cropId:'nabo',quantity:1,createdAt:'2026-11-10T00:00:00Z',state:'DELIVERED'}]};
  renderGifts(root,catalog,{nabo:3,'batata-doce':1},[{id:'bob',name:'Bob'},{id:'eva',name:'Eva'}],gifts,now,fields=>sent.push(fields));
  const find=label=>nodes(root).find(n=>n.textContent===label);
  assert.ok(find('<Ana> enviou 2 semente(s) de Nabo'));
  assert.equal(find('Eva').disabled,true);
  assert.equal(find('Enviar presente').disabled,true);
  find('Bob').onclick();find('Batata-doce (1)').onclick();
  assert.ok(find('Batata-doce exige VIP ativo do seu amigo.'));assert.equal(find('Enviar presente').disabled,true);
  find('Nabo (3)').onclick();
  for(let i=0;i<5;i++)if(!find('+').disabled)find('+').onclick();
  assert.ok(find('3 semente(s)'));assert.equal(find('+').disabled,true);
  find('Enviar presente').onclick();assert.deepEqual(sent,[]);
  find('Cancelar').onclick();assert.ok(!find('Confirmar'));
  find('Enviar presente').onclick();assert.ok(find('Enviar 3 semente(s) de Nabo para Bob?'));
  find('Confirmar').onclick();assert.deepEqual(JSON.parse(JSON.stringify(sent)),[{recipientId:'bob',cropId:'nabo',quantity:3}]);
  renderGifts(root,catalog,{},[],{received:[],sent:[]},now,()=>{});
  assert.ok(find('Nenhum presente recebido ainda.'));
 });

test('audio controls keep separate channel toggles and volume',()=>{
 const {root,renderAudioSettings}=panels(),changes=[];
 renderAudioSettings(root,{effects:false,music:true,effectsVolume:.65,musicVolume:.3},(...args)=>changes.push(args));
 const all=nodes(root),check=all.find(n=>n.tag==='input'&&n.attributes['aria-label']==='Efeitos');
 check.checked=true;check.onchange();assert.deepEqual(changes[0],['effects',true,.65]);
 const volume=all.find(n=>n.attributes['aria-label']==='Volume de Música');volume.value='10';volume.oninput();assert.deepEqual(changes[1],['music',true,.1]);
});
