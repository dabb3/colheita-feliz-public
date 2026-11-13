import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {ApiError,acceptFarm} from '../src/api.ts';
import * as presentation from '../src/presentation.ts';
import * as uiPresentation from '../src/ui-presentation.ts';
import * as avatar from '../src/avatar.ts';
import * as inviteUrl from '../src/invite-url.ts';

const farm=owner=>({owner,name:owner,revision:'1',tiles:[],animals:[],seedCounts:{}});
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve};}
test('friend list renders each persisted avatar and falls back safely without changing visit target',async()=>{
 const {h,elements}=app();let target='';
 h.set({own:farm('alice'),api:{get:path=>{if(path==='/v1/friends')return Promise.resolve([{id:'bob',name:'Bob',avatar:{id:'female-2'}},{id:'carol',name:'Carol',avatar:{id:'google',photoUrl:'https://lh3.googleusercontent.com/a/carol'}},{id:'dan',name:'Dan'}]);target=path;return Promise.resolve(farm('bob'));}}});
 await h.friends();const images=descendants(elements.get('modal-content')).filter(node=>node.tag==='img'&&node.className==='friend-avatar');
 assert.equal(images.length,3);assert.equal(images[0].src,'/assets/classic/icons/avatar_feminino2.png');assert.equal(images[1].src,'https://lh3.googleusercontent.com/a/carol');assert.equal(images[1].referrerPolicy,'no-referrer');
 assert.equal(images[2].src,avatar.CLASSIC_AVATAR);images[1].onerror();assert.equal(images[1].src,avatar.CLASSIC_AVATAR);assert.equal(images[1].onerror,null);
 await descendants(elements.get('modal-content')).find(node=>node.textContent==='Visitar fazenda').onclick();assert.equal(target,'/v1/farms/bob/visit');
});
function app(preferences=new Map(),pushLogoutOptions){
 const intervals=new Map(),elements=new Map(),listeners=new Map(),audioClips=[],observers=[],timeouts=new Map();let nextTimeout=0;
 const make=(tag='div')=>({tag,value:'',selectedIndex:0,hidden:false,children:[],attributes:{},dataset:{},style:{setProperty(key,value){this[key]=value;}},scrollTop:0,scrollLeft:0,classList:{add(){},remove(){},toggle(){}},
  replaceChildren(...nodes){if(this.contains(ctx.document.activeElement))ctx.document.activeElement=null;this.children=nodes;},
  append(...nodes){this.children.push(...nodes);},prepend(...nodes){this.children.unshift(...nodes);},insertBefore(node,before){this.children.splice(this.children.indexOf(before),0,node);},replaceChild(node,before){this.children.splice(this.children.indexOf(before),1,node);return before;},replaceWith(node){const parent=descendants(elements.get('modal-content')).find(candidate=>candidate.children.includes(this));parent?.replaceChild(node,this);},
  removeAttribute(key){delete this.attributes[key];},setAttribute(key,value){this.attributes[key]=String(value);},getAttribute(key){return this.attributes[key]??null;},
  contains(target){return !!target&&(target===this||this.children.some(child=>child?.contains?.(target)));},
  get firstElementChild(){return this.children[0]??null;},get lastElementChild(){return this.children.at(-1)??null;},
  querySelectorAll(selector){return descendants(this).slice(1).filter(node=>selector==='[data-focus-key]'?node.getAttribute('data-focus-key')!==null:selector.startsWith('.')?(node.className??'').split(/\s+/).includes(selector.slice(1)):false);},
  querySelector(selector){return this.querySelectorAll(selector)[0]??null;},focus(){ctx.document.activeElement=this;},
  getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(){},close(){this.open=false;},showModal(){this.open=true;}});
 const storage={getItem:()=>null,setItem(){},removeItem(){}};
 const ctx={reconcilePush:async()=>{},connectPush:async()=>{},suspendPush:pushLogoutOptions?.suspendPush??(async()=>{}),createClient:()=>({auth:{signOut:pushLogoutOptions?.signOut}}),renderPushSettings:()=>{},...presentation,...uiPresentation,...avatar,...inviteUrl,history:{state:null,replaceState(){}},window:{localStorage:{getItem:key=>preferences.get(key)??null,setItem:(key,value)=>preferences.set(key,value)},COLHEITA_CONFIG:{development:!pushLogoutOptions},setInterval(callback,delay){intervals.set(delay,callback);return delay;},clearInterval(id){intervals.delete(id);},setTimeout(callback){const id=++nextTimeout;timeouts.set(id,callback);return id;},clearTimeout(id){timeouts.delete(id);},matchMedia(){return{matches:false};},requestAnimationFrame(callback){callback();return 1;},addEventListener(){}},document:{hidden:false,getElementById(id){if(!elements.has(id))elements.set(id,make());return elements.get(id);},createElement:make,createElementNS(_namespace,tag){return make(tag);},createTextNode:text=>text,addEventListener(name,handler){listeners.set(name,handler);}},location:{href:'http://localhost',origin:'http://localhost'},sessionStorage:storage,navigator:{onLine:true},URL,ApiError,acceptFarm,animalPath:()=>'',animalStatusText:(status)=>status,animalActionLabel:(status)=>status==='READY'?'Recolher produto':null,clearTimeout(){},ResizeObserver:class{constructor(callback){this.callback=callback;observers.push(this);}observe(target){this.target=target;}disconnect(){this.disconnected=true;}},Audio:class{paused=true;playCount=0;loop=false;volume=0;constructor(path){this.path=path;audioClips.push(this);}addEventListener(){}play(){this.paused=false;this.playCount++;return Promise.resolve();}pause(){this.paused=true;}},HTMLInputElement:class{},HTMLSelectElement:class{}};
 const uiSource=readFileSync(new URL('../src/ui.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(uiSource),ctx);
 const notificationSource=readFileSync(new URL('../src/notifications.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(notificationSource),ctx);
 let source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('void start();','');
 source+='\nglobalThis.h={refresh,logout,syncMusic,toggleMusic,toggleEffects,playEffect,shop,animalPopup,missions,friends,receive,dialog,health,connect,setLoginStep,pickTool,tileAction,settings,avatarSettings,notify,notifyAction,dialogGeneration:()=>dialogGeneration,acceptInvitation,get:()=>({own,visited}),set:(v)=>{own=v.own;visited=v.visited;api=v.api;catalog=v.catalog??{crops:[],fertilizers:[],animals:[],vip:{greenPrice:20}};}};';
 runInNewContext(stripTypeScriptTypes(source),ctx);return{h:{...ctx.h,runTimeouts(){for(const [id,callback]of [...timeouts]){timeouts.delete(id);callback();}}},elements,listeners,audioClips,observers,ctx,intervals};
}
function text(node){return(node.textContent??'')+(node.children??[]).map(child=>typeof child==='object'?text(child):child).join('');}
function descendants(node){return[node,...(node.children??[]).flatMap(child=>typeof child==='object'?descendants(child):[])];}

test('connection diagnostics is available with a pending action and cannot replay it',async()=>{
 const {h,elements}=app(),result=deferred();let runs=0;
 const pending={path:'/v1/me/actions',body:'private-pending'};
 h.set({own:farm('alice'),api:{pending,diagnose(){runs++;return result.promise;},mutate(){assert.fail('mutation');},retry(){assert.fail('retry');}}});
 h.settings();const open=descendants(elements.get('modal-content')).find(n=>n.textContent==='Testar conexão');assert.ok(open);open.onclick();
 assert.equal(runs,1);const run=descendants(elements.get('modal-content')).find(n=>n.textContent==='Testar novamente');assert.equal(run.disabled,true);run.onclick();assert.equal(runs,1);
 result.resolve({report:'Diagnóstico parcial seguro'});await new Promise(r=>setTimeout(r,0));
 const report=descendants(elements.get('modal-content')).find(n=>n.tag==='textarea');assert.equal(report.value,'Diagnóstico parcial seguro');assert.equal(run.disabled,false);
 assert.doesNotMatch(text(elements.get('modal-content')),/private-pending/);
});
test('late connection diagnostic cannot replace another panel',async()=>{
 const {h,elements}=app(),result=deferred();
 h.set({own:farm('alice'),api:{pending:null,diagnose:()=>result.promise}});h.settings();descendants(elements.get('modal-content')).find(n=>n.textContent==='Testar conexão').onclick();
 h.dialog('Outro painel');result.resolve({report:'late report'});await new Promise(r=>setTimeout(r,0));assert.equal(elements.get('modal-title').textContent,'Outro painel');assert.doesNotMatch(text(elements.get('modal-content')),/late report/);
});
test('connection diagnostic copy has a manual fallback and ignores stale-account results',async()=>{
 const {h,elements,ctx}=app();let selected=false;
 h.set({own:farm('alice'),api:{pending:null,diagnose:async()=>({report:'safe report'})}});h.settings();descendants(elements.get('modal-content')).find(n=>n.textContent==='Testar conexão').onclick();await new Promise(r=>setTimeout(r,0));
 const report=descendants(elements.get('modal-content')).find(n=>n.tag==='textarea');report.select=()=>{selected=true;};
 await descendants(elements.get('modal-content')).find(n=>n.textContent==='Copiar diagnóstico').onclick();assert.equal(selected,true);assert.equal(ctx.document.activeElement,report);assert.match(text(elements.get('modal-content')),/Cópia automática indisponível/);
 const pending=deferred();h.set({own:farm('alice'),api:{pending:null,diagnose:()=>pending.promise}});h.settings();descendants(elements.get('modal-content')).find(n=>n.textContent==='Testar conexão').onclick();
 h.set({own:farm('bob'),api:{pending:null}});pending.resolve({report:'previous account'});await new Promise(r=>setTimeout(r,0));assert.doesNotMatch(text(elements.get('modal-content')),/previous account/);
});

test('notification badge and panel follow own state during a visit and retain list scroll on refresh',()=>{
 const {h,elements}=app();
 const notes=[{id:'one',type:'THEFT',message:'João furtou 1 Morango.',createdAt:'2026-11-06T12:00:00Z',read:false,metadata:{}},{id:'two',type:'VIP_EXPIRED',message:'VIP expirou',createdAt:'2026-11-06T11:00:00Z',read:false,metadata:{}}];
 const own={...farm('alice'),notifications:notes,unreadNotifications:2};
 h.set({own,visited:farm('bob'),api:{pending:false,busy:false}});h.receive({});
 assert.equal(elements.get('notification-badge').textContent,'2');elements.get('notifications').onclick();
 let list=descendants(elements.get('modal-content')).find(n=>n.className==='notification-list');assert.ok(list);list.scrollTop=340;
 h.receive({farm:{...own,revision:'2',notifications:notes.map((n,i)=>({...n,read:i===0})),unreadNotifications:1}});
 list=descendants(elements.get('modal-content')).find(n=>n.className==='notification-list');assert.equal(list.scrollTop,340);assert.equal(elements.get('notification-badge').textContent,'1');
 h.receive({farm:{...own,revision:'3',notifications:notes.map(n=>({...n,read:true})),unreadNotifications:0}});assert.equal(elements.get('notification-badge').hidden,true);
});

test('open missions refreshes the claimed allowance at UTC rollover',async()=>{
 const {h,elements,intervals}=app(),status={amount:'100',available:false,nextResetAt:'2026-11-04T00:00:00Z'};
 const own={...farm('alice'),serverTime:'2026-11-03T23:59:59Z',dailyAllowance:status};let reads=0;
 h.set({own,api:{pending:null,get:async path=>{assert.equal(path,'/v1/me/farm');reads++;return {...own,serverTime:'2026-11-04T00:00:00Z',dailyAllowance:{...status,available:true}};},prepareRequestId:async()=>{}}});
 h.missions();assert.equal(reads,0);assert.match(text(elements.get('modal-content')),/Já resgatado/);
 h.runTimeouts();assert.equal(reads,0);intervals.get(30_000)();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(reads,1);assert.match(text(elements.get('modal-content')),/Resgatar 100 ouro/);
 h.runTimeouts();assert.equal(reads,1);
});

test('missions claims the own daily allowance explicitly once, including while visiting',async()=>{
 const {h,elements}=app(),reply=deferred(),sent=[];
 const allowance={amount:'100',available:true,nextResetAt:'2026-11-04T00:00:00Z'};
 const own={...farm('alice'),coins:'500',greenCoins:'5',level:1,currentXp:'0',dailyAllowance:allowance};
 h.set({own,visited:{...farm('bob'),revision:'88'},api:{pending:null,mutate:(path,command)=>{sent.push({path,command});return reply.promise;},prepareRequestId:()=>Promise.resolve()}});
 h.missions();assert.equal(sent.length,0);
 const claim=descendants(elements.get('modal-content')).find(node=>node.textContent==='Resgatar 100 ouro');assert.ok(claim);assert.equal(claim.disabled,false);
 claim.onclick();claim.onclick();assert.equal(sent.length,1);
 assert.equal(sent[0].path,'/v1/me/actions');assert.equal(JSON.stringify(sent[0].command),JSON.stringify({action:'CLAIM_DAILY_ALLOWANCE',expectedRevision:'1'}));
 reply.resolve({farm:{...own,revision:'2',coins:'600',dailyAllowance:{...allowance,available:false}},result:{success:true,message:'Ajuda diária resgatada: +100 ouro.'}});
 await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(h.get().own.coins,'600');assert.equal(descendants(elements.get('modal-content')).find(node=>node.textContent==='Já resgatado').disabled,true);
});

test('visit defaults to inspection and a touch or cursor click never sends a social action',async()=>{
 const {h,ctx,elements,listeners}=app();let tap,client;const sent=[];
 ctx.Scene=class{constructor(_canvas,_catalog,onTile){tap=onTile;}update(){}close(){}};
 ctx.ApiClient=class{pending=null;constructor(){client=this;}get(){return Promise.resolve(farm('alice'));}prepareRequestId(){return Promise.resolve();}mutate(path,command){sent.push({path,command});return Promise.resolve({});}};
 h.set({own:undefined});await h.connect('alice',async()=> 'synthetic-token');
 client.get=path=>Promise.resolve(path==='/v1/friends'?[{id:'bob',name:'Bob'}]:{...farm('bob'),serverTime:'2026-11-03T12:00:00Z'});
  const catalog={crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[],vip:{greenPrice:20}};
  h.set({own:farm('alice'),api:client,catalog});
 await h.friends();await descendants(elements.get('modal-content')).find(node=>node.textContent==='Visitar fazenda').onclick();
 await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(elements.get('tools').children[0].getAttribute('aria-label'),'Inspecionar plantas');
 assert.equal(elements.get('tools').children[0].getAttribute('aria-pressed'),'true');
 const bounds={left:0,top:0,width:390,height:844};elements.get('game').getBoundingClientRect=()=>bounds;elements.get('farm').getBoundingClientRect=()=>bounds;
 const panel=elements.get('plant-health');panel.getBoundingClientRect=()=>({width:360,height:300});
 const tile={column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,season:1,seasons:1,remainingSeconds:3600,nominalRemainingSeconds:3600};
 tap(tile,'touch');assert.equal(panel.hidden,false);assert.match(text(panel),/Nabo/);assert.equal(sent.length,0);
 await h.tileAction({...tile,state:'READY',remainingSeconds:0,nominalRemainingSeconds:0});assert.equal(sent.length,0);assert.ok(!text(panel).includes('Furtar colheita'));
 h.pickTool('STEAL');await h.tileAction(tile);assert.equal(sent.length,1);assert.equal(sent[0].command.action,'STEAL');
  h.set({own:farm('alice'),api:client,catalog});h.pickTool('CURSOR');
  tap(tile,'mouse'); // A touch-generated click can arrive as a MouseEvent.
  assert.equal(panel.hidden,false);assert.match(text(panel),/Nabo/);assert.equal(sent.length,1);
  listeners.get('pointerdown')({pointerType:'mouse',target:elements.get('game')});
  assert.equal(panel.hidden,true);
});

test('pest tools exist only in visits and send the selected plot with its revisions',async()=>{
 const {h,elements}=app(),own=farm('alice'),visited={...farm('bob'),revision:'42'},sent=[];
 const api={pending:null,mutate:async(path,command)=>{sent.push({path,command});return{result:{success:false,message:'Disponível em 1h 0min 0s.'}};},prepareRequestId:()=>Promise.resolve()};
 h.set({own,api});h.receive({});assert.ok(!text(elements.get('tools')).includes('1/6h'));
 h.set({own,visited,api});h.receive({});
 for(const [action,label,asset]of [['SPREAD_INSECTS','Lançar inseto · 1/6h','verme_1'],['SPREAD_WEEDS','Lançar erva · 1/6h','erva_daninha_1']]){
  const choice=descendants(elements.get('tools')).find(n=>n.getAttribute?.('aria-label')===label);assert.ok(choice);assert.ok(choice.children.some(n=>n.src===`/assets/classic/effects/${asset}.png`));
  choice.onclick();await h.tileAction({column:2,row:1,harvestRevision:'9'});
  assert.equal(text(elements.get('notice')),'Disponível em 1h 0min 0s.');
  const last=sent.at(-1);assert.equal(last.path,'/v1/farms/bob/visit-actions');assert.equal(JSON.stringify(last.command),JSON.stringify({action,column:2,row:1,harvestRevision:'9',expectedRevision:'42'}));
 }
 h.set({own,api});h.receive({});assert.ok(!descendants(elements.get('tools')).some(n=>n.getAttribute?.('aria-label')?.startsWith('Lançar')));
});

test('pinned pest hint follows tool selection without restarting the growth counter',()=>{
 const {h,ctx}=app();let clientNow=Date.parse('2026-11-03T12:00:00Z');ctx.Date=class extends Date{static now(){return clientNow;}};
 h.set({own:farm('alice'),visited:{...farm('bob'),serverTime:'2026-11-03T12:00:00Z'},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[],vip:{greenPrice:20}}});h.receive({});
 const bounds={left:0,top:0,width:1280,height:720};ctx.document.getElementById('game').getBoundingClientRect=()=>bounds;ctx.document.getElementById('farm').getBoundingClientRect=()=>bounds;
 const panel=ctx.document.getElementById('plant-health');panel.getBoundingClientRect=()=>({width:360,height:280});
 const tile={column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,season:1,seasons:1,remainingSeconds:3600,nominalRemainingSeconds:3600};
 h.pickTool('SPREAD_INSECTS');h.health(tile,{x:585,y:400},'tap');assert.match(text(panel),/Lançar inseto nesta planta/);
 clientNow+=60000;h.pickTool('SPREAD_WEEDS');assert.match(text(panel),/Lançar erva nesta planta/);assert.ok(!text(panel).includes('Lançar inseto nesta planta'));
 assert.equal(panel.querySelector('.crop-growth-value').textContent,'59min');
});

test('live shop explains nominal duration and the live popup qualifies its forecast',()=>{
 const {h,ctx}=app(),crop={id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000,regrowthSeconds:0,seasons:1,requiredLevel:1,seedPrices:{GOLD:300},vipOnly:false};
 h.set({own:{...farm('alice'),level:1,serverTime:'2026-11-03T01:00:00Z'},catalog:{crops:[crop],animals:[],fertilizers:[],vip:{greenPrice:20}}});
 h.shop();assert.match(text(ctx.document.getElementById('modal-content')),/10 h com rega em dia/);assert.match(text(ctx.document.getElementById('modal-content')),/Tempo nominal:/);
 ctx.document.getElementById('modal').close();
 const bounds={left:0,top:0,width:1280,height:720};ctx.document.getElementById('game').getBoundingClientRect=()=>bounds;ctx.document.getElementById('farm').getBoundingClientRect=()=>bounds;
 const panel=ctx.document.getElementById('plant-health');panel.getBoundingClientRect=()=>({width:360,height:300});
 let clientNow=Date.parse('2030-01-01T12:00:00Z');ctx.Date=class extends Date{static now(){return clientNow;}};
 const tile={cropId:'nabo',state:'GROWING',watered:true,season:1,seasons:1,dry:false,paused:false,weeds:false,insects:0,remainingSeconds:60000,nominalRemainingSeconds:36000};
 h.health(tile,{x:585,y:400},'tap');
 assert.match(text(panel),/Com rega em dia e sem pragas: 10 h restantes/);
 assert.match(text(panel),/Sem nova rega e sem pragas: 16 h 40 min restantes/);
 assert.ok(text(panel).includes(new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(Date.parse('2026-11-03T17:40:00Z'))));
 const estimate=panel.querySelector('.crop-growth-estimate').textContent;
 const nominal=panel.querySelector('.crop-growth-nominal').textContent;
 assert.ok(nominal.includes(new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(Date.parse('2026-11-03T11:00:00Z'))));
 clientNow+=60000;h.runTimeouts();
 assert.ok(panel.querySelector('.crop-growth-estimate').textContent.endsWith(estimate.slice(estimate.indexOf('Previsão:'))));
 assert.match(text(panel),/Com rega em dia e sem pragas: 9 h 59 min restantes/);
 assert.ok(panel.querySelector('.crop-growth-nominal').textContent.endsWith(nominal.slice(nominal.indexOf('Previsão:'))));
 h.set({own:{...farm('alice'),level:1,serverTime:'2026-11-03T01:02:00Z'},catalog:{crops:[crop],animals:[],fertilizers:[],vip:{greenPrice:20}}});
 h.health(tile,{x:585,y:400},'refresh');
 assert.ok(text(panel).includes(new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(Date.parse('2026-11-03T17:42:00Z'))));
 h.health({...tile,dry:true},{x:585,y:400},'refresh');
 clientNow+=120000;h.runTimeouts();
 assert.ok(panel.querySelector('.crop-growth-nominal').textContent.includes(new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(Date.parse('2026-11-03T11:03:00Z'))));
});

test('login can return to its first step before the invitation fade completes',()=>{
 const {h,ctx}=app(),login=ctx.document.getElementById('login-step'),invite=ctx.document.getElementById('invite-step');invite.hidden=true;
 h.setLoginStep('invite','Bob');assert.equal(invite.hidden,false);
 h.setLoginStep('login');assert.equal(login.hidden,false);assert.equal(invite.hidden,true);
});

test('action notice is docked above the actual toolbar and reserves room below the item tray',()=>{
 const {h,elements,ctx}=app();h.set({own:farm('alice')});ctx.window.innerHeight=844;
 ctx.document.getElementById('game').getBoundingClientRect=()=>({top:0,height:844});
 ctx.document.getElementById('tools-bar').getBoundingClientRect=()=>({top:734,height:110});
 ctx.document.getElementById('farm-header').getBoundingClientRect=()=>({top:8,height:132});
 ctx.document.getElementById('notice').getBoundingClientRect=()=>({height:80});
 ctx.document.getElementById('app');
 h.notify('Compre primeiro o próximo lote indicado.');
 assert.equal(elements.get('app').style['--notice-bottom'],'122px');
 assert.equal(elements.get('app').style['--tools-top'],'110px');
 assert.equal(elements.get('app').style['--notice-height'],'80px');
 assert.equal(elements.get('app').style['--hud-bottom'],'140px');
 h.notifyAction('Novo nível','HARVEST',undefined,{coins:1,greens:1,xp:1,level:2,leveledUp:true});
 assert.equal(elements.get('app').style['--notice-bottom'],'122px');
 assert.equal(elements.get('app').style['--notice-height'],'80px');
});

test('settings offer avatar selection without a duplicate list of land plots',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});h.settings();
 assert.match(text(elements.get('modal-content')),/Trocar avatar/);
 assert.doesNotMatch(text(elements.get('modal-content')),/Lista de terras/);
});

