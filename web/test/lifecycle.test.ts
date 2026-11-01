import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {ApiError,acceptFarm} from '../src/api.ts';
import * as presentation from '../src/presentation.ts';
import * as uiPresentation from '../src/ui-presentation.ts';
import * as avatar from '../src/avatar.ts';

const farm=owner=>({owner,name:owner,revision:'1',tiles:[],animals:[],seedCounts:{}});
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve};}
function app(){
 const elements=new Map(),listeners=new Map(),audioClips=[],observers=[];
 const make=(tag='div')=>({tag,value:'',selectedIndex:0,hidden:false,children:[],attributes:{},style:{setProperty(key,value){this[key]=value;}},scrollTop:0,scrollLeft:0,classList:{add(){},remove(){},toggle(){}},
  replaceChildren(...nodes){if(this.contains(ctx.document.activeElement))ctx.document.activeElement=null;this.children=nodes;},
  append(...nodes){this.children.push(...nodes);},prepend(...nodes){this.children.unshift(...nodes);},insertBefore(node,before){this.children.splice(this.children.indexOf(before),0,node);},
  setAttribute(key,value){this.attributes[key]=String(value);},getAttribute(key){return this.attributes[key]??null;},
  contains(target){return !!target&&(target===this||this.children.some(child=>child?.contains?.(target)));},
  querySelectorAll(selector){return descendants(this).slice(1).filter(node=>selector==='[data-focus-key]'?node.getAttribute('data-focus-key')!==null:selector==='.market-scroll'?node.className==='market-scroll':false);},
  querySelector(selector){return this.querySelectorAll(selector)[0]??null;},focus(){ctx.document.activeElement=this;},
  getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(){},close(){this.open=false;},showModal(){this.open=true;}});
 const storage={getItem:()=>null,setItem(){},removeItem(){}};
 const ctx={...presentation,...uiPresentation,...avatar,window:{COLHEITA_CONFIG:{development:true},setInterval(){},setTimeout(){},addEventListener(){}},document:{hidden:false,getElementById(id){if(!elements.has(id))elements.set(id,make());return elements.get(id);},createElement:make,createTextNode:text=>text,addEventListener(name,handler){listeners.set(name,handler);}},location:{href:'http://localhost',origin:'http://localhost'},sessionStorage:storage,navigator:{onLine:true},URL,ApiError,acceptFarm,animalPath:()=>'',animalStatusText:(status)=>status,animalActionLabel:(status)=>status==='READY'?'Recolher produto':null,clearTimeout(){},ResizeObserver:class{constructor(callback){this.callback=callback;observers.push(this);}observe(target){this.target=target;}disconnect(){this.disconnected=true;}},Audio:class{paused=true;playCount=0;loop=false;volume=0;constructor(){audioClips.push(this);}play(){this.paused=false;this.playCount++;return Promise.resolve();}pause(){this.paused=true;}},HTMLInputElement:class{},HTMLSelectElement:class{}};
 const uiSource=readFileSync(new URL('../src/ui.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(uiSource),ctx);
 let source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('void start();','');
 source+='\nglobalThis.h={refresh,logout,toggleMusic,shop,missions,friends,receive,dialog,health,connect,pickTool,settings,avatarSettings,notify,notifyAction,dialogGeneration:()=>dialogGeneration,acceptInvitation,get:()=>({own,visited}),set:(v)=>{own=v.own;visited=v.visited;api=v.api;catalog=v.catalog??{crops:[],fertilizers:[],animals:[],vip:{greenPrice:20}};}};';
 runInNewContext(stripTypeScriptTypes(source),ctx);return{h:ctx.h,elements,listeners,audioClips,observers,ctx};
}
function text(node){return(node.textContent??'')+(node.children??[]).map(child=>typeof child==='object'?text(child):child).join('');}
function descendants(node){return[node,...(node.children??[]).flatMap(child=>typeof child==='object'?descendants(child):[])];}

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

test('Google avatar can be selected from the authenticated photo without changing farm data',async()=>{
 const {h,elements,ctx}=app();ctx.Scene=class{update(){}close(){}};
 ctx.ApiClient=class{pending=null;get(){return Promise.resolve(farm('alice'));}prepareRequestId(){return Promise.resolve();}};
 h.set({own:undefined});await h.connect('alice',async()=> 'synthetic-token','https://lh3.googleusercontent.com/a/synthetic');
 h.avatarSettings();descendants(elements.get('modal-content')).find(node=>node.textContent==='Foto do Google').onclick();
 const image=descendants(elements.get('wallet')).find(node=>node.className==='avatar');
 assert.equal(image.src,'https://lh3.googleusercontent.com/a/synthetic');assert.equal(h.get().own.revision,'1');
 await h.connect('bob',async()=> 'synthetic-token');h.avatarSettings();
 assert.equal(descendants(elements.get('modal-content')).find(node=>node.textContent==='Foto do Google').disabled,true);
 assert.equal(descendants(elements.get('wallet')).find(node=>node.className==='avatar').src,'/assets/classic/icons/avatar_masculino.png');
});

test('avatar picker offers and applies every supplied game avatar without changing the farm',()=>{
 const {h,elements}=app(),initial=farm('alice');h.set({own:initial});h.avatarSettings();
 const options=descendants(elements.get('modal-content')).filter(node=>node.tag==='button');
 assert.equal(options.length,7);
 for(const {label,file} of avatar.GAME_AVATARS){
  descendants(elements.get('modal-content')).find(node=>node.textContent===label).onclick();
  assert.equal(descendants(elements.get('wallet')).find(node=>node.className==='avatar').src,`/assets/classic/icons/${file}`);
  assert.equal(descendants(elements.get('modal-content')).find(node=>node.textContent===label).getAttribute('aria-pressed'),'true');
  assert.equal(h.get().own,initial);
 }
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
 const sell=descendants(content).find(node=>node.getAttribute('aria-label')==='Vender 1 Nabo');
 sell.focus();content.scrollTop=180;sell.onclick();
 reply.resolve({farm:{...stocked,revision:'2',produceLots:[{itemId:'crop0',quantity:1,currency:'GOLD'}],depositTotals:{GOLD:12}},result:{success:false,message:'synthetic update'}});
 await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(commands,1);assert.equal(content.scrollTop,180);
 assert.equal(ctx.document.activeElement.getAttribute('aria-label'),'Vender 1 Nabo');
 h.receive({farm:{...stocked,revision:'3',produceLots:[],depositTotals:{}}});
 assert.equal(ctx.document.activeElement,elements.get('close-modal'));
});

test('late sale confirmation does not reopen a deposit that the player closed',async()=>{
 const {h,elements}=app(),{own}=marketFixture(h),reply=deferred();
 h.set({own:{...own,produceLots:[],depositTotals:{GOLD:12}},api:{pending:null,mutate:()=>reply.promise,prepareRequestId:async()=>{}}});
 elements.get('deposit').onclick();
 descendants(elements.get('modal-content')).find(node=>node.textContent?.startsWith('Vender tudo ·')).onclick();
 elements.get('modal').close();reply.resolve({farm:{...own,revision:'2'},result:{success:false,message:'synthetic update'}});
 await new Promise(resolve=>setTimeout(resolve,0));assert.equal(elements.get('modal').open,false);
});

test('active wallet uses original coin icons, exact XP and the own balance during a visit',()=>{
 const {h,elements}=app(),own={...farm('alice'),name:'Uma fazenda com um nome de 40 caracteres!',level:2,currentXp:'9007199254740993.1',xpToNext:'18014398509481986.2',coins:'9007199254740993.1',greenCoins:'5'};
 h.set({own,visited:{...farm('bob'),name:'Amigo'}});h.receive({});
 const nodes=descendants(elements.get('wallet'));
 assert.ok(nodes.some(n=>n.tag==='img'&&n.src?.endsWith('moeda-comum.png')));
 assert.ok(nodes.some(n=>n.tag==='img'&&n.src?.endsWith('moeda-verde.png')));
 assert.ok(nodes.some(n=>n.textContent==='Visitando Amigo'));
 assert.ok(nodes.some(n=>n.textContent==='9007199254740993.1'));
 assert.equal(nodes.find(n=>n.tag==='progress').value,50);
 assert.equal(elements.get('home').hidden,false);
});

test('fertilizer tool shows the selected stock and direct land access opens the existing list',()=>{
 const {h,elements}=app();h.set({own:{...farm('alice'),fertilizers:{comum:7},tiles:[{column:0,row:0,unlocked:true,state:'GRASS'}]}});h.receive({});
 const tools=elements.get('tools').children;assert.equal(text(tools.at(-1)).includes('x7'),true);
 elements.get('plots').onclick();assert.equal(elements.get('modal-title').textContent,'Terras da fazenda');
 assert.match(text(elements.get('modal-content')),/Terra 1, 1: grama/);
});

test('resize observer repositions the active tooltip and disconnects when the session closes',async()=>{
 const {h,elements,ctx,observers}=app();
 ctx.Scene=class{update(){}close(){}};
 ctx.ApiClient=class{pending=null;get(){return Promise.resolve(farm('alice'));}prepareRequestId(){return Promise.resolve();}};
 h.set({own:undefined});await h.connect('alice',async()=> 'synthetic-token');
 let width=2560,height=1080;
 for(const id of ['game','farm'])elements.get(id).getBoundingClientRect=()=>({left:0,top:0,width,height});
 const panel=elements.get('plant-health');panel.getBoundingClientRect=()=>({width:330,height:160});
 h.health({cropId:'nabo',state:'GROWING',season:1,seasons:1,remainingSeconds:60,nominalRemainingSeconds:60},{x:585,y:400});
 assert.equal(panel.style.left,'857px');
 width=390;height=844;observers[0].callback();assert.equal(panel.style.left,'12px');
 await h.logout();assert.equal(observers[0].disconnected,true);assert.equal(panel.hidden,true);
});

test('crop tooltip follows contain projection, repositions unchanged content and stays inside the game',()=>{
 const {h,elements,ctx}=app();h.set({own:farm('alice')});
 let width=2560,height=1080;
 for(const id of ['game','farm'])ctx.document.getElementById(id).getBoundingClientRect=()=>({left:20,top:30,width,height});
 const panel=ctx.document.getElementById('plant-health');panel.style={};panel.getBoundingClientRect=()=>({width:330,height:160});
 const tile={cropId:'nabo',stage:1,state:'GROWING',season:1,seasons:1,remainingSeconds:3600,nominalRemainingSeconds:3600};
 h.health(tile,{x:585,y:400});
 assert.equal(panel.style.left,'857px');assert.equal(panel.style.top,'490px');
 width=390;height=844;h.health(tile,{x:585,y:400});
 assert.equal(panel.style.left,'12px');assert.equal(panel.style.top,'419.8125px');
 h.health(tile,{x:1600,y:900});assert.equal(panel.style.left,'48px');assert.equal(panel.style.top,'541.6875px');
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
test('music pauses while hidden and on logout, then resumes when visible',async()=>{
 const {h,listeners,ctx,audioClips}=app();h.set({own:farm('alice')});h.toggleMusic();const music=audioClips[0];assert.equal(music.playCount,1);
 ctx.document.hidden=true;listeners.get('visibilitychange')();assert.equal(music.paused,true);
 ctx.document.hidden=false;listeners.get('visibilitychange')();assert.equal(music.playCount,2);
 await h.logout();assert.equal(music.paused,true);
});
test('open owned-animal screen refreshes after a successful state update',()=>{
 const {h,elements}=app(),farmState={...farm('alice'),level:1,feed:{producer:1},animals:[{id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0}]};
 h.set({own:farmState,catalog:{crops:[],fertilizers:[],feedPrices:{producer:10,growth:10},animals:[{id:'vaca',name:'Vaca',kind:'PRODUCER',productName:'Leite'}],vip:{greenPrice:20}}});
 h.shop('owned');assert.match(text(elements.get('modal-content')),/Recolher produto/);
 h.receive({farm:{...farmState,revision:'2',animals:[{id:'vaca',stage:'ADULT',status:'PRODUCING',remainingSeconds:3600}]}});
 assert.doesNotMatch(text(elements.get('modal-content')),/Recolher produto/);assert.match(text(elements.get('modal-content')),/PRODUCING/);
});
test('open missions screen refreshes progress after a farm update',()=>{
 const {h,elements}=app(),farmState={...farm('alice'),missions:[{id:'m',title:'Colher',green:1,progress:1,target:2,done:false}]};
 h.set({own:farmState});h.missions();assert.match(text(elements.get('modal-content')),/1 \/ 2/);
 h.receive({farm:{...farmState,revision:'2',missions:[{...farmState.missions[0],progress:2,done:true}]}});
 assert.match(text(elements.get('modal-content')),/2 \/ 2 · Concluída · recompensa recebida/);
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
 let source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.TestScene=Scene;',ctx);
 for(const revision of ['1','2']){const scene=new ctx.TestScene(canvas,{},tile=>calls.push(tile.harvestRevision),()=>{},()=>{});scene.update({...farm('alice'),tiles:[{column:0,row:0,harvestRevision:revision}]});if(revision==='1')scene.close();}
 assert.deepEqual(cancelled,[1]);
 for(const handler of handlers.get('click'))handler({clientX:585,clientY:400});assert.deepEqual(calls,['2']);
});
