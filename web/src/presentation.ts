export function periodAt(date:Date):'day'|'sunset'|'night' {
 const minute=date.getHours()*60+date.getMinutes();
 return minute>=360 && minute<1020 ? 'day' : minute>=1020 && minute<=1110 ? 'sunset' : 'night';
}
export function tileCenter(column:number,row:number) {return {x:585+(column-row)*85,y:400+(column+row)*36};}
export function pickTile(x:number,y:number):{column:number;row:number}|null {
 for(let row=0;row<5;row++) for(let column=0;column<6;column++) {
  const p=tileCenter(column,row);
  if(Math.abs(x-p.x)/85+Math.abs(y-p.y)/36<=1) return {column,row};
 }
 return null;
}
export function animalPath(id:string,stage:string,sleep:boolean,open:boolean) {
 if(id==='pavao'&&open&&!sleep) return 'classic/animals/pavao/pavao-aberto.png';
 const folder=id==='pombo'?'pomba':id==='burro'?'burrinho':id;
 let stem=folder;
 if(id==='pombo') stem=`pomba-${stage==='BABY'?'bebe':stage==='YOUNG'?'jovem':'adulta'}`;
 if(id==='burro') stem=`burrinho${stage==='ADULT'?'':stage==='BABY'?'-bebe':'-jovem'}`;
 if(id==='porco') stem=`porco-${stage==='BABY'?'bebe':stage==='YOUNG'?'jovem':'adulto'}`;
 if(id==='coelho') stem=stage==='ADULT'?(sleep?'coelho-adulto':'coelho'):stage==='BABY'?'coelho-bebe':'coelho-jovem';
 if(id==='cachorro-mastiff') stem='mastiff';
 if(id==='cabra') stem=sleep?'cabra-adulta':'cabra';
 return `classic/animals/${folder}/${stem}${sleep&&id!=='pombo'&&id!=='abelha'?'-dormindo':''}.png`;
}
// Presentation coordinates only; ownership, stages and eligibility come from the API.
export const animalLayout:Record<string,[number,number,number]>={
 galinha:[1170,425,62],pato:[1350,350,48],porco:[1480,325,125],ovelha:[1375,435,84],vaca:[1515,455,145],
 abelha:[1210,60,156],pombo:[1330,230,110],coelho:[195,100,72],burro:[300,470,140],pavao:[1020,620,80],
 avestruz:[1220,635,90],cabra:[180,570,110],cavalo:[345,45,150],'cachorro-robo':[785,490,110],'cachorro-mastiff':[1430,60,135]
};