test('settings render the persisted owner photo without sending a referrer',()=>{
 const {h,elements}=app();h.set({own:{...farm('alice'),avatar:{id:'google',photoUrl:'https://lh3.googleusercontent.com/a/owner-photo'}}});h.settings();
 const avatar=descendants(elements.get('modal-content')).find(node=>node.className==='profile-avatar');
 assert.equal(avatar.src,'https://lh3.googleusercontent.com/a/owner-photo');assert.equal(avatar.referrerPolicy,'no-referrer');
});

test('out-of-order refresh responses with equal revisions keep the newest projection in the UI',async()=>{
 const {h}=app(),first=deferred(),second=deferred();let reads=0;
 const initial={...farm('alice'),revision:'7',serverTime:'2026-11-01T12:00:00Z'};
 const older={...initial,serverTime:'2026-11-01T12:01:00Z'},newer={...initial,serverTime:'2026-11-01T12:02:00Z'};
 h.set({own:initial,api:{pending:null,get:()=>++reads===1?first.promise:second.promise,prepareRequestId:async()=>{}}});
 const slow=h.refresh(),fast=h.refresh();second.resolve(newer);await fast;first.resolve(older);await slow;
 assert.equal(h.get().own,newer);
});

function marketFixture(h){
 const crops=Array.from({length:20},(_,i)=>({id:`crop${i}`,name:`Planta ${i}`,spriteStem:'nabo',growthSeconds:3600,seasons:1,requiredLevel:1,seedPrices:{GOLD:10},vipOnly:false}));
 const own={...farm('alice'),level:1,coins:'500',greenCoins:'5',currentXp:'0',xpToNext:'25'};
 h.set({own,catalog:{crops,animals:[],fertilizers:[],feedPrices:{producer:120,growth:80},vip:{greenPrice:100}}});
 return {own,crops};
}

