import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {awayDuration,pluralItem} from '../src/away-report.ts';

test('absence duration reads like the game',()=>{
 assert.equal(awayDuration(33600),'9h20');assert.equal(awayDuration(10800),'3h');assert.equal(awayDuration(172800),'48h');
 assert.equal(awayDuration(172801),'2 dias');assert.equal(awayDuration(259200),'3 dias');
});
test('item names are lowercase Portuguese plurals',()=>{
 assert.equal(pluralItem('Morango',12),'12 morangos');assert.equal(pluralItem('Abóbora',6),'6 abóboras');assert.equal(pluralItem('Ovo',1),'1 ovo');
 assert.equal(pluralItem('Pimentão',2),'2 pimentões');assert.equal(pluralItem('Girassol',2),'2 girassóis');assert.equal(pluralItem('Flor',3),'3 flores');
});

function dom(){
 const make=tag=>({tag,children:[],attributes:{},dataset:{},textContent:'',className:'',hidden:false,disabled:false,style:{},
  classList:{list:new Set(),add(c){this.list.add(c);},remove(c){this.list.delete(c);},toggle(c,on){on?this.list.add(c):this.list.delete(c);},contains(c){return this.list.has(c);}},
  append(...v){this.children.push(...v);},prepend(...v){this.children.unshift(...v);},replaceChildren(...v){this.children=v;},
  setAttribute(k,v){this.attributes[k]=String(v);},getAttribute(k){return this.attributes[k]??null;},focus(){}});
 const ctx={document:{createElement:make,createElementNS:(_ns,tag)=>make(tag)},localStorage:undefined,matchMedia:()=>({matches:true})};
 const load=name=>readFileSync(new URL('../src/'+name,import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(load('ui.ts')+'\n'+load('presentation.ts')+'\n'+load('away-report.ts'))+'\nglobalThis.render=renderAwayReport;',ctx);
 const nodes=n=>[n,...(n.children??[]).flatMap(c=>typeof c==='object'?nodes(c):[])];
 const flat=n=>(n.textContent??'')+(n.children??[]).map(c=>typeof c==='object'?flat(c):String(c)).join('');
 const all=n=>flat(n);
 return {ctx,make,nodes,all};
}
const base={id:'r1',awaySeconds:33600,harvests:[],products:[],readyProducts:[],thefts:[],defenses:[],care:{waterings:0,pestsRemoved:0,animalCares:0},pending:[]};
function handlers(over={}){return{renew:async()=>undefined,renewVip(){},feed:async()=>undefined,buyFeed(){},close(){},careOpen:false,vipActive:true,onCareToggle(){},...over};}

test('empty sections are omitted and a single product reads on one line',()=>{
 const {ctx,make,all}=dom();const root=make('div'),foot=make('div');
 ctx.render(root,foot,{...base,products:[{productId:'ovo',name:'Ovo',animalId:'galinha',quantity:3}]},handlers());
 const text=all(root);
 assert.match(text,/Sua fazenda rendeu!/);assert.match(text,/Enquanto você esteve fora por /);assert.match(text,/9h20/);
 assert.match(text,/3 ovos no depósito/);assert.doesNotMatch(text,/produtos no depósito|colheitas prontas|Visitas|Proteção|cuidados|Precisa/);
 assert.match(all(foot),/Ir para a fazenda/);
});
test('several products, harvests, thefts, defense and care render with the approved copy',()=>{
 const {ctx,make,all}=dom();const root=make('div'),foot=make('div');
 ctx.render(root,foot,{...base,
  harvests:[{cropId:'morango',name:'Morango',stem:'morango',quantity:12},{cropId:'abobora',name:'Abóbora',stem:'abobora',quantity:6}],
  products:[{productId:'ovo',name:'Ovo',animalId:'galinha',quantity:3},{productId:'leite',name:'Leite',animalId:'vaca',quantity:1}],
  thefts:[{at:'2026-11-13T02:48:00Z',actor:'Bob',itemId:'morango',name:'Morango',stem:'morango',quantity:2}],
  defenses:[{at:'2026-11-13T04:15:00Z',actor:'Carol'}],
  care:{waterings:18,pestsRemoved:6,animalCares:3}},handlers());
 const text=all(root);
 for(const piece of['18','colheitas prontas','12 morangos','6 abóboras','4 produtos no depósito','3 ovos','1 leite','Bob levou 2 morangos.',
  'Seus protetores impediram a tentativa','Tentativa de Carol impedida.','27','cuidados automáticos','regas','pragas removidas','refeições'])assert.ok(text.includes(piece),piece);
});
test('renew asks for cost first, confirms once and shows the result',async()=>{
 const {ctx,make,nodes,all}=dom();const root=make('div'),foot=make('div');let calls=0,release;
 ctx.render(root,foot,{...base,pending:[{kind:'AUTOMATION',serviceId:'water',name:'Rega automática',expiredAt:'2026-11-13T06:12:00Z'}]},
  handlers({renew:id=>{calls++;assert.equal(id,'water');return new Promise(r=>release=r);}}));
 assert.match(all(root),/Rega automática expirou/);
 nodes(root).find(n=>n.textContent==='Renovar').onclick();
 assert.match(all(root),/Renovar por 30 dias: 100 verdes/);assert.equal(calls,0);
 const confirm=nodes(root).find(n=>n.textContent==='Confirmar');confirm.onclick();confirm.onclick();
 assert.equal(calls,1);assert.ok(nodes(root).find(n=>n.textContent==='Confirmar')?.disabled??true);
 release(undefined);await new Promise(r=>setTimeout(r,0));
 assert.match(all(root),/Rega automática ativa ✓/);
});
test('a failed renewal shows the server message and offers the action again',async()=>{
 const {ctx,make,nodes,all}=dom();const root=make('div'),foot=make('div');
 ctx.render(root,foot,{...base,pending:[{kind:'AUTOMATION',serviceId:'water',name:'Rega automática',expiredAt:'2026-11-13T06:12:00Z'}]},
  handlers({renew:async()=>'Ative o cartão VIP para contratar este serviço.'}));
 nodes(root).find(n=>n.textContent==='Renovar').onclick();nodes(root).find(n=>n.textContent==='Confirmar').onclick();await new Promise(r=>setTimeout(r,0));
 assert.match(all(root),/Ative o cartão VIP/);assert.ok(nodes(root).some(n=>n.textContent==='Renovar'));
});
test('feed uses stock, offers buying when empty and VIP opens the shop',async()=>{
 const {ctx,make,nodes,all}=dom();const root=make('div'),foot=make('div');let bought=0,vip=0,fed='';
 ctx.render(root,foot,{...base,pending:[{kind:'FEED',animalId:'cachorro-robo',name:'Cachorro',stock:4},{kind:'VIP',expiredAt:'2026-11-13T06:00:00Z'}]},
  handlers({feed:async id=>{fed=id;},renewVip:()=>vip++}));
 assert.match(all(root),/Seu cachorro está sem ração/);assert.match(all(root),/Você tem 4 rações no estoque/);assert.match(all(root),/Seu cartão VIP expirou/);
 nodes(root).find(n=>n.textContent==='Alimentar').onclick();assert.match(all(root),/Usa 1 das suas rações/);
 nodes(root).find(n=>n.textContent==='Confirmar').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(fed,'cachorro-robo');assert.match(all(root),/Cachorro alimentado ✓/);assert.match(all(root),/Você tem 3 rações no estoque/);
 nodes(root).find(n=>n.textContent==='Renovar').onclick();assert.equal(vip,1);
 const empty=dom(),r2=empty.make('div');
 empty.ctx.render(r2,empty.make('div'),{...base,pending:[{kind:'FEED',animalId:'cachorro-robo',name:'Cachorro',stock:0}]},handlers({buyFeed:()=>bought++}));
 empty.nodes(r2).find(n=>n.textContent==='Comprar ração').onclick();assert.equal(bought,1);
});
test('ready products in the barn are not described as collected',()=>{
 const {ctx,make,all}=dom();const root=make('div'),foot=make('div');
 ctx.render(root,foot,{...base,readyProducts:[{productId:'ovo',name:'Ovo',animalId:'galinha',quantity:2}]},handlers());
 assert.match(all(root),/2 ovos prontos para recolher no celeiro/);assert.doesNotMatch(all(root),/no depósito/);
});
test('collected and ready products are listed apart',()=>{
 const {ctx,make,all}=dom();const root=make('div'),foot=make('div');
 ctx.render(root,foot,{...base,products:[{productId:'ovo',name:'Ovo',animalId:'galinha',quantity:3}],readyProducts:[{productId:'leite',name:'Leite',animalId:'vaca',quantity:1}]},handlers());
 assert.match(all(root),/3 ovos no depósito/);assert.match(all(root),/1 leite prontos para recolher no celeiro/);
});
test('automation with expired VIP points to the VIP card instead of a purchase that would be refused',()=>{
 const {ctx,make,nodes,all}=dom();const root=make('div'),foot=make('div');let renews=0,vip=0;
 ctx.render(root,foot,{...base,pending:[{kind:'AUTOMATION',serviceId:'water',name:'Rega automática',expiredAt:'2026-11-13T06:12:00Z'}]},
  handlers({vipActive:false,renew:async()=>{renews++;},renewVip:()=>vip++}));
 assert.match(all(root),/Renove o cartão VIP para reativar/);assert.doesNotMatch(all(root),/100 verdes/);
 nodes(root).find(n=>n.textContent==='Renovar VIP').onclick();assert.equal(vip,1);assert.equal(renews,0);
});
