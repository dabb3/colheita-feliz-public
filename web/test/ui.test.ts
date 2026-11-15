import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as values from '../src/ui-presentation.ts';
import {animalPath,measureSpriteFootprint} from '../src/presentation.ts';

function ui(){
 const make=tag=>({tag,children:[],attributes:{},textContent:'',append(...nodes){this.children.push(...nodes);},replaceChildren(){this.children=[];},setAttribute(k,v){this.attributes[k]=v;}});
 const context={...values,animalPath,measureSpriteFootprint,document:{createElement:make}};
 const source=readFileSync(new URL('../src/ui.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.ui={image,renderWallet,renderMarket};',context);
 return{...context.ui,root:make('div'),context};
}
function nodes(root){return[root,...root.children.flatMap(child=>typeof child==='object'?nodes(child):[])];}
const crop=i=>({id:'crop'+i,name:'Planta '+i,spriteStem:'nabo',growthSeconds:72000,regrowthSeconds:0,seasons:1,requiredLevel:i===0?3:1,seedPrices:{GOLD:500},vipOnly:false});

test('final Caviar thumbnail crops transparent padding once using measured alpha bounds',()=>{
 const {image,context}=ui(),pixels=[[0,0,0,0,0],[0,0,0,0,0],[0,0,255,255,0],[0,0,255,255,0],[0,0,0,0,0]];
 const draws=[];
 const canvas={width:0,height:0,getContext:()=>({getImageData:()=>({data:new Uint8ClampedArray(pixels.flatMap(row=>row.flatMap(a=>[0,0,0,a])))}),drawImage:(...args)=>draws.push(args)}),toDataURL:()=> 'data:image/png;base64,cropped'};
 const node={tag:'img',className:'',alt:'',src:'',onload:null};
 context.document.createElement=tag=>tag==='canvas'?canvas:node;
 const result=image('classic/crops/plants_phase_one_to_five/caviar_5.png');
 result.naturalWidth=5;result.naturalHeight=5;result.onload();
 assert.equal(result.src,'data:image/png;base64,cropped');
 assert.equal(result.onload,null);
 assert.deepEqual(Array.from(draws[1].slice(1)),[2,2,2,2,0,0,2,2]);
});

test('other thumbnails keep their original asset path without canvas processing',()=>{
 const {image,context}=ui(),order=[],node={tag:'img',className:'',alt:'',onload:null};
 Object.defineProperty(node,'loading',{set(){order.push('loading');}});
 Object.defineProperty(node,'src',{set(value){this.source=value;order.push('src');},get(){return this.source;}});
 context.document.createElement=()=>node;
 const result=image('classic/crops/plants_phase_one_to_five/manga_5.png','market-art','lazy');
 assert.equal(result.src,'/assets/classic/crops/plants_phase_one_to_five/manga_5.png');
 assert.equal(result.onload,null);
 assert.deepEqual(order,['loading','src']);
});

test('Caviar thumbnail keeps its original source when canvas context is unavailable',()=>{
 const {image,context}=ui(),node={tag:'img',className:'',alt:'',src:'',onload:null};
 context.document.createElement=tag=>tag==='canvas'?{getContext:()=>null}:node;
 const result=image('classic/crops/plants_phase_one_to_five/caviar_5.png');
 result.onload();
 assert.equal(result.src,'/assets/classic/crops/plants_phase_one_to_five/caviar_5.png');
});

test('wallet uses authenticated name and exact balances with a labelled XP meter',()=>{
 const {renderWallet,root}=ui();renderWallet(root,{name:'<akaza>',level:2,currentXp:'0.5',xpToNext:'25',coins:'9007199254740993.1',greenCoins:'5'});
 const all=nodes(root);assert.ok(all.some(n=>n.textContent==='<akaza>'));
 assert.ok(all.some(n=>n.textContent==='9007199254740993.1'));
 const meter=all.find(n=>n.tag==='progress');assert.equal(meter.value,2);assert.equal(meter.attributes['aria-label'],'Experiência: 0.5 de 25 XP');
});

test('wallet offers avatar selection and falls back to the original when the photo fails',()=>{
 const {renderWallet,root}=ui();let clicked=0;
 renderWallet(root,{name:'Alice',level:1},undefined,'https://lh3.googleusercontent.com/a/synthetic',()=>clicked++);
 const all=nodes(root),pick=all.find(n=>n.attributes['aria-label']==='Trocar avatar'),avatar=all.find(n=>n.className==='avatar');
 assert.ok(pick);pick.onclick();assert.equal(clicked,1);
 assert.equal(avatar.src,'https://lh3.googleusercontent.com/a/synthetic');
 avatar.onerror();assert.equal(avatar.src,'/assets/classic/icons/avatar_masculino.png');assert.equal(avatar.onerror,null);
});

test('market scrolls through all common crops, keeps VIP separate and preserves purchase fields',()=>{
 const {renderMarket,root}=ui(),pages=[],commands=[];
 const catalog={crops:Array.from({length:12},(_,i)=>crop(i)),animals:[],fertilizers:[]};
 const farm={level:2,animals:[],vipActive:false};
 renderMarket(root,catalog,farm,'crops','common',group=>pages.push(group),command=>commands.push(command));
 let all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,12);
 assert.ok(all.some(n=>n.textContent==='20 h com rega em dia · 1 temporada(s)'));
 assert.ok(all.some(n=>n.textContent.includes('Tempo nominal:')&&n.textContent.includes('minhocas pausam')));
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