test('shop refresh retains nested and outer scroll and the focused purchase without sending it',()=>{
 const {h,elements,ctx}=app(),{own}=marketFixture(h);h.shop();
 const content=elements.get('modal-content'),modal=elements.get('modal'),scroll=content.querySelector('.market-scroll');
 const buy=descendants(content).find(node=>node.getAttribute('aria-label')==='Comprar Planta 19 por 10 ouro');
 buy.focus();scroll.scrollTop=650;scroll.scrollLeft=20;content.scrollTop=80;modal.scrollTop=30;
 h.receive({farm:{...own,revision:'2',coins:'510'}});
 assert.equal(content.querySelector('.market-scroll').scrollTop,650);
 assert.equal(content.querySelector('.market-scroll').scrollLeft,20);
 assert.equal(content.scrollTop,80);assert.equal(modal.scrollTop,30);
 assert.notEqual(ctx.document.activeElement,buy);
 assert.equal(ctx.document.activeElement.getAttribute('aria-label'),'Comprar Planta 19 por 10 ouro');
 assert.equal(h.get().own.coins,'510');
});

test('a disabled purchase moves focus to Close instead of another product',()=>{
 const {h,elements,ctx}=app(),{own}=marketFixture(h);h.shop();
 descendants(elements.get('modal-content')).find(node=>node.getAttribute('aria-label')==='Comprar Planta 19 por 10 ouro').focus();
 h.receive({farm:{...own,revision:'2',level:0}});
 assert.equal(ctx.document.activeElement,elements.get('close-modal'));
});

test('refresh keeps focus on the VIP seed filter instead of the different VIP shop tab',()=>{
 const {h,elements,ctx}=app(),{own}=marketFixture(h);h.shop();const content=elements.get('modal-content');
 const seedFilter=()=>descendants(content).find(node=>node.className==='tabs seed-tabs').children.find(node=>node.textContent==='VIP');
 seedFilter().onclick();seedFilter().focus();h.receive({farm:{...own,revision:'2'}});
 assert.equal(ctx.document.activeElement,seedFilter());
});

