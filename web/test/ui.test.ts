import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as values from '../src/ui-presentation.ts';
import {animalPath} from '../src/presentation.ts';
import {focusMarketPage} from '../src/ui.ts';

function ui(){
 const make=tag=>({tag,children:[],attributes:{},textContent:'',append(...nodes){this.children.push(...nodes);},replaceChildren(){this.children=[];},setAttribute(k,v){this.attributes[k]=v;}});
 const context={...values,animalPath,document:{createElement:make}};
 const source=readFileSync(new URL('../src/ui.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.ui={renderWallet,renderMarket};',context);
 return{...context.ui,root:make('div')};
}
function nodes(root){return[root,...root.children.flatMap(child=>typeof child==='object'?nodes(child):[])];}
const crop=i=>({id:'crop'+i,name:'Planta '+i,spriteStem:'nabo',growthSeconds:72000,seasons:1,requiredLevel:i===0?3:1,seedPrices:{GOLD:500},vipOnly:false});

test('wallet uses authenticated name and exact balances with a labelled XP meter',()=>{
 const {renderWallet,root}=ui();renderWallet(root,{name:'<akaza>',level:2,currentXp:'0.5',xpToNext:'25',coins:'9007199254740993.1',greenCoins:'5'});
 const all=nodes(root);assert.ok(all.some(n=>n.textContent==='<akaza>'));
 assert.ok(all.some(n=>n.textContent==='9007199254740993.1'));
 const meter=all.find(n=>n.tag==='progress');assert.equal(meter.value,2);assert.equal(meter.attributes['aria-label'],'Experiência: 0.5 de 25 XP');
});

test('market limits cards, explains locks and preserves purchase fields after paging',()=>{
 const {renderMarket,root}=ui(),pages=[],commands=[];
 const catalog={crops:Array.from({length:12},(_,i)=>crop(i)),animals:[],fertilizers:[]};
 const farm={level:2,animals:[],vipActive:false};
 renderMarket(root,catalog,farm,'crops',0,page=>pages.push(page),command=>commands.push(command));
 let all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,10);
 const lock=all.find(n=>n.textContent==='Nível 3');assert.ok(lock);
 assert.equal(all.find(n=>n.tag==='button'&&n.disabled)?.disabled,true);
 all.find(n=>n.attributes['aria-label']==='Próxima página').onclick();assert.deepEqual(pages,[1]);assert.equal(commands.length,0);
 renderMarket(root,catalog,farm,'crops',1,page=>pages.push(page),command=>commands.push(command));
 all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,2);
 all.find(n=>n.attributes['aria-label']==='Comprar Planta 10 por 500 ouro').onclick();
 assert.equal(JSON.stringify(commands),JSON.stringify([{action:'BUY_SEED',itemId:'crop10',currency:'GOLD'}]));
});

test('paging restores keyboard focus and falls back when the next control is disabled',()=>{
 const focused=[],previous={disabled:false,focus:()=>focused.push('previous')},next={disabled:false,focus:()=>focused.push('next')};
 const root={querySelectorAll:()=>[previous,next]};
 focusMarketPage(root,true);next.disabled=true;focusMarketPage(root,true);
 assert.deepEqual(focused,['next','previous']);
});
