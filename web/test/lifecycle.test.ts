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
import * as backgroundCatalog from '../src/background-catalog.ts';
import * as events from '../src/events.ts';

const farm=owner=>({owner,name:owner,revision:'1',tiles:[],animals:[],seedCounts:{}});
function deferred(){let resolve,reject;const promise=new Promise((accept,fail)=>{resolve=accept;reject=fail;});return{promise,resolve,reject};}
test('friend list offers only Visitar fazenda and keeps IDs despite homonyms',async()=>{
 const {h,elements}=app();let target='';
 h.set({own:farm('alice'),api:{get:path=>{if(path==='/v1/friends')return Promise.resolve([{id:'bob',name:'Sam',avatar:{id:'female-2'}},{id:'carol',name:'Sam',avatar:{id:'google',photoUrl:'https://lh3.googleusercontent.com/a/carol'}},{id:'dan',name:'Dan'}]);target=path;return Promise.resolve(farm('bob'));}}});
 await h.friends();const images=descendants(elements.get('modal-content')).filter(node=>node.tag==='img'&&node.className==='friend-avatar');
 assert.equal(images.length,3);assert.equal(images[0].src,'/assets/classic/icons/avatar_feminino2.png');assert.equal(images[1].src,'https://lh3.googleusercontent.com/a/carol');assert.equal(images[1].referrerPolicy,'no-referrer');
 assert.equal(images[2].src,avatar.CLASSIC_AVATAR);images[1].onerror();assert.equal(images[1].src,avatar.CLASSIC_AVATAR);assert.equal(images[1].onerror,null);
 const rows=descendants(elements.get('modal-content')).filter(node=>node.className==='friend-card');assert.equal(rows[0].children.length,3);assert.equal(rows[0].children[2].textContent,'Visitar fazenda');assert.ok(!descendants(elements.get('modal-content')).some(node=>node.className==='gift-panel'));
 assert.ok(!descendants(elements.get('modal-content')).some(node=>node.textContent==='Presentear'||node.dataset?.friendGift!==undefined));
 await rows[1].children[2].onclick();assert.equal(target,'/v1/farms/carol/visit');
});
test('gift GET failure retains fixed recipient and retries inside composer',async()=>{
 const {h,elements}=app();let giftReads=0;const bob={...farm('bob'),level:10};
 h.set({own:{...farm('alice'),seedCounts:{nabo:2}},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{get:async path=>{if(path==='/v1/friends')return[{id:'bob',name:'Bob'}];if(path==='/v1/gifts'){if(giftReads++===0)throw new ApiError(500,'server failure');return{received:[],sent:[]};}if(path==='/v1/me/farm')return h.get().own;return bob;}}});
 openGift(h,elements);await new Promise(r=>setTimeout(r,0));
 const content=elements.get('modal-content');assert.equal(elements.get('modal-title').textContent,'Presentear');assert.ok(text(content).includes('Bob'));assert.match(text(content),/Não foi possível/);
 descendants(content).find(node=>node.textContent==='Tentar novamente').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(giftReads,2);assert.equal(elements.get('modal-title').textContent,'Presentear');assert.ok(descendants(content).some(n=>text(n)==='Bob'));assert.ok(descendants(content).some(n=>text(n).includes('Nabo')),text(content));
});
test('gift network failure remains scoped to composer',async()=>{
 const {h,elements}=app();h.set({own:{...farm('alice'),seedCounts:{nabo:1}},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{get:async path=>{if(path==='/v1/friends')return[{id:'bob',name:'Bob'}];throw new TypeError('network unavailable');}}});
 openGift(h,elements);await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal-title').textContent,'Presentear');assert.match(text(elements.get('modal-content')),/Não foi possível carregar/);assert.ok(!elements.has('connection'));
});
test('cancel and escape close the composer and keep the visit while POST locks X, Cancel and Escape',async()=>{
 const {h,elements,ctx}=app(),post=deferred();let mutations=0;const own={...farm('alice'),seedCounts:{nabo:4}},target={...farm('bob'),level:5};
 const api={pending:null,busy:false,get:async path=>path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:target,mutate(_path,fields){mutations++;assert.equal(fields.recipientId,'bob');return post.promise;}};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api});
 openGift(h,elements);await new Promise(r=>setTimeout(r,0));
 let footer=elements.get('modal-footer');footer.children.find(n=>n.textContent==='Cancelar').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal').open,false);assert.equal(h.get().visited.owner,'bob');assert.equal(ctx.document.activeElement,elements.get('gift-visit'));
 elements.get('gift-visit').onclick();await new Promise(r=>setTimeout(r,0));let prevented=false;elements.get('modal').listeners.cancel({preventDefault(){prevented=true;}});assert.equal(prevented,true);assert.equal(elements.get('modal').open,false,'Escape closes the composer like Cancel');
 elements.get('gift-visit').onclick();await new Promise(r=>setTimeout(r,0));footer=elements.get('modal-footer');const send=footer.children.find(n=>n.textContent==='Enviar');send.onclick();send.onclick();
 assert.equal(mutations,1);assert.equal(elements.get('modal-footer').children.find(n=>n.textContent==='Enviando…').disabled,true);assert.equal(elements.get('modal-footer').children[0].disabled,true);assert.equal(elements.get('close-modal').disabled,true);
 prevented=false;elements.get('modal').listeners.cancel({preventDefault(){prevented=true;}});assert.equal(prevented,true);elements.get('close-modal').onclick();assert.equal(elements.get('modal-title').textContent,'Presentear');assert.equal(elements.get('modal').open,true);
 post.resolve({result:{success:true,message:'Sementes enviadas.'},farm:own});await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal').open,false);assert.equal(h.get().visited.owner,'bob');assert.equal(text(elements.get('notice')),'Sementes enviadas.');assert.equal(mutations,1);assert.equal(elements.get('modal').listenerCounts.cancel,1);
});
test('visit menu keeps only Minha fazenda, Amigos and Presentear and home restores the own menu',()=>{
 const {h,elements,ctx}=app(),own={...farm('alice'),loginRewards:{completedDay:0,nextDay:1,available:true,rewards:[]}},others=['notifications','shop','deposit','missions','achievements','decorations','events','settings','more'];
 h.set({own,api:{pending:null}});h.receive({farm:own});
 assert.ok(others.every(id=>!elements.get(id).hidden));assert.equal(elements.get('home').hidden,true);assert.equal(elements.get('gift-visit').hidden,true);assert.equal(elements.get('friends').hidden,false);assert.equal(ctx.moreVisiting,false);
 h.visit({...farm('bob'),name:'Bob'});
 assert.ok(others.every(id=>elements.get(id).hidden),'own-farm panels leave the top menu');
 assert.equal(elements.get('home').hidden,false);assert.equal(elements.get('friends').hidden,false);assert.equal(elements.get('gift-visit').hidden,false);assert.equal(elements.get('gift-visit').getAttribute('aria-label'),'Presentear Bob');assert.equal(ctx.moreVisiting,true);
 assert.ok(descendants(elements.get('tools')).some(node=>node.getAttribute?.('aria-label')==='Furtar colheita'||node.title==='Furtar colheita'||text(node).includes('Furtar')),'social toolbar stays available');
 h.visit({...farm('carol'),name:'Carol'});assert.equal(elements.get('gift-visit').getAttribute('aria-label'),'Presentear Carol');
 elements.get('home').onclick();
 assert.ok(others.every(id=>!elements.get(id).hidden));assert.equal(elements.get('home').hidden,true);assert.equal(elements.get('gift-visit').hidden,true);assert.equal(ctx.moreVisiting,false);
});
test('gift from a visit goes to the visited farm even after moving to another friend',async()=>{
 const {h,elements}=app(),own={...farm('alice'),seedCounts:{nabo:2}};let sent;
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{pending:null,get:async path=>path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm(path.split('/')[3]),mutate:async(_path,fields)=>{sent=fields;return{result:{success:true,message:'Enviado'},farm:own};}}});
 openGift(h,elements,'bob','Bob');await new Promise(r=>setTimeout(r,0));elements.get('modal-footer').children[0].onclick();
 openGift(h,elements,'carol','Carol');await new Promise(r=>setTimeout(r,0));assert.ok(text(elements.get('modal-content')).includes('Carol'));
 elements.get('modal-footer').children[1].onclick();await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));
 assert.equal(sent.recipientId,'carol');assert.equal(h.get().visited.owner,'carol');
});
test('sending to one of two friends with the same name uses the visited UUID',async()=>{
 const {h,elements}=app(),own={...farm('alice'),seedCounts:{nabo:2}};let sent;
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{pending:null,get:async path=>path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm(path.includes('/carol/')?'carol':'bob'),mutate:async(_path,fields)=>{sent=fields;return{result:{success:true,message:'Enviado'},farm:own};}}});
 openGift(h,elements,'carol','Sam');await new Promise(r=>setTimeout(r,0));elements.get('modal-footer').children[1].onclick();await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));
 assert.equal(sent.recipientId,'carol');assert.equal(elements.get('modal').open,false);assert.equal(h.get().visited.owner,'carol');
});
test('definitive gift refusal reloads stock and cooldown and leaves Enviar disabled',async()=>{
 const {h,elements}=app();let own={...farm('alice'),seedCounts:{nabo:3}},gifts={received:[],sent:[]};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{pending:null,get:async path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?gifts:path==='/v1/me/farm'?own:farm('bob'),mutate:async()=>{own={...own,revision:'2',seedCounts:{nabo:0}};gifts={received:[],sent:[{recipientId:'bob',cropId:'nabo',quantity:1,createdAt:new Date().toISOString()}]};return{result:{success:false,message:'Semente indisponível.'},farm:own};}}});
 openGift(h,elements);await new Promise(r=>setTimeout(r,0));assert.equal(elements.get('modal-footer').children[1].disabled,false);
 elements.get('modal-footer').children[1].onclick();for(let i=0;i<4;i++)await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal-title').textContent,'Presentear');assert.match(text(elements.get('modal-content')),/últimas 24 horas/);assert.equal(elements.get('modal-footer').children.find(n=>n.textContent==='Enviar').disabled,true);
});
test('logout during gift POST keeps response from reopening a panel',async()=>{
 const {h,elements}=app(),post=deferred(),own={...farm('alice'),seedCounts:{nabo:1}};let mutations=0;
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{pending:null,busy:false,get:async path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm('bob'),mutate(){mutations++;return post.promise;}}});
 openGift(h,elements);await new Promise(r=>setTimeout(r,0));elements.get('modal-footer').children.find(n=>n.textContent==='Enviar').onclick();
 await h.logout();post.resolve({result:{success:true,message:'ok'},farm:own});await new Promise(r=>setTimeout(r,0));assert.equal(mutations,1);assert.equal(elements.get('modal').open,false);assert.equal(elements.get('modal-title').textContent,'Presentear');
});
test('pending gift reload offers shared receipt retry for its original recipient only',async()=>{
 const {h,elements}=app(),own={...farm('alice'),seedCounts:{nabo:0}},pending={path:'/v1/gifts',body:'{"requestId":"same-id","recipientId":"bob","cropId":"nabo","quantity":2}'};let retries=0,mutations=0;
 const api={pending,get:async path=>path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm(path.includes('/bob/')?'bob':'other'),retry:async()=>{retries++;api.pending=null;return{result:{success:true,message:'Recuperado'},farm:own};},mutate(){mutations++;}};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api});
 openGift(h,elements,'other','Sam');await new Promise(r=>setTimeout(r,0));assert.notEqual(elements.get('modal-title')?.textContent,'Presentear','another recipient cannot start while the pending gift is unresolved');assert.match(text(elements.get('notice')),/presente pendente para outro amigo/);
 openGift(h,elements,'bob','Sam');await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal-title').textContent,'Presentear');assert.ok(text(elements.get('modal-content')).includes('envio anterior'));assert.ok(text(elements.get('modal-content')).includes('Tentativa pendente: Nabo · 2 semente(s)'));
 descendants(elements.get('modal-content')).find(n=>n.textContent==='Recuperar resultado').onclick();await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));
 assert.equal(retries,1);assert.equal(mutations,0);assert.equal(elements.get('modal').open,false);assert.equal(text(elements.get('notice')),'Recuperado');assert.equal(h.get().visited.owner,'bob');
});
test('late pending gift retry stays out of a different dialog but updates the current farm',async()=>{
 for(const outcome of ['success','reject']){
  const {h,elements,ctx}=app(),reply=deferred(),own={...farm('alice'),revision:'1',seedCounts:{nabo:1}},pending={path:'/v1/gifts',body:'{"requestId":"same-id","recipientId":"bob","cropId":"nabo","quantity":1}'};
  const api={pending,get:async path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm('bob'),retry:()=>reply.promise};
  h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api});openGift(h,elements,'bob');await new Promise(r=>setTimeout(r,0));
  descendants(elements.get('modal-content')).find(n=>n.textContent==='Recuperar resultado').onclick();h.dialog('Outro painel');const content=elements.get('modal-content').children,notice=text(elements.get('notice')??{textContent:''}),focusTarget=ctx.document.createElement('button');focusTarget.focus();elements.get('modal').setAttribute('closedby','none');elements.get('close-modal').disabled=true;
  if(outcome==='success')reply.resolve({farm:{...own,revision:'2',seedCounts:{}},result:{success:true,message:'Presente confirmado'}});else reply.reject(new TypeError('offline'));
  await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));
  assert.equal(h.get().own.revision,outcome==='success'?'2':'1');assert.equal(elements.get('modal-title').textContent,'Outro painel');assert.equal(elements.get('modal-content').children,content);assert.equal(text(elements.get('notice')??{textContent:''}),notice);assert.equal(elements.get('modal').getAttribute('closedby'),'none');assert.equal(elements.get('close-modal').disabled,true);assert.equal(ctx.document.activeElement,focusTarget);
 }
});
test('late generic retry cannot replace the current panel with a returned invitation code',async()=>{
 const {h,elements,ctx}=app(),reply=deferred(),api={pending:{body:'{"action":"ACCEPT_INVITATION"}'},retry:()=>reply.promise};h.set({own:{...farm('alice'),revision:'1'},api});elements.get('retry').onclick();h.dialog('Outro painel');const children=elements.get('modal-content').children,focusTarget=ctx.document.createElement('button');focusTarget.focus();elements.get('modal').setAttribute('closedby','none');elements.get('close-modal').disabled=true;reply.resolve({farm:{...farm('alice'),revision:'2'},code:'late-code',result:{success:true,message:'Convite confirmado'}});await new Promise(r=>setTimeout(r,0));
 assert.equal(h.get().own.revision,'2');assert.equal(elements.get('modal-title').textContent,'Outro painel');assert.equal(elements.get('modal-content').children,children);assert.equal(text(elements.get('notice')??{textContent:''}),'');assert.equal(elements.get('modal').getAttribute('closedby'),'none');assert.equal(elements.get('close-modal').disabled,true);assert.equal(ctx.document.activeElement,focusTarget);
});
test('pending gift retry cannot restore farm after logout',async()=>{
 const {h,elements}=app(),reply=deferred(),own=farm('alice'),api={pending:{path:'/v1/gifts',body:'{"requestId":"same-id","recipientId":"bob","cropId":"nabo","quantity":1}'},get:async path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm('bob'),retry:()=>reply.promise};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api});openGift(h,elements,'bob');await new Promise(r=>setTimeout(r,0));descendants(elements.get('modal-content')).find(n=>n.textContent==='Recuperar resultado').onclick();await h.logout();reply.resolve({farm:{...own,revision:'2'},result:{success:true,message:'late'}});await new Promise(r=>setTimeout(r,0));
 assert.equal(h.get().own,undefined);assert.equal(elements.get('modal').open,false);
});
test('pending gift retry that succeeds after the panel changed does not close or notify the newer panel',async()=>{
 const {h,elements}=app(),reply=deferred(),own=farm('alice');
 const api={pending:{path:'/v1/gifts',body:'{"requestId":"same-id","recipientId":"bob","cropId":"nabo","quantity":1}'},get:async path=>path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm('bob'),retry:()=>reply.promise};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api});openGift(h,elements);await new Promise(r=>setTimeout(r,0));descendants(elements.get('modal-content')).find(n=>n.textContent==='Recuperar resultado').onclick();
 h.dialog('Amigos');reply.resolve({result:{success:true,message:'Presente confirmado'},farm:own});await new Promise(r=>setTimeout(r,0));await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal-title').textContent,'Amigos');assert.equal(elements.get('modal').open,true);assert.equal(text(elements.get('notice')??{textContent:''}),'');
});
test('secondary GET failure keeps receipt recovery separate from data retry',async()=>{
 const {h,elements}=app(),own={...farm('alice'),seedCounts:{nabo:1}},pending={path:'/v1/gifts',body:'{"requestId":"same-id","recipientId":"bob","cropId":"nabo","quantity":1}'};let reads=0,retries=0,mutations=0;
 const api={pending,get:async path=>{if(path==='/v1/friends')return[{id:'bob',name:'Bob'}];if(path==='/v1/gifts'){reads++;throw new Error('offline');}if(path==='/v1/me/farm')return own;return farm('bob');},retry:async()=>{retries++;throw new Error('still offline');},mutate(){mutations++;}};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api});openGift(h,elements);await new Promise(r=>setTimeout(r,0));
 let content=elements.get('modal-content');assert.ok(text(content).includes('Não foi possível carregar'));assert.ok(text(content).includes('Há um envio pendente'));
 h.receive({farm:{...own,revision:'2',seedCounts:{}}});assert.ok(text(content).includes('Tentativa pendente: Nabo · 1 semente(s)'));
 descendants(content).find(n=>n.textContent==='Tentar novamente').onclick();await new Promise(r=>setTimeout(r,0));assert.equal(retries,0);assert.equal(reads,2);
 content=elements.get('modal-content');descendants(content).find(n=>n.textContent==='Recuperar resultado').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(retries,1);assert.equal(mutations,0);assert.equal(elements.get('modal-title').textContent,'Presentear');assert.ok(text(content).includes('Sair não desfaz'));
});
test('late gift failure after navigating away does not affect the next panel',async()=>{
 const {h,elements}=app(),reply=deferred(),own={...farm('alice'),seedCounts:{nabo:1}};
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{get:path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?reply.promise:path==='/v1/me/farm'?own:farm('bob')}});
 openGift(h,elements);h.dialog('Outro painel');reply.resolve(Promise.reject(new TypeError('network unavailable')));await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal-title').textContent,'Outro painel');assert.ok(!text(elements.get('modal-content')).includes('Não foi possível'));
 const next=app(),late=deferred(),nextOwn={...farm('alice'),seedCounts:{nabo:1}};next.h.set({own:nextOwn,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'}],animals:[],fertilizers:[]},api:{get:path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?late.promise:path==='/v1/me/farm'?nextOwn:farm('bob')}});
 openGift(next.h,next.elements);await next.h.logout();late.resolve(Promise.reject(new TypeError('network unavailable')));await new Promise(r=>setTimeout(r,0));
 assert.equal(next.elements.get('modal').open,false);assert.ok(!text(next.elements.get('modal-content')).includes('Não foi possível'));
});
function app(preferences=new Map(),pushLogoutOptions){
 const intervals=new Map(),elements=new Map(),listeners=new Map(),audioClips=[],observers=[],timeouts=new Map();let nextTimeout=0,monotonic=0;
 const make=(tag='div')=>({tag,value:'',selectedIndex:0,hidden:false,children:[],get childElementCount(){return this.children.length;},attributes:{},dataset:{},style:{setProperty(key,value){this[key]=value;}},scrollTop:0,scrollLeft:0,classList:{add(){},remove(){},toggle(){}},
  replaceChildren(...nodes){if(this.contains(ctx.document.activeElement))ctx.document.activeElement=null;this.children=nodes;},
  append(...nodes){this.children.push(...nodes);},prepend(...nodes){this.children.unshift(...nodes);},insertBefore(node,before){this.children.splice(this.children.indexOf(before),0,node);},replaceChild(node,before){this.children.splice(this.children.indexOf(before),1,node);return before;},replaceWith(node){const parent=descendants(elements.get('modal-content')).find(candidate=>candidate.children.includes(this));parent?.replaceChild(node,this);},
  removeAttribute(key){delete this.attributes[key];},setAttribute(key,value){this.attributes[key]=String(value);},getAttribute(key){return this.attributes[key]??null;},
  contains(target){return !!target&&(target===this||this.children.some(child=>child?.contains?.(target)));},
  get firstElementChild(){return this.children[0]??null;},get lastElementChild(){return this.children.at(-1)??null;},
  querySelectorAll(selector){return descendants(this).slice(1).filter(node=>selector==='[data-focus-key]'?node.getAttribute('data-focus-key')!==null:selector.startsWith('.')?(node.className??'').split(/\s+/).includes(selector.slice(1)):false);},
  querySelector(selector){return this.querySelectorAll(selector)[0]??null;},focus(){if(this.tag==='div'&&this.tabIndex===undefined)return;ctx.document.activeElement=this;},
  getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler){(this.listenerCounts??={})[name]=(this.listenerCounts[name]??0)+1;(this.listeners??={})[name]=handler;},close(){this.open=false;},showModal(){this.open=true;}});
 const storage={getItem:()=>null,setItem(){},removeItem(){}};
 const ctx={syncMobileCamera(){ctx.cameraSyncs=(ctx.cameraSyncs??0)+1;},setMoreMenuVisiting(value){ctx.moreVisiting=value;},reconcilePush:async()=>{},connectPush:async()=>{},suspendPush:pushLogoutOptions?.suspendPush??(async()=>{}),createClient:()=>({auth:{signOut:pushLogoutOptions?.signOut}}),renderPushSettings:()=>{},setMonotonic(value){monotonic=value;},performance:{now:()=>monotonic},...presentation,...uiPresentation,...backgroundCatalog,...avatar,...inviteUrl,history:{state:null,replaceState(){}},window:{localStorage:{getItem:key=>preferences.get(key)??null,setItem:(key,value)=>preferences.set(key,value)},COLHEITA_CONFIG:{development:!pushLogoutOptions},setInterval(callback,delay){intervals.set(delay,callback);return delay;},clearInterval(id){intervals.delete(id);},setTimeout(callback){const id=++nextTimeout;timeouts.set(id,callback);return id;},clearTimeout(id){timeouts.delete(id);},matchMedia(){return{matches:false};},requestAnimationFrame(callback){callback();return 1;},addEventListener(){}},document:{hidden:false,getElementById(id){if(!elements.has(id))elements.set(id,make(id==='close-modal'||id==='retry'||id==='gift-visit'?'button':'div'));return elements.get(id);},createElement:make,createElementNS(_namespace,tag){return make(tag);},createTextNode:text=>text,addEventListener(name,handler){listeners.set(name,handler);}},location:{href:'http://localhost',origin:'http://localhost'},sessionStorage:storage,navigator:{onLine:true},URL,ApiError,acceptFarm,animalPath:()=>'',animalStatusText:(status)=>status,animalActionLabel:(status)=>status==='READY'?'Recolher produto':null,clearTimeout(){},ResizeObserver:class{constructor(callback){this.callback=callback;observers.push(this);}observe(target){this.target=target;}disconnect(){this.disconnected=true;}},Audio:class{paused=true;playCount=0;loop=false;volume=0;constructor(path){this.path=path;audioClips.push(this);}addEventListener(){}play(){this.paused=false;this.playCount++;return Promise.resolve();}pause(){this.paused=true;}},HTMLInputElement:class{},HTMLSelectElement:class{}};
 ctx.document.getElementById('farm-header').getBoundingClientRect=()=>({left:0,top:0,width:0,height:0,bottom:0});
 const uiSource=readFileSync(new URL('../src/ui.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(uiSource),ctx);
 const panelsSource=readFileSync(new URL('../src/ui-panels.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(panelsSource),ctx);
 const notificationSource=readFileSync(new URL('../src/notifications.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(notificationSource),ctx);
 const eventsSource=readFileSync(new URL('../src/events.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');runInNewContext(stripTypeScriptTypes(eventsSource),ctx);
 runInNewContext(stripTypeScriptTypes(readFileSync(new URL('../src/away-report.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'')),ctx);
 let source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('void start();','');
 source+='\nglobalThis.h={events,updateOwnFarm,refresh,logout,syncMusic,toggleMusic,toggleEffects,playEffect,shop,animalPopup,missions,achievements,decorations,friends,presentGift,closeGiftComposer,receive,dialog,health,renderHealth,positionHealth,animalTooltip,connect,setLoginStep,pickTool,tileAction,settings,avatarSettings,notify,notifyAction,loginCalendar,submitLoginReward,tickLoginCalendar,stopCalendar,dialogGeneration:()=>dialogGeneration,acceptInvitation,get:()=>({own,visited}),visit:value=>{visited=value;render();},calendarInfo:()=>calendarContext?{anchorServer:calendarContext.anchorServer,anchorClient:calendarContext.anchorClient,resetAt:calendarContext.resetAt,checkedResetAt:calendarContext.checkedResetAt,phase:calendarContext.phase,message:calendarContext.message}:undefined,setNow:value=>setMonotonic(value),set:(v)=>{own=v.own;visited=v.visited;api=v.api;catalog=v.catalog??{crops:[],fertilizers:[],animals:[],vip:{greenPrice:20}};}};';
 runInNewContext(stripTypeScriptTypes(source),ctx);return{h:{...ctx.h,runTimeouts(){for(const [id,callback]of [...timeouts]){timeouts.delete(id);callback();}}},elements,listeners,audioClips,observers,ctx,intervals};
}
function openGift(h,elements,owner='bob',name='Bob'){h.visit({...farm(owner),name});elements.get('gift-visit').onclick();}
function text(node){return(node.textContent??'')+(node.children??[]).map(child=>typeof child==='object'?text(child):child).join('');}
function descendants(node){return[node,...(node.children??[]).flatMap(child=>typeof child==='object'?descendants(child):[])];}
function loginFarm(owner='alice',available=true,revision='1',serverTime='2026-11-15T12:00:00Z',nextResetAt='2026-11-16T03:00:00Z'){
 return{...farm(owner),revision,serverTime,loginRewards:{completedDay:0,nextDay:1,available,streakReset:false,nextResetAt,rewards:[{day:1,kind:'GOLD',currency:'GOLD',amount:'250'}]}};
}

test('login reward claim locks double clicks and sends only action plus current revision',async()=>{
 const {h,elements}=app(),post=deferred();let mutations=0,request;
 const own=loginFarm(),api={pending:null,busy:false,async get(path){assert.equal(path,'/v1/me/farm');return own;},mutate(path,fields){mutations++;assert.equal(path,'/v1/me/actions');request=fields;return post.promise;}};
 h.set({own,visited:farm('bob'),catalog:{crops:[],animals:[],fertilizers:[],vip:{greenPrice:20}},api});h.receive({farm:own});h.loginCalendar();
 assert.equal(elements.get('modal-title').textContent,'Eventos');const claim=descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa');claim.onclick();const locked=descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa');claim.onclick();
 assert.equal(mutations,1);assert.equal(locked.disabled,true);assert.equal(JSON.stringify(request),JSON.stringify({action:'CLAIM_LOGIN_REWARD',expectedRevision:'1'}));
 const after={...own,revision:'2',serverTime:'2026-11-15T12:01:00Z',loginRewards:{...own.loginRewards,completedDay:1,nextDay:2,available:false}};
 post.resolve({farm:after,result:{success:true,message:'Dia 1 resgatado.'}});await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(h.get().own.revision,'2');assert.equal(elements.get('modal-title').textContent,'Eventos');assert.ok(text(elements.get('modal-content')).includes('Dia 1 resgatado.'));
});

test('calendar rollover rechecks the server once and clears its interval when the panel changes',async()=>{
 const {h,elements,intervals,ctx}=app(),before=loginFarm(),after=loginFarm('alice',true,'2','2026-11-16T03:00:01Z','2026-11-17T03:00:00Z');let reads=0;
 h.set({own:before,api:{pending:null,busy:false,get:async path=>{assert.equal(path,'/v1/me/farm');reads++;return after;}}});h.receive({farm:before});h.loginCalendar();
 assert.equal(intervals.has(1000),true);h.setNow(55_000_000);intervals.get(1000)();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(reads,1);assert.equal(h.get().own.loginRewards.available,true);intervals.get(1000)();await new Promise(resolve=>setTimeout(resolve,0));assert.equal(reads,1);
 h.dialog('Outro painel');assert.equal(intervals.has(1000),false);assert.equal(elements.get('modal-title').textContent,'Outro painel');
});

test('definitive reward revision rejection reloads the server status before another claim',async()=>{
 const {h,elements}=app(),before=loginFarm(),after=loginFarm('alice',false,'2');let reads=0,mutations=0;
 const api={pending:null,busy:false,get:async()=>{reads++;return after;},mutate:async()=>{mutations++;throw new ApiError(409,'A revisão mudou.');}};
 h.set({own:before,api});h.receive({farm:before});h.loginCalendar();descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').onclick();await new Promise(resolve=>setTimeout(resolve,0));
 const claim=descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa');assert.equal(mutations,1);assert.equal(reads,1);assert.equal(claim.disabled,true);assert.equal(h.get().own.revision,'2');assert.ok(text(elements.get('modal-content')).includes('A revisão mudou.'));
});

test('calendar data refresh failure leaves the same pending receipt recoverable',async()=>{
 const {h,elements}=app(),own=loginFarm(),body=JSON.stringify({action:'CLAIM_LOGIN_REWARD',expectedRevision:'1',requestId:'same-request'});let reads=0,replays=0;
 const api={pending:{path:'/v1/me/actions',body},busy:false,get:async()=>{reads++;throw new TypeError('offline');},retry:async()=>{replays++;api.pending=null;return{farm:{...own,revision:'2',loginRewards:{...own.loginRewards,completedDay:1,nextDay:2,available:false}},result:{success:true,message:'Recompensa confirmada.'}};}};
 h.set({own,api});h.receive({farm:own});h.loginCalendar();
 descendants(elements.get('modal-content')).find(node=>text(node)==='Atualizar calendário').onclick();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(reads,1);assert.ok(api.pending);assert.ok(text(elements.get('modal-content')).includes('offline'));assert.ok(descendants(elements.get('modal-content')).some(node=>text(node)==='Recuperar resultado'));
 descendants(elements.get('modal-content')).find(node=>text(node)==='Recuperar resultado').onclick();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(replays,1);assert.equal(api.pending,null);assert.equal(h.get().own.revision,'2');assert.ok(text(elements.get('modal-content')).includes('Recompensa confirmada.'));
});

test('calendar rollover GET failure recovers manually and only then allows an explicit claim',async()=>{
 const {h,elements,intervals}=app(),before=loginFarm('alice',false,'1','2026-11-16T02:59:50Z','2026-11-16T03:00:00Z'),after=loginFarm('alice',true,'2','2026-11-16T03:00:05Z','2026-11-17T03:00:00Z'),claimed={...after,revision:'3',loginRewards:{...after.loginRewards,completedDay:1,nextDay:2,available:false}};let reads=0,mutations=0;
 h.set({own:before,api:{pending:null,busy:false,get:async()=>{if(++reads===1)throw new TypeError('offline');return after;},mutate:async(path,fields)=>{mutations++;assert.equal(path,'/v1/me/actions');assert.equal(JSON.stringify(fields),JSON.stringify({action:'CLAIM_LOGIN_REWARD',expectedRevision:'2'}));return{farm:claimed,result:{success:true,message:'Dia 1 resgatado.'}};}}});h.receive({farm:before});h.loginCalendar();
 h.setNow(11_000);intervals.get(1000)();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(reads,1);assert.equal(mutations,0);assert.equal(h.calendarInfo().phase,'error');assert.ok(text(elements.get('modal-content')).includes('offline'));assert.equal(descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').disabled,true);
 descendants(elements.get('modal-content')).find(node=>text(node)==='Atualizar calendário').onclick();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(reads,2);assert.equal(mutations,0);assert.equal(h.get().own.revision,'2');assert.equal(h.calendarInfo().phase,'ready');assert.equal(descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').disabled,false);
 descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').onclick();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(mutations,1);assert.equal(h.get().own.revision,'3');assert.equal(h.get().own.loginRewards.available,false);assert.equal(descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').disabled,true);
});

test('returning from a hidden tab rechecks server time when the monotonic clock did not advance',async()=>{
 const {h,elements,listeners,ctx,intervals}=app(),before=loginFarm('alice',false,'1','2026-11-16T02:59:50Z','2026-11-16T03:00:00Z'),after=loginFarm('alice',true,'2','2026-11-16T03:00:05Z','2026-11-17T03:00:00Z');let reads=0;
 h.set({own:before,api:{pending:null,busy:false,get:async path=>{assert.equal(path,'/v1/me/farm');reads++;return after;}}});h.receive({farm:before});h.loginCalendar();
 const tabPanel=descendants(elements.get('modal-content')).find(node=>node.id==='events-tab-panel'),panel=tabPanel.children,scrollTarget=elements.get('modal-content'),refresh=descendants(scrollTarget).find(node=>text(node)==='Atualizar calendário');scrollTarget.scrollTop=125;refresh.focus();ctx.document.hidden=true;listeners.get('visibilitychange')();
 ctx.document.hidden=false;listeners.get('visibilitychange')();await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(reads,1);assert.equal(h.get().own.revision,'2');assert.equal(h.calendarInfo().anchorServer,Date.parse(after.serverTime));assert.equal(h.calendarInfo().anchorClient,0);
 assert.equal(descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').disabled,false);assert.ok(text(elements.get('modal-content')).includes('Próximo resgate em'));assert.equal(scrollTarget.scrollTop,125);assert.notEqual(tabPanel.children,panel);assert.equal(ctx.document.activeElement.getAttribute('data-focus-key'),'login-rewards-refresh');
 intervals.get(1000)();assert.match(text(elements.get('modal-content')),/Próximo resgate em 23 h 59 min 55 s\./);assert.equal(reads,1);
});

test('gift default selection and send remain blocked when stock contains only calendar seeds',async()=>{
 const {h,elements}=app(),own={...farm('alice'),seedCounts:{'semente-bronze':1}},crop={id:'semente-bronze',name:'Semente de Bronze',spriteStem:'semente-bronze',acquisitionSource:'LOGIN_REWARD'};let mutations=0;
 h.set({own,catalog:{crops:[crop],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null,get:async path=>path==='/v1/friends'?[{id:'bob',name:'Bob'}]:path==='/v1/gifts'?{received:[],sent:[]}:path==='/v1/me/farm'?own:farm('bob'),mutate(){mutations++;}}});
 openGift(h,elements);await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(elements.get('modal-title').textContent,'Presentear');assert.ok(!text(elements.get('modal-content')).includes('Semente de Bronze'));assert.equal(mutations,0);assert.equal(elements.get('modal-footer').children.find(node=>text(node)==='Enviar').disabled,true);
});

test('plant picker keeps reward seeds from the full catalog',()=>{
 const {h,elements}=app(),crop={id:'semente-bronze',name:'Semente de Bronze',spriteStem:'semente-bronze',acquisitionSource:'LOGIN_REWARD',requiredLevel:1};
 h.set({own:{...farm('alice'),level:2,seedCounts:{'semente-bronze':1}},catalog:{crops:[crop],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null}});h.pickTool('PLANT');
 assert.ok(descendants(elements.get('tool-tray')).some(node=>node.getAttribute('aria-label')==='Semente de Bronze, 1 sementes'));
});

test('seed bag shows only held seeds and moves the selection when the last one is planted',()=>{
 const {h,elements}=app(),crops=[{id:'nabo',name:'Nabo',spriteStem:'nabo',requiredLevel:1},{id:'milho',name:'Milho',spriteStem:'milho',requiredLevel:1},{id:'mini-bis',name:'Mini-Bis',spriteStem:'mini-bis',acquisitionSource:'LOGIN_REWARD',requiredLevel:1}];
 const own={...farm('alice'),level:5,seedCounts:{nabo:1,milho:0,'mini-bis':1}};
 h.set({own,catalog:{crops,animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null}});h.pickTool('PLANT');
 const labels=()=>descendants(elements.get('tool-tray')).map(node=>node.getAttribute?.('aria-label')).filter(Boolean);
 assert.deepEqual(labels(),['Nabo, 1 sementes','Mini-Bis, 1 sementes']);
 h.receive({farm:{...own,revision:'2',seedCounts:{nabo:0,milho:0,'mini-bis':1}}});
 assert.deepEqual(labels(),['Mini-Bis, 1 sementes']);
 assert.equal(descendants(elements.get('tool-tray')).find(node=>node.getAttribute?.('aria-label')==='Mini-Bis, 1 sementes').getAttribute('aria-pressed'),'true');
});

test('empty seed bag explains where to get seeds and planting does not send a command',async()=>{
 const {h,elements}=app(),sent=[];
 h.set({own:{...farm('alice'),seedCounts:{nabo:0}},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',requiredLevel:1,seedPrices:{GOLD:300}}],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null,mutate:async(_path,fields)=>{sent.push(fields);return{};}}});
 h.pickTool('PLANT');
 assert.match(text(elements.get('tool-tray')),/Você não tem sementes\. Compre na Loja ou resgate recompensas\./);
 assert.ok(!descendants(elements.get('tool-tray')).some(node=>node.className==='tray-item'));
 assert.doesNotMatch(text(elements.get('tool-tray')),/0 sementes/,'sem legenda de semente fantasma');
 descendants(elements.get('tool-tray')).find(node=>node.textContent==='Abrir Loja').onclick();
 assert.equal(elements.get('modal-title').textContent,'Loja');assert.equal(elements.get('tool-tray').hidden,true);
 await h.tileAction({column:0,row:0,unlocked:true,state:'PLOWED'});
 assert.equal(sent.length,0);assert.match(text(elements.get('notice')),/Você não tem sementes/);
});

test('stale calendar claim updates own farm silently after navigation and ignores logout response',async()=>{
 const {h,elements}=app(),first=deferred(),initial=loginFarm(),updated={...initial,revision:'2'};let api={pending:null,busy:false,mutate:()=>first.promise};
 h.set({own:initial,api});h.receive({farm:initial});h.loginCalendar();descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').onclick();h.dialog('Outro painel');first.resolve({farm:updated,result:{success:true,message:'late reward'}});await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(h.get().own.revision,'2');assert.equal(elements.get('modal-title').textContent,'Outro painel');assert.doesNotMatch(text(elements.get('modal-content')),/late reward/);
 const stale=deferred(),current=loginFarm('alice',true,'3');api={pending:null,busy:false,mutate:()=>stale.promise};h.set({own:current,api});h.receive({farm:current});h.loginCalendar();descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa').onclick();const logout=h.logout();stale.resolve({farm:{...current,revision:'4'},result:{success:true,message:'after logout'}});await logout;await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(h.get().own,undefined);assert.equal(elements.get('modal').open,false);
});

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
 h.pickTool('CLEAN_WEEDS');assert.match(text(elements.get('notice')),/limpeza elegível dá 2 XP.*até 20 XP de ajuda por dia/i);assert.match(text(elements.get('notice')),/limpar a própria praga não dá recompensa/i);
 h.set({own,api});h.receive({});assert.ok(!descendants(elements.get('tools')).some(n=>n.getAttribute?.('aria-label')?.startsWith('Lançar')));
});

test('pest tool guidance explains growth, watering and blocked cooldown without exposing protection',()=>{
 const {h,ctx,elements}=app();let clientNow=Date.parse('2026-11-03T12:00:00Z');ctx.Date=class extends Date{static now(){return clientNow;}};
 h.set({own:farm('alice'),visited:{...farm('bob'),serverTime:'2026-11-03T12:00:00Z'},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[],vip:{greenPrice:20}}});h.receive({});
 const bounds={left:0,top:0,width:1280,height:720};ctx.document.getElementById('game').getBoundingClientRect=()=>bounds;ctx.document.getElementById('farm').getBoundingClientRect=()=>bounds;
 const panel=ctx.document.getElementById('plant-health');panel.getBoundingClientRect=()=>({width:360,height:280});
 const tile={column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,season:1,seasons:1,remainingSeconds:3600,nominalRemainingSeconds:3600};
 h.pickTool('SPREAD_INSECTS');assert.match(text(elements.get('notice')),/em crescimento e já regada/);assert.match(text(elements.get('notice')),/bloqueada.*consome.*6h/i);assert.doesNotMatch(text(elements.get('notice')),/prote[cç][aã]o/i);h.health(tile,{x:585,y:400},'tap');assert.equal(panel.hidden,true);h.pickTool('CURSOR');h.health(tile,{x:585,y:400},'tap');assert.equal(panel.hidden,false);
 clientNow+=60000;h.runTimeouts();assert.ok(!text(panel).includes('Lançar inseto nesta planta'));h.pickTool('SPREAD_WEEDS');assert.equal(panel.hidden,true);
 assert.equal(panel.querySelector('.crop-growth-value').textContent,'59min');
});
test('friend cleanup guidance stays in notices while plant inspection stays closed',()=>{
 const {h,ctx}=app();h.set({own:farm('alice'),visited:farm('bob'),catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600}],animals:[],fertilizers:[],vip:{greenPrice:20}}});h.receive({});
 const bounds={left:0,top:0,width:1280,height:720};ctx.document.getElementById('game').getBoundingClientRect=()=>bounds;ctx.document.getElementById('farm').getBoundingClientRect=()=>bounds;
 const panel=ctx.document.getElementById('plant-health');panel.getBoundingClientRect=()=>({width:360,height:280});
 for(const [tool,tile,label]of [['CLEAN_WEEDS',{state:'GROWING',weeds:true},'Tirar ervas do amigo'],['CLEAN_INSECTS',{state:'GROWING',insects:1},'Matar insetos do amigo']]){
  h.pickTool(tool);h.health({...tile,cropId:'nabo',watered:true,season:1,seasons:1,remainingSeconds:60,nominalRemainingSeconds:60},{x:585,y:400},'tap');
  assert.equal(panel.hidden,true);assert.match(text(ctx.document.getElementById('notice')),/20 XP de ajuda por dia/);
 }
});

test('visits ignore protection data while the owner still sees the lock chip',()=>{
 const {h,ctx}=app(),catalog={crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[],vip:{greenPrice:20}};
 const tile={column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,season:1,seasons:1,remainingSeconds:3600,nominalRemainingSeconds:3600,protected:true};
 const bounds={left:0,top:0,width:1280,height:720};ctx.document.getElementById('game').getBoundingClientRect=()=>bounds;ctx.document.getElementById('farm').getBoundingClientRect=()=>bounds;
 const panel=ctx.document.getElementById('plant-health');panel.getBoundingClientRect=()=>({width:360,height:280});
 h.set({own:{...farm('alice'),own:true,serverTime:'2026-11-03T12:00:00Z'},catalog});h.receive({});
 const lock=()=>descendants(panel).some(n=>n.src==='/assets/classic/icons/icone-cadeado.png');
 h.health(tile,{x:585,y:400},'tap');assert.ok(lock());
 h.set({own:{...farm('alice'),own:true},visited:{...farm('bob'),own:false,serverTime:'2026-11-03T12:00:00Z'},catalog});h.receive({});
 h.pickTool('CURSOR');h.health({...tile,remainingSeconds:3599},{x:585,y:400},'tap');
 assert.ok(!lock());assert.equal(panel.hidden,false);assert.ok(!text(panel).includes('Lançar inseto nesta planta'));
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

test('love fertilizer button shows the love stock when visiting before it is picked',()=>{
 const {h,elements}=app(),own={...farm('alice'),fertilizers:{comum:22,amor:1}};
 h.set({own,visited:farm('bob'),catalog:{crops:[],fertilizers:[{id:'comum',name:'Comum'},{id:'amor',name:'Fertilizante do amor',friendsOnly:true}],animals:[]}});h.receive({});
 const love=()=>elements.get('tools').children.find(node=>node.title==='Fertilizante do amor');
 assert.equal(text(love()),'x1');
 h.pickTool('LOVE');assert.equal(text(love()),'x1');
});

test('love fertilizer selection does not replace the own farm fertilizer after a visit',()=>{
 const {h,elements}=app(),own={...farm('alice'),fertilizers:{comum:22,amor:1}};
 h.set({own,visited:farm('bob'),catalog:{crops:[],fertilizers:[{id:'comum',name:'Comum'},{id:'amor',name:'Fertilizante do amor',friendsOnly:true}],animals:[]}});h.receive({});
 h.pickTool('LOVE');h.set({own,visited:undefined});h.receive({});
 const fertilize=elements.get('tools').children.find(node=>node.title==='Adubar');
 assert.equal(fertilize.children.find(node=>node.className==='tool-count').textContent,'x22');assert.equal(fertilize.children[0].src,'/assets/classic/icons/adubo-comum.png');
 h.pickTool('FERTILIZE');assert.ok(!descendants(elements.get('tool-tray')).some(node=>node.getAttribute?.('aria-label')?.startsWith('Fertilizante do amor')));
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
test('open animal popup refreshes after a successful state update',()=>{
 const {h,elements}=app(),farmState={...farm('alice'),own:true,level:1,feed:{producer:1},animals:[{id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0}]};
 h.set({own:farmState,catalog:{crops:[],fertilizers:[],feedPrices:{producer:10,growth:10},animals:[{id:'vaca',name:'Vaca',kind:'PRODUCER',productName:'Leite'}],vip:{greenPrice:20}}});
 h.animalPopup('vaca');assert.match(text(elements.get('modal-content')),/Recolher produto/);
 h.receive({farm:{...farmState,revision:'2',animals:[{id:'vaca',stage:'ADULT',status:'PRODUCING',remainingSeconds:3600}]}});
 assert.doesNotMatch(text(elements.get('modal-content')),/Recolher produto/);assert.match(text(elements.get('modal-content')),/PRODUCING/);
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
test('animal popup shows protector feeding time left',()=>{
 const {h,elements}=app();
 h.set({own:{...farm('alice'),feed:{producer:0,growth:0},animals:[{id:'cachorro-robo',stage:'ADULT',status:'PRODUCING',remainingSeconds:10800}]},catalog:{crops:[],fertilizers:[],feedPrices:{producer:10,growth:10},animals:[{id:'cachorro-robo',name:'Cachorro',kind:'PROTECTOR'}],vip:{greenPrice:20}}});
 h.animalPopup('cachorro-robo');
 const content=elements.get('modal-content');
 assert.ok(text(content).includes('ALIMENTAÇÃO'));assert.ok(text(content).includes('3h 0min restantes'));
 const bar=descendants(content).find(node=>node.getAttribute('role')==='progressbar');
 assert.ok(bar);assert.equal(bar.getAttribute('aria-valuenow'),'50');assert.equal(bar.children[0].style.width,'50%');
});
test('Tarefas shows weekly XP tasks apart from the daily green ones and keeps finished ones until reset',()=>{
 const {h,elements}=app(),nextResetAt=new Date(Date.now()+(2*24*60+3*60+5)*60_000).toISOString();
 const weeklyTasks={weekStartsAt:'2026-11-22T03:00:00Z',nextResetAt,tasks:[{id:'harvest',title:'Colha 12 plantas',progress:0,target:12,rewardXp:100,done:false},{id:'buy-animals',title:'Compre 5 animais',progress:5,target:5,rewardXp:100,done:true},{id:'love-friends',title:'Adube 30 plantas de amigos com fertilizante do amor',progress:29,target:30,rewardXp:100,done:false}]};
 h.set({own:{...farm('alice'),missions:[{id:'care',title:'Cuidar da fazenda',green:1,progress:0,target:1,done:false}],weeklyTasks}});h.missions();
 const content=elements.get('modal-content'),caption=text(content);
 assert.equal(elements.get('modal-title').textContent,'Tarefas');
 assert.match(caption,/Diárias · Em andamento/);assert.match(caption,/Semanais/);
 assert.match(caption,/Reinicia domingo às 00:00 \(horário de Brasília\) · faltam 2d 3h \d+min/);
 const weekly=descendants(content).find(node=>node.className==='mission-section weekly-section');
 assert.match(text(weekly),/0 \/ 12/);assert.match(text(weekly),/29 \/ 30/);assert.match(text(weekly),/Compre 5 animais\+100  recebido/);
 const images=descendants(weekly).filter(node=>node.tag==='img').map(node=>node.src);
 assert.ok(images.length>=3);assert.ok(images.every(src=>src==='/assets/classic/icons/xp.png'),'XP nunca usa moeda verde');
 const daily=descendants(content).find(node=>node.className==='mission-section');
 assert.ok(descendants(daily).some(node=>node.src==='/assets/classic/icons/moeda-verde.png'));
});

test('Conquistas groups titles, equips only unlocked ones and removes the active title',async()=>{
 const {h,elements}=app(),sent=[];
 const achievements=[{id:'plant-100',category:'plant',categoryLabel:'🌱 Plantio de sementes',title:'•° Camponês •°',target:100,progress:100,unlocked:true},{id:'plant-250',category:'plant',categoryLabel:'🌱 Plantio de sementes',title:'★ Playboy Rural ★',target:250,progress:100,unlocked:false},{id:'level-30',category:'level',categoryLabel:'⭐ Níveis',title:'<b>★ Veterano VIP ★</b>',target:30,progress:30,unlocked:true}];
 const own={...farm('alice'),achievements,equippedTitleId:'level-30',equippedTitle:'<b>★ Veterano VIP ★</b>'};
 h.set({own,api:{pending:null,prepareRequestId:async()=>{},mutate:async(_path,fields)=>{sent.push(fields);return{result:{success:true,message:'ok'},farm:{...own,revision:'2'}};}}});h.achievements();
 const content=elements.get('modal-content'),caption=text(content);
 assert.equal(elements.get('modal-title').textContent,'Conquistas');
 assert.match(caption,/2 de 3 títulos liberados/);assert.match(caption,/🌱 Plantio de sementes/);assert.match(caption,/⭐ Níveis/);
 assert.match(caption,/100 \/ 250/);assert.match(caption,/Bloqueado/);assert.match(caption,/Equipado/);
 assert.ok(descendants(content).some(node=>node.textContent==='<b>★ Veterano VIP ★</b>'),'título é texto, não HTML');
 assert.ok(!descendants(content).some(node=>node.getAttribute?.('aria-label')==='Equipar título ★ Playboy Rural ★'));
 descendants(content).find(node=>node.getAttribute?.('aria-label')==='Equipar título •° Camponês •°').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(sent[0].action,'EQUIP_TITLE');assert.equal(sent[0].itemId,'plant-100');
 descendants(elements.get('modal-content')).find(node=>node.textContent==='Remover título').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(sent[1].action,'CLEAR_TITLE');assert.equal(sent[1].itemId,undefined);
});

test('visited wallet shows the public title next to the VIP badge',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});h.visit({...farm('bob'),name:'Bob',vipActive:true,equippedTitle:'♛ Rei do Gado ♛'});
 const wallet=elements.get('wallet');
 assert.ok(descendants(wallet).some(node=>node.className==='player-title'&&node.textContent==='♛ Rei do Gado ♛'));
 assert.ok(descendants(wallet).some(node=>node.className==='vip-badge'||String(node.className).includes('vip')),'VIP continua visível');
});

test('Decorações shows one pack at a time, confirms the purchase and applies owned packs for free',async()=>{
 const {h,elements}=app(),sent=[];
 const own={...farm('alice'),coins:'1000000',activeBackgroundId:'default',ownedBackgroundIds:['default','outono'],backgroundPrice:'1000000'};
 h.set({own,api:{pending:null,prepareRequestId:async()=>{},mutate:async(_path,fields)=>{sent.push(fields);return{result:{success:true,message:'ok'},farm:{...own,revision:'2',coins:'0',activeBackgroundId:'inverno',ownedBackgroundIds:['default','inverno','outono']}};}}});
 h.decorations(true);
 const content=()=>elements.get('modal-content'),find=label=>descendants(content()).find(node=>node.getAttribute?.('aria-label')===label||node.textContent===label);
 assert.equal(elements.get('modal-title').textContent,'Decorações');
 assert.match(text(content()),/Clássico1 \/ 11Padrão · Equipado/);
 find('Fundo anterior').onclick();assert.match(text(content()),/Jardim oriental11 \/ 11Não comprado/);
 find('Próximo fundo').onclick();find('Próximo fundo').onclick();
 assert.match(text(content()),/Inverno2 \/ 11Não comprado/);
 find('Noite').onclick();
 assert.ok(descendants(content()).some(node=>node.className==='decoration-preview'&&node.src==='/assets/classic/background/background-inverno-noite.png'));
 find('Comprar e aplicar · 1.000.000 ouro').onclick();
 assert.equal(sent.length,0,'a compra pede confirmação');assert.match(text(content()),/Gastar 1\.000\.000 ouro em Inverno\?/);
 find('Confirmar compra').onclick();await new Promise(r=>setTimeout(r,0));
 assert.deepEqual({...sent[0],expectedRevision:undefined},{action:'BUY_BACKGROUND',itemId:'inverno',currency:'GOLD',expectedRevision:undefined});
 assert.match(text(content()),/Inverno2 \/ 11Equipado/,'o refresh mantém o slide e mostra o fundo ativo');
 find('Próximo fundo').onclick();assert.match(text(content()),/Outono3 \/ 11Comprado/);
 find('Aplicar').onclick();await new Promise(r=>setTimeout(r,0));
 assert.deepEqual({...sent[1],expectedRevision:undefined},{action:'EQUIP_BACKGROUND',itemId:'outono',expectedRevision:undefined});
 find('Próximo fundo').onclick();
 const short=find('Ouro insuficiente');assert.ok(short);assert.equal(short.disabled,true);
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
test('fertilizers live in the Sementes tab under both seed filters with every catalog duration',()=>{
 const {h,elements}=app(),fertilizers=JSON.parse(readFileSync(new URL('../../assets/data/fertilizers.json',import.meta.url),'utf8'));
 h.set({own:{...farm('alice'),level:50},catalog:{crops:[],animals:[],fertilizers,vip:{greenPrice:10}}});h.shop();
 const content=elements.get('modal-content');
 assert.deepEqual(descendants(content).find(node=>node.className==='tabs shop-tabs').children.map(node=>node.textContent),['Sementes','Animais','VIP']);
 for(const filter of ['VIP','Comuns']){
  const caption=text(content);assert.match(caption,/Fertilizantes/);
  for(const fertilizer of fertilizers){assert.ok(caption.includes(fertilizer.name));assert.ok(caption.includes('Reduz '+uiPresentation.fertilizerDuration(fertilizer.reductionSeconds)));}
  descendants(content).find(node=>node.className==='tabs seed-tabs').children.find(node=>node.textContent===filter).onclick();
 }
});

test('seed and fertilizer cards buy 1 to 99 units in one command with the total price',async()=>{
 const {h,elements}=app(),sent=[];
 const own={...farm('alice'),level:10,coins:'1000',greenCoins:'0',seedCounts:{nabo:4},fertilizers:{comum:1}};
 const catalog={crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600,seasons:1,requiredLevel:1,seedPrices:{GOLD:30},vipOnly:false}],animals:[],fertilizers:[{id:'comum',name:'Fertilizante comum',prices:{GOLD:1000},requiredLevel:10,reductionSeconds:3600,friendsOnly:false}],vip:{greenPrice:10}};
 h.set({own,catalog,api:{pending:null,prepareRequestId:async()=>{},mutate:async(_path,fields)=>{sent.push(fields);return{result:{success:true,message:'ok'},farm:{...own,revision:'2',seedCounts:{nabo:7}}};}}});h.shop();
 const content=elements.get('modal-content'),find=label=>descendants(content).find(node=>node.getAttribute('aria-label')===label);
 assert.match(text(content),/Você tem 4/);assert.match(text(content),/Você tem 1/);
 find('Comprar Nabo por 30 ouro').onclick();await new Promise(r=>setTimeout(r,0));
 assert.deepEqual({...sent[0],expectedRevision:undefined},{action:'BUY_SEED',itemId:'nabo',currency:'GOLD',expectedRevision:undefined},'uma unidade mantém o comando antigo');
 find('Aumentar quantidade de Nabo').onclick();find('Aumentar quantidade de Nabo').onclick();
 const buy=find('Comprar 3 Nabo por 90 ouro');assert.ok(buy);assert.equal(buy.disabled,false);
 buy.onclick();await new Promise(r=>setTimeout(r,0));
 assert.deepEqual({...sent[1],expectedRevision:undefined},{action:'BUY_SEED',itemId:'nabo',currency:'GOLD',quantity:3,expectedRevision:undefined});
 assert.match(text(elements.get('modal-content')),/Você tem 7/,'o estoque vem da resposta do servidor');
 assert.ok(find('Comprar 3 Nabo por 90 ouro'),'a quantidade escolhida sobrevive ao refresh');
 const field=find('Quantidade de Nabo, de 1 a 99');field.value='150';field.onchange();assert.equal(field.value,'99');
 assert.equal(find('Diminuir quantidade de Nabo').disabled,false);assert.equal(find('Aumentar quantidade de Nabo').disabled,true);
 const expensive=find('Comprar 99 Nabo por 2.970 ouro');assert.equal(expensive.disabled,true);assert.equal(expensive.title,'Saldo de ouro insuficiente para 99 unidades');
 assert.match(text(content),/Saldo de ouro insuficiente para 99 unidades/,'o motivo aparece como texto, não só no tooltip');
 field.value='abc';field.onchange();assert.equal(field.value,'99','texto inválido volta à última quantidade válida');
 find('Aumentar quantidade de Fertilizante comum').onclick();
 assert.equal(find('Comprar 2 Fertilizante comum por 2.000 ouro').disabled,true);
});

test('typed shop quantity drives the arrows, the total and the purchase',async()=>{
 const {h,elements}=app(),sent=[];
 const own={...farm('alice'),level:10,coins:'1000',greenCoins:'0',seedCounts:{nabo:0},fertilizers:{}};
 const catalog={crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600,seasons:1,requiredLevel:1,seedPrices:{GOLD:30},vipOnly:false}],animals:[],fertilizers:[],vip:{greenPrice:10}};
 h.set({own,catalog,api:{pending:null,prepareRequestId:async()=>{},mutate:async(_path,fields)=>{sent.push(fields);return{result:{success:true,message:'ok'},farm:{...own,revision:'2'}};}}});h.shop();
 const content=elements.get('modal-content'),find=label=>descendants(content).find(node=>node.getAttribute('aria-label')===label);
 const field=find('Quantidade de Nabo, de 1 a 99');field.value='10';field.oninput();
 assert.ok(find('Comprar 10 Nabo por 300 ouro'),'o total acompanha a digitação');
 field.onkeydown({key:'ArrowUp',preventDefault(){}});assert.equal(field.value,'11');
 field.value='';field.oninput();assert.ok(find('Comprar 11 Nabo por 330 ouro'),'campo vazio não zera a quantidade');
 field.onblur();assert.equal(field.value,'11');
 find('Comprar 11 Nabo por 330 ouro').onclick();await new Promise(r=>setTimeout(r,0));
 assert.equal(sent[0].quantity,11);
 const fresh=find('Quantidade de Nabo, de 1 a 99');fresh.value='40';fresh.oninput();
 assert.equal(find('Comprar 40 Nabo por 1.200 ouro').disabled,true);assert.match(text(content),/Saldo de ouro insuficiente para 40 unidades/);
 fresh.value='2';fresh.oninput();assert.doesNotMatch(text(content),/insuficiente/);
});
test('shop quantities do not leak to the next account',async()=>{
 const {h,elements}=app();
 const catalog={crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600,seasons:1,requiredLevel:1,seedPrices:{GOLD:30},vipOnly:false}],animals:[],fertilizers:[],vip:{greenPrice:10}};
 const find=label=>descendants(elements.get('modal-content')).find(node=>node.getAttribute('aria-label')===label);
 h.set({own:{...farm('alice'),level:10,coins:'1000'},catalog});h.shop();
 find('Aumentar quantidade de Nabo').onclick();assert.ok(find('Comprar 2 Nabo por 60 ouro'));
 await h.logout();
 h.set({own:{...farm('bob'),level:10,coins:'1000'},catalog});h.shop();
 assert.ok(find('Comprar Nabo por 30 ouro'),'Bob começa em uma unidade');
});

test('weekly countdown follows the server clock, not a wrong device clock',()=>{
 const {h,elements}=app(),own={...farm('alice'),serverTime:'2026-11-20T12:00:00Z',missions:[],weeklyTasks:{weekStartsAt:'2026-11-15T03:00:00Z',nextResetAt:'2026-11-22T03:00:00Z',tasks:[]}};
 h.set({own});h.receive({farm:own});h.missions();
 assert.match(text(elements.get('modal-content')),/faltam 1d 15h 0min/,'o relógio do aparelho (hoje real) não muda a contagem');
});
test('friend list refresh keeps, changes and removes the public title next to the VIP badge',async()=>{
 const {h,elements}=app();let list=[{id:'bob',name:'Bob',vipActive:true,equippedTitle:'♛ Rei do Gado ♛'}];
 const own=farm('alice');h.set({own,api:{get:async path=>path==='/v1/friends'?list:own}});await h.friends();
 const identity=()=>descendants(elements.get('modal-content')).find(node=>node.className==='friend-identity');
 const title=()=>descendants(identity()).find(node=>node.className==='player-title')?.textContent;
 const vip=()=>descendants(identity()).some(node=>node.className?.includes?.('vip'));
 assert.equal(title(),'♛ Rei do Gado ♛');assert.ok(vip());
 const refresh=async()=>{h.receive({farm:{...h.get().own,revision:String(Number(h.get().own.revision)+1)}});for(let i=0;i<3;i++)await new Promise(r=>setTimeout(r,0));};
 await refresh();assert.equal(title(),'♛ Rei do Gado ♛','o refresh não apaga o título');assert.ok(vip(),'nem o selo VIP');
 list=[{...list[0],equippedTitle:'★ Mão na Enxada ★'}];await refresh();assert.equal(title(),'★ Mão na Enxada ★');
 list=[{...list[0],equippedTitle:null,vipActive:false}];await refresh();assert.equal(title(),undefined);assert.equal(vip(),false);
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
const awayReport={id:'11111111-1111-4111-8111-111111111111',awaySeconds:33600,harvests:[{cropId:'morango',name:'Morango',stem:'morango',quantity:12}],products:[],readyProducts:[],thefts:[],defenses:[],care:{waterings:0,pestsRemoved:0,animalCares:0},
 pending:[{kind:'AUTOMATION',serviceId:'water',name:'Rega automática',expiredAt:'2026-11-13T06:12:00Z'},{kind:'FEED',animalId:'cachorro-robo',name:'Cachorro',stock:2},{kind:'VIP',expiredAt:'2026-11-13T06:00:00Z'}]};
const notesFor=version=>({version,prNumber:42,date:'2026-11-08',items:['Novos cuidados VIP.']});
test('away report opens before update notes and closing it shows the notes',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});
 h.receive({farm:{...farm('alice'),revision:'2',awayReport,updateNotes:notesFor('v1')}});
 assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);assert.match(text(elements.get('modal-content')),/12 morangos/);
 descendants(elements.get('modal-footer')).find(n=>n.textContent==='Ir para a fazenda').onclick();
 assert.equal(elements.get('modal-title').textContent,'Notas da atualização');
});
test('away report waits for an open dialog and is not duplicated by later refreshes',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});h.dialog('Ajustes');
 h.receive({farm:{...farm('alice'),revision:'2',awayReport}});assert.equal(elements.get('modal-title').textContent,'Ajustes');
 elements.get('close-modal').onclick();assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);
 descendants(elements.get('modal-footer')).find(n=>n.textContent==='Ir para a fazenda').onclick();
 h.receive({farm:{...farm('alice'),revision:'3',awayReport}});assert.equal(elements.get('modal').open,false);
});
test('away report from a previous account is dropped',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});h.dialog('Ajustes');
 h.receive({farm:{...farm('alice'),revision:'2',awayReport}});
 h.set({own:farm('bob')});h.receive({farm:{...farm('bob'),revision:'2'}});
 elements.get('close-modal').onclick();assert.doesNotMatch(text(elements.get('modal-content')),/Sua fazenda rendeu!/);
});
test('renew and feed run the existing commands inside the panel',async()=>{
 const {h,elements}=app(),sent=[];
 h.set({own:{...farm('alice'),vipActive:true},api:{pending:null,ackAwayReport:async()=>{},mutate:async(_path,command)=>{sent.push(command);return {result:{success:true,message:'ok'},farm:{...farm('alice'),vipActive:true,revision:String(sent.length+2)}};},prepareRequestId:async()=>{}}});
 h.receive({farm:{...farm('alice'),vipActive:true,revision:'2',awayReport}});
 const find=label=>descendants(elements.get('modal-content')).find(n=>n.textContent===label);
 find('Renovar').onclick();find('Confirmar').onclick();await new Promise(r=>setTimeout(r,0));
 assert.deepEqual({action:sent[0].action,itemId:sent[0].itemId},{action:'BUY_AUTOMATION',itemId:'water'});
 assert.match(text(elements.get('modal-content')),/Rega automática ativa ✓/);assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);
 find('Alimentar').onclick();find('Confirmar').onclick();await new Promise(r=>setTimeout(r,0));
 assert.deepEqual({action:sent[1].action,itemId:sent[1].itemId},{action:'TEND_ANIMAL',itemId:'cachorro-robo'});
 assert.match(text(elements.get('modal-content')),/Cachorro alimentado ✓/);
});
test('away report is shown and acknowledged even when its snapshot loses to a newer revision',async()=>{
 const {h,elements}=app(),acks=[];h.set({own:{...farm('alice'),revision:'5'},api:{pending:null,ackAwayReport:async id=>{acks.push(id);}}});
 h.receive({farm:{...farm('alice'),revision:'4',awayReport}});
 assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);assert.equal(h.get().own.revision,'5');
 await new Promise(r=>setTimeout(r,0));assert.deepEqual(acks,[awayReport.id]);
});
test('reversed GET replies show the report once and resend a lost acknowledgement without reopening',async()=>{
 const {h,elements}=app(),acks=[];h.set({own:farm('alice'),api:{pending:null,ackAwayReport:async id=>{acks.push(id);if(acks.length===1)throw new Error('offline');}}});
 h.receive({farm:{...farm('alice'),revision:'3'}});h.receive({farm:{...farm('alice'),revision:'2',awayReport}});
 assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);
 descendants(elements.get('modal-footer')).find(n=>n.textContent==='Ir para a fazenda').onclick();await new Promise(r=>setTimeout(r,0));
 h.receive({farm:{...farm('alice'),revision:'4',awayReport}});await new Promise(r=>setTimeout(r,0));
 assert.equal(elements.get('modal').open,false);assert.deepEqual(acks,[awayReport.id,awayReport.id]);
});
test('a report that fails to render is not acknowledged and shows on the next reply',async()=>{
 const {h,elements}=app(),acks=[];h.set({own:farm('alice'),api:{pending:null,ackAwayReport:async id=>{acks.push(id);}}});
 h.receive({farm:{...farm('alice'),revision:'2',awayReport:{...awayReport,harvests:null}}});
 await new Promise(r=>setTimeout(r,0));assert.deepEqual(acks,[]);assert.equal(elements.get('modal').open,false);
 h.receive({farm:{...farm('alice'),revision:'3',awayReport}});
 assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);await new Promise(r=>setTimeout(r,0));assert.deepEqual(acks,[awayReport.id]);
});
test('a reload before the acknowledgement shows the report again',()=>{
 for(let run=0;run<2;run++){const {h,elements}=app();h.set({own:farm('alice')});
  h.receive({farm:{...farm('alice'),revision:'2',awayReport}});assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);}
});
test('two absences with identical content are two reports',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});
 h.receive({farm:{...farm('alice'),revision:'2',awayReport}});
 descendants(elements.get('modal-footer')).find(n=>n.textContent==='Ir para a fazenda').onclick();
 h.receive({farm:{...farm('alice'),revision:'3',awayReport:{...awayReport,id:'22222222-2222-4222-8222-222222222222'}}});
 assert.match(text(elements.get('modal-content')),/Sua fazenda rendeu!/);
});
test('expired VIP in the report opens the shop on the VIP tab',()=>{
 const {h,elements}=app();h.set({own:farm('alice')});
 h.receive({farm:{...farm('alice'),revision:'2',awayReport:{...awayReport,pending:[{kind:'VIP',expiredAt:'2026-11-13T06:00:00Z'}]}}});
 descendants(elements.get('modal-content')).find(n=>n.textContent==='Renovar').onclick();
 assert.equal(elements.get('modal-title').textContent,'Loja');assert.equal(elements.get('modal').dataset.shopCategory,'vip');
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

test('reconnecting re-syncs the mobile camera without stacking camera listeners',async()=>{
 const {h,elements,ctx}=app();
 ctx.Scene=class{update(){}close(){}};
 ctx.ApiClient=class{pending=null;get(){return Promise.resolve(farm('alice'));}prepareRequestId(){return Promise.resolve();}};
 for(let round=0;round<3;round++){h.set({own:undefined});await h.connect('alice',async()=> 'synthetic-token');await h.logout();}
 assert.equal(ctx.cameraSyncs,3,'each new Scene restores the camera resolution');
 assert.equal(elements.get('farm').listenerCounts.camerachange,1);
});

const sundayFixture={id:'sunday-harvest-2026-11-22',state:'ACTIVE',multiplier:2,startsAt:'2026-11-22T03:00:00Z',endsAt:'2026-11-23T03:00:00Z',nextStartsAt:'2026-11-29T03:00:00Z',nextEndsAt:'2026-11-30T03:00:00Z',cropIds:['nabo'],timezone:'America/Sao_Paulo'};
test('plant inspection closes on tool selection and ignores hover, tap and refresh from other tools',()=>{
 for(const visited of [undefined,farm('bob')]){
  const {h,elements}=app();
  h.set({own:farm('alice'),visited,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600}],animals:[],fertilizers:[]}});
  h.health(null,null);const panel=elements.get('plant-health');panel.hidden=true;panel.getBoundingClientRect=()=>({width:360,height:280});
  const tile={column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,remainingSeconds:600,nominalRemainingSeconds:600,season:1,seasons:1};
  const tools=visited?['STEAL','LOVE','SPREAD_WEEDS','SPREAD_INSECTS','CLEAN_WEEDS','CLEAN_INSECTS']:['WATER','PLOW','PLANT','FERTILIZE','HARVEST','WEED','PESTICIDE'];
  for(const tool of tools){
   h.pickTool('CURSOR');h.health(tile,{x:585,y:400},'tap');assert.equal(panel.hidden,false);
   h.pickTool(tool);assert.equal(panel.hidden,true,`${tool} must close a pinned inspection`);
   for(const pointer of ['mouse','touch','tap','refresh','resize']){h.health(tile,{x:585,y:400},pointer);h.runTimeouts();assert.equal(panel.hidden,true,`${tool}/${pointer} must not reopen inspection`);}
  }
 }
});

