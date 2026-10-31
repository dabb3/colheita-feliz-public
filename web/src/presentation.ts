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
export function actionRewardModel(before:Pick<Farm,'coins'|'greenCoins'|'currentXp'|'level'>,after:Pick<Farm,'coins'|'greenCoins'|'currentXp'|'level'>){
 const level=after.level??before.level??0,leveledUp=level>(before.level??0);
 const gain=(previous:string|undefined,current:string|undefined)=>Math.max(0,Number(current??0)-Number(previous??0));
 return {coins:gain(before.coins,after.coins),greens:gain(before.greenCoins,after.greenCoins),xp:leveledUp?0:gain(before.currentXp,after.currentXp),level,leveledUp};
}
export function cropTooltipModel(tile:Tile,name:string,vipOnly:boolean,vipActive:boolean){
 const growthProgress=tile.nominalRemainingSeconds>0?Math.max(0,Math.min(1,1-tile.remainingSeconds/tile.nominalRemainingSeconds)):tile.state==='READY'?1:0;
 const hours=Math.ceil(tile.remainingSeconds/3600),minutes=Math.ceil(tile.remainingSeconds/60);
 const remaining=hours>=24?`${Math.floor(hours/24)}d ${hours%24}h`:hours>0?`${hours}h${String(minutes%60).padStart(2,'0')}`:`${Math.max(1,minutes)} min`;
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
export const terrainSize={width:188,height:100};
export function cropSize(stage:number,aspect:number) {const height=32+stage*16;return{width:height*aspect,height};}
export function animalWidth(id:string,stage:string) {return (animalLayout[id]?.[2]??0)*(stage==='BABY'?.65:stage==='YOUNG'?.85:1);}
export function productPath(id:string,status:string):string|undefined {
 if(status!=='READY')return;
 return ({galinha:'classic/animals/ninho-geral/ninho-com-ovos.png',pato:'classic/animals/ninho-geral/ninho-com-ovos.png',
  vaca:'classic/animals/vaca/balde-vaca.png',ovelha:'classic/animals/ovelha/la-ovelha.png'} as Record<string,string>)[id];
}
export function pickTile(x:number,y:number):{column:number;row:number}|null {
 for(let row=0;row<5;row++) for(let column=0;column<6;column++) {
  const p=tileCenter(column,row);
  if(Math.abs(x-p.x)/85+Math.abs(y-p.y)/36<=1) return {column,row};
 }
 return null;
}
export function animalPath(id:string,stage:string,sleep:boolean,open:boolean,walking=false) {
 if(id==='pavao'&&open&&!sleep) return 'classic/animals/pavao/pavao-aberto.png';
 const folder=id==='pombo'?'pomba':id==='burro'?'burrinho':id;
 let stem=folder;
 if(id==='pombo') stem=`pomba-${stage==='BABY'?'bebe':stage==='YOUNG'?'jovem':'adulta'}`;
 if(id==='burro') stem=`burrinho${stage==='ADULT'?'':stage==='BABY'?'-bebe':'-jovem'}`;
 if(id==='porco') stem=`porco-${stage==='BABY'?'bebe':stage==='YOUNG'?'jovem':'adulto'}`;
 if(id==='coelho') stem=stage==='ADULT'?(sleep||walking?'coelho-adulto':'coelho'):stage==='BABY'?'coelho-bebe':'coelho-jovem';
 if(id==='cachorro-mastiff') stem='mastiff';
 if(id==='cabra') stem=sleep||walking?'cabra-adulta':'cabra';
 const mobile=!['pombo','pavao','abelha'].includes(id);
 return `classic/animals/${folder}/${stem}${sleep&&id!=='pombo'&&id!=='abelha'?'-dormindo':walking&&mobile?'-andando':''}.png`;
}
// Presentation coordinates only; ownership, stages and eligibility come from the API.
export const animalLayout:Record<string,[number,number,number]>={
 galinha:[1170,425,62],pato:[1350,350,48],porco:[1480,325,125],ovelha:[1375,435,84],vaca:[1515,455,145],
 abelha:[1210,60,156],pombo:[1330,230,110],coelho:[195,100,72],burro:[300,470,140],pavao:[1020,620,80],
 avestruz:[1220,635,90],cabra:[180,570,110],cavalo:[345,45,150],'cachorro-robo':[785,490,110],'cachorro-mastiff':[1430,60,135]
};

// Both peacock poses use the same visible height; their silhouettes have different widths.
export function peacockWidth(height:number,aspect:number){return height*aspect;}

export function poseWidth(width:number,idleAspect:number,poseAspect:number){return width/idleAspect*poseAspect;}
export function animalFlip(id:string,facingRight:boolean){return facingRight!==['cavalo','cachorro-robo','cachorro-mastiff','cabra','burro','avestruz','coelho'].includes(id);}
export function animalMotion(id:string,seconds:number,sleeping:boolean){
 const [homeX,homeY]=animalLayout[id]??[0,0];
 if(['pombo','pavao','abelha'].includes(id))return{x:homeX,y:homeY,walking:false,facingRight:false,alternate:!sleeping&&id==='pavao'&&Math.floor(seconds/4)%2!==0};
 const radius=id==='cavalo'?36:20,distance=2*radius*Math.hypot(1,.35),travel=distance/18,half=2+travel;
 const offset=[...id].reduce((sum,char)=>sum+char.charCodeAt(0),0)/37;
 const phase=(seconds+offset)%(half*2),leg=Math.floor(phase/half),local=phase%half,progress=Math.max(0,Math.min(1,(local-2)/travel));
 const dx=(leg===0?-radius:radius)+(leg===0?2*radius:-2*radius)*progress,walking=!sleeping&&local>=2;
 return{x:homeX+dx,y:homeY+dx*.35,walking,facingRight:leg===0,alternate:walking&&Math.floor((leg+progress)*distance/6)%2!==0};
}