test('explicit category navigation starts at the top and a closed panel stays closed on refresh',()=>{
 const {h,elements}=app(),{own}=marketFixture(h);h.shop();
 const content=elements.get('modal-content');content.scrollTop=80;
 descendants(content).find(node=>node.textContent==='Animais').onclick();
 assert.equal(content.scrollTop,0);assert.equal(content.querySelector('.market-scroll').scrollTop,0);
 elements.get('modal').close();h.receive({farm:{...own,revision:'2'}});
 assert.equal(elements.get('modal').open,false);
});

test('selling a product preserves the deposit position and focus until that product disappears',async()=>{
 const {h,elements,ctx}=app(),{own}=marketFixture(h);
 const stocked={...own,produceLots:[{itemId:'crop0',quantity:2,currency:'GOLD'}],depositTotals:{GOLD:23}};
 const reply=deferred();let commands=0;
 h.set({own:stocked,catalog:{crops:[{id:'crop0',name:'Nabo',spriteStem:'nabo',vipOnly:false}],animals:[],fertilizers:[],vip:{greenPrice:100}},api:{pending:null,mutate:()=>{commands++;return reply.promise;},prepareRequestId:async()=>{}}});
 h.receive({});elements.get('deposit').onclick();const content=elements.get('modal-content');
 const sell=descendants(content).find(node=>node.getAttribute('aria-label')?.startsWith('Vender 1 Nabo'));
 sell.focus();content.scrollTop=180;sell.onclick();
 reply.resolve({farm:{...stocked,revision:'2',produceLots:[{itemId:'crop0',quantity:1,currency:'GOLD'}],depositTotals:{GOLD:12}},result:{success:false,message:'synthetic update'}});
 await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(commands,1);assert.equal(content.scrollTop,180);
 assert.ok(ctx.document.activeElement.getAttribute('aria-label').startsWith('Vender 1 Nabo'));
 h.receive({farm:{...stocked,revision:'3',produceLots:[],depositTotals:{}}});
 assert.equal(ctx.document.activeElement,elements.get('close-modal'));
});

test('deposit sells one eligible item instead of requesting all VIP-mixed stock',()=>{
 const {h,elements}=app();let command;
 const own={...farm('alice'),produceLots:[{itemId:'nabo',quantity:1,currency:'GOLD',unitCost:10},{itemId:'nabo',quantity:1,currency:'GOLD',unitCost:10,vipOnly:true}]};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',vipOnly:false}],animals:[],fertilizers:[],vip:{greenPrice:100}},api:{pending:null,mutate:(_path,fields)=>{command=fields;return new Promise(()=>{});},prepareRequestId:async()=>{}}});
 elements.get('deposit').onclick();
 const single=descendants(elements.get('modal-content')).find(node=>node.getAttribute('data-focus-key')==='sell-one:nabo');single.onclick();
 assert.equal(command.action,'SELL_ITEM');
});

