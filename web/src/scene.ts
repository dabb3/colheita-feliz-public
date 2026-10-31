import type {Farm,Catalog,Tile} from './types.ts';
import {periodAt,backgroundBounds,tileCenter,tileArtRect,pickTile,animalPath,animalLayout,animalShadowRect,spriteDrawRect,measureSpriteFootprint,animalMotion,beeOffset,animalFlip,animalLayer,animalInsidePen,animationFrame,cropHeight,tileEffect,showAnimalProduct,animalProductPath,type TileEffect} from './presentation.ts';

type Sprite={image:HTMLImageElement|HTMLCanvasElement;x:number;y:number;w:number;h:number;footBottom:number;footCenterX:number;footWidth:number};
export class Scene {
 private canvas:HTMLCanvasElement;
 private ctx:CanvasRenderingContext2D;
 private sprites=new Map<string,Sprite|null>();
 private farm?:Farm;
 private tileEffects:{column:number;row:number;kind:TileEffect;started:number}[]=[];
 private catalog:Catalog;
 private hover:Tile|null=null;
 private onTile:(tile:Tile)=>void;
 private onAnimal:(id:string)=>void;
 private onHover:(tile:Tile|null,point:{x:number;y:number}|null)=>void;
 private animation:number;
 private lastFrame=0;
 private animate=(time:number)=>{if(time-this.lastFrame>=33){this.lastFrame=time;this.draw();}this.animation=window.requestAnimationFrame(this.animate);};
 private events=new AbortController();
 constructor(canvas:HTMLCanvasElement,catalog:Catalog,onTile:(tile:Tile)=>void,onAnimal:(id:string)=>void,onHover:(tile:Tile|null,point:{x:number;y:number}|null)=>void) {
  this.canvas=canvas;this.ctx=canvas.getContext('2d')!;this.catalog=catalog;this.onTile=onTile;this.onAnimal=onAnimal;this.onHover=onHover;
  canvas.width=1600;canvas.height=900;
  canvas.addEventListener('pointermove',e=>{const p=this.point(e);const pick=pickTile(p.x,p.y);this.hover=pick?this.farm?.tiles.find(t=>t.column===pick.column&&t.row===pick.row)??null:null;this.onHover(this.hover,pick?tileCenter(pick.column,pick.row):null);},{signal:this.events.signal});
  canvas.addEventListener('pointerleave',()=>{this.hover=null;this.onHover(null,null);},{signal:this.events.signal});
  canvas.addEventListener('click',e=>{
   if(!this.farm)return;const p=this.point(e),pick=pickTile(p.x,p.y);
   if(pick){const tile=this.farm.tiles.find(t=>t.column===pick.column&&t.row===pick.row);if(tile)this.onTile(tile);return;}
   const seconds=performance.now()/1000,sleep=periodAt(new Date())==='night';
   for(const animal of this.farm.animals) {const [x,y,w]=animalLayout[animal.id]??[0,0,0],motion=animalMotion(animal.id,seconds,sleep);if(Math.abs(p.x-x-motion.dx)<w/2&&p.y<900-y+motion.dy&&p.y>900-y+motion.dy-w)this.onAnimal(animal.id);}
  },{signal:this.events.signal});
  // Cap redraws at 30 fps; requestAnimationFrame pauses naturally in hidden tabs.
  this.animation=window.requestAnimationFrame(this.animate);
 }
 close(){window.cancelAnimationFrame(this.animation);this.events.abort();}
 update(farm:Farm){
  if(this.farm?.owner===farm.owner){
   const incoming=new Map(farm.tiles.map(tile=>[`${tile.column}:${tile.row}`,tile]));
   for(const previous of this.farm.tiles){const current=incoming.get(`${previous.column}:${previous.row}`);if(!current)continue;const kind=tileEffect(previous,current);if(kind)this.tileEffects.push({column:current.column,row:current.row,kind,started:performance.now()});}
   this.tileEffects=this.tileEffects.slice(-24);
  }
  this.farm=farm;this.draw();
 }
 private point(e:PointerEvent|MouseEvent){const rect=this.canvas.getBoundingClientRect(),scale=Math.max(rect.width/1600,rect.height/900);return{x:(e.clientX-rect.left-(rect.width-1600*scale)/2)/scale,y:(e.clientY-rect.top-(rect.height-900*scale)/2)/scale};}
 private sprite(path:string,trim=false,half?:number):Sprite|undefined {
  const key=`${path}:${trim}:${half??''}`;const cached=this.sprites.get(key);if(cached)return cached;if(cached===null)return;
  this.sprites.set(key,null);const image=new Image();image.src='/assets/'+path;
  image.onload=()=>{
   let source:HTMLImageElement|HTMLCanvasElement=image;
   if(half!==undefined){const part=document.createElement('canvas'),partWidth=Math.floor(image.width/2);part.width=partWidth;part.height=image.height;part.getContext('2d')!.drawImage(image,half*partWidth,0,partWidth,image.height,0,0,partWidth,image.height);source=part;}
   let x=0,y=0,w=source.width,h=source.height,footBottom=0,footCenterX=.5,footWidth=1;
   if(trim){const scratch=document.createElement('canvas');scratch.width=w;scratch.height=h;const context=scratch.getContext('2d',{willReadFrequently:true})!;context.drawImage(source,0,0);const pixels=context.getImageData(0,0,w,h).data;
    const foot=measureSpriteFootprint(source.width,source.height,(col,row)=>pixels[(row*source.width+col)*4+3]);
    x=foot.left;y=foot.top;w=foot.width;h=foot.height;footBottom=foot.footBottom;footCenterX=foot.footCenterX;footWidth=foot.footWidth;
   }
   this.sprites.set(key,{image:source,x,y,w,h,footBottom,footCenterX,footWidth});this.draw();
  };
  image.onerror=()=>{console.warn('Asset indisponível:',path);};return;
 }
 private drawSprite(s:Sprite,x:number,bottom:number,width:number,flip=false){
  const rect=spriteDrawRect(x,bottom,width,s.x,s.y,s.w,s.h,s.footBottom,s.footCenterX),ctx=this.ctx;
  if(flip){ctx.save();ctx.translate(x*2,0);ctx.scale(-1,1);}
  ctx.drawImage(s.image,s.x,s.y,s.w,s.h,rect.x,rect.y,rect.width,rect.height);
  if(flip)ctx.restore();
 }
 private thing(path:string,x:number,bottom:number,width:number,trim=false){const s=this.sprite(path,trim);if(s)this.drawSprite(s,x,bottom,width);}
 private shadow(sprite:Sprite,x:number,bottom:number,width:number){
  const ctx=this.ctx,shadow=animalShadowRect(x,bottom,width,sprite.footWidth);
  ctx.save();ctx.fillStyle='rgba(0,0,0,.26)';ctx.beginPath();ctx.ellipse(shadow.x+shadow.rx,shadow.y,shadow.rx,shadow.ry,0,0,Math.PI*2);ctx.fill();ctx.restore();
 }
 private animal(path:string,x:number,bottom:number,width:number,flip:boolean){
  const sprite=this.sprite(path,true);if(!sprite)return;
  this.shadow(sprite,x,bottom,width);
  this.drawSprite(sprite,x,bottom,width,flip);
 }
 private drawAnimal(animal:Farm['animals'][number],seconds:number,sleep:boolean){
  const layout=animalLayout[animal.id];if(!layout)return;
  const[x,y,width]=layout,motion=animalMotion(animal.id,seconds,sleep),xAt=x+motion.dx,bottom=900-y+motion.dy,hungry=animal.status==='HUNGRY';
  if(animal.id==='abelha'){
   const path='classic/animals/abelha/colmeia-abelha-atlas.png',hive=this.sprite(path,true,0),bees=this.sprite(path,true,1);
   if(hive){this.shadow(hive,x,bottom,width);this.drawSprite(hive,x,bottom,width);}
   if(!sleep&&bees&&hive)for(let i=0;i<4;i++){const offset=beeOffset(i,seconds),beeX=x+offset.x+16*(bees.footCenterX-.5),beeBottom=bottom-width*hive.h/hive.w+8-offset.y;this.drawSprite(bees,beeX,beeBottom,16);}
   if(hungry)this.feedMarker(x,bottom-width-2);
   return;
  }
  const path=animalPath(animal.id,animal.stage,sleep,motion.walking,motion.open),sprite=this.sprite(path,true);
  this.animal(path,xAt,bottom,width,animalFlip(animal.id,motion.facingRight));
  const type=this.catalog.animals.find(item=>item.id===animal.id);
  if(showAnimalProduct(animal.status,type?.kind??'',animal.id))this.product(animalProductPath(animal.id)!,x+38,bottom+12,28);
  if(hungry&&sprite)this.feedMarker(xAt,bottom-width*sprite.h/sprite.w-2);
 }
 private product(path:string,x:number,bottom:number,width:number){
  const sprite=this.sprite(path,true);if(!sprite)return;
  this.drawSprite(sprite,x,bottom,width);
 }
 private feedMarker(x:number,bottom:number){const icon=this.sprite('classic/icons/racao-generica.png');if(icon)this.ctx.drawImage(icon.image,x-9,bottom-20,18,20);}
 private crop(path:string,x:number,bottom:number,height:number,dry:boolean){
  const sprite=this.sprite(path);if(!sprite)return;
  const width=height*sprite.image.width/sprite.image.height,ctx=this.ctx;
  ctx.save();if(dry)ctx.filter='sepia(.6) saturate(.8)';ctx.drawImage(sprite.image,x-width/2,bottom-height,width,height);ctx.restore();
 }
 private matureGlow(x:number,y:number,seconds:number,offset:number){
  const pulse=.55+.45*Math.sin(seconds*3.85+offset),ctx=this.ctx,gradient=ctx.createRadialGradient(x,y,5,x,y,74);
  gradient.addColorStop(0,`rgba(255,236,150,${.45*pulse})`);gradient.addColorStop(1,'rgba(255,236,150,0)');
  ctx.save();ctx.fillStyle=gradient;ctx.fillRect(x-74,y-54,148,108);ctx.restore();
 }
 private drawTileEffects(){
  const now=performance.now(),duration=850,colors:Record<TileEffect,string>={plow:'#bd8952',plant:'#88d354',water:'#68c8ff',harvest:'#ffe36e',weed:'#a7d455',pest:'#df7a75',fertilize:'#d3a8f1'};
  this.tileEffects=this.tileEffects.filter(effect=>now-effect.started<duration);
  for(const effect of this.tileEffects){
   const progress=(now-effect.started)/duration,p=tileCenter(effect.column,effect.row),ctx=this.ctx;
   ctx.save();ctx.globalAlpha=1-progress;ctx.fillStyle=colors[effect.kind];
   for(let i=0;i<4;i++){
    const angle=i*Math.PI/2+progress*2,x=p.x+Math.cos(angle)*(12+progress*22),y=p.y-18-progress*48+Math.sin(angle)*(5+progress*7);
    ctx.beginPath();ctx.ellipse(x,y,effect.kind==='water'?3:4,effect.kind==='water'?6:4,angle,0,Math.PI*2);ctx.fill();
   }
   ctx.restore();
  }
 }
 private fence(points:number[][]){const ctx=this.ctx;for(let i=1;i<points.length;i++){
  const a=points[i-1],b=points[i];for(const height of [26,49]){ctx.save();ctx.translate(a[0],900-a[1]-height);ctx.rotate(Math.atan2(a[1]-b[1],b[0]-a[0]));const s=this.sprite('classic/fence/trilho.png');if(s)ctx.drawImage(s.image,0,-5,Math.hypot(b[0]-a[0],b[1]-a[1]),10);ctx.restore();}
 }for(const [x,y]of points)this.thing('classic/fence/mourao.png',x,903-y,25);}
 private draw(){
  if(document.hidden)return;const ctx=this.ctx,period=periodAt(new Date());
  ctx.clearRect(0,0,1600,900);ctx.fillStyle=period==='night'?'#15464f':'#84ba30';ctx.fillRect(0,0,1600,900);
  const background=this.sprite(`classic/background/background${period==='day'?'':'-'+period}.png`);
  if(background){const bounds=backgroundBounds(1600,900,background.image.width,background.image.height);ctx.drawImage(background.image,bounds.x,bounds.y,bounds.width,bounds.height);}
  this.thing('classic/buildings/dog-house.png',410,900-556,214);
  this.thing('classic/buildings/main-house.png',735,900-540,420);
  this.fence([[1108,494],[1180,509],[1262,521],[1350,528],[1440,530],[1530,525],[1620,517]]);
  if(!this.farm)return;
  const seconds=performance.now()/1000,sleep=period==='night';
  for(const animal of this.farm.animals)if(animalInsidePen(animal.id))this.drawAnimal(animal,seconds,sleep);
  this.fence([[986,410],[1072,370],[1160,334],[1250,304],[1342,280],[1434,262],[1526,250],[1618,244]]);
  for(const animal of this.farm.animals)if(animalLayer(animal.id)==='back'&&!animalInsidePen(animal.id))this.drawAnimal(animal,seconds,sleep);
  for(const tile of [...this.farm.tiles].sort((a,b)=>a.column+a.row-b.column-b.row)){
   const p=tileCenter(tile.column,tile.row);
   const terrain=!tile.unlocked||tile.state==='GRASS'?'grama-default':tile.dry?'terra-seca':tile.state==='PLOWED'?'terra-arada':'terra-molhada';
   const s=this.sprite(`classic/terrain/${terrain}.png`);
   const art=tileArtRect(tile.column,tile.row);
   if(s){ctx.save();if(!tile.unlocked)ctx.globalAlpha=.72;ctx.drawImage(s.image,art.x,art.y,art.width,art.height);ctx.restore();}
   const crop=this.catalog.crops.find(c=>c.id===tile.cropId),ready=tile.state==='READY';
   if(ready)this.matureGlow(p.x,p.y-10,seconds,tile.column+tile.row);
   if(tile.weeds)this.thing(`classic/effects/erva_daninha_${animationFrame(seconds,4,.16)+1}.png`,p.x+2,p.y+20,72,true);
   if(tile.state==='DEAD')this.thing('classic/terrain/planta-morta.png',p.x,p.y+10,86,true);
   else if(crop)this.crop(`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_${tile.stage}.png`,p.x,p.y+10,cropHeight(tile.stage),tile.dry&&!ready);
   if(tile.insects>0)this.thing(`classic/effects/verme_${Math.min(3,tile.insects)}.png`,p.x-4,p.y+21,70,true);
   if(tile.protected){ctx.fillStyle='#90def1';ctx.font='18px sans-serif';ctx.fillText('◆',p.x+58,p.y);}
   if(this.hover===tile){const highlight=this.sprite('classic/terrain/terra-destaque.png');if(highlight){ctx.save();ctx.globalAlpha=.65+.35*Math.sin(seconds*4.5);ctx.drawImage(highlight.image,p.x-96,p.y-51,192,102);ctx.restore();}}
  }
  for(const animal of this.farm.animals)if(animalLayer(animal.id)==='front')this.drawAnimal(animal,seconds,sleep);
  this.drawTileEffects();
  if(this.farm.own&&this.farm.nextPlot){const p=tileCenter(this.farm.nextPlot.column,this.farm.nextPlot.row);this.thing('classic/terrain/placa-terreno.png',p.x,p.y,65,true);}
 }
}
