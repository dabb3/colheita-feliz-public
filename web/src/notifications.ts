import type {GameNotification} from './types.ts';
import {text,control} from './ui.ts';

export const PROTECTED_ICON='/assets/classic/icons/icone-cadeado.png';
export function notificationCount(entries:GameNotification[]):number{return entries.filter(n=>!n.read).length;}
export function renderNotifications(parent:HTMLElement,entries:GameNotification[],read:(id:string)=>void,readAll:()=>void){
 const all=control('Marcar todas como lidas',readAll);all.disabled=notificationCount(entries)===0;parent.append(all);
 const list=document.createElement('section');list.className='notification-list';list.setAttribute('aria-label','Notificações recentes');parent.append(list);
 if(!entries.length)list.append(text('p','Nenhuma notificação por enquanto.'));
 for(const entry of entries){
  const row=document.createElement('article');row.className=`notification-entry${entry.read?' is-read':''}`;
  row.append(text('p',entry.message));const time=document.createElement('time');time.dateTime=entry.createdAt;time.textContent=new Date(entry.createdAt).toLocaleString('pt-BR');row.append(time);
  const mark=control(entry.read?'Lida':'Marcar como lida',()=>read(entry.id));mark.setAttribute('data-focus-key',`read-notification:${entry.id}`);mark.setAttribute('aria-label',`${entry.read?'Lida':'Marcar como lida'}: ${entry.message}`);mark.disabled=entry.read;row.append(mark);list.append(row);
 }
}
