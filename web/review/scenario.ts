import {Scene} from '../src/scene.ts';
import {animalLayout,tileCenter} from '../src/presentation.ts';
import {renderWallet,renderMarket,focusMarketPage} from '../src/ui.ts';
import {renderDeposit,renderPlantHealth,renderAnimal,renderMissions,renderFriends} from '../src/ui-panels.ts';
import type {Farm,Catalog,Tile} from '../src/types.ts';
import crops from '../../assets/data/crops.json';
import animals from '../../assets/data/animals.json';
import fertilizers from '../../assets/data/fertilizers.json';

// Isolated presentation fixtures: no authentication, network API, save or economic command.
const prices=(values:Record<string,number|undefined>)=>Object.fromEntries(Object.entries(values).filter(([,value])=>value!==undefined).map(([key,value])=>[key,value!]));
const catalog:Catalog={crops:crops.map(crop=>({...crop,seedPrices:prices(crop.seedPrices)})),animals:animals.map(animal=>({...animal,prices:prices(animal.prices)})),fertilizers:fertilizers.map(f=>({...f,prices:prices(f.prices)})),feedPrices:{producer:120,growth:80},vip:{greenPrice:0}};
const params=new URLSearchParams(location.search),period=params.get('period')??'day';
const stage=params.get('stage')??'ADULT';
const clock=()=>new Date(2026,9,30,period==='night'?22:period==='sunset'?18:12);
const canvas=document.getElementById('farm') as HTMLCanvasElement;
const scene=new Scene(canvas,catalog,tile=>{document.getElementById('notice')!.textContent=`Terra ${tile.column+1}, ${tile.row+1}`;},animalPanel,health,clock);
const tiles=Array.from({length:30},(_,i)=>{
 const row=Math.floor(i/6),column=i%6,stage=row+1,crop=crops[column];
 return{column,row,unlocked:column<5,state:column===4?'DEAD':stage===5?'READY':'GROWING',cropId:column<5?crop.id:undefined,stage,season:1,seasons:crop.seasons,
  dry:column===1,paused:false,weeds:column===2,insects:column===3?row%3+1:0,fertilized:false,protected:false,harvestRevision:'1',remainingSeconds:crop.growthSeconds*(5-stage)/4*1.6,nominalRemainingSeconds:crop.growthSeconds*(5-stage)/4};
});
const farm={owner:'review',name:'Fazenda de demonstração',level:2,currentXp:'8',xpToNext:'28',coins:'500',greenCoins:'5',revision:'1',serverTime:new Date().toISOString(),own:true,tiles,
 seedCounts:{milho:3,nabo:1},feed:{producer:2,growth:1},fertilizers:{comum:4},vipActive:false,
 produceLots:[{itemId:'milho',currency:'GOLD',quantity:2},{itemId:'milho',currency:'GREEN',quantity:1},{itemId:crops.find(c=>c.vipOnly)!.id,currency:'GREEN',quantity:1,vipOnly:true}],
 missions:[{id:'plant',title:'Cultivar todos os dias',green:1,done:true},{id:'collect',title:'Recolher a produção',green:1,done:false}],
 animals:Object.keys(animalLayout).map(id=>({id,stage,status:id==='abelha'||catalog.animals.find(a=>a.id===id)?.kind==='PROTECTOR'||stage!=='ADULT'?'PRODUCING':'READY',remainingSeconds:0})),nextPlot:{column:5,row:0}} as Farm;
scene.update(farm);
renderWallet(document.getElementById('wallet')!,farm);

const modal=document.getElementById('modal') as HTMLDialogElement;
function openPanel(title:string,draw:(root:HTMLElement)=>void){
 document.getElementById('modal-title')!.textContent=title;document.getElementById('plant-health')!.hidden=true;
 const content=document.getElementById('modal-content')!;content.replaceChildren();draw(content);if(!modal.open)modal.showModal();
}
function demonstration(){document.getElementById('notice')!.textContent='Demonstração: nenhuma ação foi enviada.';}
function animalPanel(id:string){openPanel('Animal',root=>renderAnimal(root,catalog,farm,id,true,demonstration));}
function deposit(category='products'){openPanel('Depósito',root=>renderDeposit(root,catalog,farm,category,deposit,demonstration));}
function health(tile:Tile|null){
 const panel=document.getElementById('plant-health')!;renderPlantHealth(panel,catalog,modal.open?null:tile);
 if(tile){const p=tileCenter(tile.column,tile.row);panel.style.setProperty('--health-x',`${Math.max(12,Math.min(88,p.x/16))}%`);panel.style.setProperty('--health-y',`${(p.y-45)/9}%`);}
}
function market(category='crops',page=0){
 document.getElementById('modal-title')!.textContent='Loja';document.getElementById('plant-health')!.hidden=true;
 const content=document.getElementById('modal-content')!;content.replaceChildren();
 const tabs=document.createElement('div');tabs.className='tabs';content.append(tabs);
 for(const [id,label]of [['crops','Sementes'],['animals','Animais'],['fertilizers','Fertilizantes']]){
  const button=document.createElement('button');button.textContent=label;button.setAttribute('aria-pressed',String(id===category));button.onclick=()=>market(id);tabs.append(button);
 }
 const body=document.createElement('div');content.append(body);
 renderMarket(body,catalog,farm,category,page,next=>{market(category,next);focusMarketPage(content,next>page);},()=>{document.getElementById('notice')!.textContent='Demonstração: nenhuma compra foi enviada.';});
 if(!modal.open)modal.showModal();
}
document.getElementById('shop')!.onclick=()=>market();
document.getElementById('deposit')!.onclick=()=>deposit();
document.getElementById('missions')!.onclick=()=>openPanel('Tarefas diárias',root=>renderMissions(root,farm));
document.getElementById('friends')!.onclick=()=>openPanel('Amigos — demonstração',root=>renderFriends(root,[{id:'review-bob',name:'Fazenda do amigo'},{id:'review-ana',name:'Fazenda da amiga'}],demonstration));
document.getElementById('close-modal')!.onclick=()=>modal.close();
document.querySelectorAll<HTMLButtonElement>('[data-tool]').forEach(button=>button.onclick=()=>button.setAttribute('aria-pressed',String(button.getAttribute('aria-pressed')!=='true')));
window.addEventListener('pagehide',()=>scene.close(),{once:true});
