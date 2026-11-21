import type {SocialCooldowns} from './types.ts';

const socialKeys={STEAL:'stealNextAt',SPREAD_INSECTS:'insectsNextAt',SPREAD_WEEDS:'weedsNextAt'} as const;
export function isSocialAction(action:string):action is keyof typeof socialKeys{return Object.hasOwn(socialKeys,action);}

export function socialCooldown(action:keyof typeof socialKeys,timers:SocialCooldowns|undefined,now:number,pending=false){
 if(pending)return{available:false,label:'Pendente',message:'Aguarde a confirmação do resultado da ação pendente.',waiting:false};
 const next=timers?.[socialKeys[action]];
 if(!Number.isFinite(now)||next===undefined||(next!==null&&(typeof next!=='string'||!next.endsWith('Z')||!Number.isFinite(Date.parse(next)))))
  return{available:false,label:'Verificando…',message:'Verificando o tempo no servidor. Aguarde a sincronização.',waiting:false};
 const left=next===null?0:Date.parse(next)-now;
 if(left<=0)return{available:true,label:'Pronto',message:'Disponível para uma nova tentativa a cada 3h.',waiting:false};
 const seconds=Math.ceil(left/1000),minutes=Math.ceil(seconds/60),hours=Math.floor(minutes/60);
 const label=seconds<60?`${String(seconds).padStart(2,'0')}s`:hours?`${hours}h ${String(minutes%60).padStart(2,'0')}min`:`${minutes}min`;
 return{available:false,label,message:`Você poderá tentar novamente em ${label}.`,waiting:true};
}

/** Last-use deadlines never move backwards within an account, even across delayed replies. */
export function mergeSocialCooldowns(previous:SocialCooldowns|undefined,incoming:SocialCooldowns|undefined):SocialCooldowns|undefined{
 if(!incoming||!previous)return incoming;
 const merged={...incoming};
 for(const key of Object.values(socialKeys))if(Date.parse(previous[key]??'')>Date.parse(incoming[key]??'1970-01-01T00:00:00Z'))merged[key]=previous[key];
 return Object.values(socialKeys).some(key=>merged[key]!==incoming[key])?merged:incoming;
}
