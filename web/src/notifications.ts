import type {GameNotification} from './types.ts';
import {text,control} from './ui.ts';

export const PROTECTED_ICON='/assets/classic/icons/icone-cadeado.png';
export function notificationCount(entries:GameNotification[]):number{return entries.filter(n=>!n.read).length;}
export function renderNotifications(parent:HTMLElement,entries:GameNotification[],read:(id:string)=>void,readAll:()=>void,dismiss?:(id:string)=>void,dismissRead?:()=>void){
 const all=control('Marcar todas como lidas',readAll);all.disabled=notificationCount(entries)===0;parent.append(all);
 if(dismissRead){const clear=control('Limpar lidas',dismissRead);clear.disabled=!entries.some(entry=>entry.read);parent.append(clear);}
 const list=document.createElement('section');list.className='notification-list';list.setAttribute('aria-label','Notificações recentes');parent.append(list);
 if(!entries.length)list.append(text('p','Nenhuma notificação por enquanto.'));
 for(const entry of entries){
  const row=document.createElement('article');row.className=`notification-entry${entry.read?' is-read':''}`;
  row.append(text('p',entry.message));const time=document.createElement('time');time.dateTime=entry.createdAt;time.textContent=new Date(entry.createdAt).toLocaleString('pt-BR');row.append(time);
  const mark=control(entry.read?'Lida':'Marcar como lida',()=>read(entry.id));mark.setAttribute('data-focus-key',`read-notification:${entry.id}`);mark.setAttribute('aria-label',`${entry.read?'Lida':'Marcar como lida'}: ${entry.message}`);mark.disabled=entry.read;row.append(mark);if(entry.read&&dismiss){const remove=control('×',()=>dismiss(entry.id));remove.setAttribute('aria-label',`Descartar notificação lida: ${entry.message}`);remove.setAttribute('data-focus-key',`dismiss-notification:${entry.id}`);row.append(remove);}list.append(row);
 }
}
