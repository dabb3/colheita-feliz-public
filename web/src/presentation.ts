import type {Crop,Farm,Tile} from './types.ts';
export function untilUtcMidnight(serverTime:string){
 const now=new Date(serverTime),midnight=Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate()+1),minutes=Math.max(1,Math.ceil((midnight-now.getTime())/60_000)),hours=Math.floor(minutes/60);
 return hours>0?`${hours} h${minutes%60?String(minutes%60).padStart(2,'0'):''}`:`${minutes} min`;
}
export function cropGroups(crops:Crop[]){return {common:crops.filter(crop=>!crop.vipOnly),vip:crops.filter(crop=>crop.vipOnly)};}
export function soundForAction(action:string,animalReady=false){
 if(action==='PLOW')return 'click_button';
 if(action==='PLANT'||action==='FERTILIZE'||action==='LOVE')return 'plant';
 if(action==='WATER')return 'water';
 if(action==='HARVEST'||action==='STEAL'||(action==='TEND_ANIMAL'&&animalReady))return 'harvest';
 if(action==='WEED'||action==='PESTICIDE')return 'weed';
 if(action==='TEND_ANIMAL')return 'plant';
 if(action.startsWith('BUY_'))return 'buy';
 if(action.startsWith('SELL_'))return 'sell';
 return undefined;
}
export function showAnimalProduct(status:string,kind:string,id:string){return status==='READY'&&kind==='PRODUCER'&&animalProductPath(id)!==undefined;}
export function animalProductPath(id:string){
 if(id==='galinha'||id==='pato')return 'classic/animals/ninho-geral/ninho-com-ovos.png';
 if(id==='ovelha')return 'classic/animals/ovelha/la-ovelha.png';
 if(id==='vaca')return 'classic/animals/vaca/balde-vaca.png';
 return undefined;
}
export function productPath(id:string,status:string){return status==='READY'?animalProductPath(id):undefined;}
export function actionRewardModel(before:Pick<Farm,'coins'|'greenCoins'|'currentXp'|'level'>,after:Pick<Farm,'coins'|'greenCoins'|'currentXp'|'level'>){
 const level=after.level??before.level??0,leveledUp=level>(before.level??0);
 const gain=(previous:string|undefined,current:string|undefined)=>Math.max(0,Number(current??0)-Number(previous??0));
 return {coins:gain(before.coins,after.coins),greens:gain(before.greenCoins,after.greenCoins),xp:leveledUp?0:gain(before.currentXp,after.currentXp),level,leveledUp};
}
export function cropTooltipModel(tile:Tile,name:string,vipOnly:boolean,vipActive:boolean,growthDurationSeconds=tile.nominalRemainingSeconds){
 const growthProgress=growthDurationSeconds>0?Math.max(0,Math.min(1,1-tile.nominalRemainingSeconds/growthDurationSeconds)):tile.state==='READY'?1:0;
 const minutes=Math.max(1,Math.ceil(tile.remainingSeconds/60)),hours=Math.floor(minutes/60);
 const remaining=hours>=24?`${Math.floor(hours/24)}d ${hours%24}h`:hours>0?`${hours}h${String(minutes%60).padStart(2,'0')}`:`${minutes} min`;
 const growthText=tile.state==='DEAD'?'Planta morta · use a pá':tile.state==='READY'?'Pronta! Use a luvinha':tile.weeds&&tile.insects>0?'Pausado por erva e insetos':tile.weeds?'Pausado pela erva daninha':tile.insects>0?'Pausado pelos insetos':tile.dry&&growthProgress===0?'Regue para começar':`${remaining}${tile.dry?' · seca: cresce a 50%':' restantes'}`;
 const chips:string[]=[];
 if(tile.state==='READY')chips.push('PRONTA!');
 if(tile.weeds)chips.push('PRAGA');
 if(tile.insects>0)chips.push(tile.insects===1?'VERME':`${tile.insects} VERMES`);
 if(tile.dry&&tile.state!=='READY')chips.push('SECA');
 if(tile.protected)chips.push('PROTEGIDA');
 if(!chips.length)chips.push('SAUDÁVEL');
 return {title:name,seasonText:`${tile.season} / ${tile.seasons} · ${vipOnly&&!vipActive?'venda bloqueada sem VIP':'1 unidade'}`,seasonProgress:tile.seasons>0?Math.max(0,Math.min(1,tile.season/tile.seasons)):0,growthText,growthProgress,chips:chips.slice(0,4)};
}
export function periodAt(date:Date):'day'|'sunset'|'night' {
 const minute=date.getHours()*60+date.getMinutes();
 return minute>=360 && minute<1020 ? 'day' : minute>=1020 && minute<=1110 ? 'sunset' : 'night';
}
export function backgroundBounds(viewportWidth:number,viewportHeight:number,imageWidth:number,imageHeight:number) {
 const scale=Math.max(viewportWidth/imageWidth,viewportHeight/imageHeight);
 const width=imageWidth*scale,height=imageHeight*scale;
 return {x:(viewportWidth-width)/2,y:0,width,height};
}
export function tileCenter(column:number,row:number) {return {x:585+(column-row)*85,y:400+(column+row)*36};}
export function tileArtRect(column:number,row:number) {
 const p=tileCenter(column,row);
 return {x:p.x-94,y:p.y-50,width:188,height:100};
}
export function animalShadowRect(x:number,bottom:number,width:number,footWidth=1) {
 const shadowWidth=width*footWidth*.65;
 return {x:x-shadowWidth/2,y:bottom,rx:shadowWidth/2,ry:shadowWidth*.16/2};
}
export function spriteDrawRect(anchorX:number,bottom:number,width:number,left:number,top:number,cropWidth:number,cropHeight:number,footBottom:number,footCenterX:number) {
 const scale=width/cropWidth,height=scale*cropHeight;
 return {x:anchorX-width*footCenterX,y:bottom-height*(1-footBottom),width,height};
}
export function measureSpriteFootprint(imageWidth:number,imageHeight:number,alphaAt:(x:number,y:number)=>number) {
 let left=imageWidth,right=-1,top=imageHeight,bottom=-1;
 for(let y=0;y<imageHeight;y++)for(let x=0;x<imageWidth;x++)if(alphaAt(x,y)>12){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
 if(right<left||bottom<top)return {left:0,top:0,width:imageWidth,height:imageHeight,footBottom:0,footCenterX:.5,footWidth:1};
 const band=Math.max(1,Math.round((bottom-top+1)*.12));let footLeft=imageWidth,footRight=-1;
 for(let y=bottom-band+1;y<=bottom;y++)for(let x=left;x<=right;x++)if(alphaAt(x,y)>12){footLeft=Math.min(footLeft,x);footRight=Math.max(footRight,x);}
 const cropWidth=right-left+1,cropHeight=bottom-top+1;
 return {left,top,width:cropWidth,height:cropHeight,footBottom:(top+cropHeight-bottom-1)/cropHeight,
  footCenterX:footRight>=footLeft?(footLeft+footRight+1-2*left)/(2*cropWidth):.5,footWidth:(footRight-footLeft+1)/cropWidth};
}
export function pickTile(x:number,y:number):{column:number;row:number}|null {
 for(let row=0;row<5;row++) for(let column=0;column<6;column++) {
  const p=tileCenter(column,row);
  if(Math.abs(x-p.x)/85+Math.abs(y-p.y)/36<=1) return {column,row};
 }
 return null;
}
export function animalPath(id:string,stage:string,sleep:boolean,walking:boolean,open:boolean) {
 if(id==='pavao'&&open&&!sleep) return 'classic/animals/pavao/pavao-aberto.png';
 const folder=id==='pombo'?'pomba':id==='burro'?'burrinho':id;
 let stem=folder;
 if(id==='pombo') stem=`pomba-${stage==='BABY'?'bebe':stage==='YOUNG'?'jovem':'adulta'}`;
 if(id==='burro') stem=`burrinho${stage==='ADULT'?'':stage==='BABY'?'-bebe':'-jovem'}`;
 if(id==='porco') stem=`porco-${stage==='BABY'?'bebe':stage==='YOUNG'?'jovem':'adulto'}`;
 if(id==='coelho') stem=stage==='ADULT'?(sleep||walking?'coelho-adulto':'coelho'):stage==='BABY'?'coelho-bebe':'coelho-jovem';
 if(id==='cachorro-mastiff') stem='mastiff';
 if(id==='cabra') stem=sleep||walking?'cabra-adulta':'cabra';
 const suffix=sleep&&id!=='pombo'&&id!=='abelha'?'-dormindo':walking&&id!=='pombo'&&id!=='abelha'?'-andando':'';
 return `classic/animals/${folder}/${stem}${suffix}.png`;
}
export function animalMotion(id:string,seconds:number,sleeping=false):{dx:number;dy:number;walking:boolean;facingRight:boolean;open:boolean} {
 if(sleeping){const motion=animalMotion(id,seconds);return {...motion,dx:0,dy:0,walking:false,open:false};}
 if(id==='pombo'||id==='abelha')return {dx:0,dy:0,walking:false,facingRight:false,open:false};
 if(id==='pavao')return {dx:0,dy:0,walking:false,facingRight:false,open:Math.floor(seconds/4)%2!==0};
 let hash=0;for(const char of id)hash=(hash*31+char.charCodeAt(0))>>>0;
 const random=(seed:number)=>{let value=seed>>>0;value^=value>>>16;value=Math.imul(value,0x7feb352d);value^=value>>>15;value=Math.imul(value,0x846ca68b);value^=value>>>16;return (value>>>0)/4294967296;};
 const radius=id==='cavalo'?36:20;
 const destination=(cycle:number)=>{const angle=random(hash+Math.imul(cycle+1,0x9e3779b9))*Math.PI*2,distance=radius*(.3+.7*random(hash^Math.imul(cycle+1,0x85ebca6b)));return{x:Math.cos(angle)*distance,y:Math.sin(angle)*distance*.55};};
 const cycle=Math.floor(Math.max(0,seconds)/10),elapsed=Math.max(0,seconds)-cycle*10;
 const from=cycle===0?{x:0,y:0}:destination(cycle-1),to=destination(cycle),previous=cycle<2?{x:0,y:0}:destination(cycle-2);
 const wait=cycle===0?.5+random(hash)*2.5:2+random(hash^Math.imul(cycle,0xc2b2ae35))*4;
 const duration=Math.hypot(to.x-from.x,to.y-from.y)/18,progress=Math.max(0,Math.min(1,(elapsed-wait)/duration)),moving=elapsed>=wait&&elapsed<wait+duration;
 const facingRight=moving?to.x>from.x:from.x>previous.x;
 return {dx:from.x+(to.x-from.x)*progress,dy:from.y+(to.y-from.y)*progress,walking:moving,facingRight,open:false};
}
export function beeOffset(bee:number,seconds:number) {
 const phase=seconds*1.4+bee*1.7;
 return {x:Math.sin(phase)*28,y:Math.cos(phase*1.3)*12};
}
export function animalFlip(id:string,facingRight:boolean) {
 const originalRight=['cavalo','cachorro-robo','cachorro-mastiff','cabra','burro','avestruz','coelho'].includes(id);
 return facingRight!==originalRight;
}
export function animalLayer(id:string):'back'|'front' {
 const layout=animalLayout[id];
 return animalInsidePen(id)||(layout?.[1]??0)>450?'back':'front';
}
export function animalInsidePen(id:string){return ['galinha','pato','porco','ovelha','vaca'].includes(id);}
export function animationFrame(seconds:number,frameCount:number,frameSeconds:number) {
 return Math.floor(Math.max(0,seconds)/frameSeconds)%frameCount;
}
export function cropHeight(stage:number){return 32+Math.max(1,Math.min(5,stage))*16;}
export const toolShortcutOrder=['CURSOR','PLOW','PLANT','WATER','WEED','PESTICIDE','HARVEST','FERTILIZE'];
export function gameShortcut(key:string,visiting=false):{action:'tool';tool:string}|{action:'shop'|'deposit'|'missions'|'music'|'effects'}|undefined {
 if(!visiting&&/^[1-8]$/.test(key))return {action:'tool',tool:toolShortcutOrder[Number(key)-1]};
 const actions:Record<string,'shop'|'deposit'|'missions'|'music'|'effects'>={b:'shop',v:'deposit',q:'missions',m:'music',n:'effects'};
 const action=actions[key.toLowerCase()];return action?{action}:undefined;
}
export function shortcutPanelState(isOpen:boolean,currentTitle:string,targetTitle:string):'close'|'open' {
 return isOpen&&currentTitle===targetTitle?'close':'open';
}
export function toolbarTrayToggle(current:string,next:string,open:boolean){
 return ['PLANT','FERTILIZE','LOVE'].includes(next)&&!(current===next&&open);
}
export type TileEffect='plow'|'plant'|'water'|'harvest'|'weed'|'pest'|'fertilize';
export function tileEffect(previous:Tile|null,current:Tile):TileEffect|null {
 if(!previous)return null;
 if(previous.state==='GRASS'&&current.state==='PLOWED')return 'plow';
 if(previous.cropId&&!current.cropId&&previous.state==='READY')return 'harvest';
 if(!previous.cropId&&current.cropId)return 'plant';
 if(previous.dry&&!current.dry&&!!current.cropId)return 'water';
 if(previous.weeds&&!current.weeds)return 'weed';
 if(previous.insects>0&&current.insects===0)return 'pest';
 if(!previous.fertilized&&current.fertilized)return 'fertilize';
 return null;
}
export function tileCommand(tool:string,tile:Tile,seedId:string,fertilizerId:string,visiting=false):Record<string,string|number> {
 const fields:Record<string,string|number>={action:tool,column:tile.column,row:tile.row};
 if(visiting)fields.harvestRevision=tile.harvestRevision;
 if(tool==='PLANT')fields.itemId=seedId;
 if(tool==='FERTILIZE'||(visiting&&tool==='LOVE'))fields.itemId=fertilizerId;
 return fields;
}
export function animalStatusText(status:string,kind:string,remainingSeconds:number) {
 if(status==='HUNGRY')return 'Com fome';
 if(status==='READY')return kind==='GROWING'?'Adulto pronto para venda':'Pronto para recolher';
 if(status==='PROTECTING'||(status==='PRODUCING'&&kind==='PROTECTOR'))return 'Protegendo';
 const seconds=Math.max(0,remainingSeconds),hours=Math.floor(seconds/3600),minutes=Math.floor(seconds%3600/60);
 const time=[hours?`${hours} h`:undefined,minutes?`${minutes} min`:undefined].filter(Boolean).join(' ');
 return time?`${kind==='GROWING'?'Crescendo':'Produzindo'} · ${time}`:'Em desenvolvimento';
}
export function animalActionLabel(status:string,kind:string,stage:string):string|null {
 if(status==='HUNGRY')return 'Alimentar';
 if(status==='READY'&&kind==='PRODUCER')return 'Recolher produto';
 if(status==='READY'&&kind==='GROWING'&&stage==='ADULT')return 'Vender adulto';
 return null;
}
export function animalClickCommand(id:string,visiting:boolean):{action:'TEND_ANIMAL';itemId:string}|null {
 return visiting?null:{action:'TEND_ANIMAL',itemId:id};
}
// Presentation coordinates only; ownership, stages and eligibility come from the API.
export const animalLayout:Record<string,[number,number,number]>={
 galinha:[1170,425,62],pato:[1350,350,48],porco:[1480,325,125],ovelha:[1375,435,84],vaca:[1515,455,145],
 abelha:[1210,60,156],pombo:[1330,230,110],coelho:[195,100,72],burro:[300,470,140],pavao:[1020,620,80],
 avestruz:[1220,635,90],cabra:[180,570,110],cavalo:[345,45,150],'cachorro-robo':[785,490,110],'cachorro-mastiff':[1430,60,135]
};
