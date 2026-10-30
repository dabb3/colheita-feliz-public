import {Scene} from '../src/scene.ts';
import {animalLayout} from '../src/presentation.ts';
import type {Farm,Catalog} from '../src/types.ts';
import crops from '../../assets/data/crops.json';

// Isolated presentation fixtures: no authentication, network API, save or economic command.
const catalog:Catalog={crops:crops.map(crop=>({...crop,seedPrices:Object.fromEntries(Object.entries(crop.seedPrices).filter(([,value])=>value!==undefined).map(([key,value])=>[key,value!]))})),animals:[],fertilizers:[],feedPrices:{},vip:{greenPrice:0}};
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
const farm={owner:'review',name:'Revisão visual',revision:'1',serverTime:new Date().toISOString(),own:true,tiles,
 animals:Object.keys(animalLayout).map(id=>({id,stage,status:id==='abelha'?'PRODUCING':'READY',remainingSeconds:0})),nextPlot:{column:5,row:0}} as Farm;
scene.update(farm);
document.querySelectorAll<HTMLButtonElement>('[data-tool]').forEach(button=>button.onclick=()=>button.setAttribute('aria-pressed',String(button.getAttribute('aria-pressed')!=='true')));
window.addEventListener('pagehide',()=>scene.close(),{once:true});