test('switching tools cancels a pending plant hover before it opens',()=>{
 const {h,elements}=app();h.set({own:farm('alice'),catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600}],animals:[],fertilizers:[]}});
 h.health(null,null);const panel=elements.get('plant-health');panel.hidden=true;panel.getBoundingClientRect=()=>({width:360,height:280});
 h.health({column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,remainingSeconds:600,nominalRemainingSeconds:600,season:1,seasons:1},{x:585,y:400});
 assert.equal(panel.hidden,true);h.pickTool('WATER');h.runTimeouts();assert.equal(panel.hidden,true);
});

test('the scene inspection gate tracks toolbar changes and delayed renders cannot bypass it',async()=>{
 const {h,ctx}=app();let canInspect;
 ctx.Scene=class{constructor(...args){canInspect=args.at(-1);}update(){}close(){}};
 ctx.ApiClient=class{pending=null;get(){return Promise.resolve(farm('alice'));}prepareRequestId(){return Promise.resolve();}};
 await h.connect('alice',async()=> 'synthetic-token');h.set({own:h.get().own});assert.equal(canInspect(),true);
 h.pickTool('WATER');assert.equal(canInspect(),false);
 h.renderHealth({cropId:'nabo'},{x:585,y:400});assert.equal(ctx.document.getElementById('plant-health').hidden,true);
 h.pickTool('CURSOR');assert.equal(canInspect(),true);
});

