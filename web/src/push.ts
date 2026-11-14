import {ApiError,type ApiClient} from './api.ts';
import {changeBinding,pushState,type PushState} from './push-state.ts';
export function testMessage(status:string):string{return status==='accepted'?'Enviado ao serviço de notificações':status==='pending'?'Envio ainda pendente. Não tente novamente agora.':status==='expired'?'Inscrição expirada. Desative e ative novamente.':'Serviço de notificações indisponível.';}
let lifecycle=0;
function randomToken(){return btoa(String.fromCharCode(...crypto.getRandomValues(new Uint8Array(32)))).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/,'');}
function keyBytes(key:string){return Uint8Array.from(atob(key.replace(/-/g,'+').replace(/_/g,'/')),c=>c.charCodeAt(0));}
export async function suspendPush(api?:ApiClient):Promise<void>{await clearBinding(undefined,api);}
async function clearBinding(account?:string,api?:ApiClient):Promise<void>{
 lifecycle++;
 if(typeof indexedDB==='undefined')return;
 let cleared=false;
 try{
  await pushState(state=>{
   if(account!==undefined&&state?.account!==account)return state!;
   cleared=true;
   return {...changeBinding(state),account};
  });
 }catch(error){
  // A readable old binding must not remain push-capable after a failed suppression write.
  if('serviceWorker'in navigator){
   const registration=await navigator.serviceWorker.getRegistration();
   if(registration){
    try{await registration.unregister();}
    finally{for(const notification of await registration.getNotifications())notification.close();}
   }
  }
  throw error;
 }
 if(!cleared)return;
 if('serviceWorker'in navigator){const registration=await navigator.serviceWorker.getRegistration();for(const notification of await registration?.getNotifications()??[])notification.close();}
 if(api)void reconcile(api).catch(()=>{});
}
export async function connectPush(account:string,api:ApiClient):Promise<void>{
 if(typeof indexedDB==='undefined')return;
 lifecycle++;
 let changed=false;await pushState(state=>{if(state?.account===account)return state;changed=true;return {...changeBinding(state),account};});
 if(changed&&'serviceWorker'in navigator){const registration=await navigator.serviceWorker.getRegistration();for(const notification of await registration?.getNotifications()??[])notification.close();}
 if('serviceWorker'in navigator&&(await pushState())?.active&&!await navigator.serviceWorker.getRegistration())await clearBinding(account);
 await reconcile(api);
}
export async function reconcilePush(account:string,api:ApiClient){if(typeof indexedDB!=='undefined'&&(await pushState())?.account===account)await reconcile(api);}
function definitive(error:unknown){return error instanceof ApiError&&[400,403,404,409].includes(error.status);}
async function reconcile(api:ApiClient){
 for(const item of (await pushState())?.pending??[]){
  try{await api.push('revoke',item);}catch(error){if(!definitive(error))throw error;}
  await pushState(state=>({...state!,pending:state!.pending.filter(p=>p.endpoint!==item.endpoint||p.deviceRevision!==item.deviceRevision||p.deviceToken!==item.deviceToken)}));
 }
}
export function renderPushSettings(parent:HTMLElement,account:string,api:ApiClient){
 const section=document.createElement('section');section.className='settings-section push-settings';parent.append(section);
 const title=document.createElement('h3');title.textContent='Notificações no dispositivo';section.append(title);
 const status=document.createElement('p');status.setAttribute('role','status');status.textContent='Consultando disponibilidade…';section.append(status);
 const note=document.createElement('p');note.textContent='Ativar notificações permite enviar um teste. Para receber avisos de Plantas, marque Plantas em Ajustes após ativar. Ativar ou testar não habilita esses avisos. Plantas prontas são agrupadas em até um aviso por minuto. Os horários são aproximados; a entrega depende do navegador e da conexão. Animais, furtos e pragas estarão disponíveis nas próximas entregas.';section.append(note);
 const preferences={plants:false,animals:false,thefts:false,pests:false};
 let plants!:HTMLInputElement;
 for(const [key,label]of [['plants','Plantas'],['animals','Animais'],['thefts','Furtos'],['pests','Pragas']]as const){const row=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.checked=preferences[key];input.disabled=true;if(key==='plants')plants=input;row.append(input,document.createTextNode(label+(key==='plants'?'':' (em breve)')));section.append(row);}
 const activate=document.createElement('button'),disable=document.createElement('button'),test=document.createElement('button');activate.textContent='Ativar notificações';disable.textContent='Desativar notificações';test.textContent='Enviar notificação de teste';activate.disabled=true;test.hidden=true;disable.hidden=true;section.append(activate,disable,test);
 const retry=document.createElement('button');retry.textContent='Repetir alteração de Plantas';retry.hidden=true;section.append(retry);
 const supported=typeof Notification!=='undefined'&&typeof PushManager!=='undefined'&&'serviceWorker'in navigator&&typeof indexedDB!=='undefined'&&globalThis.isSecureContext;
 let publicKey='',configurationMessage='Consultando disponibilidade…',busy=false,nextTest=0,plantEventsEnabled=false,loadedRevision:number|undefined,preferenceFailed=false,loadingPreferences=false;
 const fail=()=>{status.textContent='Não foi possível concluir. Confira a conexão e tente novamente.';};
 function matches(current:PushState|undefined,expected:PushState){return current?.account===account&&current.revision===expected.revision&&current.active?.account===account&&current.active.bindingId===expected.active?.bindingId&&current.active.endpoint===expected.active?.endpoint&&current.active.deviceToken===expected.active?.deviceToken;}
 async function refresh(){
  const state=await pushState(),current=state?.account===account,bound=current&&state?.active?.account===account;
  test.hidden=!bound||!publicKey;disable.hidden=!bound;activate.hidden=bound;
  activate.disabled=busy||!current||!publicKey||Notification.permission==='denied';disable.disabled=busy;
  plants.checked=bound?state.active!.plants===true:preferences.plants;
  plants.disabled=busy||loadingPreferences||!bound||!publicKey||!plantEventsEnabled||loadedRevision!==state?.revision||!!state?.active?.plantsPending;
  retry.hidden=!bound||!publicKey||!plantEventsEnabled||!state?.active?.plantsPending&&!preferenceFailed;retry.disabled=busy||loadingPreferences;
  retry.textContent=state?.active?.plantsPending?'Repetir alteração de Plantas':'Recarregar preferência de Plantas';
  status.textContent=!current?'A conta deste dispositivo mudou. Entre novamente para configurar notificações.':!publicKey?configurationMessage:bound?'Notificações ativadas neste dispositivo.':Notification.permission==='denied'?'Permissão negada. Altere nas configurações do navegador se desejar.':'Permissão: '+Notification.permission+'. Notificações desativadas.';
  if(bound&&publicKey&&plantEventsEnabled&&(preferenceFailed||state?.active?.plantsPending))status.textContent='Não foi possível confirmar a preferência de Plantas. Confira a conexão e tente novamente.';
 } if(!supported){status.textContent='Notificações indisponíveis neste navegador. No iPhone/iPad, adicione o jogo à Tela de Início e abra por lá.';return;}
 async function loadPreferences(){
  if(loadingPreferences)return;loadingPreferences=true;retry.disabled=true;plants.disabled=true;
  try{
   const expected=await pushState();
   if(!publicKey||!plantEventsEnabled||expected?.account!==account||expected.active?.account!==account||expected.active.plantsPending)return;
   const active=expected.active,result=await api.push<{bindingId:string;deviceRevision:number;plants:boolean}>('preferences',{endpoint:active.endpoint,deviceToken:active.deviceToken,bindingId:active.bindingId,deviceRevision:expected.revision});
   if(result.bindingId!==active.bindingId||result.deviceRevision!==expected.revision||typeof result.plants!=='boolean')throw Error('Preferência inválida.');
   await pushState(current=>{if(!matches(current,expected)||current!.active!.plantsPending)return current!;loadedRevision=current!.revision;preferenceFailed=false;return {...current!,active:{...current!.active!,plants:result.plants}};});
  }catch{preferenceFailed=true;}
  finally{loadingPreferences=false;await refresh();}
 }
 async function savePlants(repeat=false){
  if(busy||!publicKey||!plantEventsEnabled||(!repeat&&plants.disabled))return;
  const desired=plants.checked,started=lifecycle;busy=true;plants.disabled=true;retry.disabled=true;
  try{
   let reserved=false;
   const expected=await pushState(current=>{
    if(started!==lifecycle||current?.account!==account||current.active?.account!==account)return current!;
    if(repeat){if(!current.active.plantsPending)return current;reserved=true;return current;}
    if(current.revision!==loadedRevision||current.active.plantsPending)return current;
    if(!Number.isSafeInteger(current.revision+1))throw Error('Revisão indisponível.');
    reserved=true;return {...current,revision:current.revision+1,active:{...current.active,plants:desired,plantsPending:true}};
   });
   if(!reserved||started!==lifecycle||!expected?.active)return;
   const active=expected.active,result=await api.push<{bindingId:string;deviceRevision:number;plants:boolean}>('preferences',{endpoint:active.endpoint,deviceToken:active.deviceToken,bindingId:active.bindingId,deviceRevision:expected.revision,plants:active.plants});
   if(result.bindingId!==active.bindingId||result.deviceRevision!==expected.revision||result.plants!==active.plants)throw Error('Preferência inválida.');
   await pushState(current=>{if(started!==lifecycle||!matches(current,expected))return current!;loadedRevision=current!.revision;preferenceFailed=false;const {plantsPending,...binding}=current!.active!;return {...current!,active:binding};});
  }catch{preferenceFailed=true;}
  finally{busy=false;await refresh();}
 }
 plants.onchange=()=>savePlants();retry.onclick=async()=>{if(busy)return;if((await pushState())?.active?.plantsPending)await savePlants(true);else await loadPreferences();};
 void refresh().catch(fail);
 void api.push<{enabled:boolean;publicKey:string;plantEventsEnabled?:boolean}>('config').then(async config=>{
  if(config.enabled&&config.publicKey){publicKey=config.publicKey;plantEventsEnabled=config.plantEventsEnabled===true;}
  else configurationMessage='Notificações ainda não configuradas no servidor. Você pode desativar a vinculação local.';
  await refresh();
  await loadPreferences();
 }).catch(()=>{
  configurationMessage='Serviço de notificações indisponível. Você pode desativar a vinculação local.';
  void refresh().catch(fail);
 });
 activate.onclick=async()=>{
  if(busy||!publicKey||activate.disabled)return;
  busy=true;activate.disabled=true;
  const started=lifecycle;let revision:number|undefined,subscription:PushSubscription|null=null,rejectedSubscription=false;
  try{
   // Safari requires this call in the synchronous click stack. Account ownership
   // is checked in IndexedDB afterwards; permission belongs to the origin.
   if(navigator.userActivation&&!navigator.userActivation.isActive){status.textContent='Toque novamente em Ativar notificações para permitir no navegador.';return;}
   if(await Notification.requestPermission()!=='granted'){await refresh();return;}
   if(started!==lifecycle)return;
   let reserved=false;
   const intent=await pushState(state=>{
    if(started!==lifecycle||state?.account!==account)return state!;
    reserved=true;return changeBinding(state);
   });
   if(!reserved||started!==lifecycle){await refresh();return;}
   revision=intent!.revision;
   if(started!==lifecycle||(await pushState())?.revision!==revision)return;
   await reconcile(api);
   const registration=await navigator.serviceWorker.register('/push-worker.js');
   await navigator.serviceWorker.ready;
   subscription=await registration.pushManager.getSubscription();
   subscription??=await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:keyBytes(publicKey)});
   const endpoint=subscription.endpoint,deviceToken=intent?.deviceToken??randomToken(),bindingId=randomToken();
   let installed=false;
   const state=await pushState(current=>{
    if(started!==lifecycle||current?.account!==account||current?.revision!==revision)return current!;
    installed=true;return {...current!,deviceToken,active:{account,endpoint,deviceToken,bindingId,plants:preferences.plants}};
   });
   if(!installed)return;
   try{await api.push('subscribe',{endpoint,keys:subscription.toJSON().keys,deviceToken,bindingId,deviceRevision:state!.revision,preferences});}
   catch(error){rejectedSubscription=definitive(error);throw error;}
   if((await pushState())?.revision!==state!.revision)return;
   loadedRevision=state!.revision;
   await refresh();
  }catch{
   let cleared=false;
   const clearedState=await pushState(current=>{
    if(current?.account!==account||current?.revision!==revision)return current!;
    cleared=true;return changeBinding(current);
   });
   if(rejectedSubscription&&subscription&&cleared){
    const current=await pushState();
    if(started===lifecycle&&current?.account===account&&current.revision===clearedState?.revision&&!current.active){
     try{await subscription.unsubscribe();}catch{/* Local suppression and its pending revocation remain authoritative. */}
    }
   }
   fail();
  }finally{busy=false;const state=await pushState();disable.disabled=false;activate.disabled=state?.account!==account||!publicKey||Notification.permission==='denied';plants.disabled=state?.active?.account!==account||!publicKey||!plantEventsEnabled||loadedRevision!==state.revision||!!state.active.plantsPending;}
 };
 disable.onclick=async()=>{
  if(busy)return;busy=true;let failed=false;
  try{await clearBinding(account);await refresh();await reconcile(api);}
  catch{failed=true;}
  finally{busy=false;await refresh();if(failed)status.textContent='Notificações locais desativadas. A vinculação será reconciliada quando a conexão e o armazenamento se recuperarem.';}
 }; test.onclick=async()=>{if(busy||!publicKey||test.hidden||Date.now()<nextTest)return;busy=true;test.disabled=true;nextTest=Date.now()+60_000;try{const s=await pushState();if(s?.active?.account!==account)return;const result=await api.push<{status:string}>('test',{endpoint:s.active.endpoint,deviceToken:s.active.deviceToken,bindingId:s.active.bindingId,deviceRevision:s.revision,testId:crypto.randomUUID()});status.textContent=testMessage(result.status);}catch{status.textContent='Não foi possível confirmar o envio. Aguarde um minuto antes de tentar novamente.';}finally{busy=false;setTimeout(()=>{test.disabled=false;},60_000);}};
}
