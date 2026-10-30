import type {Farm,Catalog,Tile} from './types.ts';
import {periodAt,tileCenter,pickTile,animalPath,animalLayout} from './presentation.ts';

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
 private events=new AbortController();
 constructor(canvas:HTMLCanvasElement,catalog:Catalog,onTile:(tile:Tile)=>void,onAnimal:(id:string)=>void,onHover:(tile:Tile|null)=>void) {
  this.canvas=canvas;this.ctx=canvas.getContext('2d')!;this.catalog=catalog;this.onTile=onTile;this.onAnimal=onAnimal;this.onHover=onHover;
  canvas.width=1600;canvas.height=900;
  canvas.addEventListener('pointermove',e=>{const p=this.point(e);const pick=pickTile(p.x,p.y);this.hover=pick?this.farm?.tiles.find(t=>t.column===pick.column&&t.row===pick.row)??null:null;this.onHover(this.hover);},{signal:this.events.signal});
  canvas.addEventListener('pointerleave',()=>{this.hover=null;this.onHover(null);},{signal:this.events.signal});
  canvas.addEventListener('click',e=>{
   if(!this.farm)return;const p=this.point(e),pick=pickTile(p.x,p.y);
   if(pick){const tile=this.farm.tiles.find(t=>t.column===pick.column&&t.row===pick.row);if(tile)this.onTile(tile);return;}
   for(const animal of this.farm.animals) {const [x,y,w]=animalLayout[animal.id]??[0,0,0];if(Math.abs(p.x-x)<w/2&&p.y<900-y&&p.y>900-y-w)this.onAnimal(animal.id);}
  },{signal:this.events.signal});
  // Static scene needs only gentle bee/peacock updates, not a busy 60 fps loop.
  this.animation=window.setInterval(()=>this.draw(),200);
 }
 close(){clearInterval(this.animation);this.events.abort();}
 update(farm:Farm){this.farm=farm;this.draw();}
 private point(e:PointerEvent|MouseEvent){const rect=this.canvas.getBoundingClientRect();return{x:(e.clientX-rect.left)*1600/rect.width,y:(e.clientY-rect.top)*900/rect.height};}
 private sprite(path:string,trim=false):Sprite|undefined {
  const key=path+trim;const cached=this.sprites.get(key);if(cached)return cached;if(cached===null)return;
  this.sprites.set(key,null);const image=new Image();image.src='/assets/'+path;
  image.onload=()=>{
   let x=0,y=0,w=image.width,h=image.height;
   if(trim){const scratch=document.createElement('canvas');scratch.width=w;scratch.height=h;const context=scratch.getContext('2d',{willReadFrequently:true})!;context.drawImage(image,0,0);const pixels=context.getImageData(0,0,w,h).data;
    let left=w,top=h,right=0,bottom=0;
    for(let row=0;row<h;row++)for(let col=0;col<w;col++)if(pixels[(row*w+col)*4+3]>12){left=Math.min(left,col);top=Math.min(top,row);right=Math.max(right,col);bottom=Math.max(bottom,row);}
    if(right>=left&&bottom>=top){x=left;y=top;w=right-left+1;h=bottom-top+1;}
   }
   this.sprites.set(key,{image,x,y,w,h});this.draw();
  };
  image.onerror=()=>{console.warn('Asset indisponível:',path);};return;
 }
 private thing(path:string,x:number,bottom:number,width:number,trim=false){const s=this.sprite(path,trim);if(s)this.ctx.drawImage(s.image,s.x,s.y,s.w,s.h,x-width/2,bottom-width*s.h/s.w,width,width*s.h/s.w);}
 private fence(points:number[][]){const ctx=this.ctx;for(let i=1;i<points.length;i++){
  const a=points[i-1],b=points[i];for(const height of [26,49]){ctx.save();ctx.translate(a[0],900-a[1]-height);ctx.rotate(Math.atan2(a[1]-b[1],b[0]-a[0]));const s=this.sprite('classic/fence/trilho.png');if(s)ctx.drawImage(s.image,0,-5,Math.hypot(b[0]-a[0],b[1]-a[1]),10);ctx.restore();}
 }for(const [x,y]of points)this.thing('classic/fence/mourao.png',x,903-y,25);}
 private draw(){
  if(document.hidden)return;const ctx=this.ctx,period=periodAt(new Date());
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
   const s=this.sprite(`classic/terrain/${terrain}.png`,true);
   if(s)ctx.drawImage(s.image,s.x,s.y,s.w,s.h,p.x-86,p.y-37,172,74);
   if(tile.state==='DEAD')this.thing('classic/terrain/planta-morta.png',p.x,p.y+8,105,true);
   else if(tile.cropId){const crop=this.catalog.crops.find(c=>c.id===tile.cropId);if(crop)this.thing(`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_${tile.stage}.png`,p.x,p.y+10,tile.stage<3?60:110,true);}
   if(tile.weeds){ctx.fillStyle='#428519';ctx.font='24px sans-serif';ctx.fillText('♣',p.x-50,p.y);}
   if(tile.insects){ctx.fillStyle='#542745';ctx.font='20px sans-serif';ctx.fillText('●',p.x+38,p.y-10);}
   if(tile.protected){ctx.fillStyle='#90def1';ctx.font='18px sans-serif';ctx.fillText('◆',p.x+58,p.y);}
   if(this.hover===tile){ctx.strokeStyle='#fff4a2';ctx.lineWidth=3;ctx.beginPath();ctx.moveTo(p.x,p.y-36);ctx.lineTo(p.x+85,p.y);ctx.lineTo(p.x,p.y+36);ctx.lineTo(p.x-85,p.y);ctx.closePath();ctx.stroke();}
  }
  if(this.farm.own&&this.farm.nextPlot){const p=tileCenter(this.farm.nextPlot.column,this.farm.nextPlot.row);this.thing('classic/terrain/placa-terreno.png',p.x,p.y,65,true);}
  for(const animal of this.farm.animals){
   const layout=animalLayout[animal.id];if(!layout)continue;const[x,y,w]=layout,bottom=900-y;
   if(animal.id==='abelha'){
    const s=this.sprite('classic/animals/abelha/colmeia-abelha-atlas.png');if(s){ctx.drawImage(s.image,0,0,s.image.width/2,s.image.height,x-w/2,bottom-w,w,w);if(period!=='night')for(let i=0;i<3;i++){const t=Date.now()/1000+i;ctx.drawImage(s.image,s.image.width/2,0,s.image.width/2,s.image.height,x+Math.sin(t)*22+i*8,bottom-w+Math.cos(t)*9-14,22,22);}}
   }else{
    const path=animalPath(animal.id,animal.stage,period==='night',Math.floor(Date.now()/5000)%2===0);
    // Visible alpha bounds meet the ground anchor; avoid the old detached shadow.
    this.thing(path,x,bottom,w,true);
   }
  }
  this.fence([[986,410],[1072,370],[1160,334],[1250,304],[1342,280],[1434,262],[1526,250],[1618,244]]);
 }
}