test('plant panels stay between real controls or switch to document flow when space is insufficient',()=>{
 for(const [width,height,panelHeight,toolsTop,trayTop,headerHeight]of [[390,844,260,640,520,176],[844,390,160,330,300,90],[390,667,360,470,440,176]]){
  const {h,ctx}=app();ctx.window.innerWidth=width;ctx.window.innerHeight=height;
  ctx.window.matchMedia=query=>({matches:query.includes('orientation:landscape')?width>height&&height<=650:width<=1100});
  const bounds={left:0,top:0,width,height};
  for(const id of ['game','farm'])ctx.document.getElementById(id).getBoundingClientRect=()=>bounds;
  ctx.document.getElementById('farm-header').getBoundingClientRect=()=>({top:0,height:headerHeight});
  ctx.document.getElementById('tools-bar').getBoundingClientRect=()=>({left:0,top:toolsTop,width,height:height-toolsTop});
  const tray=ctx.document.getElementById('tool-tray');tray.hidden=false;tray.getBoundingClientRect=()=>({left:0,top:trayTop,width,height:50});
  const panel=ctx.document.getElementById('plant-health');panel.className='crop-tooltip';panel.getBoundingClientRect=()=>({width:Number.parseFloat(panel.style.width)||360,height:panelHeight});
  h.positionHealth(panel,ctx.document.getElementById('game'),{x:585,y:400});
  if(panelHeight>trayTop-headerHeight-24){
   assert.equal(panel.dataset.layout,'flow');assert.equal(panel.style.position,'relative');assert.equal(panel.style.maxHeight,'none');continue;
  }
  assert.equal(panel.style.position,'fixed');assert.equal(panel.dataset.layout,'fixed');
  const left=Number.parseFloat(panel.style.left),top=Number.parseFloat(panel.style.top),panelWidth=Number.parseFloat(panel.style.width);
  assert.ok(left>=12&&left+panelWidth<=width-12);
  assert.ok(top>=headerHeight+12&&top+panelHeight<=trayTop-12,`${width}×${height}: the entire panel must fit between header and tray`);
  tray.hidden=true;h.positionHealth(panel,ctx.document.getElementById('game'),{x:585,y:400});
  assert.equal(Number.parseFloat(panel.style.top)+panelHeight,toolsTop-12);
 }
});

