import type {AwayReport} from './types.ts';
import {text,image} from './ui.ts';
import {animalProductPath} from './presentation.ts';

export interface AwayHandlers {
  /** Each async action resolves to an error message, or undefined on success. */
  renew(serviceId:string):Promise<string|undefined>;
  renewVip():void;
  feed(animalId:string):Promise<string|undefined>;
  buyFeed():void;
  close():void;
  careOpen:boolean;
  onCareToggle(open:boolean):void;
}

export function awayDuration(seconds:number):string{
 if(seconds>172800)return `${Math.floor(seconds/86400)} dias`;
 const hours=Math.floor(seconds/3600),minutes=Math.floor(seconds%3600/60);
 return minutes?`${hours}h${String(minutes).padStart(2,'0')}`:`${hours}h`;
}
export function pluralItem(name:string,quantity:number):string{
 const word=name.toLocaleLowerCase('pt-BR');if(quantity===1)return `1 ${word}`;
 const plural=word.endsWith('ão')?word.slice(0,-2)+'ões':word.endsWith('ol')?word.slice(0,-2)+'óis':word.endsWith('el')?word.slice(0,-2)+'éis'
  :/[au]l$/.test(word)?word.slice(0,-1)+'is':/[rz]$/.test(word)?word+'es':word+'s';
 return `${quantity} ${plural}`;
}
const cropArt=(stem:string)=>`classic/crops/plants_phase_one_to_five/${stem}_5.png`;
const productArt=(animalId:string)=>animalProductPath(animalId)??'classic/icons/deposit-icon.png';
const clock=(at:string)=>new Date(at).toLocaleTimeString('pt-BR',{hour:'2-digit',minute:'2-digit'});
const lower=(name:string)=>name.toLocaleLowerCase('pt-BR');

function section(parent:HTMLElement,kind:string,label:string,title?:string){
 const node=document.createElement('section');node.className=`away-card ${kind}`;node.setAttribute('aria-label',label);
 if(title){const heading=text('h3',title);node.append(heading);}parent.append(node);return node;
}
function itemLine(parent:HTMLElement,items:{art:string;label:string}[]){
 const line=document.createElement('div');line.className='away-items';
 for(const item of items){const entry=text('span','','away-item');entry.append(image(item.art),text('span',item.label));line.append(entry);}
 parent.append(line);
}
function event(parent:HTMLElement,art:string,lines:string[],at:string,className=''){
 const row=document.createElement('p');row.className=`away-event ${className}`.trim();
 const body=document.createElement('span');body.append(text('b',lines[0]));if(lines[1])body.append(text('small',lines[1]));
 const time=text('time',clock(at));time.setAttribute('datetime',at);
 row.append(image(art),body,time);parent.append(row);
}
function sprig(left:boolean){
 const svg=document.createElementNS('http://www.w3.org/2000/svg','svg') as unknown as HTMLElement;
 svg.setAttribute('class',`away-sprig${left?' is-left':''}`);svg.setAttribute('viewBox','0 0 48 30');svg.setAttribute('aria-hidden','true');
 for(const [d,fill] of [['M4 15 Q24 14 46 15',''],['M18 15 Q12 3 24 1 Q27 10 18 15Z','#7fbf3a'],['M18 15 Q12 27 24 29 Q27 20 18 15Z','#6aae2e'],
  ['M33 15 Q30 6 40 5 Q41 12 33 15Z','#8fce45'],['M33 15 Q30 24 40 25 Q41 18 33 15Z','#7fbf3a']]){
  const path=document.createElementNS('http://www.w3.org/2000/svg','path') as unknown as HTMLElement;path.setAttribute('d',d);
  if(fill)path.setAttribute('fill',fill);else{path.setAttribute('stroke','#4f8f2a');path.setAttribute('stroke-width','2.6');path.setAttribute('fill','none');path.setAttribute('stroke-linecap','round');}
  svg.append(path);
 }
 return svg;
}

