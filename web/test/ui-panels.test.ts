import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as values from '../src/ui-presentation.ts';
import {animalPath,productPath} from '../src/presentation.ts';

function panels(){
 const make=tag=>({tag,children:[],attributes:{},textContent:'',style:{setProperty(){}},append(...nodes){this.children.push(...nodes);},replaceChildren(){this.children=[];},setAttribute(k,v){this.attributes[k]=v;}});
 const ctx={...values,animalPath,productPath,document:{createElement:make}};
 const load=name=>readFileSync(new URL('../src/'+name,import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(load('ui.ts')+'\n'+load('ui-panels.ts'))+'\nglobalThis.panels={renderDeposit,renderPlantHealth,renderAnimal,renderMissions,renderFriends,renderAudioSettings:typeof renderAudioSettings==="undefined"?undefined:renderAudioSettings};',ctx);
 return{...ctx.panels,root:make('div')};
}
function nodes(root){return[root,...root.children.flatMap(child=>typeof child==='object'?nodes(child):[])];}
const catalog={crops:[{id:'milho',name:'Milho',spriteStem:'milho',vipOnly:false},{id:'vip',name:'Especial',spriteStem:'nabo',vipOnly:true}],animals:[{id:'vaca',name:'Vaca',kind:'PRODUCER',productId:'leite',productName:'Leite'}],fertilizers:[],feedPrices:{producer:10,growth:20}};

test('deposit preserves mixed currency quantities and skips VIP without blocking sell all',()=>{
 const {root,renderDeposit}=panels(),commands=[];
 renderDeposit(root,catalog,{vipActive:false,produceLots:[{itemId:'milho',quantity:2,currency:'GOLD'},{itemId:'milho',quantity:1,currency:'GREEN'},{itemId:'vip',quantity:1,currency:'GREEN',vipOnly:true}]},'products',()=>{},c=>commands.push(c));
 const all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,2);
 assert.ok(all.some(n=>n.textContent==='2 em ouro · 1 em verdes'));
 const sellAll=all.find(n=>n.textContent==='Vender tudo');assert.ok(!sellAll.disabled);sellAll.onclick();
 const buttons=all.filter(n=>n.textContent==='Vender este produto');assert.equal(buttons[1].disabled,true);buttons[0].onclick();
 assert.equal(JSON.stringify(commands),JSON.stringify([{action:'SELL_ALL'},{action:'SELL_ITEM',itemId:'milho'}]));
});

test('plant health separates nominal and unwatered times and hides stale content',()=>{
 const {root,renderPlantHealth}=panels();
 renderPlantHealth(root,catalog,{cropId:'milho',state:'GROWING',stage:1,season:1,seasons:1,nominalRemainingSeconds:72000,remainingSeconds:115200});
 let all=nodes(root);assert.ok(all.some(n=>n.textContent==='Com rega contínua: 20 h'));
 assert.ok(all.some(n=>n.textContent==='Sem nova rega: 1 d 8 h'));
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
 nodes(root).find(n=>n.textContent==='Visitar <Carlos>').onclick();assert.deepEqual(visits,['bob']);
});

test('audio controls keep separate channel toggles and volume',()=>{
 const {root,renderAudioSettings}=panels(),changes=[];
 renderAudioSettings(root,{effects:false,music:true,effectsVolume:.65,musicVolume:.3},(...args)=>changes.push(args));
 const all=nodes(root),check=all.find(n=>n.tag==='input'&&n.attributes['aria-label']==='Efeitos');
 check.checked=true;check.onchange();assert.deepEqual(changes[0],['effects',true,.65]);
 const volume=all.find(n=>n.attributes['aria-label']==='Volume de Música');volume.value='10';volume.oninput();assert.deepEqual(changes[1],['music',true,.1]);
});