test('deposit disables product bulk sale when some lots require VIP',()=>{
 const {h,elements}=app();
 h.set({own:{...farm('alice'),produceLots:[{itemId:'nabo',quantity:2,currency:'GOLD',unitCost:10},{itemId:'nabo',quantity:1,currency:'GOLD',unitCost:10,vipOnly:true}]},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',vipOnly:false}],animals:[],fertilizers:[],vip:{greenPrice:100}}});
 elements.get('deposit').onclick();
 const bulk=descendants(elements.get('modal-content')).find(node=>node.getAttribute('data-focus-key')==='sell-product:nabo');
 assert.equal(bulk.disabled,true);
});

test('deposit uses server crop quotes instead of applying acquisition multipliers',()=>{
 const {h,elements}=app();
 const own={...farm('alice'),vipActive:true,
  produceLots:[{itemId:'coco',quantity:6,currency:'GREEN',unitCost:20,unitSale:'5.5',vipOnly:true}],
  depositTotals:{GREEN:'33.0'},itemSaleTotals:{coco:{GREEN:'33.0'}},itemSaleSingles:{coco:{GREEN:'5.5'}}};
 h.set({own,catalog:{crops:[{id:'coco',name:'Coco',spriteStem:'coco',vipOnly:true}],animals:[],fertilizers:[],vip:{greenPrice:100}}});
 elements.get('deposit').onclick();
 const single=descendants(elements.get('modal-content')).find(node=>node.getAttribute('data-focus-key')==='sell-one:coco');
 assert.match(single.title,/5,5 verdes/);
 const bulk=descendants(elements.get('modal-content')).find(node=>node.getAttribute('data-focus-key')==='sell-product:coco');
 assert.match(bulk.title,/33/);
 assert.ok(!text(elements.get('modal-content')).includes('150'));
});

test('mixed legacy and v1 deposit follows server quotes when VIP expires and renews',()=>{
 const {h,elements}=app();
 const lots=[{itemId:'melancia',quantity:2,currency:'GOLD',unitCost:1975,unitSale:'2271.25'},
  {itemId:'melancia',quantity:1,currency:'GOLD',unitCost:1975,unitSale:'1204.4'},
  {itemId:'coco',quantity:1,currency:'GREEN',unitCost:20,unitSale:'5.5',vipOnly:true}];
 const own={...farm('alice'),vipActive:false,produceLots:lots,depositTotals:{GOLD:'5746.9'},
  itemSaleTotals:{melancia:{GOLD:'5746.9'},coco:{}},itemSaleSingles:{melancia:{GOLD:'2271.3'},coco:{}}};
 h.set({own,catalog:{crops:[{id:'melancia',name:'Melancia',spriteStem:'melancia'},{id:'coco',name:'Coco',spriteStem:'coco',vipOnly:true}],animals:[],fertilizers:[],vip:{greenPrice:100}}});
 const control=key=>descendants(elements.get('modal-content')).find(node=>node.getAttribute('data-focus-key')===key);
 elements.get('deposit').onclick();
 assert.match(control('sell-one:melancia').title,/2.271,3 ouro/);
 assert.match(control('sell-product:melancia').title,/5.746,9 ouro/);
 assert.equal(control('sell-one:coco').disabled,true);
 h.receive({farm:{...own,revision:'2',vipActive:true,
  produceLots:[{...lots[0],unitSale:'2468.75'},{...lots[1],unitSale:'1270.3'},lots[2]],
  depositTotals:{GOLD:'6207.8',GREEN:'5.5'},itemSaleTotals:{melancia:{GOLD:'6207.8'},coco:{GREEN:'5.5'}},
  itemSaleSingles:{melancia:{GOLD:'2468.8'},coco:{GREEN:'5.5'}}}});
 assert.match(control('sell-one:melancia').title,/2.468,8 ouro/);
 assert.match(control('sell-product:melancia').title,/6.207,8 ouro/);
 assert.equal(control('sell-one:coco').disabled,false);
 assert.match(control('sell-one:coco').title,/5,5 verdes/);
});

test('late sale confirmation does not reopen a deposit that the player closed',async()=>{
 const {h,elements}=app(),{own}=marketFixture(h),reply=deferred();
 h.set({own:{...own,produceLots:[],depositTotals:{GOLD:12}},api:{pending:null,mutate:()=>reply.promise,prepareRequestId:async()=>{}}});
 elements.get('deposit').onclick();
 descendants(elements.get('modal-content')).find(node=>node.getAttribute('aria-label')?.startsWith('Vender tudo,')).onclick();
 elements.get('modal').close();reply.resolve({farm:{...own,revision:'2'},result:{success:false,message:'synthetic update'}});
 await new Promise(resolve=>setTimeout(resolve,0));assert.equal(elements.get('modal').open,false);
});

test('active wallet uses original coin icons, exact XP and the public owner balance during a visit',()=>{
 const {h,elements}=app(),own={...farm('alice'),name:'Uma fazenda com um nome de 40 caracteres!',level:2,currentXp:'9007199254740993.1',xpToNext:'18014398509481986.2',coins:'9007199254740993.1',greenCoins:'5'};
 h.set({own,visited:{...farm('bob'),name:'Amigo',level:own.level,currentXp:own.currentXp,xpToNext:own.xpToNext,coins:own.coins,greenCoins:own.greenCoins}});h.receive({});
 const nodes=descendants(elements.get('wallet'));
 assert.ok(nodes.some(n=>n.tag==='img'&&n.src?.endsWith('moeda-comum.png')));
 assert.ok(nodes.some(n=>n.tag==='img'&&n.src?.endsWith('moeda-verde.png')));
 assert.ok(nodes.some(n=>n.textContent==='Visitando Amigo'));
 assert.ok(nodes.some(n=>n.textContent==='9007199254740993.1'));
 assert.equal(nodes.find(n=>n.tag==='progress').value,50);
 assert.equal(elements.get('home').hidden,false);
});

test('fertilizer tool shows the selected stock and keyboard land access opens the existing list',()=>{
 const {h,elements,listeners}=app();h.set({own:{...farm('alice'),fertilizers:{comum:7},tiles:[{column:0,row:0,unlocked:true,state:'GRASS'}]}});h.receive({});
 const tools=elements.get('tools').children;assert.equal(text(tools.at(-1)).includes('x7'),true);
 h.pickTool('FERTILIZE');
 assert.match(elements.get('farm').style.cursor,/cursors\/adubo-comum.png/);
 listeners.get('keydown')({key:'t',target:{}});assert.equal(elements.get('modal-title').textContent,'Terras da fazenda');
 assert.match(text(elements.get('modal-content')),/Terra 1, 1: grama/);
});

test('scene pointer follows tools and selected fertilizer across visits and returning home',()=>{
 const {h,elements}=app(),initial={...farm('alice'),fertilizers:{rapido:3}};
 h.set({own:initial,catalog:{crops:[],fertilizers:[{id:'rapido',name:'Rápido'},{id:'amor',friendsOnly:true}],animals:[]}});h.receive({});
 for(const [tool,icon]of [['PLOW','pa-ui'],['PLANT','saco-ui'],['WATER','regador-ui'],['WEED','herbicida-ui'],['PESTICIDE','pesticida-ui'],['HARVEST','mao-restaurada']]){
  h.pickTool(tool);assert.equal(elements.get('farm').style.cursor,`url("/assets/classic/icons/cursors/${icon}.png") 16 16, auto`);
 }
 h.pickTool('FERTILIZE');descendants(elements.get('tool-tray')).find(node=>node.getAttribute('aria-label')==='Rápido, 3 no estoque').onclick();
 assert.match(elements.get('farm').style.cursor,/adubo-rapido.png/);
 h.set({own:initial,visited:farm('bob')});h.receive({});assert.match(elements.get('farm').style.cursor,/cursor-action.png/);
 h.pickTool('LOVE');assert.match(elements.get('farm').style.cursor,/adubo-amor.png/);
 h.set({own:initial});h.receive({});assert.equal(elements.get('farm').style.cursor,'url("/assets/classic/icons/cursor-action.png") 1 1, auto');
 assert.equal(elements.get('tools').children[0].children[0].src,'/assets/classic/icons/hud/cursor-ui.png');
});

test('resize observer repositions the active tooltip and disconnects when the session closes',async()=>{
 const {h,elements,ctx,observers}=app();
 ctx.Scene=class{update(){}close(){}};
 ctx.ApiClient=class{pending=null;get(){return Promise.resolve(farm('alice'));}prepareRequestId(){return Promise.resolve();}};
 h.set({own:undefined});await h.connect('alice',async()=> 'synthetic-token');h.set({own:h.get().own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[],vip:{greenPrice:20}}});
 let gameBounds={left:380,top:33.75,width:1800,height:1012.5},canvasBounds={...gameBounds};
 elements.get('game').getBoundingClientRect=()=>gameBounds;elements.get('farm').getBoundingClientRect=()=>canvasBounds;
 const panel=elements.get('plant-health');panel.hidden=true;panel.getBoundingClientRect=()=>({width:330,height:160});
 h.health({cropId:'nabo',state:'GROWING',watered:true,season:1,seasons:1,remainingSeconds:60,nominalRemainingSeconds:60},{x:585,y:400});
 h.runTimeouts();
 const projected=presentation.worldToScreen({x:585,y:400},presentation.sceneTransform(canvasBounds.width,canvasBounds.height));
 assert.equal(panel.style.left,`${projected.x-180}px`);
 gameBounds={left:0,top:0,width:390,height:844};canvasBounds={left:8,top:365.4375,width:374,height:210.375};observers[0].callback();assert.equal(panel.style.left,'12px');assert.ok(Number.parseFloat(panel.style.top)>=12&&Number.parseFloat(panel.style.top)<844);
 await h.logout();assert.equal(observers[0].disconnected,true);assert.equal(panel.hidden,true);
});

test('crop tooltip follows contain projection, repositions unchanged content and stays inside the game',()=>{
 const {h,elements,ctx}=app();h.set({own:farm('alice'),catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[],vip:{greenPrice:20}}});
 let gameBounds={left:380,top:33.75,width:1800,height:1012.5},canvasBounds={...gameBounds};
 ctx.document.getElementById('game').getBoundingClientRect=()=>gameBounds;ctx.document.getElementById('farm').getBoundingClientRect=()=>canvasBounds;
 const panel=ctx.document.getElementById('plant-health');panel.hidden=true;panel.getBoundingClientRect=()=>({width:330,height:160});
 const tile={cropId:'nabo',stage:1,state:'GROWING',watered:true,season:1,seasons:1,remainingSeconds:3600,nominalRemainingSeconds:3600};
 h.health(tile,{x:585,y:400});
 h.runTimeouts();
 const projected=presentation.worldToScreen({x:585,y:400},presentation.sceneTransform(canvasBounds.width,canvasBounds.height));
 assert.equal(panel.style.left,`${projected.x-180}px`);assert.equal(panel.style.top,`${projected.y-160-54-12}px`);
 gameBounds={left:0,top:0,width:390,height:844};canvasBounds={left:8,top:365.4375,width:374,height:210.375};h.health(tile,{x:585,y:400});
 assert.equal(panel.style.left,'12px');assert.ok(Number.parseFloat(panel.style.top)>=12&&Number.parseFloat(panel.style.top)<844);
 h.health(tile,{x:1600,y:900});assert.equal(panel.style.left,'18px');assert.ok(Number.parseFloat(panel.style.top)>=12&&Number.parseFloat(panel.style.top)<844);
 h.dialog('Loja');assert.equal(panel.hidden,true);
});
test('late visit response cannot reopen a visit after returning home',async()=>{
 const {h,elements}=app(),started=deferred(),reply=deferred();
  h.set({own:farm('alice'),visited:farm('bob'),api:{pending:null,prepareRequestId:async()=>{},get:path=>path==='/v1/me/farm'?Promise.resolve(farm('alice')):(started.resolve(),reply.promise)}});
 const refresh=h.refresh();await started.promise;elements.get('home').onclick();reply.resolve(farm('bob'));await refresh;
 assert.equal(h.get().visited,undefined);
});
test('late retry response cannot restore a logged-out farm',async()=>{
 const {h,elements}=app(),reply=deferred();
 h.set({own:farm('alice'),api:{pending:{},retry:()=>reply.promise}});
 const retry=elements.get('retry').onclick();await h.logout();reply.resolve({farm:farm('alice')});await retry;
 assert.equal(h.get().own,undefined);assert.equal(elements.get('game').hidden,true);
});
test('music continues while hidden and pauses on logout',async()=>{
 const {h,listeners,ctx,audioClips}=app();h.set({own:farm('alice')});h.toggleMusic();const music=audioClips[0];assert.equal(music.playCount,1);
 ctx.document.hidden=true;listeners.get('visibilitychange')();assert.equal(music.paused,false);
 ctx.document.hidden=false;listeners.get('visibilitychange')();assert.equal(music.playCount,1);
 await h.logout();assert.equal(music.paused,true);
});
test('open owned-animal screen refreshes after a successful state update',()=>{
 const {h,elements}=app(),farmState={...farm('alice'),level:1,feed:{producer:1},animals:[{id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0}]};
 h.set({own:farmState,catalog:{crops:[],fertilizers:[],feedPrices:{producer:10,growth:10},animals:[{id:'vaca',name:'Vaca',kind:'PRODUCER',productName:'Leite'}],vip:{greenPrice:20}}});
 h.shop('owned');assert.match(text(elements.get('modal-content')),/Coletar/);
 h.receive({farm:{...farmState,revision:'2',animals:[{id:'vaca',stage:'ADULT',status:'PRODUCING',remainingSeconds:3600}]}});
 assert.doesNotMatch(text(elements.get('modal-content')),/Coletar/);assert.match(text(elements.get('modal-content')),/Produzindo/);
});
test('protector popup shows remaining feeding progress at 6h, 3h and hunger',()=>{
 const {h,elements}=app(),catalog={crops:[],fertilizers:[],animals:[{id:'cachorro-robo',name:'Cachorro',kind:'PROTECTOR'}],vip:{greenPrice:20}};
 for(const [status,remaining,expected]of [['PRODUCING',21600,100],['PRODUCING',10800,50],['HUNGRY',0,0]]){
  h.set({own:{...farm('alice'),animals:[{id:'cachorro-robo',stage:'ADULT',status,remainingSeconds:remaining}]},catalog});
  h.animalPopup('cachorro-robo');
  const bar=descendants(elements.get('modal-content')).find(node=>node.getAttribute('role')==='progressbar');
  assert.ok(bar);assert.equal(bar.getAttribute('aria-valuenow'),String(expected));
  assert.equal(bar.getAttribute('aria-label'),`Alimentação: ${expected}%`);
  assert.equal(bar.children[0].style.width,`${expected}%`);
 }
});
test('owned animals show protector feeding progress',()=>{
 const {h,elements}=app();
 h.set({own:{...farm('alice'),feed:{producer:0,growth:0},animals:[{id:'cachorro-robo',stage:'ADULT',status:'PRODUCING',remainingSeconds:10800}]},catalog:{crops:[],fertilizers:[],feedPrices:{producer:10,growth:10},animals:[{id:'cachorro-robo',name:'Cachorro',kind:'PROTECTOR'}],vip:{greenPrice:20}}});
 h.shop('owned');
 const card=descendants(elements.get('modal-content')).find(node=>node.className==='owned-animal-card');
 assert.ok(card);assert.ok(text(card).includes('ALIMENTAÇÃO'));assert.ok(text(card).includes('3h 0min restantes'));
 const bar=descendants(card).find(node=>node.getAttribute('role')==='progressbar');
 assert.ok(bar);assert.equal(bar.getAttribute('aria-valuenow'),'50');assert.equal(bar.children[0].style.width,'50%');
});
test('open missions screen refreshes progress after a farm update',()=>{
 const {h,elements}=app(),farmState={...farm('alice'),missions:[{id:'m',title:'Colher',green:1,progress:1,target:2,done:false}]};
 h.set({own:farmState});h.missions();assert.match(text(elements.get('modal-content')),/1 \/ 2/);
 h.receive({farm:{...farmState,revision:'2',missions:[{...farmState.missions[0],progress:2,done:true}]}});
 assert.match(text(elements.get('modal-content')),/Concluídas hoje \(1\)/);assert.match(text(elements.get('modal-content')),/Colher/);
});
test('late invitation acceptance does not close a newer dialog',async()=>{
 const {h,elements}=app(),reply=deferred();h.set({own:farm('alice'),api:{pending:null,mutate:()=>reply.promise,get:()=>Promise.resolve(farm('alice'))}});
 h.dialog('Amigos');const acceptance=h.acceptInvitation('invite',h.dialogGeneration(),true);h.dialog('Loja');reply.resolve({});await acceptance;
 assert.equal(elements.get('modal').open,true);assert.equal(elements.get('modal-title').textContent,'Loja');
});
test('late farm visit neither starts nor closes a newer dialog',async()=>{
 const {h,elements}=app(),reply=deferred();h.set({own:farm('alice'),api:{pending:null,get:path=>path==='/v1/friends'?Promise.resolve([{id:'bob',name:'Bob'}]):reply.promise}});
 await h.friends();descendants(elements.get('modal-content')).find(node=>node.textContent==='Visitar fazenda').onclick();h.dialog('Loja');reply.resolve(farm('bob'));
 await new Promise(resolve=>setTimeout(resolve,0));assert.equal(h.get().visited,undefined);assert.equal(elements.get('modal').open,true);assert.equal(elements.get('modal-title').textContent,'Loja');
});
test('closed canvas scene removes previous event handlers',()=>{
 const handlers=new Map(),calls=[];
 const canvas={width:1600,height:900,getContext:()=>({}),getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler,options){const list=handlers.get(name)??[];list.push(handler);handlers.set(name,list);options?.signal?.addEventListener('abort',()=>handlers.set(name,handlers.get(name).filter(h=>h!==handler)));}};
 const cancelled=[];const ctx={...presentation,window:{requestAnimationFrame:()=>1,cancelAnimationFrame:id=>cancelled.push(id)},document:{hidden:true},AbortController,pickTile:()=>({column:0,row:0})};
 let source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.TestScene=Scene;',ctx);
 for(const revision of ['1','2']){const scene=new ctx.TestScene(canvas,{},tile=>calls.push(tile.harvestRevision),()=>{},()=>{});scene.update({...farm('alice'),tiles:[{column:0,row:0,harvestRevision:revision}]});if(revision==='1')scene.close();}
 assert.deepEqual(cancelled,[1]);
 for(const handler of handlers.get('click'))handler({clientX:585,clientY:400});assert.deepEqual(calls,['2']);
});