/** Two-step action row: nothing is spent on the first click; the result replaces the button. */
function task(parent:HTMLElement,art:string,title:string,detail:string,actionLabel:string,cost:string|undefined,run:()=>Promise<string|undefined>|void,done?:string){
 const row=document.createElement('div');row.className='away-task';
 const info=document.createElement('span');info.className='away-task-text';const detailNode=text('small',detail);info.append(text('b',title),detailNode);
 const action=document.createElement('span');action.className='away-task-action';
 row.append(image(art),info,action);parent.append(row);
 let busy=false;
 const idle=(message?:string)=>{row.classList.remove('is-asking');action.replaceChildren();
  if(message)action.append(text('span',message,'away-task-error'));
  const start=text('button',actionLabel) as HTMLButtonElement;start.type='button';start.onclick=()=>{if(cost===undefined){void run();return;}ask();};action.append(start);};
 const ask=()=>{row.classList.add('is-asking');action.replaceChildren();
  const confirm=text('button','Confirmar','away-confirm') as HTMLButtonElement,cancel=text('button','Cancelar','away-cancel') as HTMLButtonElement;
  confirm.type='button';cancel.type='button';
  confirm.onclick=async()=>{if(busy)return;busy=true;confirm.disabled=true;cancel.disabled=true;
   try{const error=await run();if(error)idle(error);else finish();}finally{busy=false;}};
  cancel.onclick=()=>{if(!busy)idle();};
  action.append(text('span',cost!,'away-cost'),confirm,cancel);};
 const finish=()=>{row.classList.remove('is-asking');action.replaceChildren(text('span',done??'','away-done'));};
 idle();
 return {setDetail:(value:string)=>{detailNode.textContent=value;}};
}

export function renderAwayReport(parent:HTMLElement,footer:HTMLElement,report:AwayReport,handlers:AwayHandlers){
 const best=[...report.harvests].sort((a,b)=>b.quantity-a.quantity)[0],bestProduct=[...report.products].sort((a,b)=>b.quantity-a.quantity)[0];
 const header=document.createElement('header');header.className='away-header';
 const basket=document.createElement('div');basket.className='away-basket';basket.append(image('classic/animals/ovelha/cesta-vazia.png','away-basket-base'));
 if(best||bestProduct)basket.append(image(best?cropArt(best.stem):productArt(bestProduct!.animalId),'away-basket-fill'));
 const titleRow=document.createElement('div');titleRow.className='away-title-row';
 const title=text('h2','Sua fazenda rendeu!');title.id='away-title';titleRow.append(sprig(true),title,sprig(false));
 const subtitle=text('p','Enquanto você esteve fora por ');subtitle.append(text('b',awayDuration(report.awaySeconds)),'.');
 header.append(basket,titleRow,subtitle);parent.append(header);
 const body=document.createElement('div');body.className='away-body';parent.append(body);

 if(report.harvests.length||report.products.length){
  const reward=section(body,'is-reward','Recompensas');
  if(report.harvests.length){
   const total=report.harvests.reduce((sum,item)=>sum+item.quantity,0);
   const hero=document.createElement('div');hero.className='away-hero';
   const art=document.createElement('span');art.className='away-hero-art';for(const item of report.harvests.slice(0,2))art.append(image(cropArt(item.stem)));
   const copy=document.createElement('div');const headline=document.createElement('div');headline.append(text('span',String(total),'away-big'),' ',text('span','colheitas prontas','away-label'));
   copy.append(headline);itemLine(copy,report.harvests.map(item=>({art:cropArt(item.stem),label:pluralItem(item.name,item.quantity)})));
   hero.append(art,copy);reward.append(hero);
  }
  if(report.products.length){
   const sub=document.createElement('div');sub.className='away-sub';const total=report.products.reduce((sum,item)=>sum+item.quantity,0);
   const copy=document.createElement('div');
   if(report.products.length===1){const only=report.products[0];sub.append(image(productArt(only.animalId)));copy.append(text('span',pluralItem(only.name,only.quantity)+' no depósito','away-label'));}
   else{sub.append(image('classic/icons/deposit-icon.png'));const headline=document.createElement('div');headline.append(text('span',String(total),'away-big'),' ',text('span','produtos no depósito','away-label'));
    copy.append(headline);itemLine(copy,report.products.map(item=>({art:productArt(item.animalId),label:pluralItem(item.name,item.quantity)})));}
   sub.append(copy);reward.append(sub);
  }
 }
 if(report.thefts.length){const visits=section(body,'is-theft','Visitas','Visitas na sua fazenda');
  for(const theft of report.thefts)event(visits,cropArt(theft.stem),[`${theft.actor} levou ${pluralItem(theft.name,theft.quantity)}.`],theft.at);}
 if(report.defenses.length){const guard=section(body,'is-guard','Proteção','Proteção da fazenda');
  for(const defense of report.defenses)event(guard,'classic/animals/cachorro/cachorro.png',[`Seu ${lower(defense.protectorName)} protegeu a fazenda`,`Tentativa de ${defense.actor} impedida.`],defense.at,'is-defense');}
 const care=report.care,careTotal=care.waterings+care.pestsRemoved+care.animalCares;
 if(careTotal){
  const box=section(body,'is-vip','Cuidados automáticos');const details=document.createElement('details') as HTMLDetailsElement;details.open=handlers.careOpen;
  const summary=document.createElement('summary');summary.append(image('classic/icons/cartao-vip.png'),text('span',''),text('span','','away-chevron'));
  (summary.children[1] as HTMLElement).append(text('b',String(careTotal)),' cuidados automáticos');
  const cards=document.createElement('div');cards.className='away-care';
  for(const [art,count,label] of [['classic/icons/hud/regador-ui.png',care.waterings,'regas'],['classic/icons/hud/herbicida-ui.png',care.pestsRemoved,'pragas removidas'],['classic/icons/racao-generica.png',care.animalCares,'refeições']] as const){
   const card=document.createElement('div');card.append(image(art),text('b',String(count)),text('span',label));cards.append(card);
  }
  details.append(summary,cards);box.append(details);
  summary.onclick=(event:Event)=>{event.preventDefault();toggleCare(details,cards,!details.open);handlers.onCareToggle(details.open);};
 }
 if(report.pending.length){
  const todo=section(body,'is-todo','Pendências','Precisa da sua atenção');
  for(const item of report.pending){
   if(item.kind==='AUTOMATION')task(todo,'classic/icons/vip/upgrade_vip_regador.png',`${item.name} expirou`,`às ${clock(item.expiredAt)}`,'Renovar','Renovar por 30 dias: 100 verdes',()=>handlers.renew(item.serviceId),`${item.name} ativa ✓`);
   else if(item.kind==='VIP')task(todo,'classic/icons/cartao-vip.png','Seu cartão VIP expirou',`às ${clock(item.expiredAt)}`,'Renovar',undefined,()=>handlers.renewVip());
   else if(item.stock<=0)task(todo,'classic/icons/racao-generica.png',`Seu ${lower(item.name)} está sem ração`,'Você não tem ração no estoque','Comprar ração',undefined,()=>handlers.buyFeed());
   else{let stock=item.stock;const row=task(todo,'classic/icons/racao-generica.png',`Seu ${lower(item.name)} está sem ração`,`Você tem ${stock} rações no estoque`,'Alimentar','Usa 1 das suas rações',
    async()=>{const error=await handlers.feed(item.animalId);if(!error){stock--;row.setDetail(stock===1?'Você tem 1 ração no estoque':`Você tem ${stock} rações no estoque`);}return error;},`${item.name} alimentado ✓`);}
  }
 }
 const close=text('button','Ir para a fazenda','away-close') as HTMLButtonElement;close.type='button';close.onclick=()=>handlers.close();footer.append(close);
 return close;
}

