import {createClient} from '@supabase/supabase-js';
import type {Farm,Catalog,Tile,Reply,CommandFields} from './types.ts';
import {ApiClient,ApiError,acceptFarm} from './api.ts';
import {Scene} from './scene.ts';
import {animalPath,tileCenter} from './presentation.ts';
import {renderWallet,renderMarket,focusMarketPage} from './ui.ts';
import {renderDeposit,renderPlantHealth,renderAnimal,renderMissions,renderFriends} from './ui-panels.ts';

const config=window.COLHEITA_CONFIG;
const element=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id) as T;
const modal=element<HTMLDialogElement>('modal'),content=element('modal-content');
const select=element<HTMLSelectElement>('item');
let api:ApiClient|undefined,catalog:Catalog,own:Farm|undefined,visited:Farm|undefined,scene:Scene|undefined;
let tool='PLOW',selectedSeed='nabo',selectedFertilizer='comum',sound=false,busy=false;
let toastTimer:number,sessionGeneration=0,navigationGeneration=0;
let modalGeneration=0,refreshPanel:(()=>void)|undefined;
const login=element('login');
const inviteFromUrl=new URL(location.href).searchParams.get('invite');
if(inviteFromUrl)sessionStorage.setItem('colheita:invitation',inviteFromUrl);
const supabase=config.development?undefined:createClient(config.supabaseUrl,config.supabaseKey,{auth:{flowType:'pkce',detectSessionInUrl:true}});
const toolDefinitions=[['PLOW','Arar / limpar','pa-ui'],['PLANT','Plantar','saco-ui'],['WATER','Regar','regador-ui'],['HARVEST','Colher','mao-restaurada'],['WEED','Remover ervas','herbicida-ui'],['PESTICIDE','Remover insetos','pesticida-ui'],['FERTILIZE','Adubar','adubo-comum']];
function button(label:string,action:()=>void|Promise<void>,parent:HTMLElement=content){const b=document.createElement('button');b.textContent=label;b.onclick=()=>void action();parent.append(b);return b;}
function paragraph(text:string,parent:HTMLElement=content){const p=document.createElement('p');p.textContent=text;parent.append(p);return p;}
function notify(message:string){element('notice').textContent=message;clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>element('notice').textContent='',7000);}
function dialog(title:string){modalGeneration++;refreshPanel=undefined;element('plant-health').hidden=true;element('modal-title').textContent=title;content.replaceChildren();if(!modal.open)modal.showModal();}
modal.addEventListener('close',()=>{modalGeneration++;refreshPanel=undefined;});
function updatePanel(){
 if(!modal.open||!refreshPanel)return;
 const focused=document.activeElement,key=focused&&content.contains(focused)?focused.getAttribute('data-focus-key'):null;
 refreshPanel();
 if(key){const replacement=[...content.querySelectorAll<HTMLButtonElement>('[data-focus-key]')].find(node=>node.getAttribute('data-focus-key')===key&&!node.disabled);(replacement??element('close-modal')).focus();}
}
function showConnection(message?:string){element('connection').hidden=!message;element('connection').textContent=message??'';}
function syncPending(){element('pending').hidden=!api?.pending;element<HTMLButtonElement>('retry').disabled=!!api?.busy;}
function display(){return visited??own;}
function render(){
 if(!own)return;
 element('welcome').hidden=true;element('game').hidden=false;
 renderWallet(element('wallet'),own,visited?.name);
 element('home').hidden=!visited;
 const tutorial=element('tutorial');tutorial.hidden=!!visited||!own.tutorial;tutorial.replaceChildren();
 if(own.tutorial){const strong=document.createElement('strong');strong.textContent=own.tutorial.title??'';tutorial.append(strong);paragraph(own.tutorial.hint??'',tutorial);}
 scene?.update(display()!);syncPending();renderTools();
}
function renderTools(){
 const toolbar=element('tools');toolbar.replaceChildren();
 const definitions=visited?[['STEAL','Furtar colheita','mao-restaurada'],['LOVE','Fertilizante do amor','adubo-amor']]:toolDefinitions;
 if(!definitions.some(d=>d[0]===tool))tool=definitions[0][0];
 for(const [id,label,icon] of definitions){const b=button(label,()=>{tool=id;renderTools();},toolbar);b.title=label;b.setAttribute('aria-label',label);b.setAttribute('aria-pressed',String(tool===id));b.textContent='';const image=document.createElement('img');image.alt='';image.src=`/assets/classic/icons/${icon.startsWith('adubo-')?'':'hud/'}${icon}.png`;b.append(image);}
 const prior=select.value;select.replaceChildren();
 const choices=tool==='FERTILIZE'?catalog.fertilizers.filter(f=>!f.friendsOnly).map(f=>({id:f.id,name:`${f.name} (${own?.fertilizers?.[f.id]??0})`})):
  catalog.crops.filter(c=>(own?.seedCounts?.[c.id]??0)>0).map(c=>({id:c.id,name:`${c.name} (${own?.seedCounts?.[c.id]})`}));
 for(const choice of choices){const option=document.createElement('option');option.value=choice.id;option.textContent=choice.name;select.append(option);}
 select.value=choices.some(c=>c.id===prior)?prior:tool==='FERTILIZE'?selectedFertilizer:selectedSeed;
 if(select.selectedIndex<0&&choices.length)select.selectedIndex=0;
 element('item-label').hidden=!['PLANT','FERTILIZE'].includes(tool);
 const fertilizing=tool==='FERTILIZE',choiceId=select.value;
 element('item-title').textContent=fertilizing?'Adubo':'Semente';
 const preview=element<HTMLImageElement>('item-preview');preview.hidden=!choiceId||!['PLANT','FERTILIZE'].includes(tool);
 const crop=catalog.crops.find(c=>c.id===choiceId);
 if(choiceId)preview.src='/assets/'+(fertilizing?`classic/icons/adubo-${choiceId}.png`:`classic/crops/plants_phase_one_to_five/${crop?.spriteStem}_5.png`);
 select.onchange=()=>{if(tool==='FERTILIZE')selectedFertilizer=select.value;else selectedSeed=select.value;renderTools();};
}
async function refresh(){
 if(!api||busy)return;const client=api,generation=sessionGeneration,navigation=navigationGeneration;
 try{
  const farm=await client.get<Farm>('/v1/me/farm');if(generation!==sessionGeneration)return;own=acceptFarm(own,farm);
  if(visited&&navigation===navigationGeneration){const target=visited.owner;const incoming=await client.get<Farm>(`/v1/farms/${target}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration||visited?.owner!==target)return;visited=acceptFarm(visited,incoming);}
  showConnection();render();updatePanel();
 }catch(error){if(generation===sessionGeneration)handleError(error);}
}
function handleError(error:unknown){
 const message=error instanceof Error?error.message:'Falha de conexão.';
 if(error instanceof ApiError){notify(message);if(error.status===409||error.status===410)void refresh();}
 else{showConnection('Conexão interrompida. Aguarde o servidor e tente novamente.');notify(message);}
 syncPending();
}
function receive(reply:Reply){
 if(reply.farm)own=acceptFarm(own,reply.farm);
 if(reply.visitedFarm&&visited?.owner===reply.visitedFarm.owner)visited=acceptFarm(visited,reply.visitedFarm);
 if(reply.result)notify(reply.result.message);
 render();
 updatePanel();
}
async function action(fields:CommandFields,path='/v1/me/actions'){
 if(!api||!own||busy)return;
 if(!navigator.onLine){notify('Sem conexão. Reconecte antes de agir.');return;}
 busy=true;let stale=false;const client=api,generation=sessionGeneration;
 try{const reply=await client.mutate(path,{...fields,expectedRevision:path==='/v1/me/actions'?own.revision:visited!.revision});if(generation===sessionGeneration){receive(reply);if(reply.result?.success)playAction(fields.action as string);}}
 catch(error){stale=error instanceof ApiError&&[409,410].includes(error.status);if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();if(stale)void refresh();}}
}
function playAction(action:string){if(!sound)return;const file=action==='WATER'?'water':action==='PLANT'?'plant':action==='HARVEST'?'harvest':action.startsWith('BUY_')?'buy':action.startsWith('SELL_')?'sell':action==='WEED'?'weed':undefined;if(file){const audio=new Audio(`/assets/sounds/${file}.wav`);audio.volume=.35;void audio.play().catch(()=>{});}}
async function tileAction(tile:Tile){
 if(visited){await action({action:tool,column:tile.column,row:tile.row,harvestRevision:tile.harvestRevision},`/v1/farms/${visited.owner}/visit-actions`);return;}
 if(!tile.unlocked){if(own?.nextPlot?.column===tile.column&&own.nextPlot.row===tile.row&&confirm('Comprar esta terra por 50 moedas verdes?'))await action({action:'BUY_PLOT',column:tile.column,row:tile.row});return;}
 const fields:CommandFields={action:tool,column:tile.column,row:tile.row};
 if(tool==='PLANT'){if(!select.value){notify('Compre uma semente na loja.');return;}fields.itemId=select.value;}
 if(tool==='FERTILIZE')fields.itemId=select.value||selectedFertilizer;
 await action(fields);
}
function health(tile:Tile|null){
 const panel=element('plant-health');renderPlantHealth(panel,catalog,modal.open?null:tile);
 if(tile){const p=tileCenter(tile.column,tile.row);panel.style.setProperty('--health-x',`${Math.max(12,Math.min(88,p.x/16))}%`);panel.style.setProperty('--health-y',`${(p.y-45)/9}%`);}
}
function animalPanel(id:string,ownAnimal=false){
 dialog(catalog.animals.find(a=>a.id===id)?.name??'Animal');
 refreshPanel=()=>{const farm=ownAnimal?own:display();if(farm)renderAnimal(content,catalog,farm,id,ownAnimal||!visited,fields=>void action(fields));};refreshPanel();
}
function tileList(){dialog('Terras da fazenda');const states:Record<string,string>={GRASS:'grama',PLOWED:'arada',DEAD:'planta morta',READY:'pronta',GROWING:'crescendo'};const grid=document.createElement('div');grid.className='tile-list';content.append(grid);for(const tile of display()?.tiles??[]){const crop=catalog.crops.find(c=>c.id===tile.cropId);const b=button(`Terra ${tile.column+1}, ${tile.row+1}: ${!tile.unlocked?'bloqueada':crop?.name??states[tile.state]}`,async()=>{modal.close();await tileAction(tile);},grid);b.title='Aplicar a ferramenta selecionada';}}
function card(name:string,image?:string){const c=document.createElement('article');c.className='card';if(image){const img=document.createElement('img');img.loading='lazy';img.src='/assets/'+image;img.alt='';c.append(img);}const title=document.createElement('strong');title.textContent=name;c.append(title);return c;}
function shop(category='crops',page=0){
 dialog('Loja');const tabs=document.createElement('div');tabs.className='tabs';content.append(tabs);
 for(const [id,label]of [['crops','Sementes'],['animals','Animais'],['fertilizers','Fertilizantes'],['owned','Meus animais'],['vip','VIP']]){const tab=button(label,()=>shop(id),tabs);tab.setAttribute('aria-pressed',String(category===id));}
 const grid=document.createElement('div');grid.className=['crops','animals','fertilizers'].includes(category)?'market-body':'cards';content.append(grid);
 if(['crops','animals','fertilizers'].includes(category))renderMarket(grid,catalog,own,category,page,next=>{shop(category,next);focusMarketPage(content,next>page);},fields=>void action(fields));
 if(category==='vip'){paragraph('VIP por 30 dias. Libera as culturas VIP conforme seu nível.',grid);button(`Ativar VIP · ${catalog.vip.greenPrice} verdes`,()=>action({action:'BUY_VIP'}),grid);}
 if(category==='owned'){
  if(!own?.animals.length)paragraph('Nenhum animal comprado.',grid);
  for(const animal of own?.animals??[]){const type=catalog.animals.find(a=>a.id===animal.id);const c=card(type?.name??animal.id,animalPath(animal.id,animal.stage,false,false));grid.append(c);button('Ver animal',()=>animalPanel(animal.id,true),c);}
 }
}
function deposit(category='products'){
 if(!own)return;dialog('Depósito');
 refreshPanel=()=>{if(own)renderDeposit(content,catalog,own,category,id=>deposit(id),fields=>void action(fields));};refreshPanel();
}
function missions(){dialog('Tarefas diárias');refreshPanel=()=>{if(own)renderMissions(content,own);};refreshPanel();}
function invitationForm(parent:HTMLElement){
 const input=document.createElement('input');input.placeholder='Cole o código de convite';input.setAttribute('aria-label','Código de convite');input.value=sessionStorage.getItem('colheita:invitation')??'';parent.append(input);
 button('Aceitar convite',async()=>{
  if(!api){notify('Entre antes de aceitar o convite.');return;}
  const client=api,generation=sessionGeneration,panelGeneration=modalGeneration;
  try{await client.mutate('/v1/friend-invitations/accept',{code:input.value.trim()});if(generation!==sessionGeneration)return;await refresh();if(own&&panelGeneration===modalGeneration)modal.close();}
  catch(error){handleError(error);}finally{syncPending();}
 },parent);
}
async function friends(){
 if(!api)return;const client=api,generation=sessionGeneration;dialog('Amigos');const panelGeneration=modalGeneration;
 const controls=document.createElement('div');controls.className='friend-controls';content.append(controls);button('Criar convite',async()=>{
  try{const reply=await client.mutate('/v1/friend-invitations',{});if(generation!==sessionGeneration||panelGeneration!==modalGeneration||!modal.open)return;const code=paragraph(reply.code??'',controls);code.id='invite-code';const url=new URL(location.origin);url.searchParams.set('invite',reply.code??'');const link=document.createElement('a');link.href=url.href;link.textContent='Link de convite';controls.append(link);paragraph('Válido por sete dias, para um aceite.',controls);}
  catch(error){handleError(error);}finally{syncPending();}
 },controls);invitationForm(controls);const listRoot=document.createElement('div');content.append(listRoot);paragraph('Carregando amigos…',listRoot);
 try{const list=await client.get<{id:string;name:string}[]>('/v1/friends');if(generation!==sessionGeneration||panelGeneration!==modalGeneration||!modal.open)return;renderFriends(listRoot,list,id=>{void(async()=>{const navigation=++navigationGeneration;try{const farm=await client.get<Farm>(`/v1/farms/${id}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration||panelGeneration!==modalGeneration||!modal.open)return;visited=farm;tool='STEAL';render();modal.close();}catch(error){handleError(error);}})();});}
 catch(error){handleError(error);}
}
async function connect(user:string,token:()=>Promise<string>){
 sessionGeneration++;navigationGeneration++;busy=false;own=undefined;visited=undefined;scene?.close();const generation=sessionGeneration;
 api=new ApiClient(config.api,user,token,sessionStorage);syncPending();
 scene=new Scene(element<HTMLCanvasElement>('farm'),catalog,tile=>void tileAction(tile),animalPanel,health);
 try{await refresh();if(generation===sessionGeneration&&!own){paragraph('Esta conta precisa de um convite para começar.',login);invitationForm(login);}}
 catch(error){handleError(error);}
}
async function start(){
 login.replaceChildren();
 if(supabase)button('Entrar com Google',async()=>{const {error}=await supabase.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin}});if(error)notify(error.message);},login);
 showConnection('Conectando à fazenda… O primeiro acesso pode levar cerca de um minuto.');
 try{
  const response=await fetch(config.api+'/health',{signal:AbortSignal.timeout(65_000),cache:'no-store'});if(!response.ok)throw new Error('Servidor indisponível.');const health=await response.json();
  catalog=await (await fetch(config.api+'/v1/catalog',{signal:AbortSignal.timeout(65_000)})).json();
  showConnection();
  if(config.development){if(!health.development)throw new Error('Contas locais exigem servidor de desenvolvimento.');for(const name of ['Alice','Bob'])button(`Testar como ${name}`,async()=>{sessionStorage.setItem('colheita:local-user',name);await connect(name.toLowerCase(),async()=>`dev-${name.toLowerCase()}`);},login);const previous=sessionStorage.getItem('colheita:local-user');if(previous==='Alice'||previous==='Bob')await connect(previous.toLowerCase(),async()=>`dev-${previous.toLowerCase()}`);}
  else{
   const {data,error}=await supabase!.auth.getSession();if(error)throw error;
   if(data.session){const userId=data.session.user.id;await connect(userId,async()=>{const {data,error}=await supabase!.auth.getSession();if(error||!data.session||data.session.user.id!==userId)throw new ApiError(401,'Entre novamente com a mesma conta para continuar.');return data.session.access_token;});}
  }
 }catch(error){showConnection();notify(error instanceof Error?error.message:'Servidor indisponível.');button('Tentar conectar novamente',start,login);}
}
element('shop').onclick=()=>shop();element('deposit').onclick=()=>deposit();element('missions').onclick=missions;element('friends').onclick=()=>void friends();
element('home').onclick=()=>{navigationGeneration++;visited=undefined;tool='PLOW';render();};element('close-modal').onclick=()=>modal.close();
element('accessible-tiles').onclick=tileList;
element('credits').onclick=()=>{dialog('Créditos');paragraph('Reimplementação independente e não oficial de Colheita Feliz. Acervo visual derivado da referência indicada no repositório, com uso não comercial.');const link=document.createElement('a');link.href='/assets/REFERENCE_LICENSE';link.textContent='Licença e atribuição dos assets';content.append(link);};
element('sound').onclick=()=>{sound=!sound;element('sound').textContent=sound?'Som: ligado':'Som: desligado';element('sound').setAttribute('aria-pressed',String(sound));};
element('retry').onclick=async()=>{if(!api||busy)return;const client=api,generation=sessionGeneration;busy=true;try{const reply=await client.retry();if(generation!==sessionGeneration)return;receive(reply);if(reply.code){dialog('Convite confirmado');paragraph(reply.code);}}catch(error){if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();await refresh();}}};
element('logout').onclick=async()=>{sessionGeneration++;navigationGeneration++;busy=false;api=undefined;own=undefined;visited=undefined;scene?.close();sessionStorage.removeItem('colheita:local-user');element('game').hidden=true;element('welcome').hidden=false;syncPending();if(supabase)await supabase.auth.signOut();};
window.addEventListener('offline',()=>showConnection('Sem conexão. Reconecte antes de agir.'));
window.addEventListener('online',()=>void refresh());
document.addEventListener('keydown',event=>{if(!own||modal.open||event.target instanceof HTMLInputElement||event.target instanceof HTMLSelectElement)return;const keys:Record<string,string>={'2':'PLOW','3':'PLANT','4':'WATER','5':'WEED','6':'PESTICIDE','7':'HARVEST','8':'FERTILIZE'};if(!visited&&keys[event.key]){tool=keys[event.key];renderTools();}else if(event.key.toLowerCase()==='b')shop();else if(event.key.toLowerCase()==='v')deposit();});
document.addEventListener('visibilitychange',()=>{if(!document.hidden)void refresh();});
window.setInterval(()=>{if(!document.hidden)void refresh();},30_000);
void start();