test('leaving inspection restores the scene after the flow fallback',()=>{
 const {h,ctx,listeners}=app(),classes=new Set();h.set({own:farm('alice'),catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600}],animals:[],fertilizers:[]}});
 const game=ctx.document.getElementById('game');game.classList={add:name=>classes.add(name),remove:name=>classes.delete(name)};
 ctx.window.innerWidth=390;ctx.window.innerHeight=667;ctx.window.matchMedia=query=>({matches:!query.includes('orientation:landscape')});
 game.getBoundingClientRect=()=>({left:0,top:0,width:390,height:667});ctx.document.getElementById('farm').getBoundingClientRect=game.getBoundingClientRect;
 ctx.document.getElementById('farm-header').getBoundingClientRect=()=>({top:0,height:176});ctx.document.getElementById('tools-bar').getBoundingClientRect=()=>({top:470});ctx.document.getElementById('tool-tray').hidden=true;
 ctx.document.getElementById('plant-health').getBoundingClientRect=()=>({height:360});
 h.health({column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,remainingSeconds:600,nominalRemainingSeconds:600,season:1,seasons:1},{x:585,y:400},'mouse');h.runTimeouts();
 assert.equal(classes.has('inspection-flow'),true);
 h.health(null,null,'mouse');assert.equal(ctx.document.getElementById('plant-health').hidden,false,'hiding the canvas must not dismiss inspection');
 for(const id of ['tools-bar','tool-tray','farm-header']){listeners.get('pointerdown')({target:ctx.document.getElementById(id)});assert.equal(classes.has('inspection-flow'),true,'controls must not move before their click');}
 h.pickTool('WATER');assert.equal(classes.has('inspection-flow'),false);assert.equal(ctx.document.getElementById('plant-health').hidden,true);
});

