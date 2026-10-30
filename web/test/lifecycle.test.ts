import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {ApiError,acceptFarm} from '../src/api.ts';

const farm=owner=>({owner,name:owner,revision:'1',tiles:[],animals:[],seedCounts:{}});
function deferred(){let resolve;const promise=new Promise(r=>resolve=r);return{promise,resolve};}
function app(){
 const wallets=[],panels=[];
 const elements=new Map();const make=()=>({value:'',selectedIndex:0,hidden:false,children:[],style:{setProperty(){}},replaceChildren(){this.children=[];},append(...nodes){this.children.push(...nodes);},setAttribute(){},addEventListener(){},close(){this.open=false;},showModal(){this.open=true;}});
 const storage={getItem:()=>null,setItem(){},removeItem(){}};
 const ctx={window:{COLHEITA_CONFIG:{development:true},setInterval(){},setTimeout(){},addEventListener(){}},document:{getElementById(id){if(!elements.has(id))elements.set(id,make());return elements.get(id);},createElement:make,createTextNode:text=>text,addEventListener(){}},location:{href:'http://localhost',origin:'http://localhost'},sessionStorage:storage,navigator:{onLine:true},URL,ApiError,acceptFarm,renderWallet(...args){wallets.push(args);},renderMarket(){},renderDeposit(...args){panels.push(["deposit",...args]);},renderMissions(...args){panels.push(["missions",...args]);},renderAnimal(...args){panels.push(["animal",...args]);},renderPlantHealth(){},renderFriends(...args){panels.push(["friends",...args]);},tileCenter:()=>({x:585,y:400}),clearTimeout(){},HTMLInputElement:class{},HTMLSelectElement:class{}};
 let source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('void start();','');
 source+='\nglobalThis.h={refresh,friends,health,invitationForm,animalPanel:typeof animalPanel==="undefined"?undefined:animalPanel,get:()=>({own,visited}),set:(v)=>{own=v.own;visited=v.visited;api=v.api;catalog={crops:[],fertilizers:[],animals:[]};}};';
 runInNewContext(stripTypeScriptTypes(source),ctx);return{h:ctx.h,elements,wallets,panels,doc:ctx.document};
}

test('live client renders the same wallet as the visual sample',()=>{
 const {h,elements,wallets}=app(),own=farm('alice');h.set({own,visited:farm('bob')});
 elements.get('home').onclick();assert.equal(wallets.length,1);assert.equal(wallets[0][1],own);assert.equal(wallets[0][2],undefined);
});
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

test('inventory and individual animal are integrated with own versus visited identity',()=>{
 const {h,elements,panels}=app(),own=farm('alice'),visited=farm('bob');h.set({own,visited});
 elements.get('deposit').onclick();assert.equal(panels[0]?.[0],'deposit');assert.equal(panels[0][3],own);
 h.animalPanel('vaca');const animal=panels.find(p=>p[0]==='animal');assert.equal(animal[3],visited);assert.equal(animal[4],'vaca');assert.equal(animal[5],false);
 h.animalPanel('vaca',true);const ownAnimal=panels.at(-1);assert.equal(ownAnimal[3],own);assert.equal(ownAnimal[5],true);
});

test('late friend list cannot append to a different modal',async()=>{
 const {h,elements}=app(),reply=deferred();h.set({own:farm('alice'),api:{get:()=>reply.promise}});
 const request=h.friends();elements.get('deposit').onclick();const content=elements.get('modal-content'),before=[...content.children];
 reply.resolve([{id:'bob',name:'Bob'}]);await request;assert.deepEqual(content.children,before);
});

test('polling refreshes the open animal panel after a temporal state change',async()=>{
 const {h,panels}=app(),own=farm('alice'),ready={...own,revision:'2',animals:[{id:'vaca',status:'READY'}]};
 h.set({own,api:{get:()=>Promise.resolve(ready)}});h.animalPanel('vaca');await h.refresh();
 const animals=panels.filter(p=>p[0]==='animal');assert.equal(animals.length,2);assert.equal(animals[1][3],ready);
});

test('panel polling retains focus on the matching action',async()=>{
 const {h,elements,doc}=app(),own=farm('alice');h.set({own,api:{get:()=>Promise.resolve({...own,revision:'2'})}});h.animalPanel('vaca');
 let focused=0;doc.activeElement={getAttribute:()=> 'Alimentar'};const content=elements.get('modal-content');content.contains=()=>true;
 content.querySelectorAll=()=>[{getAttribute:()=> 'Alimentar',disabled:false,focus:()=>focused++}];
 await h.refresh();assert.equal(focused,1);
});

test('late invitation acceptance cannot close a newer modal',async()=>{
 const {h,elements}=app(),reply=deferred(),own=farm('alice');
 h.set({own,api:{get:path=>Promise.resolve(path==='/v1/friends'?[]:own),mutate:()=>reply.promise}});await h.friends();
 const controls=elements.get('modal-content').children[0];controls.children.find(n=>n.textContent==='Aceitar convite').onclick();
 elements.get('deposit').onclick();reply.resolve({});await new Promise(resolve=>setImmediate(resolve));
 assert.equal(elements.get('modal').open,true);
});
