import {createClient} from '@supabase/supabase-js';
import type {Farm,Catalog,Tile,Reply,CommandFields} from './types.ts';
import {ApiClient,ApiError,acceptFarm} from './api.ts';
import {Scene} from './scene.ts';
import {takeInvite} from './invite-url.ts';
import {renderWallet,control,text,renderDailyAllowance} from './ui.ts';
import {durationLabel,seedGrowthLabel,CROP_TIME_NOTICE,cropTimingText} from './ui-presentation.ts';
import {GAME_AVATARS,googleAvatar,avatarSource,readAvatar,saveAvatar,type AvatarChoice} from './avatar.ts';
import {periodAt,sceneTransform,worldToScreen,tileCenter,animalPath,animalStatusText,animalActionLabel,animalClickCommand,gameShortcut,shortcutPanelState,toolbarTrayToggle,tileCommand,cropGroups,soundForAction,actionRewardModel,cropRemainingAfter,cropNominalRemainingAfter} from './presentation.ts';

const config=window.COLHEITA_CONFIG;
const element=<T extends HTMLElement=HTMLElement>(id:string)=>document.getElementById(id) as T;
const modal=element<HTMLDialogElement>('modal'),content=element('modal-content'),modalFooter=element('modal-footer');
let api:ApiClient|undefined,catalog:Catalog,own:Farm|undefined,visited:Farm|undefined,scene:Scene|undefined;
let sceneResize:ResizeObserver|undefined;
let avatarUser='',googlePhoto:string|undefined,avatarChoice:AvatarChoice='classic',avatarStore:Storage|undefined;
try{avatarStore=window.localStorage;}catch{/* Playing remains available when browser preferences are blocked. */}
let musicTrack='';
let tool='CURSOR',selectedSeed='nabo',selectedFertilizer='comum',trayOpen=false,effectsEnabled=false,musicEnabled=false,effectsVolume=.65,musicVolume=.25,music:HTMLAudioElement|undefined,busy=false,seedMarketGroup:'common'|'vip'='common',refreshDialog:(()=>void)|undefined;
let toastTimer:number,loginTransitionTimer:number,sessionGeneration=0,navigationGeneration=0,dialogGeneration=0,healthTile:Tile|null=null,healthPoint:{x:number;y:number}|null=null;
let goldAnimationFrame=0;
const depositNumber=new Intl.NumberFormat('pt-BR',{maximumFractionDigits:1});
const depositPriceNumber=new Intl.NumberFormat('pt-BR',{maximumFractionDigits:2});
const login=element('login');
const {invite:inviteFromUrl,cleaned:cleanedUrl}=takeInvite(location.href);
if(inviteFromUrl&&cleanedUrl){sessionStorage.setItem('colheita:invitation',inviteFromUrl);history.replaceState(history.state,'',cleanedUrl);}
const supabase=config.development?undefined:createClient(config.supabaseUrl,config.supabaseKey,{auth:{flowType:'pkce',detectSessionInUrl:true}});
const toolDefinitions=[['CURSOR','Selecionar','cursor-ui'],['PLOW','Arar / limpar','pa-ui'],['PLANT','Plantar','saco-ui'],['WATER','Regar','regador-ui'],['WEED','Remover ervas','herbicida-ui'],['PESTICIDE','Remover insetos','pesticida-ui'],['HARVEST','Colher','mao-restaurada'],['FERTILIZE','Adubar','adubo-comum']];
function button(label:string,action:()=>void|Promise<void>,parent:HTMLElement=content,focusKey?:string){const b=control(label,()=>void action());if(focusKey)b.setAttribute('data-focus-key',focusKey);parent.append(b);return b;}
function enableTabNavigation(list:HTMLElement){list.addEventListener('keydown',event=>{if(!(event instanceof KeyboardEvent))return;const tabs=[...list.querySelectorAll<HTMLButtonElement>('[role="tab"]:not(:disabled)')],current=tabs.indexOf(document.activeElement as HTMLButtonElement);if(current<0)return;let next=current;if(event.key==='ArrowRight')next=(current+1)%tabs.length;else if(event.key==='ArrowLeft')next=(current+tabs.length-1)%tabs.length;else if(event.key==='Home')next=0;else if(event.key==='End')next=tabs.length-1;else return;event.preventDefault();tabs[next].focus();tabs[next].click();});}
function paragraph(text:string,parent:HTMLElement=content){const p=document.createElement('p');p.textContent=text;parent.append(p);return p;}
function githubIcon(){const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('viewBox','0 0 16 16');icon.setAttribute('aria-hidden','true');const mark=document.createElementNS('http://www.w3.org/2000/svg','path');mark.setAttribute('fill','currentColor');mark.setAttribute('d','M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38v-1.49c-2.23.48-2.7-1.08-2.7-1.08-.36-.92-.89-1.16-.89-1.16-.73-.5.06-.49.06-.49.81.06 1.23.83 1.23.83.71 1.22 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.83-2.15-.08-.2-.36-1.02.08-2.12 0 0 .68-.22 2.2.82a7.65 7.65 0 0 1 4 0c1.5-1.04 2.19-.82 2.19-.82.44 1.1.16 1.92.08 2.12.52.56.83 1.28.83 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48v2.19c0 .21.15.46.55.38A8.01 8.01 0 0 0 16 8c0-4.42-3.58-8-8-8z');icon.append(mark);return icon;}
function audioIcon(muted:boolean){const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('viewBox','0 0 24 24');icon.setAttribute('aria-hidden','true');const speaker=document.createElementNS('http://www.w3.org/2000/svg','path');speaker.setAttribute('fill','currentColor');speaker.setAttribute('d','M3 9v6h4l5 4V5L7 9H3z');icon.append(speaker);const sound=document.createElementNS('http://www.w3.org/2000/svg','path');sound.setAttribute('fill','none');sound.setAttribute('stroke','currentColor');sound.setAttribute('stroke-width','2');sound.setAttribute('stroke-linecap','round');sound.setAttribute('d',muted?'M4 4l16 16':'M15.5 8.5a5 5 0 0 1 0 7M18.5 5.5a9 9 0 0 1 0 13');icon.append(sound);return icon;}
function formatToolDuration(seconds:number){const minutes=Math.max(1,Math.ceil(seconds/60));return minutes>=60?`${Math.floor(minutes/60)} h${String(minutes%60).padStart(2,'0')}`:`${minutes} min`;}
function layoutHud(){
 const root=element('app'),game=element('game'),bar=element('tools-bar').getBoundingClientRect(),notice=element('notice');
 if(notice.textContent?.trim()&&!notice.querySelector('.notice-close')){const close=button('×',()=>{clearTimeout(toastTimer);notice.replaceChildren();notice.classList.remove('level-notice','reward-notice');layoutHud();},notice);close.className='notice-close';close.setAttribute('aria-label','Fechar aviso');close.title='Fechar aviso';}
 const toolsTop=own&&!game.hidden?Math.max(0,(window.innerHeight||game.getBoundingClientRect().height)-bar.top):0;
 root.style.setProperty('--tools-top',`${toolsTop}px`);root.style.setProperty('--notice-bottom',`${toolsTop+12}px`);
 root.style.setProperty('--notice-height',`${notice.textContent||notice.children.length?notice.getBoundingClientRect().height:0}px`);
 const header=element('farm-header').getBoundingClientRect();root.style.setProperty('--hud-bottom',`${header.top+header.height}px`);root.style.setProperty('--notice-top','12px');root.style.setProperty('--notice-left','50%');
}
function notify(message:string){const notice=element('notice');notice.classList.remove('level-notice','reward-notice');notice.replaceChildren(document.createTextNode(message));layoutHud();clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>{notice.replaceChildren();layoutHud();},7000);}
function saveAudioSettings(){try{avatarStore?.setItem(`colheita:audio:${avatarUser}`,JSON.stringify({effects:effectsEnabled,music:musicEnabled,effectsVolume,musicVolume}));}catch{/* Audio preferences are optional when browser storage is blocked. */}}
function loadAudioSettings(){try{const saved=JSON.parse(avatarStore?.getItem(`colheita:audio:${avatarUser}`)??'{}');effectsEnabled=typeof saved.effects==='boolean'?saved.effects:false;musicEnabled=typeof saved.music==='boolean'?saved.music:false;effectsVolume=typeof saved.effectsVolume==='number'&&Number.isFinite(saved.effectsVolume)?Math.max(0,Math.min(1,saved.effectsVolume)):.65;musicVolume=typeof saved.musicVolume==='number'&&Number.isFinite(saved.musicVolume)?Math.max(0,Math.min(1,saved.musicVolume)):.25;}catch{effectsEnabled=false;musicEnabled=false;effectsVolume=.65;musicVolume=.25;}}
function syncAudioRow(channel:'music'|'effects'){
 const row=content.querySelector<HTMLElement>(`.audio-setting[data-audio-channel="${channel}"]`);if(!row)return;
 const enabled=channel==='music'?musicEnabled:effectsEnabled,muted=!enabled,slider=row.querySelector<HTMLInputElement>('input[type="range"]'),value=row.querySelector<HTMLOutputElement>('output'),mute=row.querySelector<HTMLButtonElement>('.audio-mute');
 if(!slider||!value||!mute)return;row.classList.toggle('is-muted',muted);slider.style.setProperty('--range-progress',`${slider.value}%`);value.textContent=muted?'Mudo':`${slider.value}%`;mute.setAttribute('aria-pressed',String(muted));mute.setAttribute('aria-label',`${muted?'Ativar':'Silenciar'} ${channel==='music'?'música':'efeitos'}`);mute.replaceChildren(audioIcon(muted));
}
function updateAudioSettings(channel:'music'|'effects',enabled:boolean,volume:number){if(channel==='music'){musicEnabled=enabled;musicVolume=volume;if(music)music.volume=volume;syncMusic();}else{effectsEnabled=enabled;effectsVolume=volume;if(animalSound){animalSound.volume=volume;if(!enabled)animalSound.pause();}if(!enabled)stopEffects();else for(const clip of activeEffects)clip.volume=volume;syncAmbience(periodAt(new Date())==='night');}syncAudioRow(channel);saveAudioSettings();}
function toggleEffects(){effectsEnabled=!effectsEnabled;if(!effectsEnabled){animalSound?.pause();stopEffects();}syncAmbience(periodAt(new Date())==='night');saveAudioSettings();syncAudioRow('effects');}
let ambience:HTMLAudioElement[]=[];
let ambiencePeriod:'day'|'night'|undefined;
function syncAmbience(night:boolean){
 const period=night?'night':'day';
 if(!effectsEnabled||!own||document.hidden){for(const clip of ambience)clip.pause();return;}
 if(ambiencePeriod!==period){
  for(const clip of ambience)clip.pause();
  const tracks=night?['grilos-ambientacao']:['ventos-leves-ambientacao','passaros-ambientacao'];
  ambience=tracks.map(track=>{const clip=new Audio('/assets/sounds/'+track+'.mp3');clip.loop=true;return clip;});
  ambiencePeriod=period;
 }
 for(const clip of ambience){clip.volume=effectsVolume*.18;if(clip.paused)void clip.play().catch(()=>{});}
}
function syncMusic(period=periodAt(new Date())){
 if(document.hidden)stopEffects();
 syncAmbience(period==='night');
 if(period==='night'||document.hidden||!effectsEnabled)animalSound?.pause();
 if(!musicEnabled||!own||document.hidden){music?.pause();return;}
 const track=period==='night'?'noite.mp3':'farm_loop.mp3';
 if(!music||musicTrack!==track){music?.pause();music=new Audio('/assets/sounds/'+track);musicTrack=track;music.loop=true;}
 music.volume=musicVolume;if(music.paused)void music.play().catch(()=>{});
}
function toggleMusic(){musicEnabled=!musicEnabled;saveAudioSettings();syncAudioRow('music');syncMusic();}
function shortcutPanel(title:string,open:()=>void){if(shortcutPanelState(modal.open,element('modal-title').textContent,title)==='close')modal.close();else open();}
function actionIcon(action:string,itemId?:string){
 if(action==='CLAIM_DAILY_ALLOWANCE')return 'classic/icons/moeda-comum.png';
 if(action==='SPREAD_INSECTS')return 'classic/effects/verme_1.png';
 if(action==='SPREAD_WEEDS')return 'classic/effects/erva_daninha_1.png';
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
 layoutHud();clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>{notice.replaceChildren();notice.classList.remove('level-notice','reward-notice');layoutHud();},reward.leveledUp?2600:3200);
}
function animateGoldBalance(from:number,to:number){
 const balance=element('wallet').querySelector<HTMLElement>('.balances > div[aria-label$="ouro"] strong');
 if(!balance)return;
 if(goldAnimationFrame)cancelAnimationFrame(goldAnimationFrame);
 if(matchMedia('(prefers-reduced-motion: reduce)').matches){balance.textContent=depositNumber.format(to);return;}
 const start=performance.now();
 const tick=(now:number)=>{const progress=Math.min(1,(now-start)/600);balance.textContent=depositNumber.format(from+(to-from)*progress);if(progress<1)goldAnimationFrame=requestAnimationFrame(tick);else goldAnimationFrame=0;};
 balance.textContent=depositNumber.format(from);goldAnimationFrame=requestAnimationFrame(tick);
}
function notifySale(coins:number,greens:number){
 const notice=element('notice');notice.classList.remove('level-notice');notice.classList.add('reward-notice');notice.replaceChildren();
 for(const [amount,currency,icon]of [[coins,'ouro','moeda-comum'],[greens,'verdes','moeda-verde']] as [number,string,string][]){if(amount<=0)continue;const image=document.createElement('img');image.src=`/assets/classic/icons/${icon}.png`;image.alt='';const message=document.createElement('span');message.textContent=`+${depositNumber.format(amount)} ${currency}`;notice.append(image,message);}
 layoutHud();clearTimeout(toastTimer);toastTimer=window.setTimeout(()=>{notice.replaceChildren();notice.classList.remove('reward-notice');layoutHud();},3200);
}
async function fadeSoldDepositRows(fields:CommandFields,farm:Farm){
 const actionName=String(fields.action);if(!['SELL_ITEM','SELL_ITEM_ALL','SELL_ALL'].includes(actionName))return;
 const previous=new Map<string,number>();for(const lot of own?.produceLots??[])previous.set(lot.itemId,(previous.get(lot.itemId)??0)+lot.quantity);
 const remaining=new Set((farm.produceLots??[]).map(lot=>lot.itemId));
 const rows=[...content.querySelectorAll<HTMLElement>('.deposit-item[data-product-id]')].filter(row=>{
  const id=row.dataset.productId!;return (actionName==='SELL_ALL'||id===fields.itemId)&&previous.has(id)&&!remaining.has(id);
 });
 if(!rows.length)return;
 rows.forEach(row=>row.classList.add('is-removing'));
 await new Promise(resolve=>window.setTimeout(resolve,matchMedia('(prefers-reduced-motion: reduce)').matches?0:180));
}
let missionsTimer:number|undefined;
function dialog(title:string){dialogGeneration++;refreshDialog=undefined;closeHealth();element('modal-title').textContent=title;modal.classList.toggle('shop-dialog',title==='Loja');modal.classList.toggle('deposit-dialog',title==='Depósito');modal.classList.toggle('settings-dialog',title==='Ajustes');modal.classList.toggle('missions-dialog',title==='Missões');if(title!=='Missões'){window.clearInterval(missionsTimer);missionsTimer=undefined;}content.replaceChildren();content.scrollTop=0;modalFooter.replaceChildren();modalFooter.hidden=true;if(!modal.open)modal.showModal();}
function settings(){
 dialog('Ajustes');
 paragraph('Som, perfil e conta da fazenda.',content).className='settings-intro';
 const sections=document.createElement('div');sections.className='settings-sections';content.append(sections);
 const audio=document.createElement('section');audio.className='settings-section settings-audio';sections.append(audio);
 const audioTitle=document.createElement('h3');audioTitle.textContent='Áudio';audio.append(audioTitle);
 for(const [channel,name]of [['music','Música'],['effects','Efeitos']] as const){
  const row=document.createElement('div');row.className='audio-setting';row.setAttribute('data-audio-channel',channel);
  const label=document.createElement('label');label.textContent=name;const volume=document.createElement('input');volume.type='range';volume.id=`audio-volume-${channel}`;volume.min='0';volume.max='100';volume.value=String((channel==='music'?musicVolume:effectsVolume)*100);volume.setAttribute('aria-label',`Volume ${channel==='music'?'da música':'dos efeitos'}`);
  const value=document.createElement('output');value.setAttribute('aria-live','polite');const mute=control('',()=>{const enabled=channel==='music'?musicEnabled:effectsEnabled;updateAudioSettings(channel,!enabled,Number(volume.value)/100);});mute.className='audio-mute';
  volume.oninput=()=>updateAudioSettings(channel,true,Number(volume.value)/100);row.append(label,volume,value,mute);audio.append(row);syncAudioRow(channel);
 }
 paragraph('Preferências salvas por conta neste navegador.',audio).className='muted';
 const profile=document.createElement('section');profile.className='settings-section settings-profile';sections.append(profile);const profileTitle=document.createElement('h3');profileTitle.textContent='Seu perfil';profile.append(profileTitle);
 const player=document.createElement('div');player.className='profile-player';const avatar=document.createElement('img');avatar.className='profile-avatar';avatar.src=avatarSource(avatarChoice,googlePhoto);avatar.alt=`Avatar de ${own?.name??'jogador'}`;avatar.onerror=()=>{avatar.onerror=null;avatar.src=avatarSource('classic');};const details=document.createElement('div');details.className='profile-details';const playerName=document.createElement('strong');playerName.className='profile-name';playerName.textContent=own?.name??'Jogador';details.append(playerName);button('Trocar avatar',avatarSettings,details);player.append(avatar,details);profile.append(player);
 const about=document.createElement('section');about.className='settings-section settings-about';sections.append(about);const aboutTitle=document.createElement('h3');aboutTitle.textContent='Sobre';about.append(aboutTitle);
 const repository=document.createElement('a');repository.className='settings-action repository-link';repository.href='https://github.com/dabb3/colheita-feliz-public';repository.target='_blank';repository.rel='noopener noreferrer';repository.setAttribute('aria-label','Abrir repositório do projeto no GitHub em nova aba');const repositoryName=document.createElement('strong');repositoryName.textContent='Repositório do projeto';const repositoryArrow=document.createElement('span');repositoryArrow.className='repository-external';repositoryArrow.textContent='↗';repository.append(githubIcon(),repositoryName,repositoryArrow);about.append(repository);
 const developers=document.createElement('div');developers.className='developer-links';for(const name of ['dabb3','mrlsouz']){const link=document.createElement('a');link.href=`https://github.com/${name}`;link.append(document.createTextNode(`@${name}`),githubIcon());link.target='_blank';link.rel='noopener noreferrer';developers.append(link);}about.append(developers);
 const logoutButton=button('Sair da conta',logout,modalFooter);logoutButton.className='settings-logout';modalFooter.hidden=false;
}
function closeScene(){stopEffects();animalSound?.pause();for(const clip of ambience)clip.pause();sceneResize?.disconnect();sceneResize=undefined;scene?.close();health(null,null);}
function avatarSettings(){
 dialog('Seu avatar');paragraph('Escolha um dos seis avatares do jogo ou a foto da sua conta Google. Esta preferência vale neste navegador.');
 const options=document.createElement('div');options.className='avatar-options';content.append(options);
 for(const {id:choice,label}of [...GAME_AVATARS,{id:'google',label:'Foto do Google'}] as const){
  const pick=button(label,()=>{avatarChoice=choice;saveAvatar(avatarStore,avatarUser,choice);render();settings();},options);
  pick.setAttribute('aria-pressed',String(avatarChoice===choice));pick.disabled=choice==='google'&&!googlePhoto;
  if(choice!=='google'||googlePhoto){const image=document.createElement('img');image.src=avatarSource(choice,googlePhoto);image.alt='';image.referrerPolicy='no-referrer';pick.prepend(image);}
 }
 if(!googlePhoto)paragraph('A foto do Google não está disponível nesta sessão.');
}
async function logout(){sessionGeneration++;navigationGeneration++;busy=false;music?.pause();api=undefined;own=undefined;visited=undefined;closeScene();sessionStorage.removeItem('colheita:local-user');modal.close();element('game').hidden=true;element('welcome').hidden=false;setLoginStep('login');syncPending();if(supabase)await supabase.auth.signOut();}
function showConnection(message?:string){element('connection').hidden=!message;element('connection').textContent=message??'';}
function syncPending(){element('pending').hidden=!api?.pending;element<HTMLButtonElement>('retry').disabled=!!api?.busy;}
function display(){return visited??own;}
function refreshOpenDialog(){
 if(!modal.open||!refreshDialog)return;
 const hadFocus=content.contains(document.activeElement),key=document.activeElement?.getAttribute('data-focus-key');
 const scrollContainers=()=>[modal,content,...content.querySelectorAll<HTMLElement>('.market-scroll')];
 const scroll=scrollContainers().map(node=>({top:node.scrollTop,left:node.scrollLeft}));
 refreshDialog();
 if(hadFocus){
  const target=Array.from(content.querySelectorAll<HTMLButtonElement>('[data-focus-key]')).find(node=>node.getAttribute('data-focus-key')===key&&!node.disabled);
  (target??element('close-modal')).focus({preventScroll:true});
 }
 scrollContainers().forEach((node,index)=>{const position=scroll[index];if(position){node.scrollTop=position.top;node.scrollLeft=position.left;}});
}
function render(){
 if(!own)return;
 syncMusic();
 element('welcome').hidden=true;element('game').hidden=false;
 renderWallet(element('wallet'),own,visited?.name,avatarSource(avatarChoice,googlePhoto),avatarSettings);
 element('home').hidden=!visited;
 const tutorial=element('tutorial');tutorial.hidden=!!visited||!own.tutorial;tutorial.replaceChildren();
 if(own.tutorial){const strong=document.createElement('strong');strong.textContent=own.tutorial.title??'';tutorial.append(strong);paragraph(own.tutorial.hint??'',tutorial);}
 scene?.update(display()!);syncPending();renderTools();refreshOpenDialog();const currentHealthTile=healthTile&&display()?.tiles.find(tile=>tile.column===healthTile!.column&&tile.row===healthTile!.row);health(currentHealthTile??null,healthPoint,'refresh');layoutHud();
}
function renderTools(){
 const toolbar=element('tools');toolbar.replaceChildren();
 const definitions=visited?[['CURSOR','Inspecionar plantas','cursor-ui'],['STEAL','Furtar colheita','mao-restaurada'],['LOVE','Fertilizante do amor','adubo-amor'],['SPREAD_INSECTS','Lançar inseto · 1/24h','verme_1'],['SPREAD_WEEDS','Lançar erva · 1/24h','erva_daninha_1']]:toolDefinitions;
 if(!definitions.some(d=>d[0]===tool)){tool=definitions[0][0];trayOpen=false;}
 element('farm').style.cursor='url("/assets/classic/icons/cursor-action.png") 1 1, auto';
 for(const [index,[id,label,icon]] of definitions.entries()){
  const b=button(label,()=>pickTool(id),toolbar);b.classList.add('tool-button');b.title=label;b.setAttribute('aria-label',label);b.setAttribute('aria-pressed',String(tool===id));b.textContent='';
  const pest=id==='SPREAD_INSECTS'||id==='SPREAD_WEEDS';
  const selectedIcon=id==='FERTILIZE'?`adubo-${selectedFertilizer}`:icon,image=document.createElement('img');image.alt='';image.src=pest?`/assets/classic/effects/${icon}.png`:`/assets/classic/icons/${selectedIcon.startsWith('adubo-')?'':'hud/'}${selectedIcon}.png`;b.append(image);
  if(id===tool&&id!=='CURSOR'&&!pest)element('farm').style.cursor=`url("/assets/classic/icons/cursors/${selectedIcon}.png") 16 16, auto`;
  if(pest){const limit=document.createElement('span');limit.className='tool-count';limit.textContent='1/24h';b.append(limit);}
  if(id==='PLANT'){
   const crop=catalog.crops.find(item=>item.id===selectedSeed),seedCount=own?.seedCounts?.[selectedSeed]??0;
   if(crop){const seed=document.createElement('img');seed.className='tool-selected-item';seed.alt='';seed.src=`/assets/classic/crops/seeds/semente_${crop.spriteStem}.png`;b.append(seed);const count=document.createElement('span');count.className='tool-count';count.textContent=`x${seedCount}`;b.append(count);}
  }
  if(id==='FERTILIZE'||id==='LOVE'){const count=document.createElement('span');count.className='tool-count';count.textContent=`x${own?.fertilizers?.[selectedFertilizer]??0}`;b.append(count);}
  if(!visited){const key=document.createElement('span');key.className='tool-key';key.textContent=String(index+1);b.append(key);}
 }
 renderToolTray();
}
function pickTool(id:string){const previous=tool;trayOpen=toolbarTrayToggle(previous,id,trayOpen);tool=id;if(id==='LOVE'&&!catalog.fertilizers.some(f=>f.id===selectedFertilizer&&f.friendsOnly))selectedFertilizer='amor';renderTools();if(healthTile&&healthPoint)renderHealth(healthTile,healthPoint,true);if(id==='SPREAD_INSECTS'||id==='SPREAD_WEEDS')notify('Escolha uma planta de um amigo, em crescimento e sem proteção. Limite: uma aplicação deste tipo a cada 24h.');}
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
  const farm=await client.get<Farm>('/v1/me/farm');if(generation!==sessionGeneration)return;updateOwnFarm(farm);
  if(visited&&navigation===navigationGeneration){const target=visited.owner;const incoming=await client.get<Farm>(`/v1/farms/${target}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration||visited?.owner!==target)return;visited=acceptFarm(visited,incoming);}
  showConnection();render();void client.prepareRequestId().catch(()=>{});
 }catch(error){if(generation===sessionGeneration)handleError(error);}
}
function handleError(error:unknown){
 const message=error instanceof Error?error.message:'Falha de conexão.';
 if(error instanceof ApiError){notify(message);if(error.status===409||error.status===410)void refresh();}
 else{showConnection('Conexão interrompida. Aguarde o servidor e tente novamente.');notify(message);}
 syncPending();
}
function updateOwnFarm(farm:Farm){
 const previous=own;
 own=acceptFarm(own,farm);
 if(previous&&own!==previous&&own.tiles.some(tile=>tile.state==='READY'&&previous.tiles.some(old=>
  old.column===tile.column&&old.row===tile.row&&old.cropId===tile.cropId&&old.season===tile.season&&old.state==='GROWING'
 )))playEffect('planta-pronta-para-colher');
}
function receive(reply:Reply,announce=true){
 if(reply.farm)updateOwnFarm(reply.farm);
 if(reply.visitedFarm&&visited?.owner===reply.visitedFarm.owner)visited=acceptFarm(visited,reply.visitedFarm);
 if(reply.result&&announce)notify(reply.result.message);
 render();
}
async function action(fields:CommandFields,path='/v1/me/actions'){
 if(!api||!own||busy)return;
 if(!navigator.onLine){notify('Sem conexão. Reconecte antes de agir.');return;}
 busy=true;let stale=false;const client=api,generation=sessionGeneration,previousFarm=own,animalReady=fields.action==='TEND_ANIMAL'&&own.animals.some(animal=>animal.id===fields.itemId&&animal.status==='READY');
 try{const reply=await client.mutate(path,{...fields,expectedRevision:path==='/v1/me/actions'?own.revision:visited!.revision});if(generation===sessionGeneration){if(reply.result?.success&&reply.farm){await fadeSoldDepositRows(fields,reply.farm);if(generation!==sessionGeneration)return;}receive(reply,false);if(reply.result?.success&&own){const reward=actionRewardModel(previousFarm,own),selling=String(fields.action).startsWith('SELL_');if(selling){notifySale(reward.coins,reward.greens);if(reward.coins>0)animateGoldBalance(Number(previousFarm.coins??0),Number(own.coins??0));}else notifyAction(reply.result.message,String(fields.action),typeof fields.itemId==='string'?fields.itemId:undefined,reward);const cue=reward.leveledUp?'levelup':soundForAction(String(fields.action),animalReady);if(cue)playEffect(cue);}else if(reply.result){playEffect('acao-bloqueada');notify(reply.result.message);}}}
 catch(error){stale=error instanceof ApiError&&[409,410].includes(error.status);if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();if(stale)void refresh();else void client.prepareRequestId().catch(()=>{});}}
}
let animalSound:HTMLAudioElement|undefined;
function playAnimalSound(id:string){
 if(!effectsEnabled||tool!=='CURSOR'||modal.open||document.hidden||periodAt(new Date())==='night')return;
 animalSound?.pause();
 animalSound=new Audio('/assets/sounds/animals/'+id+'.wav');
 animalSound.volume=effectsVolume;
 void animalSound.play().catch(()=>{});
}
const activeEffects=new Set<HTMLAudioElement>();
function stopEffects(){
 for(const audio of activeEffects)audio.pause();
 activeEffects.clear();
}
function playEffect(file:string){
 if(!effectsEnabled||document.hidden)return;
 const audio=new Audio('/assets/sounds/'+file+'.wav');
 const release=()=>activeEffects.delete(audio);
 audio.addEventListener('ended',release,{once:true});
 audio.addEventListener('error',release,{once:true});
 audio.volume=effectsVolume;
 activeEffects.add(audio);
 void audio.play().catch(release);
}
async function tileAction(tile:Tile){
 if(tool==='CURSOR'&&tile.cropId){health(tile,tileCenter(tile.column,tile.row),'tap');return;}
 if(visited&&tool==='CURSOR')return;
 if(visited){await action(tileCommand(tool,tile,selectedSeed,selectedFertilizer,true),`/v1/farms/${visited.owner}/visit-actions`);return;}
 if(!tile.unlocked){await action({action:'BUY_PLOT',column:tile.column,row:tile.row});return;}
 if(tool==='CURSOR')return;
 await action(tileCommand(tool,tile,selectedSeed,selectedFertilizer));
}
let healthKey='',healthDataKey='',healthOpenTimer:number|undefined,healthClockTimer:number|undefined,healthPinned=false,healthAnchorAt=0,healthBaseRemaining=0,healthBaseNominal=0;
function clearHealthTimers(){window.clearTimeout(healthOpenTimer);window.clearTimeout(healthClockTimer);healthOpenTimer=undefined;healthClockTimer=undefined;}
function closeHealth(){clearHealthTimers();healthPinned=false;healthTile=null;healthPoint=null;healthKey='';healthDataKey='';element('plant-health').hidden=true;}
function health(tile:Tile|null,point:{x:number;y:number}|null,pointerType='mouse'){
 if(healthPinned&&!['tap','resize','refresh'].includes(pointerType))return;
 const panel=element('plant-health');
 if(pointerType==='touch')return;
 if(modal.open||!tile?.cropId||!point){if(!healthPinned||pointerType==='refresh')closeHealth();return;}
 const sameTile=healthTile?.column===tile.column&&healthTile?.row===tile.row;
 healthTile=tile;healthPoint=point;
 if(panel.hidden&&pointerType!=='tap'){
  if(sameTile&&healthOpenTimer!==undefined)return;
  window.clearTimeout(healthOpenTimer);healthOpenTimer=window.setTimeout(()=>{healthOpenTimer=undefined;if(healthTile?.column===tile.column&&healthTile?.row===tile.row&&healthPoint)renderHealth(healthTile,healthPoint);},150);return;
 }
 if(pointerType==='tap')healthPinned=true;
 renderHealth(tile,point);
}
function renderHealth(tile:Tile,point:{x:number;y:number},force=false){
 const panel=element('plant-health'),game=element('game'),farm=display(),crop=catalog.crops.find(item=>item.id===tile.cropId);if(!crop||!farm)return;
 const dataKey=JSON.stringify([tile.cropId,tile.state,tile.stage,tile.season,tile.seasons,tile.remainingSeconds,tile.nominalRemainingSeconds,tile.watered,tile.dry,tile.paused,tile.weeds,tile.insects,tile.fertilized,tile.protected,farm.serverTime,(visited?own?.vipActive:farm.vipActive)]);
 const key=JSON.stringify([dataKey,tool]);
 if(!force&&key===healthKey){positionHealth(panel,game,point);return;}
 if(dataKey!==healthDataKey){healthDataKey=dataKey;healthAnchorAt=Date.now();healthBaseRemaining=tile.remainingSeconds;healthBaseNominal=tile.nominalRemainingSeconds;}
 healthKey=key;healthTile=tile;healthPoint=point;panel.hidden=false;panel.replaceChildren();
 const remaining=healthRemaining(tile),ready=tile.state==='READY'||tile.state==='GROWING'&&remaining<=0,dead=tile.state==='DEAD',paused=tile.paused;
 const heading=document.createElement('div');heading.className='crop-tooltip-head';const icon=document.createElement('img');icon.className='crop-tooltip-icon';icon.src=`/assets/classic/crops/seeds/semente_${crop.spriteStem}.png`;icon.alt='';const name=document.createElement('strong');name.textContent=crop.name;heading.append(icon,name);
 if(crop.vipOnly){const vip=document.createElement('span');vip.className='crop-vip-badge';vip.textContent='VIP';heading.append(vip);}
 const state=document.createElement('span');state.className=`crop-state-badge${ready?' is-ready':dead?' is-dead':paused||tile.dry?' is-problem':''}`;state.textContent=dead?'Morta':ready?'Pronta':paused?'Parada':tile.dry?'Seca':'Saudável';heading.append(state);panel.append(heading);
 const growth=document.createElement('section');growth.className='crop-growth';const caption=document.createElement('div');caption.className='crop-progress-caption';caption.append(document.createElement('span'));caption.firstElementChild!.textContent='CRESCIMENTO';const value=document.createElement('strong');value.className='crop-growth-value';caption.append(value);const track=document.createElement('div');track.className='crop-growth-track';track.setAttribute('role','progressbar');track.setAttribute('aria-valuemin','0');track.setAttribute('aria-valuemax','100');track.setAttribute('aria-label','Progresso de crescimento');const fill=document.createElement('span');fill.className='crop-growth-fill';track.append(fill);const estimate=document.createElement('p');estimate.className='crop-growth-estimate';const nominal=document.createElement('p');nominal.className='crop-growth-nominal';const condition=document.createElement('p');condition.className='crop-growth-condition';growth.append(caption,track,nominal,condition,estimate);panel.append(growth);
 const season=document.createElement('section');season.className='crop-season';const seasonLabel=document.createElement('p');seasonLabel.className='crop-season-label';seasonLabel.textContent=`Temporada ${tile.season} de ${tile.seasons}`;seasonLabel.setAttribute('aria-label',seasonLabel.textContent);const segments=document.createElement('div');segments.className='crop-season-track';segments.setAttribute('role','img');segments.setAttribute('aria-label',seasonLabel.textContent);for(let index=0;index<tile.seasons;index++){const segment=document.createElement('span');if(index<tile.season)segment.className='is-filled';segment.setAttribute('aria-hidden','true');segments.append(segment);}season.append(seasonLabel,segments);panel.append(season);
 if(!dead&&crop.vipOnly&&!(visited?own?.vipActive:farm.vipActive)){const warning=document.createElement('p');warning.className='crop-vip-warning';const vipIcon=document.createElement('img');vipIcon.src='/assets/classic/icons/cartao-vip.png';vipIcon.alt='';warning.append(vipIcon,document.createTextNode('Venda exige VIP'));panel.append(warning);}
 const chips=document.createElement('div');chips.className='crop-status-chips';if(!dead){if(tile.weeds)addChip(chips,'Ervas','/assets/classic/icons/hud/herbicida-ui.png','problem');if(tile.insects>0)addChip(chips,tile.insects===1?'Inseto':`${tile.insects} insetos`,'/assets/classic/icons/hud/pesticida-ui.png','problem');if(tile.dry&&tile.state!=='READY')addChip(chips,'Seca','/assets/classic/icons/hud/regador-ui.png','problem');if(tile.fertilized)addChip(chips,'Adubada','/assets/classic/icons/adubo-comum.png','benefit');if(tile.protected)addChip(chips,'Protegida','/assets/classic/animals/abelha/abelha.png','benefit');}if(chips.childElementCount)panel.append(chips);
 const action=healthAction(tile,ready,dead);if(action){const hint=document.createElement('div');hint.className='crop-action-hint';const toolIcon=document.createElement('img');toolIcon.src=action.icon;toolIcon.alt='';const verb=document.createElement('span');verb.textContent=action.label;hint.append(toolIcon,verb);if(action.key){const keycap=document.createElement('kbd');keycap.textContent=action.key;hint.append(keycap);}panel.append(hint);}
 updateHealthDisplay(tile,remaining,ready,dead);positionHealth(panel,game,point);scheduleHealthClock();
}
function healthRemaining(tile:Tile){const elapsed=(Date.now()-healthAnchorAt)/1000;return cropRemainingAfter(tile,healthBaseRemaining,elapsed);}
function healthProgress(tile:Tile){if(tile.state==='READY')return 1;if(tile.state==='DEAD')return 0;const crop=catalog.crops.find(item=>item.id===tile.cropId),duration=crop?(tile.season===1?crop.growthSeconds:crop.regrowthSeconds):0;if(tile.state==='GROWING'&&healthRemaining(tile)<=0)return 1;if(duration<=0)return 0;const elapsed=(Date.now()-healthAnchorAt)/1000,nominalRemaining=cropNominalRemainingAfter(tile,healthBaseNominal,elapsed);return Math.max(0,Math.min(1,1-nominalRemaining/duration));}
function updateHealthDisplay(tile:Tile,remaining=healthRemaining(tile),ready=tile.state==='READY'||tile.state==='GROWING'&&remaining<=0,dead=tile.state==='DEAD'){
 const panel=element('plant-health'),progress=healthProgress(tile),value=panel.querySelector<HTMLElement>('.crop-growth-value'),track=panel.querySelector<HTMLElement>('.crop-growth-track'),fill=panel.querySelector<HTMLElement>('.crop-growth-fill'),estimate=panel.querySelector<HTMLElement>('.crop-growth-estimate'),badge=panel.querySelector<HTMLElement>('.crop-state-badge'),nominal=panel.querySelector<HTMLElement>('.crop-growth-nominal'),condition=panel.querySelector<HTMLElement>('.crop-growth-condition');
 if(!value||!track||!fill||!estimate||!badge||!nominal||!condition)return;
 const unwatered=tile.state==='GROWING'&&!tile.watered;
 value.textContent=dead?'Planta morta':ready?'Pronta para colher':tile.paused?'Crescimento pausado':unwatered?'Regue para começar':formatRemaining(remaining);
 track.setAttribute('aria-valuenow',String(Math.round(progress*100)));fill.style.width=progress*100+'%';track.classList.toggle('is-ready',ready);track.classList.toggle('is-dead',dead);
 const elapsed=(Date.now()-healthAnchorAt)/1000;
 const nominalRemaining=cropNominalRemainingAfter(tile,healthBaseNominal,elapsed),serverAt=Date.parse(display()?.serverTime??'');
 const timing=cropTimingText({...tile,state:ready?'READY':tile.state,remainingSeconds:remaining,nominalRemainingSeconds:nominalRemaining},serverAt+tile.remainingSeconds*1000,serverAt+(elapsed+nominalRemaining)*1000);
 nominal.textContent=timing.nominal;nominal.hidden=!timing.nominal;condition.textContent=timing.condition;condition.hidden=!timing.condition;estimate.textContent=timing.estimate;estimate.hidden=!timing.estimate;
 badge.textContent=dead?'Morta':ready?'Pronta':tile.paused?'Parada':tile.dry?'Seca':'Saudável';badge.classList.toggle('is-ready',ready);badge.classList.toggle('is-dead',dead);badge.classList.toggle('is-problem',!ready&&!dead&&(tile.paused||tile.dry));
}
function formatRemaining(seconds:number){const total=Math.max(0,Math.ceil(seconds));if(total<60)return `${total}s`;const minutes=Math.ceil(total/60),hours=Math.floor(minutes/60),days=Math.floor(hours/24);if(days)return `${days}d ${hours%24}h`;return hours?`${hours}h ${minutes%60}min`:`${minutes}min`;}
function addChip(parent:HTMLElement,label:string,iconPath:string,kind:'problem'|'benefit'){const chip=document.createElement('span');chip.className=`crop-status-chip is-${kind}`;const icon=document.createElement('img');icon.src=iconPath;icon.alt='';chip.append(icon,document.createTextNode(label));parent.append(chip);}
function healthAction(tile:Tile,ready:boolean,dead:boolean){if(visited){if(tool==='CURSOR')return undefined;if(tool==='SPREAD_INSECTS'||tool==='SPREAD_WEEDS'){if(ready||dead||!tile.watered||tile.protected||(tool==='SPREAD_INSECTS'?tile.insects>0:tile.weeds))return undefined;return tool==='SPREAD_INSECTS'?{label:'Lançar inseto nesta planta · 1/24h',icon:'/assets/classic/effects/verme_1.png'}:{label:'Lançar erva nesta planta · 1/24h',icon:'/assets/classic/effects/erva_daninha_1.png'};}return ready?{label:'Furtar colheita',icon:'/assets/classic/icons/hud/mao-restaurada.png'}:undefined;}if(dead)return{label:'Remover com a pá',icon:'/assets/classic/icons/hud/pa-ui.png',key:'2'};if(tile.weeds)return{label:'Tirar ervas',icon:'/assets/classic/icons/hud/herbicida-ui.png',key:'5'};if(tile.insects>0)return{label:'Matar insetos',icon:'/assets/classic/icons/hud/pesticida-ui.png',key:'6'};if(tile.dry&&tile.state==='GROWING')return{label:'Regar',icon:'/assets/classic/icons/hud/regador-ui.png',key:'4'};if(ready)return{label:'Colher com a luvinha',icon:'/assets/classic/icons/hud/mao-restaurada.png',key:'7'};return undefined;}
function positionHealth(panel:HTMLElement,game:HTMLElement,point:{x:number;y:number}){const bounds=game.getBoundingClientRect(),canvas=element('farm').getBoundingClientRect(),projected=worldToScreen(point,sceneTransform(canvas.width,canvas.height)),x=canvas.left-bounds.left+projected.x,y=canvas.top-bounds.top+projected.y,width=Math.max(0,Math.min(360,bounds.width-24)),margin=12,tileTop=54,tileSide=54;panel.style.width=`${width}px`;const height=panel.getBoundingClientRect().height;let left=x-width/2,top=y-height-tileTop-12,placement='above';if(top<margin){if(y+tileTop+height+12<=bounds.height-margin){top=y+tileTop+12;placement='below';}else if(x+tileSide+width+12<=bounds.width-margin){left=x+tileSide+12;top=Math.max(margin,Math.min(bounds.height-height-margin,y-height/2));placement='right';}else if(x-tileSide-width-12>=margin){left=x-tileSide-width-12;top=Math.max(margin,Math.min(bounds.height-height-margin,y-height/2));placement='left';}else{top=Math.max(margin,Math.min(bounds.height-height-margin,y-height-tileTop-12));}}if(placement==='above'||placement==='below')left=Math.max(margin,Math.min(bounds.width-width-margin,left));panel.style.left=`${left}px`;panel.style.top=`${top}px`;panel.dataset.placement=placement;panel.style.setProperty('--caret-x',`${Math.max(16,Math.min(width-16,x-left))}px`);panel.style.setProperty('--caret-y',`${Math.max(16,Math.min(height-16,y-top))}px`);}
function scheduleHealthClock(){window.clearTimeout(healthClockTimer);const tile=healthTile,panel=element('plant-health');if(panel.hidden||!tile||tile.state!=='GROWING'||!tile.watered||tile.paused||healthRemaining(tile)<=0)return;const remaining=healthRemaining(tile),delay=remaining<60?1000:Math.min(60_000,Math.max(1000,(remaining-59)*1000));healthClockTimer=window.setTimeout(()=>{if(!healthTile||!healthPoint)return;const current=healthTile,point=healthPoint,left=healthRemaining(current);updateHealthDisplay(current,left);if(left<=0)renderHealth(current,point,true);else scheduleHealthClock();},delay);}
function tileList(){dialog('Terras da fazenda');const states:Record<string,string>={GRASS:'grama',PLOWED:'arada',DEAD:'planta morta',READY:'pronta',GROWING:'crescendo'};const grid=document.createElement('div');grid.className='tile-list';content.append(grid);for(const tile of display()?.tiles??[]){const crop=catalog.crops.find(c=>c.id===tile.cropId);const b=button(`Terra ${tile.column+1}, ${tile.row+1}: ${!tile.unlocked?'bloqueada':crop?.name??states[tile.state]}`,async()=>{modal.close();await tileAction(tile);},grid);b.title='Aplicar a ferramenta selecionada';}}
function card(name:string,image?:string){const c=document.createElement('article');c.className='card';if(image){const img=document.createElement('img');img.loading='lazy';img.src='/assets/'+image;img.alt='';c.append(img);}const title=document.createElement('strong');title.textContent=name;c.append(title);return c;}
function marketCard(name:string,imagePath:string,details:string,level:number,prices:Record<string,number>,kind:string,id:string,vip=false){
 const c=document.createElement('article');c.className='card market-card';const title=document.createElement('h3');title.textContent=name;const image=document.createElement('img');image.className='market-art';image.loading='lazy';image.src=`/assets/${imagePath}`;image.alt='';const description=paragraph(details,c);description.className='market-details';const levelLabel=document.createElement('small');levelLabel.textContent=`Nível ${level}${vip?' · VIP':''}`;
 const locked=(own?.level??0)<level||vip&&!own?.vipActive;if(locked)c.classList.add('locked');c.prepend(title);c.insertBefore(image,description);c.append(levelLabel);
 if(locked){const badge=document.createElement('span');badge.className='lock-badge';badge.textContent=(own?.level??0)<level?`Nível ${level}`:'Exige VIP';c.append(badge);}
 for(const [currency,price]of Object.entries(prices)){const label=currency==='GOLD'?'ouro':'verdes',buy=button(`Comprar · ${price}`,()=>action({action:kind,itemId:id,currency}),c,`buy:${kind}:${id}:${currency}`);buy.className='market-buy';buy.disabled=locked;buy.setAttribute('aria-label',`Comprar ${name} por ${price} ${label}`);buy.title=locked?((own?.level??0)<level?`Nível ${level}`:'Exige VIP'):`${price} ${label}`;const coin=document.createElement('img');coin.className='price-coin';coin.src=`/assets/classic/icons/moeda-${currency==='GOLD'?'comum':'verde'}.png`;coin.alt='';buy.append(coin);}
 return c;
}
type FeedId='producer'|'growth';
const feedDetails:{id:FeedId;name:string;badge:string;description:string;accent:string}[]=[
 {id:'producer',name:'Ração de produção',badge:'🥚',description:'Mantém a produção ativa por até 8 horas.',accent:'production'},
 {id:'growth',name:'Ração de crescimento',badge:'🌱',description:'Mantém o crescimento ativo por até 12 horas.',accent:'growth'},
];
function feedIcon(id:FeedId,size:'small'|'large'){
 const meta=feedDetails.find(item=>item.id===id)!;const icon=document.createElement('span');icon.className=`feed-icon feed-icon-${size} feed-icon-${meta.accent}`;icon.setAttribute('aria-hidden','true');const image=document.createElement('img');image.src='/assets/classic/icons/racao-generica.png';image.alt='';icon.append(image);const badge=text('span',meta.badge,'feed-icon-badge');icon.append(badge);return icon;
}
async function buyFeed(id:FeedId,quantity:number){
 const before=own?.feed?.[id]??0;
 for(let i=0;i<quantity;i++){const current=own?.feed?.[id]??before;await action({action:'BUY_FEED',itemId:id});if((own?.feed?.[id]??current)<=current)break;}
 const purchased=(own?.feed?.[id]??before)-before;if(purchased<=0)return;
 const meta=feedDetails.find(item=>item.id===id)!;notify(`+${purchased} ${meta.name}`);content.querySelector(`[data-feed-stock="${id}"]`)?.classList.add('feed-stock-pulse');
}
function shop(category='crops',focusKey?:string){
 dialog('Loja');const tabs=document.createElement('div');tabs.className='tabs shop-tabs';tabs.setAttribute('role','tablist');tabs.setAttribute('aria-label','Seções da Loja');tabs.setAttribute('aria-orientation','horizontal');content.append(tabs);
 refreshDialog=()=>shop(category);
 const tabItems=[['crops','Sementes'],['animals','Animais'],['fertilizers','Fertilizantes'],['vip','VIP'],['owned','Meus animais']] as const;
 for(const [id,label]of tabItems){const tab=button(label,()=>shop(id,`shop-tab:${id}`),tabs,`shop-tab:${id}`);tab.className=`shop-tab${id==='owned'?' owned-animals-tab':''}`;tab.id=`shop-tab-${id}`;tab.setAttribute('role','tab');tab.setAttribute('aria-selected',String(category===id));tab.setAttribute('aria-controls','shop-panel');tab.tabIndex=category===id?0:-1;if(id==='owned'){const icon=document.createElement('img');icon.src='/assets/classic/icons/icone-minha-fazenda.png';icon.alt='';icon.setAttribute('aria-hidden','true');tab.prepend(icon);}}
 enableTabNavigation(tabs);
 const panel=document.createElement('div');panel.className='shop-panel';panel.id='shop-panel';panel.setAttribute('role','tabpanel');panel.setAttribute('aria-labelledby',`shop-tab-${category}`);panel.tabIndex=0;content.append(panel);
 const scroll=document.createElement('div');scroll.className='market-scroll';panel.append(scroll);
 if(category==='crops'){
  const groups=document.createElement('div');groups.className='tabs seed-tabs';groups.setAttribute('role','tablist');groups.setAttribute('aria-label','Tipo de semente');groups.setAttribute('aria-orientation','horizontal');panel.insertBefore(groups,scroll);
  for(const [id,label]of [['common','Comuns'],['vip','VIP']] as const){const tab=button(label,()=>{seedMarketGroup=id;shop(category,`seed-group:${id}`);},groups,`seed-group:${id}`);tab.className='shop-tab';tab.id=`seed-tab-${id}`;tab.setAttribute('role','tab');tab.setAttribute('aria-selected',String(seedMarketGroup===id));tab.setAttribute('aria-controls','seed-panel');tab.tabIndex=seedMarketGroup===id?0:-1;}
  enableTabNavigation(groups);
  const seedPanel=document.createElement('div');seedPanel.className='seed-panel';seedPanel.id='seed-panel';seedPanel.setAttribute('role','tabpanel');seedPanel.setAttribute('aria-labelledby',`seed-tab-${seedMarketGroup}`);seedPanel.tabIndex=0;panel.replaceChild(seedPanel,scroll);seedPanel.append(text('p',CROP_TIME_NOTICE,'muted crop-time-notice'),scroll);
  const cards=document.createElement('div');cards.className='market-cards';scroll.append(cards);
  for(const crop of cropGroups(catalog.crops)[seedMarketGroup])cards.append(marketCard(crop.name,`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`,seedGrowthLabel(crop),crop.requiredLevel,crop.seedPrices,'BUY_SEED',crop.id,crop.vipOnly));
 }
 if(category==='animals'){
  const feedMarket=document.createElement('section');feedMarket.className='animal-feed-market';feedMarket.append(text('h3','Rações'));
  for(const meta of feedDetails){let quantity=1;const price=catalog.feedPrices[meta.id],unitPrice=Number(price??0),hasEligibleAnimal=(own?.animals??[]).some(animal=>{const kind=catalog.animals.find(item=>item.id===animal.id)?.kind;return kind==='PROTECTOR'||(meta.id==='producer'?kind==='PRODUCER':kind==='GROWING');});
   const option=document.createElement('article');option.className=`animal-feed-option feed-${meta.accent}`;const main=document.createElement('div');main.className='animal-feed-main';main.append(feedIcon(meta.id,'large'));const description=document.createElement('div');description.className='animal-feed-description';description.append(text('strong',meta.name),text('span',meta.description));main.append(description);const stock=text('span',`×${own?.feed?.[meta.id]??0}`,'deposit-quantity animal-feed-stock');stock.dataset.feedStock=meta.id;stock.setAttribute('role','status');stock.setAttribute('aria-live','polite');stock.setAttribute('aria-label',`${own?.feed?.[meta.id]??0} unidades de ${meta.name.toLocaleLowerCase('pt-BR')}`);main.append(stock);option.append(main);
   const controls=document.createElement('div');controls.className='animal-feed-controls';const selector=document.createElement('div');selector.className='animal-feed-quantity';selector.setAttribute('role','group');selector.setAttribute('aria-label',`Quantidade de ${meta.name.toLocaleLowerCase('pt-BR')}`);const count=document.createElement('output');count.className='animal-feed-quantity-value';count.value='1';count.setAttribute('aria-live','polite');const minus=button('−',()=>{quantity=Math.max(1,quantity-1);update();},selector,'feed-minus');minus.className='animal-feed-stepper';minus.setAttribute('aria-label',`Diminuir quantidade de ${meta.name.toLocaleLowerCase('pt-BR')}`);selector.append(minus,count);const plus=button('+',()=>{quantity=Math.min(99,quantity+1);update();},selector,'feed-plus');plus.className='animal-feed-stepper';plus.setAttribute('aria-label',`Aumentar quantidade de ${meta.name.toLocaleLowerCase('pt-BR')}`);controls.append(selector);
   const buy=button('',()=>void buyFeed(meta.id,quantity),controls,`buy-feed:${meta.id}`);buy.className='animal-feed-buy';buy.setAttribute('aria-label',`Comprar ${quantity} ${meta.name.toLocaleLowerCase('pt-BR')} por ${depositNumber.format(unitPrice*quantity)} ouro`);const coin=document.createElement('img');coin.src='/assets/classic/icons/moeda-comum.png';coin.alt='';coin.setAttribute('aria-hidden','true');
   function update(){const total=unitPrice*quantity,hasGold=unitPrice>0&&Number(own?.coins??0)>=total;count.value=String(quantity);minus.disabled=quantity<=1;plus.disabled=quantity>=99;buy.disabled=!hasEligibleAnimal||!hasGold;buy.replaceChildren();if(!hasEligibleAnimal)buy.append(document.createTextNode('Requer animal compatível'));else if(!hasGold)buy.append(document.createTextNode('Ouro insuficiente'));else buy.append(document.createTextNode(`Comprar · ${depositNumber.format(total)} `),coin);buy.setAttribute('aria-label',!hasEligibleAnimal?`Comprar ${meta.name.toLocaleLowerCase('pt-BR')}: requer animal compatível`:!hasGold?'Ouro insuficiente':`Comprar ${quantity} ${meta.name.toLocaleLowerCase('pt-BR')} por ${depositNumber.format(total)} ouro`);}
   update();option.append(controls);feedMarket.append(option);
  }
  scroll.append(feedMarket);
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
  const producerFeed=own?.feed?.producer??0,growthFeed=own?.feed?.growth??0,feedCount=producerFeed+growthFeed,feedStatus=document.createElement('section');feedStatus.className='owned-feed-summary';feedStatus.append(text('strong',`Ração: ${feedCount}`));for(const meta of feedDetails){const count=own?.feed?.[meta.id]??0,entry=document.createElement('span');entry.className='owned-feed-count';entry.append(feedIcon(meta.id,'small'),text('span',`${meta.id==='producer'?'Produção':'Crescimento'} ×${count}`));feedStatus.append(entry);}const buyFeed=button('Comprar ração',()=>shop('animals','buy-feed:producer'),feedStatus,'owned-buy-feed');buyFeed.className='owned-feed-link';scroll.append(feedStatus);
  const animals=own?.animals??[];
  if(!animals.length){const empty=document.createElement('div');empty.className='owned-empty';const art=document.createElement('img');art.src=`/assets/${animalPath('galinha','ADULT',false,false,false)}`;art.alt='';art.setAttribute('aria-hidden','true');art.loading='lazy';const title=text('h3','Você ainda não tem animais');const products=catalog.animals.filter(type=>type.kind==='PRODUCER').slice(0,4).map(type=>type.productName.toLocaleLowerCase('pt-BR'));const productText=products.length?`Eles produzem ${products.join(', ')}${catalog.animals.filter(type=>type.kind==='PRODUCER').length>products.length?', entre outros produtos':''}.`:'Eles produzem itens para sua fazenda.';empty.append(art,title,text('p',productText));const viewAnimals=button('Ver animais à venda',()=>shop('animals','shop-tab:animals'),empty,'owned-empty-shop');viewAnimals.className='owned-empty-shop';scroll.append(empty);}
  else{const cards=document.createElement('div');cards.className='owned-animal-list';scroll.append(cards);const stages:Record<string,string>={BABY:'Filhote',YOUNG:'Jovem',ADULT:'Adulto'};
   for(const animal of animals){const type=catalog.animals.find(item=>item.id===animal.id);if(!type)continue;const cardNode=document.createElement('article');cardNode.className='owned-animal-card';const art=document.createElement('img');art.className='owned-animal-art';art.src=`/assets/${animalPath(animal.id,animal.stage,false,false,false)}`;art.alt='';art.loading='lazy';const details=document.createElement('div');details.className='owned-animal-details';const heading=document.createElement('div');heading.className='owned-animal-heading';heading.append(text('strong',type.name),text('span',stages[animal.stage]??animal.stage,'owned-animal-stage'));const statusLabel=animal.status==='HUNGRY'?'Com fome':animal.status==='READY'?(type.kind==='GROWING'?'Adulto pronto para venda':'Pronto para coletar'):type.kind==='PROTECTOR'?'Protegendo':type.kind==='GROWING'?'Crescendo':'Produzindo';const status=text('span',statusLabel,`animal-state animal-state-${animal.status.toLowerCase()}`);heading.append(status);details.append(heading);
    const duration=type.kind==='PRODUCER'?(type.productionSeconds??0):(type.babySeconds??0)+(type.youngSeconds??0);
    if(duration>0){const remaining=Math.max(0,animal.remainingSeconds),progress=remaining===0?100:Math.round(Math.max(0,Math.min(100,(1-remaining/duration)*100))),progressLabel=type.kind==='GROWING'?'Crescimento':'Produção',progressCaption=document.createElement('div');progressCaption.className='crop-progress-caption';progressCaption.append(text('span',progressLabel),text('strong',remaining>0?`${durationLabel(remaining)} restantes`:'Pronto'));const track=document.createElement('div');track.className=`crop-growth-track${animal.status==='READY'?' is-ready':''}`;track.setAttribute('role','progressbar');track.setAttribute('aria-valuenow',String(progress));track.setAttribute('aria-valuemin','0');track.setAttribute('aria-valuemax','100');track.setAttribute('aria-label',`${progressLabel}: ${progress}%, ${remaining>0?`${durationLabel(remaining)} restantes`:'pronto'}`);const fill=document.createElement('span');fill.className='crop-growth-fill';fill.style.width=`${progress}%`;track.append(fill);details.append(progressCaption,track);}
    cardNode.append(art,details);const actions=document.createElement('div');actions.className='owned-animal-actions';const label=animal.status==='HUNGRY'?'Alimentar':animal.status==='READY'&&type.kind==='PRODUCER'?'Coletar':animal.status==='READY'&&type.kind==='GROWING'&&animal.stage==='ADULT'?'Vender adulto':undefined;
    if(label){const feedAvailable=type.kind==='PRODUCER'?producerFeed:type.kind==='PROTECTOR'?feedCount:growthFeed,care=button(label,()=>action({action:'TEND_ANIMAL',itemId:animal.id}),actions,`tend:${animal.id}`);if(label==='Alimentar'&&feedAvailable===0){care.disabled=true;care.setAttribute('aria-disabled','true');care.title='Sem ração. Compre na Loja, aba Animais.';actions.append(text('small','Sem ração para alimentar.','owned-feed-warning'));}}
    cardNode.append(actions);cards.append(cardNode);
   }
  }
 }
 if(focusKey){const target=[...content.querySelectorAll<HTMLButtonElement>('[data-focus-key]')].find(item=>item.dataset.focusKey===focusKey);target?.focus({preventScroll:true});}
}
function animalPopup(id:string){
 const farm=display(),animal=farm?.animals.find(item=>item.id===id);if(!animal)return;
 const type=catalog.animals.find(item=>item.id===id);if(!type)return;
 const stage:Record<string,string>={BABY:'Filhote',YOUNG:'Jovem',ADULT:'Adulto'};
 dialog(type.name);
 refreshDialog=()=>animalPopup(id);
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
 refreshDialog=deposit;
 const lots=own?.produceLots??[],totals=own?.depositTotals??{},vip=!!own?.vipActive,multiplier=vip?1.25:1.15;
 const quote=(source:typeof lots)=>{
  const available=source.filter(lot=>!((lot.vipOnly??catalog.crops.find(crop=>crop.id===lot.itemId)?.vipOnly)&&!vip));
  const currencies=new Map<string,number>();for(const lot of available)currencies.set(lot.currency,(currencies.get(lot.currency)??0)+Number(lot.unitCost??0)*multiplier*lot.quantity);
  return{available,quantity:available.reduce((sum,lot)=>sum+lot.quantity,0),currencies};
 };
 const currencyText=(entries:Map<string,number>)=>[...entries].filter(([,amount])=>amount>0).map(([currency,amount])=>`${depositNumber.format(Math.ceil(amount*10)/10)} ${currency==='GOLD'?'ouro':'verdes'}`).join(' e ');
 const summary=document.createElement('section');summary.className='deposit-summary-line';
 const gold=document.createElement('div');gold.className='deposit-total';const goldImage=document.createElement('img');goldImage.src='/assets/classic/icons/moeda-comum.png';goldImage.alt='';const goldAmount=document.createElement('strong');goldAmount.textContent=depositNumber.format(Number(totals.GOLD??0));gold.append(text('span','Total:'),goldAmount,goldImage);summary.append(gold);
 const greenAmount=Number(totals.GREEN??0);if(greenAmount>0){const green=document.createElement('div');green.className='deposit-green';const image=document.createElement('img');image.src='/assets/classic/icons/moeda-verde.png';image.alt='';green.append(text('span',depositNumber.format(greenAmount)),image);summary.append(green);}
 const sellEverything=quote(lots),sellAll=button('Vender tudo',()=>{
  const confirmation=document.createElement('div');confirmation.className='deposit-confirmation';confirmation.setAttribute('role','group');confirmation.setAttribute('aria-label','Confirmar venda total');confirmation.append(text('span',`Vender ${sellEverything.quantity} itens por ${currencyText(sellEverything.currencies)||'0 ouro'}?`));
  const confirm=button('Confirmar venda',()=>action({action:'SELL_ALL'}),confirmation,'confirm-sell-all');confirm.className='deposit-confirm';const cancel=button('Cancelar',()=>deposit(),confirmation,'cancel-sell-all');cancel.className='deposit-cancel';sellAll.replaceWith(confirmation);
 },summary,'sell-all');sellAll.className='deposit-sell-all';sellAll.disabled=sellEverything.quantity===0;sellAll.setAttribute('aria-label',`Vender tudo, ${sellEverything.quantity} itens por ${currencyText(sellEverything.currencies)||'0 ouro'}`);summary.append(sellAll);content.append(summary);

 const products=[...new Set(lots.map(lot=>lot.itemId))].map(id=>{
  const itemLots=lots.filter(lot=>lot.itemId===id),crop=catalog.crops.find(candidate=>candidate.id===id),animal=catalog.animals.find(candidate=>candidate.productId===id),itemQuote=quote(itemLots);
  const icon=crop?`classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`:animal?animal.id==='galinha'||animal.id==='pato'?'classic/animals/ninho-geral/ninho-com-ovos.png':animal.id==='ovelha'?'classic/animals/ovelha/la-ovelha.png':animal.id==='vaca'?'classic/animals/vaca/balde-vaca.png':animalPath(animal.id,'ADULT',false,false,false):'classic/icons/deposit-icon.png';
  return{id,itemLots,crop,animal,name:crop?.name??animal?.productName??id,icon,count:itemLots.reduce((sum,lot)=>sum+lot.quantity,0),...itemQuote};
 }).sort((a,b)=>[...b.currencies.values()].reduce((sum,value)=>sum+value,0)-[...a.currencies.values()].reduce((sum,value)=>sum+value,0));

 const section=document.createElement('section');section.className='deposit-group';const heading=document.createElement('h3');heading.textContent='Colheitas';section.append(heading);
 if(!products.length){const empty=document.createElement('p');empty.className='deposit-harvest-empty';empty.textContent='Nada colhido ainda. Suas colheitas aparecem aqui. 🌾';section.append(empty);}
 for(const item of products){
  const row=document.createElement('article');row.className='deposit-item';row.dataset.productId=item.id;const artwork=document.createElement('div');artwork.className='deposit-artwork';const image=document.createElement('img');image.src=`/assets/${item.icon}`;image.alt='';artwork.append(image);const badge=text('span',`×${item.count}`,'deposit-quantity');badge.setAttribute('aria-label',`${item.count} unidades`);artwork.append(badge);row.append(artwork);
  const details=document.createElement('div');details.className='deposit-item-details';details.append(text('strong',item.name));
  const price=document.createElement('div');price.className='deposit-price';
  const priceGroups=new Map<string,{currency:string;unitCost:number;quantity:number}>();for(const lot of item.itemLots){const eligible=!((lot.vipOnly??item.crop?.vipOnly)&&!vip);if(!eligible)continue;const key=`${lot.currency}:${lot.unitCost??0}`;const group=priceGroups.get(key)??{currency:lot.currency,unitCost:Number(lot.unitCost??0),quantity:0};group.quantity+=lot.quantity;priceGroups.set(key,group);}
  if(priceGroups.size){for(const group of priceGroups.values()){const unit=group.unitCost*multiplier,subtotal=unit*group.quantity;const line=document.createElement('span');line.append(document.createTextNode(`${group.quantity} × ${depositPriceNumber.format(unit)} = ${depositPriceNumber.format(subtotal)} `));const coin=document.createElement('img');coin.src=`/assets/classic/icons/moeda-${group.currency==='GOLD'?'comum':'verde'}.png`;coin.alt='';line.append(coin);price.append(line);}}
  details.append(price);
  if(item.count>item.quantity){const locked=text('small','🔒 Venda exige VIP','deposit-vip-note');details.append(locked);}
  row.append(details);const actions=document.createElement('div');actions.className='deposit-item-actions';
  const locked=item.quantity===0,mixedVipLots=item.count>item.quantity,sellQuantity=locked?item.count:item.quantity,singleLot=item.available[0],singlePrice=singleLot?currencyText(new Map([[singleLot.currency,Number(singleLot.unitCost??0)*multiplier]])):'0 ouro',single=button(sellQuantity===1?'Vender':'Vender 1',()=>action({action:'SELL_ITEM',itemId:item.id}),actions,`sell-one:${item.id}`);single.disabled=locked;single.setAttribute('aria-disabled',String(locked));single.title=locked?'Venda exige VIP':`Vender 1 ${item.name} por ${singlePrice}`;single.setAttribute('aria-label',locked?`Vender 1 ${item.name}; venda exige VIP`:`Vender 1 ${item.name} por ${singlePrice}`);
  if(sellQuantity>1){const allPrice=currencyText(item.currencies),all=button(`Vender ${sellQuantity}`,()=>action({action:'SELL_ITEM_ALL',itemId:item.id}),actions,`sell-product:${item.id}`);all.disabled=locked||mixedVipLots;all.setAttribute('aria-disabled',String(all.disabled));all.title=locked||mixedVipLots?'Venda de todos os lotes exige VIP':`Vender ${sellQuantity} ${item.name} por ${allPrice}`;all.setAttribute('aria-label',locked||mixedVipLots?`Vender ${sellQuantity} ${item.name}; venda de todos os lotes exige VIP`:`Vender ${sellQuantity} ${item.name} por ${allPrice}`);}
  if(locked)row.classList.add('is-vip-locked');if(locked||mixedVipLots){for(const control of actions.querySelectorAll('button'))if(control.disabled)control.setAttribute('aria-describedby',`vip-note-${item.id}`);const note=details.querySelector('.deposit-vip-note');if(note)note.id=`vip-note-${item.id}`;}
  row.append(actions);section.append(row);
 }
 content.append(section);
 const feedSection=document.createElement('section');feedSection.className='deposit-feed';feedSection.append(text('h3','Ração armazenada'));const feed=Object.entries(own?.feed??{}).filter(([,count])=>count>0);
 if(feed.length){for(const [id,count]of feed){const row=document.createElement('article');row.className='deposit-item deposit-feed-row';const art=document.createElement('div');art.className='deposit-artwork';const image=document.createElement('img');image.src='/assets/classic/icons/racao-generica.png';image.alt='';art.append(image,text('span',`×${count}`,'deposit-quantity'));row.append(art);const details=document.createElement('div');details.className='deposit-item-details';details.append(text('strong',id==='producer'?'Ração de produção':'Ração de crescimento'),text('span',`${count} ${count===1?'unidade armazenada':'unidades armazenadas'}`));row.append(details);feedSection.append(row);}}
 else{const empty=document.createElement('p');empty.className='deposit-feed-empty';empty.append(document.createTextNode('Nenhuma ração armazenada · '));const store=button('Ver na Loja ›',()=>shop(),empty,'deposit-feed-shop');store.className='deposit-store-link';feedSection.append(empty);}
 content.append(feedSection);
}
function missions(){
 dialog('Missões');
 refreshDialog=missions;
 renderDailyAllowance(content,own?.dailyAllowance,()=>void action({action:'CLAIM_DAILY_ALLOWANCE'}));
 const daily=own?.missions??[],active=daily.filter(mission=>!mission.done),completed=daily.filter(mission=>mission.done),hasTutorial=!!own?.tutorial,activeCount=active.length+(hasTutorial?1:0);
 const activeSection=document.createElement('section');activeSection.className='mission-section';activeSection.append(sectionHeading('Em andamento'));
 if(own?.tutorial){const total=Math.max(1,own.tutorialTotal??6),progress=Math.min(own.tutorialStep??0,total);activeSection.append(missionCard('Tutorial',own.tutorial.title??'',own.tutorial.hint??'',progress,total));}
 for(const mission of active)activeSection.append(missionCard('Diária',mission.title,missionDescription(mission.id),mission.progress,mission.target,mission.green));
 if(activeCount)content.append(activeSection);
 else{const empty=document.createElement('div');empty.className='missions-empty';empty.append(text('strong','Tudo feito por hoje! 🌾'),countdownNode(true));content.append(empty);}
 const completedSection=document.createElement('section');completedSection.className='mission-section';completedSection.append(sectionHeading(`Concluídas hoje (${completed.length})`));
 for(const mission of completed)completedSection.append(completedMission(mission.title,mission.green));content.append(completedSection);
 const footerTimer=countdownNode();
 const timerWrap=document.createElement('div');timerWrap.className='missions-footer-timer';timerWrap.append(footerTimer);
 const vip=document.createElement('div');vip.className='missions-footer-vip';const vipIcon=document.createElement('img');vipIcon.className='missions-vip-icon';vipIcon.src='/assets/classic/icons/cartao-vip.png';vipIcon.alt='';vip.append(vipIcon);if(own?.vipActive){vip.append(text('strong','VIP ativo'));const remaining=remainingVip(own.vipExpiresAt);if(remaining)vip.append(text('span',` · ${remaining}`));}else{vip.append(text('span','Desbloqueie culturas VIP e venda produtos por +25%.'));const activate=button('Ativar VIP',()=>shop('vip'),vip);activate.className='missions-vip-button';}
 modalFooter.append(timerWrap,vip);modalFooter.hidden=false;updateMissionCountdown();if(missionsTimer===undefined)missionsTimer=window.setInterval(updateMissionCountdown,60_000);
}
function sectionHeading(label:string){const heading=document.createElement('h3');heading.className='mission-section-title';heading.textContent=label;return heading;}
function missionDescription(id:string){return id==='care'?'Plante, regue, colha ou cuide dos animais.':id==='trade'?'Compre ou venda itens na fazenda.':'Avance esta tarefa para receber a recompensa.';}
function countdownText(showLocalTime=true){const now=new Date(),midnight=new Date(Date.UTC(now.getUTCFullYear(),now.getUTCMonth(),now.getUTCDate()+1)),minutes=Math.max(0,Math.ceil((midnight.getTime()-now.getTime())/60_000)),hours=Math.floor(minutes/60),parts=new Intl.DateTimeFormat('pt-BR',{hour:'2-digit',hourCycle:'h23'}).formatToParts(midnight),hour=parts.find(part=>part.type==='hour')?.value??'00';return `Novas missões em ${hours?`${hours}h `:''}${minutes%60}min${showLocalTime?` (às ${hour}h)`:''}`;}
function countdownNode(compact=false){const timer=text('span','','missions-countdown'),clock=document.createElement('span');clock.className='missions-clock-icon';clock.setAttribute('aria-hidden','true');clock.textContent='◷';timer.dataset.compact=String(compact);timer.append(clock,text('span',countdownText(!compact)));timer.setAttribute('aria-live','off');return timer;}
function updateMissionCountdown(){for(const timer of modal.querySelectorAll<HTMLElement>('.missions-countdown')){const value=timer.lastElementChild;if(value)value.textContent=countdownText(timer.dataset.compact!=='true');}}
function remainingVip(expiresAt?:string){const expiry=Date.parse(expiresAt??'');if(!Number.isFinite(expiry))return '';const minutes=Math.max(0,Math.ceil((expiry-Date.now())/60_000)),days=Math.floor(minutes/1440),hours=Math.floor(minutes%1440/60);return days?`${days}d ${hours}h restantes`:hours?`${hours}h restantes`:`${minutes%60}min restantes`;}
function missionCard(category:string,titleText:string,description:string,value:number,max:number,reward?:number){const card=document.createElement('article');card.className='mission-card';const heading=document.createElement('div');heading.className='mission-heading';heading.append(text('span',category,'mission-category'),text('strong',titleText));if(reward&&reward>0)heading.append(missionReward(reward));card.append(heading);const detail=text('p','','mission-description');appendKeycaps(detail,description);card.append(detail);const progressRow=document.createElement('div');progressRow.className='mission-progress-row';const progress=document.createElement('progress');progress.max=Math.max(1,max);progress.value=Math.min(value,progress.max);progress.setAttribute('role','progressbar');progress.setAttribute('aria-valuenow',String(progress.value));progress.setAttribute('aria-valuemax',String(progress.max));progress.setAttribute('aria-label',`${titleText}: ${progress.value} de ${progress.max}`);progressRow.append(progress,text('span',`${progress.value} / ${progress.max}`));card.append(progressRow);return card;}
function completedMission(titleText:string,reward:number){const row=document.createElement('article');row.className='mission-card mission-card-completed';const title=document.createElement('strong');const check=text('span','✓','mission-check');check.setAttribute('aria-hidden','true');title.append(check,document.createTextNode(titleText));row.append(title);const amount=document.createElement('span');amount.className='mission-completed-reward';const coin=document.createElement('img');coin.src='/assets/classic/icons/moeda-verde.png';coin.alt='';amount.append(document.createTextNode(`+${reward} `),coin,document.createTextNode(' recebido'));row.append(amount);return row;}
function missionReward(value:number){const reward=document.createElement('span');reward.className='mission-reward';const coin=document.createElement('img');coin.src='/assets/classic/icons/moeda-verde.png';coin.alt='';reward.append(document.createTextNode(`+${value} `),coin);return reward;}
function appendKeycaps(parent:HTMLElement,value:string){for(const part of value.split(/\((\d)\)/g)){if(/^\d$/.test(part)){const key=document.createElement('kbd');key.textContent=part;parent.append(key);}else parent.append(document.createTextNode(part));}}
function invitationForm(parent:HTMLElement){
 const input=document.createElement('input');input.placeholder='Cole o código de convite';input.setAttribute('aria-label','Código de convite');input.value=sessionStorage.getItem('colheita:invitation')??'';parent.append(input);
 button('Aceitar convite',async()=>{await acceptInvitation(input.value.trim(),dialogGeneration,modal.open);},parent);
}
function setLoginStep(step:'login'|'invite',name=''){
 const loginStep=element('login-step'),inviteStep=element('invite-step'),target=step==='invite'?inviteStep:loginStep,current=step==='invite'?loginStep:inviteStep;
 if(step==='invite')element('invite-greeting').textContent=`Olá, ${name||'fazendeiro'}!`;
 window.clearTimeout(loginTransitionTimer);
 if(!target.hidden){current.hidden=true;current.classList.remove('is-exiting','is-entering');target.classList.remove('is-exiting','is-entering');return;}
 if(window.matchMedia('(prefers-reduced-motion: reduce)').matches){current.hidden=true;current.classList.remove('is-exiting');target.hidden=false;target.classList.remove('is-entering');return;}
 current.classList.add('is-exiting');target.hidden=false;target.classList.add('is-entering');window.requestAnimationFrame(()=>target.classList.remove('is-entering'));
 loginTransitionTimer=window.setTimeout(()=>{current.hidden=true;current.classList.remove('is-exiting');target.classList.remove('is-entering');},200);
}
function setupLoginInvitation(){
 const form=element<HTMLFormElement>('login-invite-form'),input=element<HTMLInputElement>('login-invite-code'),submit=element<HTMLButtonElement>('login-invite-submit'),label=element('login-invite-label'),error=element('login-invite-error');
 input.value=sessionStorage.getItem('colheita:invitation')??'';submit.disabled=!input.value.trim();input.oninput=()=>{submit.disabled=!input.value.trim()||submit.getAttribute('aria-busy')==='true';error.hidden=true;};
 form.onsubmit=async event=>{event.preventDefault();const code=input.value.trim();if(!code||submit.disabled)return;const generation=sessionGeneration;error.hidden=true;submit.disabled=true;submit.setAttribute('aria-busy','true');label.textContent='Validando…';
  const accepted=await acceptInvitation(code,0,false,()=>{error.hidden=false;});if(generation!==sessionGeneration)return;submit.removeAttribute('aria-busy');label.textContent='Aceitar convite';submit.disabled=!input.value.trim();if(!accepted)error.hidden=false;
 };
 element<HTMLButtonElement>('login-switch-account').onclick=()=>void logout();
}
async function acceptInvitation(code:string,panelGeneration:number,closeOnSuccess:boolean,onError?:()=>void):Promise<boolean>{
 if(!api){if(onError)onError();else notify('Entre antes de aceitar o convite.');return false;}
 const client=api,generation=sessionGeneration;
 try{await client.mutate('/v1/friend-invitations/accept',{code});if(generation!==sessionGeneration)return false;await refresh();if(own&&closeOnSuccess&&modal.open&&dialogGeneration===panelGeneration)modal.close();return true;}
 catch(error){if(generation!==sessionGeneration)return false;if(onError)onError();else handleError(error);return false;}finally{syncPending();}
}
async function friends(){
 if(!api)return;const client=api,generation=sessionGeneration;dialog('Amigos');const panelGeneration=dialogGeneration;
 const invites=document.createElement('section');invites.className='invite-card';const title=document.createElement('h3');title.textContent='Convidar ou adicionar amigo';invites.append(title);paragraph('Crie um link válido por sete dias ou aceite um convite recebido.',invites);button('Criar convite',async()=>{
  try{const reply=await client.mutate('/v1/friend-invitations',{});if(generation!==sessionGeneration)return;let result=invites.querySelector<HTMLElement>('.invite-result');if(!result){result=document.createElement('div');result.className='invite-result';invites.append(result);}result.replaceChildren();const code=document.createElement('strong');code.textContent=reply.code??'';code.id='invite-code';const url=new URL(location.origin);url.searchParams.set('invite',reply.code??'');const link=document.createElement('a');link.href=url.href;link.textContent='Abrir link de convite';const expiry=document.createElement('span');expiry.textContent='Expira em sete dias · uso único';result.append(code,link,expiry);}
  catch(error){handleError(error);}finally{syncPending();}
 },invites);invitationForm(invites);content.append(invites);
 const heading=document.createElement('h3');heading.className='friends-title';heading.textContent='Seus amigos';content.append(heading);const listPanel=document.createElement('div');listPanel.className='friend-list';content.append(listPanel);
 try{const list=await client.get<{id:string;name:string}[]>('/v1/friends');if(generation!==sessionGeneration||dialogGeneration!==panelGeneration||!modal.open)return;for(const friend of list){const row=document.createElement('article');row.className='friend-card';const badge=document.createElement('span');badge.className='friend-avatar';badge.textContent=friend.name.slice(0,1).toUpperCase();const name=document.createElement('strong');name.textContent=friend.name;const visit=button('Visitar fazenda',async()=>{const navigation=++navigationGeneration;try{const farm=await client.get<Farm>(`/v1/farms/${friend.id}/visit`);if(generation!==sessionGeneration||navigation!==navigationGeneration||dialogGeneration!==panelGeneration||!modal.open)return;visited=farm;tool='CURSOR';render();modal.close();}catch(error){handleError(error);}},row);row.prepend(badge,name);listPanel.append(row);}if(!list.length)paragraph('Nenhum amigo adicionado ainda.',listPanel);}
 catch(error){handleError(error);}
}
async function connect(user:string,token:()=>Promise<string>,photo?:unknown,displayName=''){
 sessionGeneration++;navigationGeneration++;busy=false;music?.pause();music=undefined;musicTrack='';own=undefined;visited=undefined;closeScene();const generation=sessionGeneration;
 api=new ApiClient(config.api,user,token,sessionStorage);syncPending();
 avatarUser=`${config.api}:${user}`;googlePhoto=googleAvatar(photo);avatarChoice=readAvatar(avatarStore,avatarUser);loadAudioSettings();
 scene=new Scene(element<HTMLCanvasElement>('farm'),catalog,tile=>{if(healthPinned&&!(tool==='CURSOR'&&tile.cropId))closeHealth();void tileAction(tile);},(id,pointerType)=>{if(pointerType==='touch'&&tool==='CURSOR'){playAnimalSound(id);animalPopup(id);return;}const command=animalClickCommand(id,!!visited);if(command)void action(command);else animalPopup(id);},health,syncMusic,()=>{if(healthPinned)closeHealth();},playAnimalSound);
sceneResize=new ResizeObserver(()=>{layoutHud();health(healthTile,healthPoint,'resize');});for(const id of ['farm','farm-header','tools-bar','notice'])sceneResize.observe(element(id));
 try{await refresh();if(generation===sessionGeneration&&!own)setLoginStep('invite',displayName||user);}
 catch(error){handleError(error);}
}
async function start(){
 login.replaceChildren();
 setupLoginInvitation();const loginError=element('login-error');loginError.hidden=true;element('login-invite-error').hidden=true;
 let googleButton:HTMLButtonElement;
 googleButton=button('Entrar com Google',async()=>{
   if(!supabase)return;
   loginError.hidden=true;googleButton.disabled=true;googleButton.setAttribute('aria-busy','true');googleLabel.textContent='Entrando…';
   try{const {error}=await supabase.auth.signInWithOAuth({provider:'google',options:{redirectTo:location.origin}});if(error)throw error;}
   catch{googleButton.disabled=false;googleButton.removeAttribute('aria-busy');googleLabel.textContent='Entrar com Google';loginError.hidden=false;}
  },login);
 googleButton.className='login-google';
 const googleMark=document.createElement('span');googleMark.className='login-google-mark';googleMark.setAttribute('aria-hidden','true');
 const logo=document.createElementNS('http://www.w3.org/2000/svg','svg');logo.setAttribute('viewBox','0 0 24 24');
 for(const [fill,path]of [['#4285F4','M23.49 12.27c0-.79-.07-1.54-.19-2.27H12v4.3h6.44a5.5 5.5 0 0 1-2.39 3.61v3h3.88c2.27-2.09 3.56-5.17 3.56-8.64z'],['#34A853','M12 24c3.24 0 5.95-1.08 7.93-2.92l-3.88-3c-1.08.72-2.45 1.15-4.05 1.15-3.12 0-5.77-2.1-6.72-4.94H1.27v3.09A12 12 0 0 0 12 24z'],['#FBBC05','M5.28 14.29a7.2 7.2 0 0 1 0-4.58V6.62H1.27a12 12 0 0 0 0 10.76l4.01-3.09z'],['#EA4335','M12 4.77c1.76 0 3.34.6 4.58 1.8L20 3.15C17.95 1.24 15.24 0 12 0 7.31 0 3.23 2.69 1.27 6.62l4.01 3.09c.95-2.84 3.6-4.94 6.72-4.94z']] as const){const shape=document.createElementNS('http://www.w3.org/2000/svg','path');shape.setAttribute('fill',fill);shape.setAttribute('d',path);logo.append(shape);}
 googleMark.append(logo);const spinner=document.createElementNS('http://www.w3.org/2000/svg','svg');spinner.classList.add('login-spinner');spinner.setAttribute('viewBox','0 0 24 24');spinner.setAttribute('aria-hidden','true');const ring=document.createElementNS('http://www.w3.org/2000/svg','circle');ring.setAttribute('cx','12');ring.setAttribute('cy','12');ring.setAttribute('r','9');spinner.append(ring);const googleLabel=document.createElement('span');googleLabel.className='login-google-label';googleLabel.textContent='Entrar com Google';googleButton.replaceChildren(googleMark,spinner,googleLabel);
 if(!supabase){googleButton.disabled=true;googleButton.title='Login Google indisponível em desenvolvimento.';}
 showConnection('Conectando à fazenda… O primeiro acesso pode levar cerca de um minuto.');
 try{
  const response=await fetch(config.api+'/health',{signal:AbortSignal.timeout(65_000),cache:'no-store'});if(!response.ok)throw new Error('Servidor indisponível.');const health=await response.json();
  catalog=await (await fetch(config.api+'/v1/catalog',{signal:AbortSignal.timeout(65_000)})).json();selectedSeed=catalog.crops[0]?.id??selectedSeed;
  showConnection();
  if(config.development){if(!health.development)throw new Error('Contas locais exigem servidor de desenvolvimento.');const tests=document.createElement('div');tests.className='login-test-group';const caption=document.createElement('span');caption.className='login-test-label';caption.textContent='Modo de teste';tests.append(caption);const actions=document.createElement('div');actions.className='login-test-actions';for(const name of ['Alice','Bob']){const testButton=button(`Testar como ${name}`,async()=>{sessionStorage.setItem('colheita:local-user',name);await connect(name.toLowerCase(),async()=>`dev-${name.toLowerCase()}`,undefined,name);},actions);testButton.className='login-test-button';}tests.append(actions);login.append(tests);const previous=sessionStorage.getItem('colheita:local-user');if(previous==='Alice'||previous==='Bob')await connect(previous.toLowerCase(),async()=>`dev-${previous.toLowerCase()}`,undefined,previous);}
  else{
   const {data,error}=await supabase!.auth.getSession();if(error)throw error;
   if(data.session){const userId=data.session.user.id,metadata=data.session.user.user_metadata;const displayName=String(metadata?.full_name??metadata?.name??data.session.user.email?.split('@')[0]??'Fazendeiro');await connect(userId,async()=>{const {data,error}=await supabase!.auth.getSession();if(error||!data.session||data.session.user.id!==userId)throw new ApiError(401,'Entre novamente com a mesma conta para continuar.');return data.session.access_token;},metadata?.avatar_url??metadata?.picture,displayName);}
  }
 }catch(error){showConnection();notify(error instanceof Error?error.message:'Servidor indisponível.');button('Tentar conectar novamente',start,login);}
}
element('shop').onclick=()=>shop();element('deposit').onclick=deposit;element('missions').onclick=missions;element('friends').onclick=()=>void friends();
element('home').onclick=()=>{navigationGeneration++;visited=undefined;tool='PLOW';render();};element('settings').onclick=()=>shortcutPanel('Ajustes',settings);element('close-modal').onclick=()=>modal.close();
element('retry').onclick=async()=>{if(!api||busy)return;const client=api,generation=sessionGeneration;busy=true;try{const reply=await client.retry();if(generation!==sessionGeneration)return;receive(reply);if(reply.code){dialog('Convite confirmado');paragraph(reply.code);}}catch(error){if(generation===sessionGeneration)handleError(error);}finally{if(generation===sessionGeneration){busy=false;syncPending();await refresh();}}};
window.addEventListener('offline',()=>showConnection('Sem conexão. Reconecte antes de agir.'));
window.addEventListener('online',()=>void refresh());
document.addEventListener('pointerdown',event=>{if(healthPinned&&!element('farm').contains(event.target as Node)&&!element('plant-health').contains(event.target as Node))closeHealth();});
modal.addEventListener('close',()=>{health(healthTile,healthPoint);window.clearInterval(missionsTimer);missionsTimer=undefined;});
document.addEventListener('keydown',event=>{if(!own||event.repeat||event.target instanceof HTMLInputElement||event.target instanceof HTMLSelectElement)return;const shortcut=gameShortcut(event.key,!!visited);if(!shortcut)return;if(shortcut.action==='tool'){if(!modal.open)pickTool(shortcut.tool);return;}if(shortcut.action==='shop')shortcutPanel('Loja',()=>shop());else if(shortcut.action==='deposit')shortcutPanel('Depósito',deposit);else if(shortcut.action==='missions')shortcutPanel('Missões',missions);else if(shortcut.action==='plots')shortcutPanel('Terras da fazenda',tileList);else if(shortcut.action==='music')toggleMusic();else if(shortcut.action==='effects')toggleEffects();});
document.addEventListener('visibilitychange',()=>{syncMusic();if(!document.hidden)void refresh();});
window.setInterval(()=>{if(!document.hidden)void refresh();},30_000);
void start();