for(const pointer of ['mouse','tap'])test(`${pointer} inspection restores its pinning intent after flow and resize`,()=>{
 for(const [width,height,layout]of [[390,844,'fixed'],[1366,900,'anchored']]){
  const {h,ctx}=app();h.set({own:farm('alice'),catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:3600}],animals:[],fertilizers:[]}});
  let size={left:0,top:0,width:390,height:667};
  ctx.window.innerWidth=size.width;ctx.window.innerHeight=size.height;ctx.window.matchMedia=query=>({matches:query.includes('orientation:landscape')?size.width>size.height&&size.height<=650:size.width<=1100});
  const game=ctx.document.getElementById('game'),panel=ctx.document.getElementById('plant-health');
  game.getBoundingClientRect=()=>size;ctx.document.getElementById('farm').getBoundingClientRect=()=>size;
  ctx.document.getElementById('farm-header').getBoundingClientRect=()=>({top:0,height:176});ctx.document.getElementById('tools-bar').getBoundingClientRect=()=>({top:size.height-184});ctx.document.getElementById('tool-tray').hidden=true;
  panel.hidden=true;panel.getBoundingClientRect=()=>({height:360});
  const tile={column:0,row:0,cropId:'nabo',state:'GROWING',watered:true,remainingSeconds:600,nominalRemainingSeconds:600,season:1,seasons:1},point={x:585,y:400};
  h.health(tile,point,pointer);h.runTimeouts();assert.equal(panel.dataset.layout,'flow');
  h.health(null,null,'mouse');assert.equal(panel.hidden,false,'flow temporarily retains both hover and tap inspections');
  size={left:0,top:0,width,height};ctx.window.innerWidth=width;ctx.window.innerHeight=height;
  h.health(tile,point,'resize');assert.equal(panel.dataset.layout,layout);
  h.health(null,null,'mouse');assert.equal(panel.hidden,pointer==='mouse','only an explicit tap stays pinned after resize');
  panel.listeners.pointerleave();assert.equal(panel.hidden,pointer==='mouse');
 }
});

