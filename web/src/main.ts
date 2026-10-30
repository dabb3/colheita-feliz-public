import {createClient} from '@supabase/supabase-js';
import type {Farm,Catalog,Tile,Reply,CommandFields} from './types.ts';
import {ApiClient,ApiError,acceptFarm} from './api.ts';
import {Scene} from './scene.ts';
import {animalPath} from './presentation.ts';

const config=window.COLHEITA_CONFIG;
const element=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id) as T;
const modal=element<HTMLDialogElement>('modal'),content=element('modal-content');
const select=element<HTMLSelectElement>('item');
let api:ApiClient|undefined,catalog:Catalog,own:Farm|undefined,visited:Farm|undefined,scene:Scene|undefined;
let tool='PLOW',selectedSeed='nabo',selectedFertilizer='comum',sound=false,busy=false;
let toastTimer:number,sessionGeneration=0,navigationGeneration=0;
const login=element('login');
const inviteFromUrl=new URL(location.href).searchParams.get('invite');
if(inviteFromUrl)sessionStorage.setItem('colheita:invitation',inviteFromUrl);
const supabase=config.development?undefined:createClient(config.supabaseUrl,config.supabaseKey,{auth:{flowType:'pkce',detectSessionInUrl:true}});
const toolDefinitions=[['PLOW','Arar / limpar','pa-ui'],['PLANT','Plantar','saco-ui'],['WATER','Regar','regador-ui'],['HARVEST','Colher','mao-restaurada'],['WEED','Remover ervas','herbicida-ui'],['PESTICIDE','Remover insetos','pesticida-ui'],['FERTILIZE','Adubar','adubo-comum']];
function button(label:string,action:()=>void|Promise<void>,parent:HTMLElement=content){const b=document.createElement('button');b.textContent=label;b.onclick=()=>void action();parent.append(b);return b;}
function paragraph(text:string,parent:HTMLElement=content){const p=document.createElement('p');p.textContent=text;parent.append(p);return p;}
function notify(message:string){element('notice').textContent=message;clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>element('notice').textContent='',7000);}
function dialog(title:string){element('modal-title').textContent=title;content.replaceChildren();if(!modal.open)modal.showModal();}
function showConnection(message?:string){element('connection').hidden=!message;element('connection').textContent=message??'';}
function syncPending(){element('pending').hidden=!api?.pending;element<HTMLButtonElement>('retry').disabled=!!api?.busy;}
function display(){return visited??own;}
function render(){
 if(!own)return;
 element('welcome').hidden=true;element('game').hidden=false;
 const wallet=element('wallet');wallet.replaceChildren();const title=document.createElement('strong');title.textContent=visited?`Visitando ${visited.name}`:own.name;wallet.append(title);
 wallet.append(document.createTextNode(`Nível ${own.level} · ${own.currentXp} / ${own.xpToNext} XP`),document.createElement('br'),document.createTextNode(`🟡 ${own.coins} ouro   🟢 ${own.greenCoins} verdes`));
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
 select.onchange=()=>{if(tool==='FERTILIZE')selectedFertilizer=select.value;else selectedSeed=select.value;};
}
async function refresh(){
 if(!api||busy)return;const client=api,generation=sessionGeneration,navigation=navigationGeneration;
 try{
  const farm=await client.get<Farm>('/v1/me/farm');if(generation!==sessionGeneration)return;own=acceptFarm(own,farm);
  if(visited&&navigation===navigationGeneration){const target=visited.owner;const incoming=await client.get<Farm>(`/v1/farms/${target}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration||visited?.owner!==target)return;visited=acceptFarm(visited,incoming);}
  showConnection();render();
 }catch(error){if(generation===sessionGeneration)handleError(error);}
}
function handleError(error:unknown){
 const message=error instanceof Error?error.message:'Falha de conexão.';
 if(error instanceof ApiError){notify(message);if(error.status===409)void refresh();}
 else{showConnection('Conexão interrompida. Aguarde o servidor e tente novamente.');notify(message);}
 syncPending();
}
function receive(reply:Reply){
 if(reply.farm)own=acceptFarm(own,reply.farm);
 if(reply.visitedFarm&&visited?.owner===reply.visitedFarm.owner)visited=acceptFarm(visited,reply.visitedFarm);
 if(reply.result)notify(reply.result.message);
 render();
}
async function action(fields:CommandFields,path='/v1/me/actions'){
 if(!api||!own||busy)return;
 if(!navigator.onLine){notify('Sem conexão. Reconecte antes de agir.');return;}
 busy=true;let stale=false;const client=api,generation=sessionGeneration;
 try{const reply=await client.mutate(path,{...fields,expectedRevision:path==='/v1/me/actions'?own.revision:visited!.revision});if(generation===sessionGeneration){receive(reply);if(reply.result?.success)playAction(fields.action as string);}}
 catch(error){stale=error instanceof ApiError&&error.status===409;if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();if(stale)void refresh();}}
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
 const panel=element('plant-health');panel.hidden=!tile?.cropId;if(!tile?.cropId)return;
 const crop=catalog.crops.find(c=>c.id===tile.cropId);
 const hours=Math.ceil(tile.remainingSeconds/3600);
 panel.textContent=`${crop?.name??tile.cropId} · temporada ${tile.season}/${tile.seasons} · ${tile.state==='DEAD'?'Morta':tile.state==='READY'?'Pronta para colher':`${Math.ceil(tile.nominalRemainingSeconds/3600)} h com rega contínua${tile.dry?'':`; ${hours} h sem nova rega`}`}${tile.dry?' · precisa de água':''}${tile.paused?' · crescimento pausado':''}${tile.protected?' · protegida':''}`;
}
function tileList(){dialog('Terras da fazenda');const states:Record<string,string>={GRASS:'grama',PLOWED:'arada',DEAD:'planta morta',READY:'pronta',GROWING:'crescendo'};const grid=document.createElement('div');grid.className='tile-list';content.append(grid);for(const tile of display()?.tiles??[]){const crop=catalog.crops.find(c=>c.id===tile.cropId);const b=button(`Terra ${tile.column+1}, ${tile.row+1}: ${!tile.unlocked?'bloqueada':crop?.name??states[tile.state]}`,async()=>{modal.close();await tileAction(tile);},grid);b.title='Aplicar a ferramenta selecionada';}}
function card(name:string,image?:string){const c=document.createElement('article');c.className='card';if(image){const img=document.createElement('img');img.loading='lazy';img.src='/assets/'+image;img.alt='';c.append(img);}const title=document.createElement('strong');title.textContent=name;c.append(title);return c;}
function shop(category='crops'){
 dialog('Loja');const tabs=document.createElement('div');tabs.className='tabs';content.append(tabs);
 for(const [id,label]of [['crops','Sementes'],['animals','Animais'],['fertilizers','Fertilizantes'],['owned','Meus animais'],['vip','VIP']])button(label,()=>shop(id),tabs);
 const grid=document.createElement('div');grid.className='cards';content.append(grid);
 if(category==='crops')for(const crop of catalog.crops){const c=card(crop.name,`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`);grid.append(c);paragraph(`${crop.growthSeconds/3600} h · ${crop.seasons} temporada(s) · nível ${crop.requiredLevel}${crop.vipOnly?' · VIP':''}`,c);prices(c,crop.seedPrices,'BUY_SEED',crop.id,crop.requiredLevel,crop.vipOnly);}
 if(category==='animals')for(const animal of catalog.animals){const c=card(animal.name,animal.id==='abelha'?'classic/animals/abelha/abelha.png':animalPath(animal.id,'ADULT',false,false));grid.append(c);paragraph(`Nível ${animal.requiredLevel} · ${animal.productName}`,c);prices(c,animal.prices,'BUY_ANIMAL',animal.id,animal.requiredLevel);}
 if(category==='fertilizers')for(const fertilizer of catalog.fertilizers){const c=card(fertilizer.name,`classic/icons/adubo-${fertilizer.id}.png`);grid.append(c);paragraph(`Nível ${fertilizer.requiredLevel} · reduz ${fertilizer.reductionSeconds/60} min${fertilizer.friendsOnly?' · em amigos':''}`,c);prices(c,fertilizer.prices,'BUY_FERTILIZER',fertilizer.id,fertilizer.requiredLevel);}
 if(category==='vip'){paragraph('VIP por 30 dias. Libera as culturas VIP conforme seu nível.',grid);button(`Ativar VIP · ${catalog.vip.greenPrice} verdes`,()=>action({action:'BUY_VIP'}),grid);}
 if(category==='owned'){
  if(!own?.animals.length)paragraph('Nenhum animal comprado.',grid);
  for(const animal of own?.animals??[]){const type=catalog.animals.find(a=>a.id===animal.id)!;const c=card(type.name,animalPath(animal.id,animal.stage,false,false));grid.append(c);const stage:Record<string,string>={BABY:'Filhote',YOUNG:'Jovem',ADULT:'Adulto'},status:Record<string,string>={HUNGRY:'Com fome',PRODUCING:type.kind==='PROTECTOR'?'Protegendo':'Em desenvolvimento',READY:'Pronto'};paragraph(`${stage[animal.stage]} · ${status[animal.status]} · ${Math.ceil(animal.remainingSeconds/3600)} h`,c);
   button('Cuidar / recolher',()=>action({action:'TEND_ANIMAL',itemId:animal.id}),c);
   const feedId=type.kind==='PRODUCER'?'producer':'growth';button(`Ração · ${catalog.feedPrices[feedId]} ouro`,()=>action({action:'BUY_FEED',itemId:feedId}),c);
   if(type.kind==='GROWING')button('Vender adulto',()=>action({action:'SELL_ANIMAL',itemId:animal.id}),c);
  }
 }
}
function prices(parent:HTMLElement,prices:Record<string,number>,kind:string,id:string,level:number,vip=false){
 for(const [currency,price]of Object.entries(prices)){const b=button(`${price} ${currency==='GOLD'?'ouro':'verdes'}`,()=>action({action:kind,itemId:id,currency}),parent);b.disabled=(own?.level??0)<level||(vip&&!own?.vipActive);}
}
function deposit(){
 dialog('Depósito');button('Vender tudo',()=>action({action:'SELL_ALL'}));paragraph('Produtos VIP bloqueados são ignorados. Cada item é vendido na moeda de aquisição.');
 const groups=new Map<string,number>();for(const lot of own?.produceLots??[])groups.set(lot.itemId,(groups.get(lot.itemId)??0)+lot.quantity);
 for(const [id,count]of groups){const name=catalog.crops.find(c=>c.id===id)?.name??catalog.animals.find(a=>a.productId===id)?.productName??id;const row=document.createElement('div');row.className='row';paragraph(`${name}: ${count}`,row);button('Vender',()=>action({action:'SELL_ITEM',itemId:id}),row);content.append(row);}
 if(!groups.size)paragraph('Nenhum produto armazenado.');
 paragraph(`Ração: ${Object.entries(own?.feed??{}).map(([id,n])=>`${id==='producer'?'produção':'crescimento'}: ${n}`).join(', ')||'nenhuma'}`);
}
function missions(){dialog('Tarefas diárias');for(const mission of own?.missions??[])paragraph(`${mission.done?'✓':'○'} ${mission.title} · ${mission.green} moeda verde`);paragraph('As recompensas são creditadas pelo servidor ao concluir as tarefas.');paragraph(own?.vipActive?`VIP ativo até ${own.vipExpiresAt}`:'VIP inativo.');}
function invitationForm(parent:HTMLElement){
 const input=document.createElement('input');input.placeholder='Cole o código de convite';input.setAttribute('aria-label','Código de convite');input.value=sessionStorage.getItem('colheita:invitation')??'';parent.append(input);
 button('Aceitar convite',async()=>{
  if(!api){notify('Entre antes de aceitar o convite.');return;}
  const client=api,generation=sessionGeneration;
  try{await client.mutate('/v1/friend-invitations/accept',{code:input.value.trim()});if(generation!==sessionGeneration)return;await refresh();if(own)modal.close();}
  catch(error){handleError(error);}finally{syncPending();}
 },parent);
}
async function friends(){
 if(!api)return;const client=api,generation=sessionGeneration;dialog('Amigos');button('Criar convite',async()=>{
  try{const reply=await client.mutate('/v1/friend-invitations',{});if(generation!==sessionGeneration)return;const code=paragraph(reply.code??'');code.id='invite-code';const url=new URL(location.origin);url.searchParams.set('invite',reply.code??'');const link=document.createElement('a');link.href=url.href;link.textContent='Link de convite';content.append(link);paragraph('Válido por sete dias, para um aceite.');}
  catch(error){handleError(error);}finally{syncPending();}
 });invitationForm(content);
 try{const list=await client.get<{id:string;name:string}[]>('/v1/friends');if(generation!==sessionGeneration)return;for(const friend of list)button(`Visitar ${friend.name}`,async()=>{const navigation=++navigationGeneration;try{const farm=await client.get<Farm>(`/v1/farms/${friend.id}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration)return;visited=farm;tool='STEAL';render();modal.close();}catch(error){handleError(error);}});if(!list.length)paragraph('Nenhum amigo ainda. Crie um convite e compartilhe o link.');}
 catch(error){handleError(error);}
}
async function connect(user:string,token:()=>Promise<string>){
 sessionGeneration++;navigationGeneration++;busy=false;own=undefined;visited=undefined;scene?.close();const generation=sessionGeneration;
 api=new ApiClient(config.api,user,token,sessionStorage);syncPending();
 scene=new Scene(element<HTMLCanvasElement>('farm'),catalog,tile=>void tileAction(tile),()=>shop('owned'),health);
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
element('shop').onclick=()=>shop();element('deposit').onclick=deposit;element('missions').onclick=missions;element('friends').onclick=()=>void friends();
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
