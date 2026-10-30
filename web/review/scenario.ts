import {Scene} from '../src/scene.ts';
import {animalLayout} from '../src/presentation.ts';
import {renderWallet,renderMarket,focusMarketPage} from '../src/ui.ts';
import type {Farm,Catalog} from '../src/types.ts';
import crops from '../../assets/data/crops.json';
import animals from '../../assets/data/animals.json';
import fertilizers from '../../assets/data/fertilizers.json';

// Isolated presentation fixtures: no authentication, network API, save or economic command.
const prices=(values:Record<string,number|undefined>)=>Object.fromEntries(Object.entries(values).filter(([,value])=>value!==undefined).map(([key,value])=>[key,value!]));
const catalog:Catalog={crops:crops.map(crop=>({...crop,seedPrices:prices(crop.seedPrices)})),animals:animals.map(animal=>({...animal,prices:prices(animal.prices)})),fertilizers:fertilizers.map(f=>({...f,prices:prices(f.prices)})),feedPrices:{},vip:{greenPrice:0}};
const params=new URLSearchParams(location.search),period=params.get('period')??'day';
const stage=params.get('stage')??'ADULT';
const clock=()=>new Date(2026,9,30,period==='night'?22:period==='sunset'?18:12);
const canvas=document.getElementById('farm') as HTMLCanvasElement;
const scene=new Scene(canvas,catalog,tile=>{document.getElementById('notice')!.textContent=`Terra ${tile.column+1}, ${tile.row+1}`;},id=>{document.getElementById('notice')!.textContent=id;},()=>{},clock);
const tiles=Array.from({length:30},(_,i)=>{
 const row=Math.floor(i/6),column=i%6,stage=row+1,crop=crops[column];
 return{column,row,unlocked:column<5,state:column===4?'DEAD':stage===5?'READY':'GROWING',cropId:column<5?crop.id:undefined,stage,season:1,seasons:crop.seasons,
  dry:column===1,paused:false,weeds:column===2,insects:column===3?row%3+1:0,fertilized:false,protected:false,harvestRevision:'1',remainingSeconds:3600,nominalRemainingSeconds:3600};
});
const farm={owner:'review',name:'Fazenda de demonstração',level:2,currentXp:'8',xpToNext:'28',coins:'500',greenCoins:'5',revision:'1',serverTime:new Date().toISOString(),own:true,tiles,
 animals:Object.keys(animalLayout).map(id=>({id,stage,status:id==='abelha'?'PRODUCING':'READY',remainingSeconds:0})),nextPlot:{column:5,row:0}} as Farm;
scene.update(farm);
renderWallet(document.getElementById('wallet')!,farm);

const modal=document.getElementById('modal') as HTMLDialogElement;
function market(category='crops',page=0){
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
document.getElementById('close-modal')!.onclick=()=>modal.close();
document.querySelectorAll<HTMLButtonElement>('[data-tool]').forEach(button=>button.onclick=()=>button.setAttribute('aria-pressed',String(button.getAttribute('aria-pressed')!=='true')));
window.addEventListener('pagehide',()=>scene.close(),{once:true});