test('desktop plant popups respect visible bounds and tools while animal positioning stays anchored',()=>{
 const {h,ctx}=app();ctx.window.innerWidth=1366;ctx.window.innerHeight=768;
 const game=ctx.document.getElementById('game'),panel=ctx.document.getElementById('plant-health');
 game.getBoundingClientRect=()=>({left:0,top:0,width:1366,height:768});ctx.document.getElementById('farm').getBoundingClientRect=game.getBoundingClientRect;
 ctx.document.getElementById('tools-bar').getBoundingClientRect=()=>({top:670,height:80});ctx.document.getElementById('tool-tray').hidden=true;
 panel.className='crop-tooltip';panel.getBoundingClientRect=()=>({width:360,height:280});
 for(const point of [{x:20,y:20},{x:1580,y:880},{x:800,y:450}]){
  h.positionHealth(panel,game,point);const top=Number.parseFloat(panel.style.top),left=Number.parseFloat(panel.style.left);
  assert.equal(panel.style.position,'absolute');assert.ok(top>=12&&top+280<=658);assert.ok(left>=12&&left+360<=1354);
 }
 ctx.window.matchMedia=()=>({matches:true});panel.className='crop-tooltip animal-tooltip';h.positionHealth(panel,game,{x:800,y:450});
 assert.equal(panel.style.position,'absolute');assert.equal(panel.dataset.layout,'anchored');
});

