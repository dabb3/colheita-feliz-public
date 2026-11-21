// Isolated real-UI fixture. No accounts, API, player save or economic command.
import {build} from 'esbuild';
import {readFile,mkdir} from 'node:fs/promises';
import {createServer} from 'node:http';
import {resolve,dirname,extname,sep} from 'node:path';
import {fileURLToPath} from 'node:url';

const web=resolve(dirname(fileURLToPath(import.meta.url)),'..'),root=resolve(web,'..');
const output=resolve(root,'.tools/social-visual-review');
await mkdir(output,{recursive:true});
const data=async name=>JSON.parse(await readFile(resolve(root,`assets/data/${name}.json`),'utf8'));
const crops=await data('crops'),animals=await data('animals'),fertilizers=await data('fertilizers');
const catalog={crops,animals,fertilizers,feedPrices:{growth:80,producer:120},vip:{greenPrice:100}};
const farm={owner:'review-alice',name:'Fazenda de revisão',own:true,revision:'1',serverTime:'2026-11-21T12:00:00Z',level:10,currentXp:'100',xpToNext:'200',coins:'500',greenCoins:'5',seedCounts:{},fertilizers:{amor:5},animals:[],
 socialCooldowns:{stealNextAt:null,insectsNextAt:null,weedsNextAt:null},
 tiles:Array.from({length:30},(_,i)=>({column:i%6,row:Math.floor(i/6),unlocked:true,state:i<6?'READY':'GROWING',cropId:'nabo',stage:i<6?5:2,season:1,seasons:1,watered:true,dry:false,paused:false,weeds:i%6===2,insects:i%6===3?2:0,fertilized:false,protected:false,harvestRevision:'1',remainingSeconds:3600,nominalRemainingSeconds:3600}))};
const bootstrap=`
catalog=${JSON.stringify(catalog)};
const reviewFarm=${JSON.stringify(farm)} as Farm;
const reviewBase=Date.parse(reviewFarm.serverTime),reviewParams=new URLSearchParams(location.search);
let reviewElapsed=0,reviewRequests=0;
Object.defineProperty(performance,'now',{value:()=>reviewElapsed});
const mode=reviewParams.get('state');
if(mode==='waiting'){reviewFarm.socialCooldowns!.stealNextAt=new Date(reviewBase+59000).toISOString();reviewFarm.socialCooldowns!.insectsNextAt=new Date(reviewBase+7500000).toISOString();}
if(mode==='boundary')reviewFarm.socialCooldowns!.insectsNextAt=new Date(reviewBase+2000).toISOString();
if(mode==='unknown')delete reviewFarm.socialCooldowns;
api={pending:null,prepareRequestId:async()=>{},get:async(path:string)=>path==='/v1/me/farm'?{...own!,serverTime:new Date(reviewBase+reviewElapsed).toISOString()}:visited,
 mutate:async(_path:string,fields:CommandFields)=>{
  reviewRequests++;
  const key=({STEAL:'stealNextAt',SPREAD_INSECTS:'insectsNextAt',SPREAD_WEEDS:'weedsNextAt'} as const)[fields.action as 'STEAL'];
  if(!key)return {result:{success:false,message:'Esta revisão demonstra apenas os timers sociais.'},farm:own};
  const nextAt=new Date(reviewBase+reviewElapsed+10800000).toISOString();
  return {farm:{...own!,revision:String(BigInt(own!.revision)+1n),serverTime:new Date(reviewBase+reviewElapsed).toISOString(),socialCooldowns:{...own!.socialCooldowns!,[key]:nextAt}},
   result:{success:false,message:'Um animal protetor impediu a tentativa. Nova tentativa em 3 horas.'}};
 }
} as unknown as ApiClient;
updateOwnFarm(reviewFarm);
visited={...reviewFarm,owner:'review-bob',name:'Fazenda do amigo',own:false,socialCooldowns:undefined};
tool='CURSOR';
scene=new Scene(element<HTMLCanvasElement>('farm'),catalog,tile=>void tileAction(tile),()=>{},health);
render();syncMobileCamera();
(window as unknown as {socialReview:unknown}).socialReview={advance(ms:number){reviewElapsed+=ms;},requests:()=>reviewRequests,
 receive(snapshot:Farm){receive({farm:snapshot});},own:()=>own,tool:()=>tool};
`;
const original=await readFile(resolve(web,'src/main.ts'),'utf8');
if(!original.includes('void start();'))throw Error('Missing startup marker: do not run the real application in this fixture');
const source=original.replace('void start();',bootstrap);
await build({stdin:{contents:source,resolveDir:resolve(web,'src'),loader:'ts'},bundle:true,outfile:resolve(output,'app.js'),target:'es2022'});
const mime={'.html':'text/html','.js':'text/javascript','.css':'text/css','.png':'image/png','.jpg':'image/jpeg','.mp3':'audio/mpeg','.wav':'audio/wav'};
createServer(async(req,res)=>{
 try{
  if(req.method!=='GET')throw Error('Read-only fixture');
  const path=decodeURIComponent(new URL(req.url,'http://localhost').pathname);
  if(path==='/config.js'){res.writeHead(200,{'Content-Type':'text/javascript'});res.end('window.COLHEITA_CONFIG={development:true,api:"",supabaseUrl:"",supabaseKey:""};');return;}
  let paths;
  if(path==='/')paths=[resolve(web,'index.html')];
  else if(path==='/app.js')paths=[resolve(output,'app.js')];
  else if(path==='/style.css')paths=[resolve(web,'style.css')];
  else if(path.startsWith('/assets/')){
   paths=[resolve(web,'public','.'+path),resolve(root,'.'+path)];
   if(!paths[0].startsWith(resolve(web,'public/assets')+sep)||!paths[1].startsWith(resolve(root,'assets')+sep))throw Error('Invalid asset path');
  }else throw Error('Unknown path');
  let bytes;for(const file of paths){try{bytes=await readFile(file);break;}catch{}}
  if(!bytes)throw Error('Missing file');
  res.writeHead(200,{'Content-Type':mime[extname(paths[0])]??'application/octet-stream','Cache-Control':'no-store'});res.end(bytes);
 }catch{res.writeHead(404);res.end('Not found');}
}).listen(5177,'127.0.0.1',()=>console.log('Social toolbar review: http://127.0.0.1:5177/?state=waiting — no API or save'));
