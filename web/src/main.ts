import {createClient} from '@supabase/supabase-js';
import type {Farm,Catalog,Tile,Reply,CommandFields} from './types.ts';
import {ApiClient,ApiError,acceptFarm} from './api.ts';
import {Scene} from './scene.ts';
import {animalPath,animalStatusText,animalActionLabel,animalClickCommand,gameShortcut,shortcutPanelState,toolbarTrayToggle,tileCommand,cropTooltipModel,untilUtcMidnight,cropGroups,soundForAction,actionRewardModel} from './presentation.ts';

const config=window.COLHEITA_CONFIG;
const element=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id) as T;
const modal=element<HTMLDialogElement>('modal'),content=element('modal-content');
let api:ApiClient|undefined,catalog:Catalog,own:Farm|undefined,visited:Farm|undefined,scene:Scene|undefined;
let tool='CURSOR',selectedSeed='nabo',selectedFertilizer='comum',trayOpen=false,effectsEnabled=false,musicEnabled=false,music:HTMLAudioElement|undefined,busy=false,seedMarketGroup:'common'|'vip'='common';
let toastTimer:number,sessionGeneration=0,navigationGeneration=0;
const login=element('login');
const inviteFromUrl=new URL(location.href).searchParams.get('invite');
if(inviteFromUrl)sessionStorage.setItem('colheita:invitation',inviteFromUrl);
const supabase=config.development?undefined:createClient(config.supabaseUrl,config.supabaseKey,{auth:{flowType:'pkce',detectSessionInUrl:true}});
const toolDefinitions=[['CURSOR','Selecionar','cursor-ui'],['PLOW','Arar / limpar','pa-ui'],['PLANT','Plantar','saco-ui'],['WATER','Regar','regador-ui'],['WEED','Remover ervas','herbicida-ui'],['PESTICIDE','Remover insetos','pesticida-ui'],['HARVEST','Colher','mao-restaurada'],['FERTILIZE','Adubar','adubo-comum']];
function button(label:string,action:()=>void|Promise<void>,parent:HTMLElement=content){const b=document.createElement('button');b.textContent=label;b.onclick=()=>void action();parent.append(b);return b;}
function paragraph(text:string,parent:HTMLElement=content){const p=document.createElement('p');p.textContent=text;parent.append(p);return p;}
function formatToolDuration(seconds:number){const minutes=Math.max(1,Math.ceil(seconds/60));return minutes>=60?`${Math.floor(minutes/60)} h${String(minutes%60).padStart(2,'0')}`:`${minutes} min`;}
function notify(message:string){const notice=element('notice');notice.classList.remove('level-notice','reward-notice');notice.replaceChildren(document.createTextNode(message));clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>notice.replaceChildren(),7000);}
function toggleEffects(){effectsEnabled=!effectsEnabled;const control=document.getElementById('sound');if(control){control.setAttribute('aria-pressed',String(effectsEnabled));const state=control.querySelector('.settings-state');if(state)state.textContent=effectsEnabled?'Ligado':'Desligado';}}
function toggleMusic(){musicEnabled=!musicEnabled;const control=document.getElementById('music');if(control){control.setAttribute('aria-pressed',String(musicEnabled));const state=control.querySelector('.settings-state');if(state)state.textContent=musicEnabled?'Ligado':'Desligado';}if(musicEnabled){music??=new Audio('/assets/sounds/farm_loop.mp3');music.loop=true;music.volume=.25;void music.play().catch(()=>{});}else music?.pause();}
function shortcutPanel(title:string,open:()=>void){if(shortcutPanelState(modal.open,element('modal-title').textContent,title)==='close')modal.close();else open();}
function actionIcon(action:string,itemId?:string){
 const icon:Record<string,string>={PLOW:'hud/pa-ui',PLANT:'hud/saco-ui',WATER:'hud/regador-ui',WEED:'hud/herbicida-ui',PESTICIDE:'hud/pesticida-ui',HARVEST:'hud/mao-restaurada',STEAL:'hud/mao-restaurada',FERTILIZE:'adubo-comum',LOVE:'adubo-amor'};
 if(action==='TEND_ANIMAL'&&itemId)return animalPath(itemId,'ADULT',false,false,false);
 if(action.startsWith('BUY_')||action.startsWith('SELL_'))return `classic/icons/${action.startsWith('BUY_')?'moeda-comum':'moeda-verde'}.png`;
 const path=icon[action];return path?`classic/icons/${path}.png`:undefined;
}
function notifyAction(message:string,action:string,itemId:string|undefined,reward:ReturnType<typeof actionRewardModel>){
 const notice=element('notice');notice.replaceChildren();const icon=actionIcon(action,itemId);if(icon){const image=document.createElement('img');image.src=`/assets/${icon}`;image.alt='';notice.append(image);}
 const text=document.createElement('span');text.textContent=message;notice.append(text);const gains=document.createElement('div');gains.className='notice-rewards';
 for(const [amount,label,asset]of [[reward.xp,'XP','classic/icons/xp.png'],[reward.coins,'ouro','classic/icons/moeda-comum.png'],[reward.greens,'verdes','classic/icons/moeda-verde.png']] as [number,string,string][]){if(amount<=0)continue;const item=document.createElement('span');const image=document.createElement('img');image.src=`/assets/${asset}`;image.alt='';item.append(image,document.createTextNode(`+${amount} ${label}`));gains.append(item);}
 if(gains.childElementCount)notice.append(gains);notice.classList.remove('level-notice');notice.classList.add('reward-notice');
 if(reward.leveledUp){notice.replaceChildren();const title=document.createElement('strong');title.textContent=`Nível ${reward.level}!`;const unlocked=catalog.crops.filter(crop=>crop.requiredLevel===reward.level).map(crop=>crop.name);const sub=document.createElement('span');sub.textContent=unlocked.length?`${unlocked.join(', ')} liberado(s) na Loja`:'Continue colhendo para liberar mais culturas';notice.append(title,sub);notice.classList.add('level-notice');}
 clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>{notice.replaceChildren();notice.classList.remove('level-notice','reward-notice');},reward.leveledUp?2600:3200);
}
function dialog(title:string){element('modal-title').textContent=title;modal.classList.toggle('shop-dialog',title==='Loja');modal.classList.toggle('deposit-dialog',title==='Depósito');modal.classList.toggle('settings-dialog',title==='Ajustes');content.replaceChildren();content.scrollTop=0;if(!modal.open)modal.showModal();}
function settings(){
 dialog('Ajustes');
 paragraph('Som, ferramentas e conta da fazenda.',content).className='settings-intro';
 const sections=document.createElement('div');sections.className='settings-sections';content.append(sections);
 const audio=document.createElement('section');audio.className='settings-section settings-audio';sections.append(audio);
 const audioTitle=document.createElement('h3');audioTitle.textContent='Áudio';audio.append(audioTitle);
 const toggles=document.createElement('div');toggles.className='settings-toggles';audio.append(toggles);
 const addToggle=(id:string,label:string,description:string,enabled:boolean,action:()=>void)=>{
  const control=button('',action,toggles);control.id=id;control.className='settings-toggle';control.setAttribute('aria-label',label);control.setAttribute('aria-pressed',String(enabled));
  const text=document.createElement('span');text.className='settings-toggle-copy';const title=document.createElement('strong');title.textContent=label;const detail=document.createElement('small');detail.textContent=description;text.append(title,detail);
  const state=document.createElement('span');state.className='settings-state';state.textContent=enabled?'Ligado':'Desligado';control.append(text,state);
 };
 addToggle('sound','Efeitos sonoros','Sons das ações na fazenda',effectsEnabled,toggleEffects);
 addToggle('music','Música ambiente','Trilha sonora durante o jogo',musicEnabled,toggleMusic);
 const game=document.createElement('section');game.className='settings-section';sections.append(game);
 const gameTitle=document.createElement('h3');gameTitle.textContent='Jogo';game.append(gameTitle);
 const plots=button('',tileList,game);plots.className='settings-action';const plotCopy=document.createElement('span');plotCopy.className='settings-toggle-copy';const plotName=document.createElement('strong');plotName.textContent='Lista de terras';const plotDescription=document.createElement('small');plotDescription.textContent='Ver e selecionar cada terreno';plotCopy.append(plotName,plotDescription);const plotArrow=document.createElement('span');plotArrow.className='settings-chevron';plotArrow.textContent='›';plots.append(plotCopy,plotArrow);
 const info=document.createElement('section');info.className='settings-section';sections.append(info);
 const infoTitle=document.createElement('h3');infoTitle.textContent='Informações e conta';info.append(infoTitle);
 const credits=button('',()=>{dialog('Créditos');paragraph('Reimplementação independente e não oficial de Colheita Feliz. Acervo visual derivado da referência indicada no repositório, com uso não comercial.');const link=document.createElement('a');link.href='/assets/REFERENCE_LICENSE';link.textContent='Licença e atribuição dos assets';content.append(link);},info);credits.className='settings-action';const creditsCopy=document.createElement('span');creditsCopy.className='settings-toggle-copy';const creditsName=document.createElement('strong');creditsName.textContent='Créditos';const creditsDescription=document.createElement('small');creditsDescription.textContent='Sobre o projeto e os assets';creditsCopy.append(creditsName,creditsDescription);const creditsArrow=document.createElement('span');creditsArrow.className='settings-chevron';creditsArrow.textContent='›';credits.append(creditsCopy,creditsArrow);
 const logoutButton=button('Sair da conta',logout,info);logoutButton.className='settings-logout';
}
async function logout(){sessionGeneration++;navigationGeneration++;busy=false;api=undefined;own=undefined;visited=undefined;scene?.close();sessionStorage.removeItem('colheita:local-user');modal.close();element('game').hidden=true;element('welcome').hidden=false;syncPending();if(supabase)await supabase.auth.signOut();}
function showConnection(message?:string){element('connection').hidden=!message;element('connection').textContent=message??'';}
function syncPending(){element('pending').hidden=!api?.pending;element<HTMLButtonElement>('retry').disabled=!!api?.busy;}
function display(){return visited??own;}
function render(){
 if(!own)return;
 element('welcome').hidden=true;element('game').hidden=false;
 const wallet=element('wallet');wallet.replaceChildren();const avatar=document.createElement('img');avatar.className='profile-avatar';avatar.src='/assets/classic/icons/avatar_masculino.png';avatar.alt='';const details=document.createElement('div');details.className='profile-details';const title=document.createElement('strong');title.textContent=visited?`Visitando ${visited.name}`:own.name;details.append(title,document.createTextNode(`Nível ${own.level}`));
 const xp=document.createElement('div');xp.className='profile-xp';const progress=document.createElement('progress');progress.max=Math.max(1,Number(own.xpToNext)||0);progress.value=Math.max(0,Math.min(Number(own.currentXp)||0,progress.max));progress.setAttribute('aria-label',`Experiência: ${own.currentXp} de ${own.xpToNext} XP`);const label=document.createElement('span');label.textContent=`${own.currentXp} / ${own.xpToNext} XP`;xp.append(progress,label);details.append(xp,document.createTextNode(`🟡 ${own.coins} ouro   🟢 ${own.greenCoins} verdes`));wallet.append(avatar,details);
 element('home').hidden=!visited;
 const tutorial=element('tutorial');tutorial.hidden=!!visited||!own.tutorial;tutorial.replaceChildren();
 if(own.tutorial){const strong=document.createElement('strong');strong.textContent=own.tutorial.title??'';tutorial.append(strong);paragraph(own.tutorial.hint??'',tutorial);}
 scene?.update(display()!);syncPending();renderTools();
}
function renderTools(){
 const toolbar=element('tools');toolbar.replaceChildren();
 const definitions=visited?[['STEAL','Furtar colheita','mao-restaurada'],['LOVE','Fertilizante do amor','adubo-amor']]:toolDefinitions;
 if(!definitions.some(d=>d[0]===tool)){tool=definitions[0][0];trayOpen=false;}
 for(const [index,[id,label,icon]] of definitions.entries()){
  const b=button(label,()=>pickTool(id),toolbar);b.classList.add('tool-button');b.title=label;b.setAttribute('aria-label',label);b.setAttribute('aria-pressed',String(tool===id));b.textContent='';
  const selectedIcon=id==='FERTILIZE'?`adubo-${selectedFertilizer}`:icon,image=document.createElement('img');image.alt='';image.src=`/assets/classic/icons/${selectedIcon.startsWith('adubo-')?'':'hud/'}${selectedIcon}.png`;b.append(image);
  if(id==='PLANT'){
   const crop=catalog.crops.find(item=>item.id===selectedSeed),seedCount=own?.seedCounts?.[selectedSeed]??0;
   if(crop){const seed=document.createElement('img');seed.className='tool-selected-item';seed.alt='';seed.src=`/assets/classic/crops/seeds/semente_${crop.spriteStem}.png`;b.append(seed);const count=document.createElement('span');count.className='tool-count';count.textContent=`x${seedCount}`;b.append(count);}
  }
  if(!visited){const key=document.createElement('span');key.className='tool-key';key.textContent=String(index+1);b.append(key);}
 }
 renderToolTray();
}
function pickTool(id:string){const previous=tool;trayOpen=toolbarTrayToggle(previous,id,trayOpen);tool=id;if(id==='LOVE'&&!catalog.fertilizers.some(f=>f.id===selectedFertilizer&&f.friendsOnly))selectedFertilizer='amor';renderTools();}
function renderToolTray(){
 const tray=element('tool-tray');tray.replaceChildren();const seeds=tool==='PLANT',fertilizers=tool==='FERTILIZE'||tool==='LOVE';
 tray.hidden=!trayOpen||(!seeds&&!fertilizers);if(tray.hidden)return;
 const title=document.createElement('strong');title.className='tray-title';title.textContent=seeds?'Sementes':tool==='LOVE'?'Fertilizante do amor':'Fertilizantes';tray.append(title);
 const grid=document.createElement('div');grid.className='tray-items';tray.append(grid);
 if(seeds){
  for(const crop of catalog.crops){const unlocked=(own?.level??0)>=crop.requiredLevel,count=own?.seedCounts?.[crop.id]??0,item=button('',()=>{
   if(!unlocked){notify(`${crop.name} libera no nível ${crop.requiredLevel}.`);return;}
   selectedSeed=crop.id;trayOpen=false;renderTools();notify(`Semente selecionada: ${crop.name}.`);
  },grid);item.className='tray-item';item.setAttribute('aria-pressed',String(selectedSeed===crop.id));item.setAttribute('aria-label',`${crop.name}, ${unlocked?`${count} sementes`:`libera no nível ${crop.requiredLevel}`}`);if(!unlocked)item.classList.add('locked');
   const image=document.createElement('img');image.alt='';image.src=`/assets/classic/crops/seeds/semente_${crop.spriteStem}.png`;item.append(image);const badge=document.createElement('span');badge.textContent=unlocked?`x${count}`:`Nv ${crop.requiredLevel}`;item.append(badge);
   item.onmouseenter=()=>{const caption=document.getElementById('tool-tray-caption');if(caption)caption.textContent=unlocked?`${crop.name} · ${count} sementes`:`${crop.name} · libera no nível ${crop.requiredLevel}`;};
   item.onmouseleave=()=>{const caption=document.getElementById('tool-tray-caption');if(caption)caption.textContent=selected?`${selected} · ${detail}`:'';};
  }
 }else{
  for(const fertilizer of catalog.fertilizers.filter(item=>tool==='LOVE'?item.friendsOnly:true)){
   const count=own?.fertilizers?.[fertilizer.id]??0,item=button('',()=>{selectedFertilizer=fertilizer.id;trayOpen=false;renderTools();notify(`Fertilizante selecionado: ${fertilizer.name}.`);},grid);
   item.className='tray-item';item.setAttribute('aria-pressed',String(selectedFertilizer===fertilizer.id));item.setAttribute('aria-label',`${fertilizer.name}, ${count} no estoque`);
   const image=document.createElement('img');image.alt='';image.src=`/assets/classic/icons/adubo-${fertilizer.id}.png`;item.append(image);const badge=document.createElement('span');badge.textContent=`x${count}`;item.append(badge);
   item.onmouseenter=()=>{const caption=document.getElementById('tool-tray-caption');if(caption)caption.textContent=`${fertilizer.name} · reduz ${formatToolDuration(fertilizer.reductionSeconds)}${fertilizer.friendsOnly?' · plantação de amigos':` · ${count} no estoque`}`;};
   item.onmouseleave=()=>{const caption=document.getElementById('tool-tray-caption');if(caption)caption.textContent=selected?`${selected} · ${detail}`:'';};
  }
 }
 const selected=seeds?catalog.crops.find(item=>item.id===selectedSeed)?.name:catalog.fertilizers.find(item=>item.id===selectedFertilizer)?.name;
 const count=seeds?own?.seedCounts?.[selectedSeed]??0:own?.fertilizers?.[selectedFertilizer]??0,selectedFertilizerType=catalog.fertilizers.find(item=>item.id===selectedFertilizer);
 const detail=seeds?`${count} sementes`:selectedFertilizerType?.friendsOnly?'plantação de amigos':`reduz ${formatToolDuration(selectedFertilizerType?.reductionSeconds??0)} · ${count} no estoque`;
 const caption=document.createElement('span');caption.id='tool-tray-caption';caption.className='tray-caption';caption.textContent=selected?`${selected} · ${detail}`:'';tray.append(caption);
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
 if(error instanceof ApiError){notify(message);if(error.status===409||error.status===410)void refresh();}
 else{showConnection('Conexão interrompida. Aguarde o servidor e tente novamente.');notify(message);}
 syncPending();
}
function receive(reply:Reply,announce=true){
 if(reply.farm)own=acceptFarm(own,reply.farm);
 if(reply.visitedFarm&&visited?.owner===reply.visitedFarm.owner)visited=acceptFarm(visited,reply.visitedFarm);
 if(reply.result&&announce)notify(reply.result.message);
 render();
}
async function action(fields:CommandFields,path='/v1/me/actions'){
 if(!api||!own||busy)return;
 if(!navigator.onLine){notify('Sem conexão. Reconecte antes de agir.');return;}
 busy=true;let stale=false;const client=api,generation=sessionGeneration,previousFarm=own,animalReady=fields.action==='TEND_ANIMAL'&&own.animals.some(animal=>animal.id===fields.itemId&&animal.status==='READY');
 try{const reply=await client.mutate(path,{...fields,expectedRevision:path==='/v1/me/actions'?own.revision:visited!.revision});if(generation===sessionGeneration){receive(reply,false);if(reply.result?.success&&own){const reward=actionRewardModel(previousFarm,own);notifyAction(reply.result.message,String(fields.action),typeof fields.itemId==='string'?fields.itemId:undefined,reward);const cue=reward.leveledUp?'levelup':soundForAction(String(fields.action),animalReady);if(cue)playEffect(cue);}else if(reply.result)notify(reply.result.message);}}
 catch(error){stale=error instanceof ApiError&&[409,410].includes(error.status);if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();if(stale)void refresh();}}
}
function playEffect(file:string){if(!effectsEnabled)return;const audio=new Audio(`/assets/sounds/${file}.wav`);audio.volume=.65;void audio.play().catch(()=>{});}
async function tileAction(tile:Tile){
 if(visited){await action(tileCommand(tool,tile,selectedSeed,selectedFertilizer,true),`/v1/farms/${visited.owner}/visit-actions`);return;}
 if(!tile.unlocked){await action({action:'BUY_PLOT',column:tile.column,row:tile.row});return;}
 if(tool==='CURSOR')return;
 await action(tileCommand(tool,tile,selectedSeed,selectedFertilizer));
}
let healthKey='';
function health(tile:Tile|null,point:{x:number;y:number}|null){
 const panel=element('plant-health'),game=element('game');
 panel.hidden=!tile?.cropId||!point;
 if(!tile?.cropId||!point){healthKey='';return;}
 const crop=catalog.crops.find(c=>c.id===tile.cropId),model=cropTooltipModel(tile,crop?.name??tile.cropId,!!crop?.vipOnly,!!display()?.vipActive);
 const key=JSON.stringify([tile.cropId,tile.state,tile.season,tile.seasons,tile.remainingSeconds,tile.nominalRemainingSeconds,tile.dry,tile.paused,tile.weeds,tile.insects,tile.protected,model]);
 const bounds=game.getBoundingClientRect(),width=Math.min(bounds.width-24,330),left=Math.max(width/2+12,Math.min(bounds.width-width/2-12,point.x/1600*bounds.width));
 panel.style.left=`${left}px`;panel.style.top=`${point.y/900*bounds.height}px`;panel.classList.toggle('above',point.y>610);
 if(key===healthKey)return;healthKey=key;panel.replaceChildren();
 const heading=document.createElement('div');heading.className='crop-tooltip-head';const title=document.createElement('strong');title.textContent=model.title;heading.append(title);
 for(const label of model.chips){const chip=document.createElement('span');chip.textContent=label;heading.append(chip);}panel.append(heading);
 for(const [label,text,value]of [['Temporada',model.seasonText,model.seasonProgress],['Crescimento',model.growthText,model.growthProgress]] as [string,string,number][]){const row=document.createElement('div');row.className='crop-progress-row';const caption=document.createElement('div');caption.className='crop-progress-caption';const name=document.createElement('span');name.textContent=label;const status=document.createElement('span');status.textContent=text;caption.append(name,status);const progress=document.createElement('progress');progress.max=1;progress.value=value;progress.setAttribute('aria-label',`${label}: ${text}`);row.append(caption,progress);panel.append(row);}
}
function tileList(){dialog('Terras da fazenda');const states:Record<string,string>={GRASS:'grama',PLOWED:'arada',DEAD:'planta morta',READY:'pronta',GROWING:'crescendo'};const grid=document.createElement('div');grid.className='tile-list';content.append(grid);for(const tile of display()?.tiles??[]){const crop=catalog.crops.find(c=>c.id===tile.cropId);const b=button(`Terra ${tile.column+1}, ${tile.row+1}: ${!tile.unlocked?'bloqueada':crop?.name??states[tile.state]}`,async()=>{modal.close();await tileAction(tile);},grid);b.title='Aplicar a ferramenta selecionada';}}
function card(name:string,image?:string){const c=document.createElement('article');c.className='card';if(image){const img=document.createElement('img');img.loading='lazy';img.src='/assets/'+image;img.alt='';c.append(img);}const title=document.createElement('strong');title.textContent=name;c.append(title);return c;}
function marketCard(name:string,imagePath:string,details:string,level:number,prices:Record<string,number>,kind:string,id:string,vip=false){
 const c=document.createElement('article');c.className='card market-card';const title=document.createElement('h3');title.textContent=name;const image=document.createElement('img');image.className='market-art';image.loading='lazy';image.src=`/assets/${imagePath}`;image.alt='';const description=paragraph(details,c);description.className='market-details';const levelLabel=document.createElement('small');levelLabel.textContent=`Nível ${level}${vip?' · VIP':''}`;
 const locked=(own?.level??0)<level||vip&&!own?.vipActive;if(locked)c.classList.add('locked');c.prepend(title);c.insertBefore(image,description);c.append(levelLabel);
 if(locked){const badge=document.createElement('span');badge.className='lock-badge';badge.textContent=(own?.level??0)<level?`Nível ${level}`:'Exige VIP';c.append(badge);}
 for(const [currency,price]of Object.entries(prices)){const label=currency==='GOLD'?'ouro':'verdes',buy=button(`Comprar · ${price}`,()=>action({action:kind,itemId:id,currency}),c);buy.className='market-buy';buy.disabled=locked;buy.setAttribute('aria-label',`Comprar ${name} por ${price} ${label}`);buy.title=locked?((own?.level??0)<level?`Nível ${level}`:'Exige VIP'):`${price} ${label}`;const coin=document.createElement('img');coin.className='price-coin';coin.src=`/assets/classic/icons/moeda-${currency==='GOLD'?'comum':'verde'}.png`;coin.alt='';buy.append(coin);}
 return c;
}
function shop(category='crops'){
 dialog('Loja');const tabs=document.createElement('div');tabs.className='tabs';content.append(tabs);
 for(const [id,label]of [['crops','Sementes'],['animals','Animais'],['fertilizers','Fertilizantes'],['owned','Meus animais'],['vip','VIP']]){const tab=button(label,()=>shop(id),tabs);tab.className='shop-tab';tab.setAttribute('aria-pressed',String(category===id));}
 const scroll=document.createElement('div');scroll.className='market-scroll';content.append(scroll);
 if(category==='crops'){
  const groups=document.createElement('div');groups.className='tabs seed-tabs';content.insertBefore(groups,scroll);
  for(const [id,label]of [['common','Comuns'],['vip','VIP']] as const){const tab=button(label,()=>{seedMarketGroup=id;shop(category);},groups);tab.className='shop-tab';tab.setAttribute('aria-pressed',String(seedMarketGroup===id));}
  const cards=document.createElement('div');cards.className='market-cards';scroll.append(cards);
  for(const crop of cropGroups(catalog.crops)[seedMarketGroup])cards.append(marketCard(crop.name,`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`,`${crop.growthSeconds/3600} h · ${crop.seasons} temporada(s)`,crop.requiredLevel,crop.seedPrices,'BUY_SEED',crop.id,crop.vipOnly));
 }
 if(category==='animals'){
  const cards=document.createElement('div');cards.className='market-cards';scroll.append(cards);
  for(const animal of catalog.animals)cards.append(marketCard(animal.name,animal.id==='abelha'?'classic/animals/abelha/abelha.png':animalPath(animal.id,'ADULT',false,false,false),animal.productName,animal.requiredLevel,animal.prices,'BUY_ANIMAL',animal.id));
 }
 if(category==='fertilizers'){
  const cards=document.createElement('div');cards.className='market-cards';scroll.append(cards);
  for(const fertilizer of catalog.fertilizers)cards.append(marketCard(fertilizer.name,`classic/icons/adubo-${fertilizer.id}.png`,`Reduz ${fertilizer.reductionSeconds/60} min${fertilizer.friendsOnly?' · em amigos':''}`,fertilizer.requiredLevel,fertilizer.prices,'BUY_FERTILIZER',fertilizer.id));
 }
 if(category==='vip'){
  const offer=document.createElement('article');offer.className='vip-offer';const image=document.createElement('img');image.src='/assets/classic/icons/cartao-vip.png';image.alt='Cartão VIP';const details=document.createElement('div');const title=document.createElement('h3');title.textContent='Cartão VIP · 30 dias';const intro=paragraph('Desbloqueie o acesso às culturas VIP respeitando o nível de cada uma.',details);intro.className='vip-intro';const benefits=document.createElement('ul');for(const benefit of ['Venda produtos com acréscimo de 25%.','Renove antes de expirar para acumular mais 30 dias.']){const item=document.createElement('li');item.textContent=benefit;benefits.append(item);}details.append(benefits);const status=paragraph(own?.vipActive?`VIP ativo até ${own.vipExpiresAt}`:'VIP inativo',details);status.className='vip-status';const buy=button(`Comprar cartão VIP · ${catalog.vip.greenPrice} verdes`,()=>action({action:'BUY_VIP'}),details);buy.className='vip-buy';buy.setAttribute('aria-label',`Comprar cartão VIP por ${catalog.vip.greenPrice} verdes`);const coin=document.createElement('img');coin.src='/assets/classic/icons/moeda-verde.png';coin.alt='';buy.append(coin);details.prepend(title);offer.append(image,details);scroll.append(offer);
 }
 if(category==='owned'){
  if(!own?.animals.length)paragraph('Nenhum animal comprado.',scroll);
  for(const animal of own?.animals??[]){const type=catalog.animals.find(a=>a.id===animal.id)!;const c=card(type.name,animalPath(animal.id,animal.stage,false,false,false));scroll.append(c);const stage:Record<string,string>={BABY:'Filhote',YOUNG:'Jovem',ADULT:'Adulto'};paragraph(`${stage[animal.stage]} · ${animalStatusText(animal.status,type.kind,animal.remainingSeconds)}`,c);
   const label=animalActionLabel(animal.status,type.kind,animal.stage);if(label)button(label,()=>action({action:'TEND_ANIMAL',itemId:animal.id}),c);
   const feedId=type.kind==='PRODUCER'?'producer':'growth';button(`Ração · ${catalog.feedPrices[feedId]} ouro`,()=>action({action:'BUY_FEED',itemId:feedId}),c);
  }
 }
}
function animalPopup(id:string){
 const farm=display(),animal=farm?.animals.find(item=>item.id===id);if(!animal)return;
 const type=catalog.animals.find(item=>item.id===id);if(!type)return;
 const stage:Record<string,string>={BABY:'Filhote',YOUNG:'Jovem',ADULT:'Adulto'};
 dialog(type.name);
 const image=id==='abelha'?'classic/animals/abelha/abelha.png':animalPath(id,animal.stage,false,false,false);
 const detail=card(type.name,image);content.append(detail);
 paragraph(`${stage[animal.stage]??animal.stage} · ${animalStatusText(animal.status,type.kind,animal.remainingSeconds)}`,detail);
 if(!visited&&farm?.own){
  const label=animalActionLabel(animal.status,type.kind,animal.stage);if(label)button(label,()=>{modal.close();void action({action:'TEND_ANIMAL',itemId:id});},detail);
  const feedId=type.kind==='PRODUCER'?'producer':'growth';
  button(`Comprar ração · ${catalog.feedPrices[feedId]} ouro`,()=>{modal.close();void action({action:'BUY_FEED',itemId:feedId});},detail);
  if(type.kind==='GROWING')button('Vender adulto',()=>{modal.close();void action({action:'SELL_ANIMAL',itemId:id});},detail);
 }else paragraph('Somente visualização durante a visita.',detail);
}
function deposit(){
 dialog('Depósito');
 const groups=new Map<string,number>();for(const lot of own?.produceLots??[])groups.set(lot.itemId,(groups.get(lot.itemId)??0)+lot.quantity);
 const totals=own?.depositTotals??{},value=Object.entries(totals).filter(([,amount])=>Number(amount)>0).map(([currency,amount])=>`${amount} ${currency==='GOLD'?'ouro':'verdes'}`).join(' + ');
 paragraph('Produtos colhidos e itens prontos para vender.',content).className='deposit-intro';
 const overview=document.createElement('section');overview.className='deposit-overview';const overviewTitle=document.createElement('h3');overviewTitle.textContent='Você recebe ao vender tudo';overview.append(overviewTitle);
 const summary=document.createElement('div');summary.className='deposit-summary';
 for(const [currency,label,icon]of [['GOLD','Ouro','moeda-comum.png'],['GREEN','Verdes','moeda-verde.png']]){const amount=totals[currency]??0,item=document.createElement('div');item.className='deposit-currency';const image=document.createElement('img');image.src=`/assets/classic/icons/${icon}`;image.alt='';const detail=document.createElement('span');detail.textContent=label;const total=document.createElement('strong');total.textContent=String(amount);item.append(image,detail,total);summary.append(item);}
 overview.append(summary);
 if(value){const sellAll=button(`Vender tudo · ${value}`,async()=>{await action({action:'SELL_ALL'});deposit();},overview);sellAll.className='deposit-sell-all';}
 content.append(overview);
 const products=[...groups].map(([id,count])=>{const crop=catalog.crops.find(c=>c.id===id),animal=catalog.animals.find(a=>a.productId===id);return{id,count,crop,animal,name:crop?.name??animal?.productName??id,icon:crop?`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`:animal?.id==='galinha'||animal?.id==='pato'?'classic/animals/ninho-geral/ninho-com-ovos.png':animal?.id==='ovelha'?'classic/animals/ovelha/la-ovelha.png':animal?.id==='vaca'?'classic/animals/vaca/balde-vaca.png':animal?animalPath(animal.id,'ADULT',false,false,false):undefined};});
 if(!products.length){const empty=document.createElement('div');empty.className='deposit-empty';const title=document.createElement('strong');title.textContent='Seu depósito está vazio';const message=document.createElement('p');message.textContent='Colha uma plantação ou recolha a produção dos animais para ver os itens aqui.';empty.append(title,message);content.append(empty);}
 for(const [kind,label]of [['crop','Colheitas'],['animal','Produtos dos animais'],['other','Outros produtos']] as const){const items=products.filter(item=>kind==='crop'?!!item.crop:kind==='animal'?!!item.animal:!item.crop&&!item.animal);if(!items.length)continue;const section=document.createElement('section');section.className='deposit-group';const heading=document.createElement('h3');heading.textContent=label;section.append(heading);
  for(const item of items){const row=document.createElement('article');row.className='deposit-item';if(item.icon){const image=document.createElement('img');image.src=`/assets/${item.icon}`;image.alt='';row.append(image);}
   const details=document.createElement('div');details.className='deposit-item-details';const title=document.createElement('strong');title.textContent=item.name;const quantity=document.createElement('span');quantity.textContent=`${item.count} ${item.count===1?'unidade armazenada':'unidades armazenadas'}`;details.append(title,quantity);if(item.crop?.vipOnly&&!own?.vipActive){const locked=document.createElement('small');locked.className='deposit-vip-note';locked.textContent='Exige VIP para vender';details.append(locked);}row.append(details);
   const sellable=item.count>0&&(!item.crop?.vipOnly||own?.vipActive),actions=document.createElement('div');actions.className='deposit-item-actions';const sellOne=button('Vender 1',async()=>{await action({action:'SELL_ITEM',itemId:item.id});deposit();},actions);sellOne.disabled=!sellable;sellOne.setAttribute('aria-label',`Vender 1 ${item.name}`);const sellProduct=button('Vender tudo',async()=>{await action({action:'SELL_ITEM_ALL',itemId:item.id});deposit();},actions);sellProduct.disabled=!sellable;sellProduct.setAttribute('aria-label',`Vender todas as unidades de ${item.name}`);row.append(actions);section.append(row);
  }content.append(section);
 }
 const feed=Object.entries(own?.feed??{}),feedSection=document.createElement('section');feedSection.className='deposit-feed';const feedTitle=document.createElement('h3');feedTitle.textContent='Ração armazenada';feedSection.append(feedTitle);if(feed.length){for(const [id,count]of feed){const item=document.createElement('div');item.className='deposit-feed-row';const label=document.createElement('span');label.textContent=id==='producer'?'Ração de produção':'Ração de crescimento';const amount=document.createElement('strong');amount.textContent=String(count);item.append(label,amount);feedSection.append(item);}}else{const message=document.createElement('p');message.textContent='Nenhuma ração armazenada.';feedSection.append(message);}content.append(feedSection);
}
function missions(){
 dialog('Tarefas diárias');
 if(own?.tutorial){const card=document.createElement('article');card.className='mission-card tutorial-mission';const heading=document.createElement('div');heading.className='mission-heading';const label=document.createElement('span');label.textContent='Tutorial';const title=document.createElement('strong');title.textContent=own.tutorial.title??'';heading.append(label,title);card.append(heading);paragraph(own.tutorial.hint??'',card);const progress=document.createElement('progress');progress.max=Math.max(1,own.tutorialTotal??6);progress.value=Math.min(own.tutorialStep??0,progress.max);progress.setAttribute('aria-label',`Tutorial: ${progress.value} de ${progress.max}`);card.append(progress);const status=document.createElement('div');status.className='mission-status';status.textContent=`${progress.value} / ${progress.max}`;card.append(status);content.append(card);}
 for(const mission of own?.missions??[]){
  const card=document.createElement('article');card.className=`mission-card${mission.done?' done':''}`;const heading=document.createElement('div');heading.className='mission-heading';
  const icon=document.createElement('span');icon.className='mission-check';icon.textContent=mission.done?'✓':'○';const title=document.createElement('strong');title.textContent=mission.title;
  const reward=document.createElement('span');reward.className='mission-reward';const coin=document.createElement('img');coin.src='/assets/classic/icons/moeda-verde.png';coin.alt='';reward.append(coin,document.createTextNode(`+${mission.green}`));heading.append(icon,title,reward);card.append(heading);
  const progress=document.createElement('progress');progress.max=Math.max(1,mission.target);progress.value=Math.min(mission.progress,mission.target);progress.setAttribute('aria-label',`${mission.title}: ${mission.progress} de ${mission.target}`);card.append(progress);
  const status=document.createElement('div');status.className='mission-status';status.append(document.createTextNode(`${mission.progress} / ${mission.target} · ${mission.done?'Concluída · recompensa recebida':'Em andamento'}`));card.append(status);content.append(card);
 }
 if(!own?.missions?.length)paragraph('Nenhuma tarefa disponível.');
 if(own?.serverTime)paragraph(`Novas missões em ${untilUtcMidnight(own.serverTime)} (meia-noite UTC).`);
 paragraph(own?.vipActive?`VIP ativo até ${own.vipExpiresAt}`:'VIP inativo.');
}
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
 if(!api)return;const client=api,generation=sessionGeneration;dialog('Amigos');
 const invites=document.createElement('section');invites.className='invite-card';const title=document.createElement('h3');title.textContent='Convidar ou adicionar amigo';invites.append(title);paragraph('Crie um link válido por sete dias ou aceite um convite recebido.',invites);button('Criar convite',async()=>{
  try{const reply=await client.mutate('/v1/friend-invitations',{});if(generation!==sessionGeneration)return;let result=invites.querySelector<HTMLElement>('.invite-result');if(!result){result=document.createElement('div');result.className='invite-result';invites.append(result);}result.replaceChildren();const code=document.createElement('strong');code.textContent=reply.code??'';code.id='invite-code';const url=new URL(location.origin);url.searchParams.set('invite',reply.code??'');const link=document.createElement('a');link.href=url.href;link.textContent='Abrir link de convite';const expiry=document.createElement('span');expiry.textContent='Expira em sete dias · uso único';result.append(code,link,expiry);}
  catch(error){handleError(error);}finally{syncPending();}
 },invites);invitationForm(invites);content.append(invites);
 const heading=document.createElement('h3');heading.className='friends-title';heading.textContent='Seus amigos';content.append(heading);const listPanel=document.createElement('div');listPanel.className='friend-list';content.append(listPanel);
 try{const list=await client.get<{id:string;name:string}[]>('/v1/friends');if(generation!==sessionGeneration)return;for(const friend of list){const row=document.createElement('article');row.className='friend-card';const badge=document.createElement('span');badge.className='friend-avatar';badge.textContent=friend.name.slice(0,1).toUpperCase();const name=document.createElement('strong');name.textContent=friend.name;const visit=button('Visitar fazenda',async()=>{const navigation=++navigationGeneration;try{const farm=await client.get<Farm>(`/v1/farms/${friend.id}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration)return;visited=farm;tool='STEAL';render();modal.close();}catch(error){handleError(error);}},row);row.prepend(badge,name);listPanel.append(row);}if(!list.length)paragraph('Nenhum amigo adicionado ainda.',listPanel);}
 catch(error){handleError(error);}
}
async function connect(user:string,token:()=>Promise<string>){
 sessionGeneration++;navigationGeneration++;busy=false;own=undefined;visited=undefined;scene?.close();const generation=sessionGeneration;
 api=new ApiClient(config.api,user,token,sessionStorage);syncPending();
 scene=new Scene(element<HTMLCanvasElement>('farm'),catalog,tile=>void tileAction(tile),id=>{const command=animalClickCommand(id,!!visited);if(command)void action(command);else animalPopup(id);},health);
 try{await refresh();if(generation===sessionGeneration&&!own){paragraph('Esta conta precisa de um convite para começar.',login);invitationForm(login);}}
 catch(error){handleError(error);}
}
async function start(){
 login.replaceChildren();
 if(supabase)button('Entrar com Google',async()=>{const {error}=await supabase.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin}});if(error)notify(error.message);},login);
 showConnection('Conectando à fazenda… O primeiro acesso pode levar cerca de um minuto.');
 try{
  const response=await fetch(config.api+'/health',{signal:AbortSignal.timeout(65_000),cache:'no-store'});if(!response.ok)throw new Error('Servidor indisponível.');const health=await response.json();
  catalog=await (await fetch(config.api+'/v1/catalog',{signal:AbortSignal.timeout(65_000)})).json();selectedSeed=catalog.crops[0]?.id??selectedSeed;
  showConnection();
  if(config.development){if(!health.development)throw new Error('Contas locais exigem servidor de desenvolvimento.');for(const name of ['Alice','Bob'])button(`Testar como ${name}`,async()=>{sessionStorage.setItem('colheita:local-user',name);await connect(name.toLowerCase(),async()=>`dev-${name.toLowerCase()}`);},login);const previous=sessionStorage.getItem('colheita:local-user');if(previous==='Alice'||previous==='Bob')await connect(previous.toLowerCase(),async()=>`dev-${previous.toLowerCase()}`);}
  else{
   const {data,error}=await supabase!.auth.getSession();if(error)throw error;
   if(data.session){const userId=data.session.user.id;await connect(userId,async()=>{const {data,error}=await supabase!.auth.getSession();if(error||!data.session||data.session.user.id!==userId)throw new ApiError(401,'Entre novamente com a mesma conta para continuar.');return data.session.access_token;});}
  }
 }catch(error){showConnection();notify(error instanceof Error?error.message:'Servidor indisponível.');button('Tentar conectar novamente',start,login);}
}
element('shop').onclick=()=>shop();element('deposit').onclick=deposit;element('missions').onclick=missions;element('friends').onclick=()=>void friends();
element('home').onclick=()=>{navigationGeneration++;visited=undefined;tool='PLOW';render();};element('settings').onclick=()=>shortcutPanel('Ajustes',settings);element('close-modal').onclick=()=>modal.close();
element('retry').onclick=async()=>{if(!api||busy)return;const client=api,generation=sessionGeneration;busy=true;try{const reply=await client.retry();if(generation!==sessionGeneration)return;receive(reply);if(reply.code){dialog('Convite confirmado');paragraph(reply.code);}}catch(error){if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();await refresh();}}};
window.addEventListener('offline',()=>showConnection('Sem conexão. Reconecte antes de agir.'));
window.addEventListener('online',()=>void refresh());
document.addEventListener('keydown',event=>{if(!own||event.repeat||event.target instanceof HTMLInputElement||event.target instanceof HTMLSelectElement)return;const shortcut=gameShortcut(event.key,!!visited);if(!shortcut)return;if(shortcut.action==='tool'){if(!modal.open)pickTool(shortcut.tool);return;}if(shortcut.action==='shop')shortcutPanel('Loja',()=>shop());else if(shortcut.action==='deposit')shortcutPanel('Depósito',deposit);else if(shortcut.action==='missions')shortcutPanel('Tarefas diárias',missions);else if(shortcut.action==='music')toggleMusic();else if(shortcut.action==='effects')toggleEffects();});
document.addEventListener('visibilitychange',()=>{if(!document.hidden)void refresh();});
window.setInterval(()=>{if(!document.hidden)void refresh();},30_000);
void start();