test('events dialog renders countdown and XP from server time, survives failures and stops on close',async()=>{
 const {h,elements,intervals}=app();
 h.set({own:{...farm('alice'),serverTime:sundayFixture.startsAt,sundayHarvestEvent:sundayFixture},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[]},api:{get:async()=>{throw Error('offline');}}});
 h.updateOwnFarm({...h.get().own});h.events();
 assert.ok(text(elements.get('modal-content')).includes('15 XP / 30 XP no evento'));
 assert.ok(text(elements.get('modal-content')).includes('Termina em 1d'));
 assert.ok(!descendants(elements.get('modal-footer')).some(node=>text(node)==='Atualizar eventos'),'no manual refresh button');
 const content=elements.get('modal-content'),panel=descendants(content).find(node=>node.id==='events-tab-panel'),first=panel.children[0];h.setNow(10000);intervals.get(1000)();assert.equal(panel.children[0],first,'timer preserves cards and scroll');
 h.setNow(86_400_000+1000);intervals.get(1000)();await new Promise(r=>setTimeout(r,0));
 assert.ok(text(content).includes('Não foi possível atualizar'),'the end of the event reloads the schedule by itself');assert.ok(h.get().own);
 h.dialog('Loja');assert.equal(intervals.has(1000),false);
});
test('missing event information leaves game accessible and shows retry',async()=>{
 const {h,elements}=app();h.set({own:farm('alice'),api:{get:async()=>{throw Error('offline');}}});h.events();await new Promise(r=>setTimeout(r,0));
 assert.ok(text(elements.get('modal-content')).includes('indisponíveis'));assert.ok(h.get().own);assert.ok(text(elements.get('modal-content')).includes('tenta de novo sozinha'));assert.ok(!descendants(elements.get('modal-footer')).some(node=>text(node)==='Atualizar eventos'));
});

