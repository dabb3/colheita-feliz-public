import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as values from '../src/ui-presentation.ts';
import {animalPath} from '../src/presentation.ts';

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

test('market scrolls through all common crops, keeps VIP separate and preserves purchase fields',()=>{
 const {renderMarket,root}=ui(),pages=[],commands=[];
 const catalog={crops:Array.from({length:12},(_,i)=>crop(i)),animals:[],fertilizers:[]};
 const farm={level:2,animals:[],vipActive:false};
 renderMarket(root,catalog,farm,'crops','common',group=>pages.push(group),command=>commands.push(command));
 let all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,12);
 const lock=all.find(n=>n.textContent==='Nível 3');assert.ok(lock);
 assert.equal(all.find(n=>n.tag==='button'&&n.disabled)?.disabled,true);
 assert.equal(all.find(n=>n.className==='market-scroll')!==undefined,true);
 all.find(n=>n.attributes['aria-label']==='Comprar Planta 1 por 500 ouro').onclick();
 assert.equal(JSON.stringify(commands),JSON.stringify([{action:'BUY_SEED',itemId:'crop1',currency:'GOLD'}]));
 all.find(n=>n.textContent==='VIP').onclick();assert.deepEqual(pages,['vip']);assert.equal(commands.length,1);
 renderMarket(root,catalog,farm,'crops','vip',group=>pages.push(group),command=>commands.push(command));
 all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,0);
 assert.ok(all.some(n=>n.textContent==='Nenhuma semente VIP disponível.'));
});