test('Christmas music switches without overlapping and returns to normal during the same night',()=>{
 const {h,audioClips}=app();h.set({own:farm('alice')});h.toggleMusic();
 h.syncMusic('day',new Date('2026-12-24T15:00:00Z'));const day=audioClips.at(-1);
 assert.equal(day.path,'/assets/sounds/musica-evento-natal-dia-e-por-do-sol.mp3');
 h.syncMusic('sunset',new Date('2026-12-24T20:00:00Z'));assert.equal(audioClips.at(-1),day);
 h.syncMusic('night',new Date('2027-01-01T02:59:59Z'));const night=audioClips.at(-1);
 assert.equal(night.path,'/assets/sounds/musica-evento-natal-noite.mp3');assert.equal(day.paused,true);
 h.syncMusic('night',new Date('2027-01-01T03:00:00Z'));const normal=audioClips.at(-1);
 assert.equal(normal.path,'/assets/sounds/noite.mp3');assert.equal(night.paused,true);assert.equal(normal.loop,true);
 assert.equal(audioClips.filter(clip=>!clip.paused).length,1);
 h.toggleMusic();assert.ok(audioClips.every(clip=>clip.paused));
});

test('night music follows scenery changes, loops and pauses the previous track',()=>{
 const {h,ctx,audioClips}=app();h.set({own:farm('alice')});h.toggleMusic();
 h.syncMusic('day',new Date('2026-01-01T12:00:00Z'));const day=audioClips.at(-1);assert.equal(day.path,'/assets/sounds/farm_loop.mp3');
 h.syncMusic('night',new Date('2026-01-01T12:00:00Z'));const night=audioClips.at(-1);assert.equal(night.path,'/assets/sounds/noite.mp3');assert.equal(day.paused,true);assert.equal(night.loop,true);assert.equal(night.volume,.25);
 const count=audioClips.length;h.syncMusic('night',new Date('2026-01-01T12:00:00Z'));assert.equal(audioClips.length,count);assert.equal(night.playCount,1);
 h.syncMusic('sunset',new Date('2026-01-01T12:00:00Z'));assert.equal(night.paused,true);assert.equal(audioClips.at(-1).path,'/assets/sounds/farm_loop.mp3');assert.equal(audioClips.filter(clip=>!clip.paused).length,1);
 h.toggleMusic();h.syncMusic('night',new Date('2026-01-01T12:00:00Z'));assert.ok(audioClips.every(clip=>clip.paused));
 ctx.document.hidden=true;h.toggleMusic();h.syncMusic('night',new Date('2026-01-01T12:00:00Z'));
 assert.equal(audioClips.filter(clip=>['farm_loop.mp3','noite.mp3','musica-evento-natal-dia-e-por-do-sol.mp3','musica-evento-natal-noite.mp3'].some(path=>clip.path.endsWith('/'+path))&&!clip.paused).length,1);
});

test('background music and ambience continue when the tab is hidden',()=>{
 const {h,listeners,ctx,audioClips}=app();h.set({own:farm('alice')});h.toggleMusic();h.toggleEffects();
 h.syncMusic('day',new Date('2026-06-01T12:00:00Z'));
 const musicPaths=['farm_loop.mp3','noite.mp3','musica-evento-natal-dia-e-por-do-sol.mp3','musica-evento-natal-noite.mp3'];
 assert.ok(audioClips.some(clip=>clip.path.endsWith('/ventos-leves-ambientacao.mp3')));ctx.document.hidden=true;listeners.get('visibilitychange')();
 assert.equal(audioClips.filter(clip=>musicPaths.some(path=>clip.path.endsWith('/'+path))&&!clip.paused).length,1);
 assert.ok(audioClips.some(clip=>clip.path.endsWith('-ambientacao.mp3')&&!clip.paused));
});

test('muting, hiding and logging out pause every active one-shot effect',async()=>{
 const {h,listeners,ctx,audioClips}=app();
 h.set({own:farm('alice')});h.toggleEffects();
 const start=()=>{h.playEffect('regador');h.playEffect('furto');return audioClips.slice(-2);};
 let clips=start();h.toggleEffects();assert.ok(clips.every(clip=>clip.paused));
 h.toggleEffects();clips=start();ctx.document.hidden=true;listeners.get('visibilitychange')();assert.ok(clips.every(clip=>clip.paused));
 ctx.document.hidden=false;clips=start();await h.logout();assert.ok(clips.every(clip=>clip.paused));
});

test('ambience follows the scenery and pauses the previous period',()=>{
 const {h,audioClips}=app();h.set({own:farm('alice')});h.toggleEffects();h.syncMusic('day');
 const day=audioClips.filter(clip=>!clip.paused);
 assert.deepEqual(day.map(clip=>clip.path.split('/').pop()),['ventos-leves-ambientacao.mp3','passaros-ambientacao.mp3']);
 h.syncMusic('night');assert.ok(day.every(clip=>clip.paused));
 const night=audioClips.at(-1);assert.match(night.path,/grilos-ambientacao/);assert.equal(night.loop,true);assert.equal(night.paused,false);
});

test('several newly ready plants play one chime and a repeated projection plays none',()=>{
 const {h,audioClips}=app();
 const tiles=[0,1].map(column=>({column,row:0,cropId:'nabo',season:1,state:'GROWING'}));
 const own={...farm('alice'),tiles};h.set({own});h.toggleEffects();
 const ready={...own,tiles:tiles.map(tile=>({...tile,state:'READY'}))};
 h.receive({farm:ready});h.receive({farm:ready});
 assert.equal(audioClips.filter(clip=>clip.path.endsWith('/planta-pronta-para-colher.wav')).length,1);
});