test('Eventos holds the daily calendar as a tab and each tab stops the other timer',async()=>{
 const {h,elements,intervals}=app(),own={...loginFarm(),sundayHarvestEvent:sundayFixture,serverTime:sundayFixture.startsAt};let reads=0;
 h.set({own,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null,busy:false,get:async()=>{reads++;return own;}}});h.receive({farm:own});
 h.events();
 const tabs=()=>descendants(elements.get('modal-content')).filter(node=>node.getAttribute?.('role')==='tab');
 assert.equal(elements.get('modal-title').textContent,'Eventos');assert.deepEqual(tabs().map(text),['Eventos ativos','Calendário Diário']);
 assert.deepEqual(tabs().map(tab=>tab.getAttribute('aria-selected')),['true','false']);assert.ok(text(elements.get('modal-content')).includes('15 XP / 30 XP no evento'));assert.equal(h.calendarInfo(),undefined);
 const dialogs=h.dialogGeneration();tabs()[1].onclick();
 assert.equal(h.dialogGeneration(),dialogs,'switching tabs keeps the same dialog');assert.deepEqual(tabs().map(tab=>tab.getAttribute('aria-selected')),['false','true']);
 assert.ok(descendants(elements.get('modal-content')).some(node=>text(node)==='Resgatar recompensa'));assert.ok(descendants(elements.get('modal-content')).some(node=>text(node)==='Atualizar calendário'),'calendar keeps its own recovery refresh');
 assert.ok(!text(elements.get('modal-content')).includes('15 XP / 30 XP no evento'));assert.equal(h.calendarInfo().phase,'ready');assert.equal(intervals.has(1000),true);
 tabs()[0].onclick();assert.equal(h.calendarInfo(),undefined,'leaving the calendar tab stops its context');assert.ok(text(elements.get('modal-content')).includes('15 XP / 30 XP no evento'));
 h.loginCalendar();assert.equal(elements.get('modal-title').textContent,'Eventos');assert.deepEqual(tabs().map(tab=>tab.getAttribute('aria-selected')),['false','true']);
 h.dialog('Loja');assert.equal(intervals.has(1000),false);assert.equal(h.calendarInfo(),undefined);assert.equal(reads,0);
});

test('a claim that finishes after leaving and reopening the calendar tab updates the calendar on screen',async()=>{
 const {h,elements}=app(),post=deferred(),before={...loginFarm(),sundayHarvestEvent:sundayFixture},after={...before,revision:'2',loginRewards:{...before.loginRewards,completedDay:1,nextDay:2,available:false}};let reads=0;
 h.set({own:before,catalog:{crops:[],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null,busy:false,get:async()=>{reads++;return before;},mutate:()=>post.promise}});h.receive({farm:before});h.loginCalendar();
 const tabs=()=>descendants(elements.get('modal-content')).filter(node=>node.getAttribute?.('role')==='tab'),claim=()=>descendants(elements.get('modal-content')).find(node=>text(node)==='Resgatar recompensa');
 claim().onclick();tabs()[0].onclick();tabs()[1].onclick();
 post.resolve({farm:after,result:{success:true,message:'Dia 1 resgatado.'}});await new Promise(resolve=>setTimeout(resolve,0));await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(h.get().own.revision,'2');assert.equal(h.calendarInfo().phase,'ready');
 assert.ok(text(elements.get('modal-content')).includes('Dia 1 resgatado.'),'the reopened tab shows the result');assert.equal(claim().disabled,true,'the claimed day is no longer offered');assert.equal(reads,0,'no extra GET is needed');
});

test('events tab reloads once at the start and once at the end of the same event ID',async()=>{
 const {h,elements,intervals}=app(),upcoming={...sundayFixture,state:'UPCOMING'},active={...sundayFixture,state:'ACTIVE'},next={...sundayFixture,id:'sunday-harvest-2026-11-29',state:'UPCOMING',startsAt:sundayFixture.nextStartsAt,endsAt:sundayFixture.nextEndsAt};
 const replies=[{...farm('alice'),revision:'2',serverTime:'2026-11-22T03:00:01Z',sundayHarvestEvent:active},{...farm('alice'),revision:'3',serverTime:'2026-11-23T03:00:01Z',sundayHarvestEvent:next}];let reads=0;
 h.set({own:{...farm('alice'),serverTime:'2026-11-22T02:59:50Z',sundayHarvestEvent:upcoming},catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[]},api:{get:async()=>replies[Math.min(reads++,1)]}});
 h.updateOwnFarm({...h.get().own});h.events();assert.equal(reads,0);
 h.setNow(11_000);intervals.get(1000)();await new Promise(r=>setTimeout(r,0));assert.equal(reads,1,'start reloads');assert.equal(h.get().own.sundayHarvestEvent.state,'ACTIVE');
 intervals.get(1000)();intervals.get(1000)();await new Promise(r=>setTimeout(r,0));assert.equal(reads,1,'no reload on every tick');
 h.setNow(11_000+86_400_000);intervals.get(1000)();await new Promise(r=>setTimeout(r,0));assert.equal(reads,2,'end reloads even with the same event ID');
 intervals.get(1000)();intervals.get(1000)();await new Promise(r=>setTimeout(r,0));assert.equal(reads,2);assert.ok(text(elements.get('modal-content')).includes('Próximo domingo'));
});

test('a calendar refresh that finishes after leaving and reopening the tab updates the calendar on screen',async()=>{
 const {h,elements}=app(),reply=deferred(),before={...loginFarm('alice',false,'1','2026-11-16T02:59:50Z','2026-11-16T03:00:00Z'),sundayHarvestEvent:sundayFixture},after={...loginFarm('alice',true,'2','2026-11-16T03:00:05Z','2026-11-17T03:00:00Z'),sundayHarvestEvent:sundayFixture};let reads=0;
 h.set({own:before,catalog:{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo',growthSeconds:36000}],animals:[],fertilizers:[],vip:{greenPrice:20}},api:{pending:null,busy:false,get:()=>{reads++;return reply.promise;}}});h.receive({farm:before});h.loginCalendar();
 const tabs=()=>descendants(elements.get('modal-content')).filter(node=>node.getAttribute?.('role')==='tab'),find=label=>descendants(elements.get('modal-content')).find(node=>text(node)===label);
 assert.equal(find('Resgatar recompensa').disabled,true);
 find('Atualizar calendário').onclick();tabs()[0].onclick();tabs()[1].onclick();assert.equal(reads,1,'reopening reuses the GET in flight');
 reply.resolve(after);await new Promise(resolve=>setTimeout(resolve,0));await new Promise(resolve=>setTimeout(resolve,0));
 assert.equal(h.get().own.revision,'2');assert.equal(h.calendarInfo().anchorServer,Date.parse(after.serverTime),'the clock of the tab on screen moves');assert.equal(h.calendarInfo().resetAt,after.loginRewards.nextResetAt);
 assert.equal(find('Resgatar recompensa').disabled,false,'the new day is offered at once');assert.ok(text(elements.get('modal-content')).includes('Próximo resgate em'));
 const late=deferred();h.set({own:after,api:{pending:null,busy:false,get:()=>late.promise}});h.receive({farm:after});h.loginCalendar();find('Atualizar calendário').onclick();tabs()[0].onclick();tabs()[1].onclick();const logout=h.logout();
 late.resolve({...after,revision:'3'});await logout;await new Promise(resolve=>setTimeout(resolve,0));assert.equal(h.get().own,undefined,'a response after logout is still dropped');
});
