import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {ApiError,acceptFarm} from '../src/api.ts';

const farm=owner=>({owner,name:owner,revision:'1',tiles:[],animals:[],seedCounts:{}});
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve};}
function app(){
 const elements=new Map(),listeners=new Map(),audioClips=[];const make=()=>({value:'',selectedIndex:0,hidden:false,children:[],classList:{add(){},remove(){},toggle(){}},replaceChildren(...nodes){this.children=nodes;},append(...nodes){this.children.push(...nodes);},prepend(...nodes){this.children.unshift(...nodes);},insertBefore(node,before){this.children.splice(this.children.indexOf(before),0,node);},setAttribute(){},querySelector(){return null;},addEventListener(){},close(){this.open=false;},showModal(){this.open=true;}});
 const storage={getItem:()=>null,setItem(){},removeItem(){}};
 const ctx={window:{COLHEITA_CONFIG:{development:true},setInterval(){},setTimeout(){},addEventListener(){}},document:{hidden:false,getElementById(id){if(!elements.has(id))elements.set(id,make());return elements.get(id);},createElement:make,createTextNode:text=>text,addEventListener(name,handler){listeners.set(name,handler);}},location:{href:'http://localhost',origin:'http://localhost'},sessionStorage:storage,navigator:{onLine:true},URL,ApiError,acceptFarm,animalPath:()=>'',animalStatusText:(status)=>status,animalActionLabel:(status)=>status==='READY'?'Recolher produto':null,clearTimeout(){},Audio:class{paused=true;playCount=0;loop=false;volume=0;constructor(){audioClips.push(this);}play(){this.paused=false;this.playCount++;return Promise.resolve();}pause(){this.paused=true;}},HTMLInputElement:class{},HTMLSelectElement:class{}};
 let source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('void start();','');
 source+='\nglobalThis.h={refresh,logout,toggleMusic,shop,receive,get:()=>({own,visited}),set:(v)=>{own=v.own;visited=v.visited;api=v.api;catalog=v.catalog??{crops:[],fertilizers:[],animals:[],vip:{greenPrice:20}};}};';
 runInNewContext(stripTypeScriptTypes(source),ctx);return{h:ctx.h,elements,listeners,audioClips,ctx};
}
function text(node){return(node.textContent??'')+(node.children??[]).map(child=>typeof child==='object'?text(child):child).join('');}
test('late visit response cannot reopen a visit after returning home',async()=>{
 const {h,elements}=app(),started=deferred(),reply=deferred();
 h.set({own:farm('alice'),visited:farm('bob'),api:{pending:null,get:path=>path==='/v1/me/farm'?Promise.resolve(farm('alice')):(started.resolve(),reply.promise)}});
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
test('closed canvas scene removes previous event handlers',()=>{
 const handlers=new Map(),calls=[];
 const canvas={width:1600,height:900,getContext:()=>({}),getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler,options){const list=handlers.get(name)??[];list.push(handler);handlers.set(name,list);options?.signal?.addEventListener('abort',()=>handlers.set(name,handlers.get(name).filter(h=>h!==handler)));}};
 const cancelled=[];const ctx={window:{requestAnimationFrame:()=>1,cancelAnimationFrame:id=>cancelled.push(id)},document:{hidden:true},AbortController,pickTile:()=>({column:0,row:0})};
 let source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.TestScene=Scene;',ctx);
 for(const revision of ['1','2']){const scene=new ctx.TestScene(canvas,{},tile=>calls.push(tile.harvestRevision),()=>{},()=>{});scene.update({...farm('alice'),tiles:[{column:0,row:0,harvestRevision:revision}]});if(revision==='1')scene.close();}
 assert.deepEqual(cancelled,[1]);
 for(const handler of handlers.get('click'))handler({clientX:585,clientY:400});assert.deepEqual(calls,['2']);
});