test('locked land requires confirmation and guards duplicate clicks',async()=>{
 const {h,elements}=app(),sent=[],reply=deferred(),own={...farm('alice'),level:5,coins:'900'};
 h.set({own,api:{pending:null,mutate:(_path,command)=>{sent.push(command);return reply.promise;},prepareRequestId:async()=>{}}});
 const tile={column:2,row:1,unlocked:false,requiredLevel:5,price:350,currency:'GOLD',purchasable:true};
 await h.tileAction(tile);assert.equal(sent.length,0);assert.match(text(elements.get('modal-content')),/3, 2/);assert.match(text(elements.get('modal-content')),/350 ouro/);assert.match(text(elements.get('modal-content')),/900 ouro/);
 descendants(elements.get('modal-content')).find(n=>n.textContent==='Cancelar').onclick();assert.equal(sent.length,0);
 await h.tileAction(tile);const escaped=descendants(elements.get('modal-content')).find(n=>n.textContent==='Confirmar compra');elements.get('modal').close();escaped.onclick();assert.equal(sent.length,0);await h.tileAction(tile);const confirm=descendants(elements.get('modal-content')).find(n=>n.textContent==='Confirmar compra');confirm.onclick();confirm.onclick();assert.equal(sent.length,1);assert.equal(sent[0].action,'BUY_PLOT');
 reply.resolve({farm:{...own,revision:'2'},result:{success:true,message:'Comprada'}});await new Promise(r=>setTimeout(r,0));
});
test('visited wallet uses public owner profile without avatar picker',()=>{
 const {h,elements}=app();h.set({own:farm('alice'),visited:{...farm('bob'),avatar:{id:'female-2'},level:8,currentXp:'30',xpToNext:'80',coins:'700'}});h.receive({});const wallet=elements.get('wallet');assert.match(text(wallet),/Nível 8/);assert.match(text(wallet),/700/);assert.equal(descendants(wallet).some(n=>n.className==='avatar-picker'),false);assert.equal(descendants(wallet).find(n=>n.className==='avatar').src,'/assets/classic/icons/avatar_feminino2.png');
});
test('avatar selection follows persisted server response',async()=>{
 const {h,elements}=app(),own=farm('alice'),sent=[];h.set({own,api:{pending:null,mutate:async(_path,command)=>{sent.push(command);return {farm:{...own,revision:'2',avatar:{id:command.itemId},avatarSelected:true},result:{success:true,message:'Salvo'}};},prepareRequestId:async()=>{}}});h.avatarSettings();descendants(elements.get('modal-content')).find(n=>n.textContent==='Feminino 3').onclick();await new Promise(r=>setTimeout(r,0));assert.equal(sent[0].action,'SET_AVATAR');assert.equal(descendants(elements.get('wallet')).find(n=>n.className==='avatar').src,'/assets/classic/icons/avatar_feminino3.png');
});

test('login imports existing browser choice only when server has no selection',async()=>{
 for(const selected of [false,true,undefined]){
  const {h,ctx}=app(new Map([['colheita:avatar:undefined:alice','female-1']])),sent=[];const own={...farm('alice'),avatarSelected:selected,avatar:{id:'male-2'}};
  ctx.Scene=class{update(){}close(){}};ctx.ApiClient=class{pending=null;get(){return Promise.resolve(own);}prepareRequestId(){return Promise.resolve();}mutate(_path,command){sent.push(command);return Promise.resolve({farm:{...own,revision:'2',avatarSelected:true,avatar:{id:command.itemId}},result:{success:true,message:'Importado'}});}};
  h.set({own:undefined});await h.connect('alice',async()=> 'token');assert.equal(sent.length,selected===false?1:0);if(sent.length){assert.equal(sent[0].action,'IMPORT_AVATAR');assert.equal(sent[0].itemId,'female-1');}
 }
});
test('fertilizer shop renders every actual catalog duration in hours and minutes',()=>{
 const {h,elements}=app(),fertilizers=JSON.parse(readFileSync(new URL('../../assets/data/fertilizers.json',import.meta.url),'utf8'));
 h.set({own:{...farm('alice'),level:50},catalog:{crops:[],animals:[],fertilizers,vip:{greenPrice:10}}});h.shop('fertilizers');const caption=text(elements.get('modal-content'));for(const fertilizer of fertilizers)assert.ok(caption.includes('Reduz '+uiPresentation.fertilizerDuration(fertilizer.reductionSeconds)));
});

test('late avatar response preserves a closed or replaced dialog',async()=>{
 for(const close of [true,false]){
  const {h,elements}=app(),own=farm('alice'),reply=deferred();h.set({own,api:{pending:null,mutate:()=>reply.promise,prepareRequestId:async()=>{}}});h.avatarSettings();descendants(elements.get('modal-content')).find(n=>n.textContent==='Feminino 3').onclick();
  if(close)elements.get('modal').close();else h.dialog('Outro painel');
  reply.resolve({farm:{...own,revision:'2',avatar:{id:'female-3'},avatarSelected:true},result:{success:true,message:'Salvo'}});await new Promise(r=>setTimeout(r,0));
  assert.equal(elements.get('modal').open,!close);assert.equal(elements.get('modal-title').textContent,close?'Seu avatar':'Outro painel');
 }
});
test('late avatar response cannot open settings in a new session',async()=>{
 const {h,elements,ctx}=app(),own=farm('alice'),reply=deferred();h.set({own,api:{pending:null,mutate:()=>reply.promise,prepareRequestId:async()=>{}}});h.avatarSettings();descendants(elements.get('modal-content')).find(n=>n.textContent==='Feminino 3').onclick();await h.logout();
 ctx.Scene=class{update(){}close(){}};ctx.ApiClient=class{pending=null;get(){return Promise.resolve(farm('bob'));}prepareRequestId(){return Promise.resolve();}};
 await h.connect('bob',async()=> 'token');h.dialog('Painel da nova sessão');reply.resolve({farm:{...own,revision:'2',avatar:{id:'female-3'},avatarSelected:true},result:{success:true,message:'Salvo'}});await new Promise(r=>setTimeout(r,0));
 assert.equal(h.get().own.owner,'bob');assert.equal(elements.get('modal-title').textContent,'Painel da nova sessão');
});

