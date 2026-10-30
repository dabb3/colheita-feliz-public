import type {Farm,Catalog,Tile,Animal} from './types.ts';
import {periodAt,tileCenter,pickTile,animalPath,animalLayout,cropSize,animalWidth,productPath,terrainSize,peacockWidth,animalMotion,animalFlip,poseWidth} from './presentation.ts';

type Sprite={image:HTMLImageElement;x:number;y:number;w:number;h:number};
export class Scene {
 private canvas:HTMLCanvasElement;
 private ctx:CanvasRenderingContext2D;
 private sprites=new Map<string,Sprite|null>();
 private farm?:Farm;
 private catalog:Catalog;
 private hover:Tile|null=null;
 private onTile:(tile:Tile)=>void;
 private onAnimal:(id:string)=>void;
 private onHover:(tile:Tile|null)=>void;
 private animation=0;
 private seconds=0;
 private animalSeconds=0;
 private lastFrame:number|undefined;
 private closed=false;
 private effects:{x:number;y:number;label:string;born:number}[]=[];
 private localTime:()=>Date;
 private events=new AbortController();
 constructor(canvas:HTMLCanvasElement,catalog:Catalog,onTile:(tile:Tile)=>void,onAnimal:(id:string)=>void,onHover:(tile:Tile|null)=>void,localTime:()=>Date=()=>new Date()) {
  this.localTime=localTime;this.canvas=canvas;this.ctx=canvas.getContext('2d')!;this.catalog=catalog;this.onTile=onTile;this.onAnimal=onAnimal;this.onHover=onHover;
  canvas.width=1600;canvas.height=900;
  canvas.addEventListener('pointermove',e=>{const p=this.point(e);const pick=pickTile(p.x,p.y);this.hover=pick?this.farm?.tiles.find(t=>t.column===pick.column&&t.row===pick.row)??null:null;this.onHover(this.hover);},{signal:this.events.signal});
  canvas.addEventListener('pointerleave',()=>{this.hover=null;this.onHover(null);},{signal:this.events.signal});
  canvas.addEventListener('click',e=>{
   if(!this.farm)return;const p=this.point(e),pick=pickTile(p.x,p.y);
   if(pick){const tile=this.farm.tiles.find(t=>t.column===pick.column&&t.row===pick.row);if(tile)this.onTile(tile);return;}
   for(const animal of [...this.farm.animals].sort((a,b)=>this.view(a).y-this.view(b).y)) {
    const {x,y,width,path}=this.view(animal),[homeX,homeY]=animalLayout[animal.id]??[0,0];
    const product=productPath(animal.id,animal.status);
    if(product&&Math.abs(p.x-homeX-38)<=18&&Math.abs(p.y-(912-homeY))<=22){this.onAnimal(animal.id);return;}
    const s=this.sprite(path,true,animal.id==='abelha'?0:undefined);
    if(s&&Math.abs(p.x-x)<=width/2&&p.y<=900-y&&p.y>=900-y-width*s.h/s.w){this.onAnimal(animal.id);return;}
   }
  },{signal:this.events.signal});
  // A bounded visual clock pauses with the tab; it never advances the economic clock.
  const visibility=()=>{clearInterval(this.animation);this.lastFrame=undefined;if(!document.hidden&&!this.closed)this.animation=window.setInterval(()=>this.tick(performance.now()),1000/30);};
  document.addEventListener('visibilitychange',visibility,{signal:this.events.signal});visibility();
 }
 close(){this.closed=true;clearInterval(this.animation);this.effects=[];this.events.abort();}
 effect(action:string,column:number,row:number){
  const label:Record<string,string>={WATER:'Regado',PLANT:'Plantado',HARVEST:'Colhido',PLOW:'Terra pronta',WEED:'Ervas removidas',PESTICIDE:'Insetos removidos',FERTILIZE:'Adubado',STEAL:'Colhido',LOVE:'Amor aplicado'};
  if(!label[action]||this.closed)return;const p=tileCenter(column,row);this.effects.push({...p,label:label[action],born:this.seconds});this.effects=this.effects.slice(-8);this.draw();
 }
 private tick(now:number){
  if(this.closed||document.hidden)return;
  const delta=this.lastFrame===undefined?0:Math.max(0,Math.min(.1,(now-this.lastFrame)/1000));this.lastFrame=now;this.seconds+=delta;
  if(periodAt(this.localTime())!=='night')this.animalSeconds+=delta;
  this.draw();
 }
 private view(animal:Animal){
  const sleeping=periodAt(this.localTime())==='night',motion=animalMotion(animal.id,this.animalSeconds,sleeping),base=animalWidth(animal.id,animal.stage);
  const path=animal.id==='abelha'?'classic/animals/abelha/colmeia-abelha-atlas.png':animalPath(animal.id,animal.stage,sleeping,motion.alternate,motion.walking&&motion.alternate);
  const pose=this.sprite(path,true,animal.id==='abelha'?0:undefined),idle=this.sprite(animalPath(animal.id,animal.stage,false,false),true);
  const width=pose&&animal.id==='pavao'?peacockWidth(base,pose.w/pose.h):pose&&idle&&motion.walking?poseWidth(base,idle.w/idle.h,pose.w/pose.h):base;
  return{...motion,path,width,flipped:!sleeping&&motion.walking&&animalFlip(animal.id,motion.facingRight)};
 }
 update(farm:Farm){
  this.farm=farm;
  if(this.hover){const previous=this.hover;this.hover=farm.tiles.find(tile=>tile.column===previous.column&&tile.row===previous.row)??null;this.onHover(this.hover);}
  this.draw();
 }
 private point(e:PointerEvent|MouseEvent){const rect=this.canvas.getBoundingClientRect();return{x:(e.clientX-rect.left)*1600/rect.width,y:(e.clientY-rect.top)*900/rect.height};}
 private sprite(path:string,trim=false,part?:number):Sprite|undefined {
  const key=`${path}:${trim}:${part??'all'}`;const cached=this.sprites.get(key);if(cached)return cached;if(cached===null)return;
  this.sprites.set(key,null);const image=new Image();image.src='/assets/'+path;
  image.onload=()=>{
   let x=part===undefined?0:part*image.width/2,y=0,w=part===undefined?image.width:image.width/2,h=image.height;
   if(trim){const scratch=document.createElement('canvas');scratch.width=w;scratch.height=h;const context=scratch.getContext('2d',{willReadFrequently:true})!;context.drawImage(image,x,0,w,h,0,0,w,h);const pixels=context.getImageData(0,0,w,h).data;
    let left=w,top=h,right=0,bottom=0;
    for(let row=0;row<h;row++)for(let col=0;col<w;col++)if(pixels[(row*w+col)*4+3]>12){left=Math.min(left,col);top=Math.min(top,row);right=Math.max(right,col);bottom=Math.max(bottom,row);}
    if(right>=left&&bottom>=top){x+=left;y=top;w=right-left+1;h=bottom-top+1;}
   }
   this.sprites.set(key,{image,x,y,w,h});this.draw();
  };
  image.onerror=()=>{console.warn('Asset indisponível:',path);};return;
 }
 private thing(path:string,x:number,bottom:number,width:number,trim=false,part?:number,flipped=false){const s=this.sprite(path,trim,part);if(s){this.ctx.save();this.ctx.translate(x,0);if(flipped)this.ctx.scale(-1,1);this.ctx.drawImage(s.image,s.x,s.y,s.w,s.h,-width/2,bottom-width*s.h/s.w,width,width*s.h/s.w);this.ctx.restore();}}
 private fence(points:number[][]){const ctx=this.ctx;for(let i=1;i<points.length;i++){
  const a=points[i-1],b=points[i];for(const height of [26,49]){ctx.save();ctx.translate(a[0],900-a[1]-height);ctx.rotate(Math.atan2(a[1]-b[1],b[0]-a[0]));const s=this.sprite('classic/fence/trilho.png');if(s)ctx.drawImage(s.image,0,-5,Math.hypot(b[0]-a[0],b[1]-a[1]),10);ctx.restore();}
 }for(const [x,y]of points)this.thing('classic/fence/mourao.png',x,903-y,25);}
 private draw(){
  if(document.hidden||this.closed)return;const ctx=this.ctx,period=periodAt(this.localTime());
  ctx.clearRect(0,0,1600,900);ctx.fillStyle=period==='night'?'#15464f':'#84ba30';ctx.fillRect(0,0,1600,900);
  const background=this.sprite(`classic/background/background${period==='day'?'':'-'+period}.png`);
  if(background)ctx.drawImage(background.image,0,0,1600,900);
  this.thing('classic/buildings/dog-house.png',410,900-556,214);
  this.thing('classic/buildings/main-house.png',735,900-540,420);
  this.fence([[1108,494],[1180,509],[1262,521],[1350,528],[1440,530],[1530,525],[1620,517]]);
  if(!this.farm)return;
  for(const tile of [...this.farm.tiles].sort((a,b)=>a.column+a.row-b.column-b.row)){
   const p=tileCenter(tile.column,tile.row);
   const terrain=!tile.unlocked||tile.state==='GRASS'?'grama-default':tile.dry?'terra-seca':tile.state==='PLOWED'?'terra-arada':'terra-molhada';
   const s=this.sprite(`classic/terrain/${terrain}.png`);
   if(s)ctx.drawImage(s.image,s.x,s.y,s.w,s.h,p.x-terrainSize.width/2,p.y-terrainSize.height/2,terrainSize.width,terrainSize.height);
   if(tile.state==='DEAD')this.thing('classic/terrain/planta-morta.png',p.x,p.y+10,86,true);
   else if(tile.cropId){
    const crop=this.catalog.crops.find(c=>c.id===tile.cropId),plant=crop?this.sprite(`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_${tile.stage}.png`,true):undefined;
    if(tile.state==='READY'){const glow=ctx.createRadialGradient(p.x,p.y-30,0,p.x,p.y-30,75);glow.addColorStop(0,'#fff5a155');glow.addColorStop(1,'#fff5a100');ctx.fillStyle=glow;ctx.fillRect(p.x-75,p.y-105,150,150);}
    if(tile.weeds){const weeds=this.sprite(`classic/effects/erva_daninha_${Math.floor(this.seconds/.16)%4+1}.png`);if(weeds)ctx.drawImage(weeds.image,p.x+2,p.y-50,72,72);}
    if(plant){const size=cropSize(tile.stage,plant.w/plant.h);ctx.save();if(tile.dry&&tile.state!=='READY')ctx.filter='sepia(.65) saturate(.6)';ctx.drawImage(plant.image,plant.x,plant.y,plant.w,plant.h,p.x-size.width/2,p.y+8-size.height,size.width,size.height);ctx.restore();}
    if(tile.insects)this.thing(`classic/effects/verme_${Math.min(3,tile.insects)}.png`,p.x-4+Math.sin(this.seconds*5+tile.column*3)*1.5,p.y+24,70);
   }
   if(tile.protected){ctx.fillStyle='#90def1';ctx.font='18px sans-serif';ctx.fillText('◆',p.x+58,p.y);}
   if(this.hover===tile){ctx.strokeStyle='#fff4a2';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(p.x,p.y-36);ctx.lineTo(p.x+85,p.y);ctx.lineTo(p.x,p.y+36);ctx.lineTo(p.x-85,p.y);ctx.closePath();ctx.stroke();}
  }
  if(this.farm.own&&this.farm.nextPlot){const p=tileCenter(this.farm.nextPlot.column,this.farm.nextPlot.row);this.thing('classic/terrain/placa-terreno.png',p.x,p.y,110,true);ctx.save();ctx.textAlign='center';ctx.fillStyle='#fff9dd';ctx.shadowColor='#67431f';ctx.shadowBlur=2;ctx.font='bold 16px sans-serif';ctx.fillText('50 verdes',p.x,p.y-70);ctx.restore();}
  const inside=new Set(['porco','ovelha','vaca','galinha','pato']);
  const ordered=[...this.farm.animals].sort((a,b)=>this.view(b).y-this.view(a).y);
  for(const animal of ordered.filter(animal=>inside.has(animal.id)))this.drawAnimal(animal,period);
  this.fence([[986,410],[1072,370],[1160,334],[1250,304],[1342,280],[1434,262],[1526,250],[1618,244]]);
  for(const animal of ordered.filter(animal=>!inside.has(animal.id)))this.drawAnimal(animal,period);
  this.effects=this.effects.filter(effect=>this.seconds-effect.born<1.2);
  for(const effect of this.effects){const age=this.seconds-effect.born;ctx.save();ctx.globalAlpha=1-age/1.2;ctx.font='bold 22px sans-serif';ctx.textAlign='center';ctx.fillStyle='#fffbd5';ctx.shadowColor='#274c23';ctx.shadowBlur=4;ctx.fillText(effect.label,effect.x,effect.y-45-age*35);ctx.restore();}
 }
 private drawAnimal(animal:Animal,period:string){
   const layout=animalLayout[animal.id];if(!layout)return;const {x,y,width:w,path,flipped}=this.view(animal),bottom=900-y,ctx=this.ctx;
   ctx.save();ctx.fillStyle='#152b1942';ctx.beginPath();ctx.ellipse(x,bottom,w*.325,w*.052,0,0,Math.PI*2);ctx.fill();ctx.restore();
   if(animal.id==='abelha'){
    const hive=this.sprite(path,true,0);
    this.thing(path,x,bottom,w,true,0);
    if(hive&&period!=='night'&&animal.status!=='HUNGRY')for(let i=0;i<3;i++){const t=this.animalSeconds*1.4+i*1.7;this.thing(path,x+Math.sin(t)*28,bottom-w*hive.h/hive.w+8-Math.cos(t*1.3)*12,16,true,1);}
   }else{
    // Visible alpha bounds meet the ground anchor; avoid the old detached shadow.
    this.thing(path,x,bottom,w,true,undefined,flipped);
   }
   const product=productPath(animal.id,animal.status);if(product)this.thing(product,layout[0]+38,912-layout[1],28,true);
 }
}
