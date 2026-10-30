import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {ApiError,acceptFarm} from '../src/api.ts';

const farm=owner=>({owner,name:owner,revision:'1',tiles:[],animals:[],seedCounts:{}});
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve};}
function app(){
 const elements=new Map();const make=()=>({value:'',selectedIndex:0,hidden:false,children:[],replaceChildren(){this.children=[];},append(...nodes){this.children.push(...nodes);},setAttribute(){},addEventListener(){},close(){},showModal(){this.open=true;}});
 const storage={getItem:()=>null,setItem(){},removeItem(){}};
 const ctx={window:{COLHEITA_CONFIG:{development:true},setInterval(){},setTimeout(){},addEventListener(){}},document:{getElementById(id){if(!elements.has(id))elements.set(id,make());return elements.get(id);},createElement:make,createTextNode:text=>text,addEventListener(){}},location:{href:'http://localhost',origin:'http://localhost'},sessionStorage:storage,navigator:{onLine:true},URL,ApiError,acceptFarm,clearTimeout(){},HTMLInputElement:class{},HTMLSelectElement:class{}};
 let source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('void start();','');
 source+='\nglobalThis.h={refresh,get:()=>({own,visited}),set:(v)=>{own=v.own;visited=v.visited;api=v.api;catalog={crops:[],fertilizers:[]};}};';
 runInNewContext(stripTypeScriptTypes(source),ctx);return{h:ctx.h,elements};
}
test('late visit response cannot reopen a visit after returning home',async()=>{
 const {h,elements}=app(),started=deferred(),reply=deferred();
 h.set({own:farm('alice'),visited:farm('bob'),api:{pending:null,get:path=>path==='/v1/me/farm'?Promise.resolve(farm('alice')):(started.resolve(),reply.promise)}});
 const refresh=h.refresh();await started.promise;elements.get('home').onclick();reply.resolve(farm('bob'));await refresh;
 assert.equal(h.get().visited,undefined);
});
test('late retry response cannot restore a logged-out farm',async()=>{
 const {h,elements}=app(),reply=deferred();
 h.set({own:farm('alice'),api:{pending:{},retry:()=>reply.promise}});
 const retry=elements.get('retry').onclick();await elements.get('logout').onclick();reply.resolve({farm:farm('alice')});await retry;
 assert.equal(h.get().own,undefined);assert.equal(elements.get('game').hidden,true);
});
test('closed canvas scene removes previous event handlers',()=>{
 const handlers=new Map(),calls=[];
 const canvas={width:1600,height:900,getContext:()=>({}),getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler,options){const list=handlers.get(name)??[];list.push(handler);handlers.set(name,list);options?.signal?.addEventListener('abort',()=>handlers.set(name,handlers.get(name).filter(h=>h!==handler)));}};
 const ctx={window:{setInterval:()=>1},clearInterval(){},document:{hidden:true},AbortController,pickTile:()=>({column:0,row:0})};
 let source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.TestScene=Scene;',ctx);
 for(const revision of ['1','2']){const scene=new ctx.TestScene(canvas,{},tile=>calls.push(tile.harvestRevision),()=>{},()=>{});scene.update({...farm('alice'),tiles:[{column:0,row:0,harvestRevision:revision}]});if(revision==='1')scene.close();}
 for(const handler of handlers.get('click'))handler({clientX:585,clientY:400});assert.deepEqual(calls,['2']);
});
