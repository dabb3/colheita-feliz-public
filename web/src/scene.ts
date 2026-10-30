import type {Farm,Catalog,Tile} from './types.ts';
import {periodAt,tileCenter,pickTile,animalPath,animalLayout,cropSize,animalWidth,productPath,terrainSize,peacockWidth} from './presentation.ts';

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
 private animation:number;
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
   for(const animal of this.farm.animals) {
    const [x,y]=animalLayout[animal.id]??[0,0],w=animalWidth(animal.id,animal.stage);
    const product=productPath(animal.id,animal.status);
    if(product&&Math.abs(p.x-x-38)<=18&&Math.abs(p.y-(912-y))<=22){this.onAnimal(animal.id);return;}
    const path=animal.id==='abelha'?'classic/animals/abelha/colmeia-abelha-atlas.png':animalPath(animal.id,animal.stage,periodAt(this.localTime())==='night',Math.floor(Date.now()/4000)%2!==0);
    const s=this.sprite(path,true,animal.id==='abelha'?0:undefined);
    const hitWidth=s&&animal.id==='pavao'?peacockWidth(w,s.w/s.h):w;
    if(s&&Math.abs(p.x-x)<=hitWidth/2&&p.y<=900-y&&p.y>=900-y-hitWidth*s.h/s.w){this.onAnimal(animal.id);return;}
   }
  },{signal:this.events.signal});
  // Static scene needs only gentle bee/peacock updates, not a busy 60 fps loop.
  this.animation=window.setInterval(()=>this.draw(),200);
 }
 close(){clearInterval(this.animation);this.events.abort();}
 update(farm:Farm){this.farm=farm;this.draw();}
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
 private thing(path:string,x:number,bottom:number,width:number,trim=false,part?:number){const s=this.sprite(path,trim,part);if(s)this.ctx.drawImage(s.image,s.x,s.y,s.w,s.h,x-width/2,bottom-width*s.h/s.w,width,width*s.h/s.w);}
 private fence(points:number[][]){const ctx=this.ctx;for(let i=1;i<points.length;i++){
  const a=points[i-1],b=points[i];for(const height of [26,49]){ctx.save();ctx.translate(a[0],900-a[1]-height);ctx.rotate(Math.atan2(a[1]-b[1],b[0]-a[0]));const s=this.sprite('classic/fence/trilho.png');if(s)ctx.drawImage(s.image,0,-5,Math.hypot(b[0]-a[0],b[1]-a[1]),10);ctx.restore();}
 }for(const [x,y]of points)this.thing('classic/fence/mourao.png',x,903-y,25);}
 private draw(){
  if(document.hidden)return;const ctx=this.ctx,period=periodAt(this.localTime());
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
    if(tile.weeds){const weeds=this.sprite(`classic/effects/erva_daninha_${Math.floor(Date.now()/160)%4+1}.png`);if(weeds)ctx.drawImage(weeds.image,p.x+2,p.y-50,72,72);}
    if(plant){const size=cropSize(tile.stage,plant.w/plant.h);ctx.save();if(tile.dry&&tile.state!=='READY')ctx.filter='sepia(.65) saturate(.6)';ctx.drawImage(plant.image,plant.x,plant.y,plant.w,plant.h,p.x-size.width/2,p.y+8-size.height,size.width,size.height);ctx.restore();}
    if(tile.insects)this.thing(`classic/effects/verme_${Math.min(3,tile.insects)}.png`,p.x-4+Math.sin(Date.now()/200+tile.column*3)*1.5,p.y+24,70);
   }
   if(tile.protected){ctx.fillStyle='#90def1';ctx.font='18px sans-serif';ctx.fillText('◆',p.x+58,p.y);}
   if(this.hover===tile){ctx.strokeStyle='#fff4a2';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(p.x,p.y-36);ctx.lineTo(p.x+85,p.y);ctx.lineTo(p.x,p.y+36);ctx.lineTo(p.x-85,p.y);ctx.closePath();ctx.stroke();}
  }
  if(this.farm.own&&this.farm.nextPlot){const p=tileCenter(this.farm.nextPlot.column,this.farm.nextPlot.row);this.thing('classic/terrain/placa-terreno.png',p.x,p.y,110,true);ctx.save();ctx.textAlign='center';ctx.fillStyle='#fff9dd';ctx.shadowColor='#67431f';ctx.shadowBlur=2;ctx.font='bold 16px sans-serif';ctx.fillText('50 verdes',p.x,p.y-70);ctx.restore();}
  for(const animal of this.farm.animals){
   const layout=animalLayout[animal.id];if(!layout)continue;const[x,y]=layout,w=animalWidth(animal.id,animal.stage),bottom=900-y;
   ctx.save();ctx.fillStyle='#152b1942';ctx.beginPath();ctx.ellipse(x,bottom,w*.325,w*.052,0,0,Math.PI*2);ctx.fill();ctx.restore();
   if(animal.id==='abelha'){
    const path='classic/animals/abelha/colmeia-abelha-atlas.png',hive=this.sprite(path,true,0);
    this.thing(path,x,bottom,w,true,0);
    if(hive&&period!=='night'&&animal.status!=='HUNGRY')for(let i=0;i<3;i++){const t=Date.now()/1000*1.4+i*1.7;this.thing(path,x+Math.sin(t)*28,bottom-w*hive.h/hive.w+8-Math.cos(t*1.3)*12,16,true,1);}
   }else{
    const path=animalPath(animal.id,animal.stage,period==='night',Math.floor(Date.now()/4000)%2!==0);
    // Visible alpha bounds meet the ground anchor; avoid the old detached shadow.
    const pose=this.sprite(path,true);
    this.thing(path,x,bottom,pose&&animal.id==='pavao'?peacockWidth(w,pose.w/pose.h):w,true);
   }
   const product=productPath(animal.id,animal.status);if(product)this.thing(product,x+38,bottom+12,28,true);
  }
  this.fence([[986,410],[1072,370],[1160,334],[1250,304],[1342,280],[1434,262],[1526,250],[1618,244]]);
 }
}
