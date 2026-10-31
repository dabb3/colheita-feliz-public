export function xpPercent(current?:string,threshold?:string):number {
 const parts=[current??'0',threshold??'0'].map(value=>value.split('.'));
 const scale=Math.max(...parts.map(part=>part[1]?.length??0));
 const [xp,total]=parts.map(([whole,fraction=''])=>BigInt(whole+fraction.padEnd(scale,'0')));
 if(total===0n)return xp>0n?100:0;
 return Number(xp*100n/total>100n?100n:xp*100n/total);
}

export function pageItems<T>(items:T[],requested:number,size=10) {
 const pages=Math.max(1,Math.ceil(items.length/size)),page=Math.max(0,Math.min(pages-1,requested));
 return {items:items.slice(page*size,(page+1)*size),page,pages,total:items.length};
}

export function purchaseLock(level:number,required:number,vipRequired:boolean,vipActive:boolean,owned:boolean):string|undefined {
 if(owned)return 'Já comprado';
 if(level<required)return `Nível ${required}`;
 if(vipRequired&&!vipActive)return 'Exige VIP';
}

export function durationLabel(seconds:number) {
 if(seconds<=0)return 'Pronto';
 const minutes=Math.ceil(seconds/60),days=Math.floor(minutes/1440),hours=Math.floor(minutes%1440/60),rest=minutes%60;
 return [days?`${days} d`:'',hours?`${hours} h`:'',rest?`${rest} min`:''].filter(Boolean).join(' ');
}

export function animalCare(animal:{stage:string;status:string},type:{kind:string}) {
 if(type.kind==='GROWING'&&animal.stage==='ADULT')return{label:'Vender adulto',action:'SELL_ANIMAL'};
 if(type.kind==='PRODUCER'&&animal.status==='READY')return{label:'Recolher produto',action:'TEND_ANIMAL'};
 if(animal.status==='HUNGRY')return{label:'Alimentar',action:'TEND_ANIMAL'};
}
