import type {ApiClient} from './api.ts';
import {changeBinding,pushState} from './push-state.ts';
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
async function reconcile(api:ApiClient){for(const item of (await pushState())?.pending??[]){await api.push('revoke',item);await pushState(state=>({...state!,pending:state!.pending.filter(p=>p.endpoint!==item.endpoint||p.deviceRevision!==item.deviceRevision)}));}}
export function renderPushSettings(parent:HTMLElement,account:string,api:ApiClient){
 const section=document.createElement('section');section.className='settings-section push-settings';parent.append(section);
 const title=document.createElement('h3');title.textContent='Notificações no dispositivo';section.append(title);
 const status=document.createElement('p');status.setAttribute('role','status');status.textContent='Consultando disponibilidade…';section.append(status);
 const note=document.createElement('p');note.textContent='Avisos automáticos de plantas, animais, furtos e pragas estarão disponíveis nas próximas entregas. A entrega depende do navegador e da conexão.';section.append(note);
 const preferences={plants:true,animals:true,thefts:true,pests:true};
 for(const [key,label]of [['plants','Plantas'],['animals','Animais'],['thefts','Furtos'],['pests','Pragas']]as const){const row=document.createElement('label'),input=document.createElement('input');input.type='checkbox';input.checked=true;input.disabled=true;row.append(input,document.createTextNode(label+' (em breve)'));section.append(row);}
 const activate=document.createElement('button'),disable=document.createElement('button'),test=document.createElement('button');activate.textContent='Ativar notificações';disable.textContent='Desativar notificações';test.textContent='Enviar notificação de teste';activate.disabled=true;test.hidden=true;disable.hidden=true;section.append(activate,disable,test);
 const supported=typeof Notification!=='undefined'&&typeof PushManager!=='undefined'&&'serviceWorker'in navigator&&typeof indexedDB!=='undefined'&&globalThis.isSecureContext;
 let publicKey='',configurationMessage='Consultando disponibilidade…',busy=false,nextTest=0;
 const fail=()=>{status.textContent='Não foi possível concluir. Confira a conexão e tente novamente.';};
 async function refresh(){
  const state=await pushState(),current=state?.account===account,bound=current&&state?.active?.account===account;
  test.hidden=!bound||!publicKey;disable.hidden=!bound;activate.hidden=bound;
  activate.disabled=!current||!publicKey||Notification.permission==='denied';
  status.textContent=!current?'A conta deste dispositivo mudou. Entre novamente para configurar notificações.':!publicKey?configurationMessage:bound?'Notificações ativadas neste dispositivo.':Notification.permission==='denied'?'Permissão negada. Altere nas configurações do navegador se desejar.':'Permissão: '+Notification.permission+'. Notificações desativadas.';
 } if(!supported){status.textContent='Notificações indisponíveis neste navegador. No iPhone/iPad, adicione o jogo à Tela de Início e abra por lá.';return;}
 void refresh().catch(fail);
 void api.push<{enabled:boolean;publicKey:string}>('config').then(async config=>{
  if(config.enabled&&config.publicKey)publicKey=config.publicKey;
  else configurationMessage='Notificações ainda não configuradas no servidor. Você pode desativar a vinculação local.';
  await refresh();
 }).catch(()=>{
  configurationMessage='Serviço de notificações indisponível. Você pode desativar a vinculação local.';
  void refresh().catch(fail);
 });
 activate.onclick=async()=>{
  if(busy||!publicKey||activate.disabled)return;
  busy=true;activate.disabled=true;
  const started=lifecycle;let revision:number|undefined;
  try{
   let reserved=false;
   const intent=await pushState(state=>{
    if(state?.account!==account)return state!;
    reserved=true;return changeBinding(state);
   });
   if(!reserved||started!==lifecycle){await refresh();return;}
   revision=intent!.revision;
   // The explicit click may ask only while its transient browser activation remains valid.
   if(navigator.userActivation&&!navigator.userActivation.isActive){status.textContent='Toque novamente em Ativar notificações para permitir no navegador.';return;}
   if(await Notification.requestPermission()!=='granted'){await refresh();return;}
   if(started!==lifecycle||(await pushState())?.revision!==revision)return;
   await reconcile(api);
   const registration=await navigator.serviceWorker.register('/push-worker.js');
   await navigator.serviceWorker.ready;
   let subscription=await registration.pushManager.getSubscription();
   subscription??=await registration.pushManager.subscribe({userVisibleOnly:true,applicationServerKey:keyBytes(publicKey)});
   const endpoint=subscription.endpoint,deviceToken=intent?.deviceToken??randomToken(),bindingId=randomToken();
   let installed=false;
   const state=await pushState(current=>{
    if(started!==lifecycle||current?.account!==account||current?.revision!==revision)return current!;
    installed=true;return {...current!,deviceToken,active:{account,endpoint,deviceToken,bindingId}};
   });
   if(!installed)return;
   await api.push('subscribe',{endpoint,keys:subscription.toJSON().keys,deviceToken,bindingId,deviceRevision:state!.revision,preferences});
   if((await pushState())?.revision!==state!.revision)return;
   await refresh();
  }catch{
   await pushState(current=>current?.revision===revision?changeBinding(current):current!);
   fail();
  }finally{busy=false;const state=await pushState();activate.disabled=state?.account!==account||!publicKey||Notification.permission==='denied';}
 };
 disable.onclick=async()=>{
  if(busy)return;busy=true;
  try{await clearBinding(account);await refresh();await reconcile(api);}
  catch{status.textContent='Notificações locais desativadas. A vinculação será reconciliada quando a conexão e o armazenamento se recuperarem.';}
  finally{busy=false;}
 }; test.onclick=async()=>{if(busy||!publicKey||test.hidden||Date.now()<nextTest)return;busy=true;test.disabled=true;nextTest=Date.now()+60_000;try{const s=await pushState();if(s?.active?.account!==account)return;const result=await api.push<{status:string}>('test',{endpoint:s.active.endpoint,deviceToken:s.active.deviceToken,bindingId:s.active.bindingId,deviceRevision:s.revision,testId:crypto.randomUUID()});status.textContent=testMessage(result.status);}catch{status.textContent='Não foi possível confirmar o envio. Aguarde um minuto antes de tentar novamente.';}finally{busy=false;setTimeout(()=>{test.disabled=false;},60_000);}};
}