const still=()=>typeof matchMedia!=='function'||matchMedia('(prefers-reduced-motion: reduce)').matches;
/** Height animation for the care cards; instant when the player prefers reduced motion. */
function toggleCare(details:HTMLDetailsElement,cards:HTMLElement,open:boolean){
 if(still()||typeof cards.animate!=='function'){details.open=open;return;}
 if(open){details.open=true;const height=cards.offsetHeight;cards.classList.remove('is-entering');void cards.offsetWidth;cards.classList.add('is-entering');
  cards.animate([{height:'0px',opacity:0},{height:`${height}px`,opacity:1}],{duration:240,easing:'cubic-bezier(.2,0,0,1)'});}
 else{const height=cards.offsetHeight;const animation=cards.animate([{height:`${height}px`,opacity:1},{height:'0px',opacity:0}],{duration:170,easing:'cubic-bezier(.3,0,1,1)'});
  animation.onfinish=()=>{details.open=false;};}
}

/** Ambient leaves behind the panel; returns a cleanup. Nothing is drawn with reduced motion. */
export function startAwayLeaves(host:HTMLElement):()=>void{
 if(still())return()=>{};
 const layer=document.createElement('div');layer.className='away-leaves';layer.setAttribute('aria-hidden','true');
 for(let i=0;i<9;i++){const leaf=document.createElement('i');leaf.style.left=`${5+Math.random()*90}%`;leaf.style.setProperty('--dx',`${Math.random()*160-80}px`);
  leaf.style.animationDuration=`${7+Math.random()*6}s`;leaf.style.animationDelay=`${-Math.random()*10}s`;leaf.style.background=['#4f9a2c','#7fb53a','#c9a23a'][i%3];layer.append(leaf);}
 host.append(layer);return()=>layer.remove();
}
