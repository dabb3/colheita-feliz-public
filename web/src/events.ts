import type {Catalog,SundayHarvestEvent} from './types.ts';
import {seedArtPath} from './presentation.ts';

export function eventStatus(event:SundayHarvestEvent|undefined,now:number):{phase:'active'|'upcoming'|'unavailable';target:string;seconds:number}{
 if(!event||!Number.isFinite(now)||event.multiplier!==2||!Array.isArray(event.cropIds))return {phase:'unavailable',target:'',seconds:0};
 const start=Date.parse(event.startsAt),end=Date.parse(event.endsAt),next=Date.parse(event.nextStartsAt);
 if(![start,end,next].every(Number.isFinite)||end<=start)return {phase:'unavailable',target:'',seconds:0};
 const nextEnd=Date.parse(event.nextEndsAt);
 const nextActive=next>start&&Number.isFinite(nextEnd)&&now>=next&&now<nextEnd;
 if(now>=end&&(!Number.isFinite(nextEnd)||now>=nextEnd||next===start))return {phase:'unavailable',target:'',seconds:0};
 const active=now>=start&&now<end||nextActive,target=nextActive?event.nextEndsAt:active?event.endsAt:now<start?event.startsAt:event.nextStartsAt;
 return {phase:active?'active':'upcoming',target,seconds:Math.max(0,Math.ceil((Date.parse(target)-now)/1000))};
}
export function eventBoosted(event:SundayHarvestEvent|undefined,cropId:string,now:number){return eventStatus(event,now).phase==='active'&&event!.cropIds.includes(cropId);}
export function eventXp(growthSeconds:number,multiplier:number){
 const format=new Intl.NumberFormat('pt-BR',{maximumFractionDigits:8}),xp=growthSeconds/3600*1.5;
 return {normal:format.format(xp),boosted:format.format(xp*multiplier)};
}
export function eventCountdown(status:ReturnType<typeof eventStatus>){
 const days=Math.floor(status.seconds/86400),hours=Math.floor(status.seconds%86400/3600),minutes=Math.floor(status.seconds%3600/60),seconds=status.seconds%60;
 return `${status.phase==='active'?'Termina':'Começa'} em ${days?`${days}d `:''}${hours}h ${minutes}min ${seconds}s`;
}
export function renderEvents(root:HTMLElement,event:SundayHarvestEvent|undefined,catalog:Catalog,now:number){
 root.replaceChildren();root.className='events-panel';
 const add=(tag:string,value:string,className='',parent=root)=>{const node=document.createElement(tag);node.textContent=value;node.className=className;parent.append(node);return node;};
 const status=eventStatus(event,now);
 if(status.phase==='unavailable'){add('p','Informações de eventos indisponíveis. A fazenda continua disponível.');return;}
 add('h3',status.phase==='active'?'Domingo de colheita · 2× XP':'Próximo domingo · 2× XP');
 add('p','Todas as culturas comuns recebem XP dobrado somente na colheita da sua fazenda. Bronze, Prata, Ouro, Diamante e culturas VIP não participam.');
 add('p',eventCountdown(status),'event-countdown');
 add('p',`Horário de São Paulo: ${new Intl.DateTimeFormat('pt-BR',{timeZone:'America/Sao_Paulo',dateStyle:'short',timeStyle:'short'}).format(Date.parse(status.target))}`,'muted');
 const grid=add('div','','event-crops');
 for(const crop of catalog.crops.filter(crop=>event!.cropIds.includes(crop.id))){
  const card=add('article','','event-crop',grid),image=document.createElement('img');image.src='/assets/'+seedArtPath(crop.spriteStem);image.alt='';image.loading='lazy';card.append(image);
  add('strong',crop.name,'',card);const xp=eventXp(crop.growthSeconds,event!.multiplier);add('span',`${xp.normal} XP / ${xp.boosted} XP no evento`,'',card);
 }
 add('p','Valores da primeira temporada. Nas seguintes, dobra o XP base da temporada. Plantas prontas antes do domingo também participam se colhidas durante o evento.');
}
