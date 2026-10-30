export function periodAt(date:Date):'day'|'sunset'|'night' {
 const minute=date.getHours()*60+date.getMinutes();
 return minute>=360 && minute<1020 ? 'day' : minute>=1020 && minute<=1110 ? 'sunset' : 'night';
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