test('VIP purchases require explicit confirmation and reject repeated confirmation',async()=>{
 const {h,elements}=app(),sent=[],pending=deferred();
 h.set({own:{...farm('alice'),vipActive:true,greenCoins:'500'},catalog:{crops:[],animals:[],fertilizers:[],vip:{greenPrice:100},automationServices:[{id:'water',name:'Rega automática',description:'Rega suas plantas.',greenPrice:100,durationDays:30}]},api:{pending:null,mutate:(_path,command)=>{sent.push(command);return pending.promise;},prepareRequestId:async()=>{}}});
 h.shop('vip');descendants(elements.get('modal-content')).find(n=>n.textContent==='Comprar cartão VIP · 100 verdes').onclick();
 assert.equal(sent.length,0);assert.match(text(elements.get('modal-content')),/Saldo: 500 verdes/);
 const confirm=descendants(elements.get('modal-content')).find(n=>n.textContent==='Confirmar compra');confirm.onclick();confirm.onclick();assert.equal(sent.length,1);assert.equal(sent[0].action,'BUY_VIP');
 pending.resolve({});await new Promise(r=>setTimeout(r,0));
 h.shop('vip');descendants(elements.get('modal-content')).find(n=>n.attributes['aria-label']==='Comprar Rega automática por 100 verdes por 30 dias').onclick();assert.match(text(elements.get('modal-content')),/não estende/);descendants(elements.get('modal-content')).find(n=>n.textContent==='Cancelar').onclick();assert.equal(sent.length,1);
});
test('claimed update notes wait for the open dialog and do not repeat, but new day and version do',()=>{
 const {h,elements}=app(),notes={version:'v1',prNumber:42,date:'2026-11-08',items:['Novos cuidados VIP.']};
 h.set({own:farm('alice')});h.dialog('Ajustes');h.receive({farm:{...farm('alice'),revision:'2',updateNotes:notes}});assert.equal(elements.get('modal-title').textContent,'Ajustes');
 elements.get('close-modal').onclick();assert.equal(elements.get('modal-title').textContent,'Notas da atualização');assert.match(text(elements.get('modal-content')),/PR #42 · 08\/11\/2026/);
 elements.get('close-modal').onclick();h.receive({farm:{...farm('alice'),revision:'3',updateNotes:notes}});assert.equal(elements.get('modal').open,false);
 h.receive({farm:{...farm('alice'),revision:'4',updateNotes:{...notes,date:'2026-11-09'}}});assert.equal(elements.get('modal').open,true);elements.get('close-modal').onclick();
 h.receive({farm:{...farm('alice'),revision:'5',updateNotes:{...notes,version:'v2'}}});assert.equal(elements.get('modal').open,true);
});

test('services show only the VIP requirement when locked and retain purchase rules when active',()=>{
 const {h,elements}=app(),services=['water','pests','animals'].map((id,i)=>({id,name:['Rega','Pragas','Animais'][i],description:'Cuidado automático '+id,greenPrice:100,durationDays:30}));
 h.set({own:{...farm('alice'),greenCoins:'500',vipActive:false,automations:services.map((s,i)=>({...s,expiresAt:'2026-12-08T12:00:00Z',status:['SUSPENDED','EXPIRED','NOT_PURCHASED'][i]}))},catalog:{crops:[],animals:[],fertilizers:[],vip:{greenPrice:100},automationServices:services}});h.shop('vip');
 const nodes=descendants(elements.get('modal-content'));
 assert.equal(nodes.filter(n=>n.textContent==='Requer VIP ativo').length,3);
 assert.equal(nodes.filter(n=>n.className==='vip-service-buy').length,0);
 assert.equal(nodes.filter(n=>n.className==='vip-service-duration').length,3);
 assert.doesNotMatch(text(elements.get('modal-content')),/Suspenso:|Expirado|Não comprado/);
 h.set({own:{...farm('alice'),greenCoins:'500',vipActive:true,vipExpiresAt:'2026-12-08T12:00:00Z',automations:[{id:'water',status:'ACTIVE',expiresAt:'2026-12-08T12:00:00Z'},{id:'pests',status:'EXPIRED'}]},catalog:{crops:[],animals:[],fertilizers:[],vip:{greenPrice:100},automationServices:services}});h.shop('vip');
 const active=descendants(elements.get('modal-content'));
 assert.match(text(elements.get('modal-content')),/Ativo até/);assert.match(text(elements.get('modal-content')),/Expirado/);
 assert.equal(active.filter(n=>n.className==='vip-service-buy'&&!n.disabled).length,3);
 h.set({own:{...farm('alice'),greenCoins:'0',vipActive:true,vipExpiresAt:'2026-12-08T12:00:00Z'},catalog:{crops:[],animals:[],fertilizers:[],vip:{greenPrice:100},automationServices:services}});h.shop('vip');
 assert.equal(descendants(elements.get('modal-content')).filter(n=>n.className==='vip-service-buy'&&n.disabled).length,3);
});
test('public visit profile and friends show only an active VIP badge and preserve visit URL',async()=>{
 const {h,elements}=app();const paths=[];h.set({own:farm('alice'),api:{get:async target=>{paths.push(target);return target==='/v1/friends'?[{id:'bob',name:'Bob',vipActive:true}]:{...farm('bob'),vipActive:true};}}});
 await h.friends();assert.ok(descendants(elements.get('modal-content')).some(n=>n.className==='public-vip-badge'));
 await descendants(elements.get('modal-content')).find(n=>n.textContent==='Visitar fazenda').onclick();await new Promise(r=>setTimeout(r,0));assert.ok(paths.includes('/v1/farms/bob/visit'));assert.ok(descendants(elements.get('wallet')).some(n=>n.className==='public-vip-badge'));
});
test('empty or unpublished notes are ignored and logout clears queued notes',async()=>{
 const {h,elements}=app();h.set({own:farm('alice')});
 for(const updateNotes of [{version:'v',prNumber:0,date:'2026-11-08',items:['x']},{version:'v',prNumber:2,date:'2026-11-08',items:[]}])h.receive({farm:{...farm('alice'),updateNotes}});
 assert.ok(!elements.get('modal').open);h.dialog('Loja');h.receive({farm:{...farm('alice'),updateNotes:{version:'v',prNumber:2,date:'2026-11-08',items:['x']}}});await h.logout();h.set({own:farm('bob')});h.receive({});assert.equal(elements.get('modal').open,false);
});


test('existing farm refresh removes expired friend VIP badge while panel is open and stops when closed',async()=>{
 const {h,elements,intervals}=app();let active=true,reads=0;const own=farm('alice');
 h.set({own,api:{get:async path=>{if(path==='/v1/friends'){reads++;return[{id:'bob',name:'Bob',vipActive:active}];}return own;},prepareRequestId:async()=>{}}});await h.friends();assert.ok(descendants(elements.get('modal-content')).some(n=>n.className==='public-vip-badge'));
 active=false;intervals.get(30_000)();await new Promise(r=>setTimeout(r,0));
 assert.ok(!descendants(elements.get('modal-content')).some(n=>n.className==='public-vip-badge'));const previous=reads;elements.get('close-modal').onclick();intervals.get(30_000)();await new Promise(r=>setTimeout(r,0));assert.equal(reads,previous);
});

test('bitten theft shows server debit and own wallet during a visit, then never reopens its receipt',async()=>{
 for(const debit of ['25','0','7.5']){
  const {h,elements,ctx}=app(),own={...farm('alice'),coins:'80',greenCoins:'3'},visited={...farm('bob'),coins:'900'};
  const reply={farm:{...own,revision:'2',coins:debit==='0'?'0':'55'},visitedFarm:{...visited,revision:'2'},result:{success:false,message:'Resposta do servidor'},theftOutcome:{kind:'BITTEN',goldDebited:debit}};
  h.set({own,visited,api:{pending:null,mutate:async()=>reply,prepareRequestId:async()=>{}}});h.pickTool('STEAL');
  await h.tileAction({column:0,row:0,state:'READY',harvestRevision:'1'});
  assert.equal(elements.get('modal').open,true);assert.equal(elements.get('modal-title').textContent,'Cuidado com o cachorro!');
  assert.equal(text(descendants(elements.get('modal-content')).find(n=>n.id==='bite-message')),`Você acabou de ser mordido por um cachorro porque estava tentando roubar algo! Você acaba de perder ${debit==='7.5'?'7,5':debit} moedas de ouro tentando fugir.`);
  assert.ok(descendants(elements.get('modal-content')).some(n=>n.src==='/assets/classic/animals/cachorro/cachorro.png'));
  assert.equal(elements.get('modal').getAttribute('aria-describedby'),'bite-message');
  assert.equal(ctx.document.activeElement.textContent,'OK');
  assert.equal(h.get().own.coins,reply.farm.coins);assert.equal(h.get().visited.owner,'bob');
  const wallet=text(elements.get('wallet'));assert.ok(wallet.includes('Visitando bob'));assert.ok(wallet.includes('900'));assert.match(text(elements.get('modal-content')),new RegExp('Seu saldo: '+reply.farm.coins+' ouro'));
  ctx.document.activeElement.onclick();assert.equal(elements.get('modal').open,false);
  await h.tileAction({column:0,row:0,state:'READY',harvestRevision:'1'});assert.equal(elements.get('modal').open,false);
  h.receive(reply);assert.equal(elements.get('modal').open,false);
  h.dialog('Loja');assert.equal(elements.get('modal').getAttribute('aria-describedby'),null);
 }
});

test('protected theft and untyped bite-like text use the normal notice',async()=>{
 for(const outcome of [{kind:'PROTECTED',goldDebited:'0'},undefined]){
  const {h,elements}=app();h.set({own:farm('alice'),visited:farm('bob'),api:{pending:null,mutate:async()=>({result:{success:false,message:'Cachorro protegeu esta plantação.'},theftOutcome:outcome}),prepareRequestId:async()=>{}}});h.pickTool('STEAL');await h.tileAction({column:0,row:0,state:'READY'});
  assert.ok(!elements.get('modal').open);assert.match(text(elements.get('notice')),/Cachorro protegeu/);
 }
});

test('late bitten theft cannot open a popup in a new session',async()=>{
 const {h,elements}=app(),reply=deferred();h.set({own:farm('alice'),visited:farm('bob'),api:{pending:null,mutate:()=>reply.promise,prepareRequestId:async()=>{}}});h.pickTool('STEAL');const action=h.tileAction({column:0,row:0,state:'READY'});await h.logout();reply.resolve({farm:{...farm('alice'),revision:'2'},result:{success:false,message:'Mordida'},theftOutcome:{kind:'BITTEN',goldDebited:'0'}});await action;assert.equal(elements.get('modal').open,false);assert.equal(h.get().own,undefined);
});

test('pending theft retry shows a bite once and keeps it stable across farm refreshes',async()=>{
 const {h,elements,ctx}=app(),own={...farm('alice'),coins:'1'},visited=farm('bob');
 const reply={farm:{...own,revision:'2',coins:'0'},result:{success:false,message:'Bloqueado'},theftOutcome:{kind:'BITTEN',goldDebited:'1'}};
 const api={pending:{body:JSON.stringify({action:'STEAL',requestId:'receipt'})},retry:async()=>reply,get:async path=>path==='/v1/me/farm'?reply.farm:visited,prepareRequestId:async()=>{}};
 h.set({own,visited,api});await elements.get('retry').onclick();assert.equal(elements.get('modal').open,true);assert.equal(ctx.document.activeElement.textContent,'OK');
 const panel=elements.get('modal-content').children;await h.refresh();assert.equal(elements.get('modal-content').children,panel);assert.equal(ctx.document.activeElement.textContent,'OK');
 elements.get('close-modal').onclick();assert.equal(elements.get('modal').open,false);await elements.get('retry').onclick();assert.equal(elements.get('modal').open,false);
});

test('typed bite on a different social action never opens the theft popup',async()=>{
 const {h,elements}=app();h.set({own:farm('alice'),visited:farm('bob'),api:{pending:null,mutate:async()=>({farm:{...farm('alice'),revision:'2'},result:{success:false,message:'Ação bloqueada'},theftOutcome:{kind:'BITTEN',goldDebited:'0'}}),prepareRequestId:async()=>{}}});h.pickTool('SPREAD_WEEDS');await h.tileAction({column:0,row:0,state:'READY'});assert.ok(!elements.get('modal').open);assert.match(text(elements.get('notice')),/Ação bloqueada/);
});
test('auth signout follows local push suppression without awaiting remote revocation',async()=>{const local=deferred();let signedOut=0;const {h,elements}=app(new Map(),{suspendPush:()=>local.promise,signOut:async()=>{signedOut++;}});h.set({own:farm('alice'),api:{pending:null}});const logout=h.logout();assert.equal(signedOut,0);assert.equal(elements.get('game').hidden,true);local.resolve();await logout;assert.equal(signedOut,1);});
