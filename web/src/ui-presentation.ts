import type {Crop,Tile,Farm} from './types.ts';

export function dailyAllowanceText(status?:Farm['dailyAllowance']) {
 if(!status)return undefined;
 const resetAt=Date.parse(status.nextResetAt);
 const reset=Number.isFinite(resetAt)?`Próxima virada: ${new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(resetAt)} (horário local; dia UTC).`:'Renova à meia-noite UTC.';
 return {title:`Ajuda diária · ${status.amount} ouro`,description:'Resgate uma vez por dia. Valores não resgatados não acumulam.',button:status.available?`Resgatar ${status.amount} ouro`:'Já resgatado',disabled:!status.available,reset};
}

export const CROP_TIME_NOTICE='Tempo nominal: mantenha a rega em dia e a planta sem pragas. Solo seco cresce à metade da velocidade; ervas e minhocas pausam o crescimento.';

export function seedGrowthLabel(crop:Pick<Crop,'growthSeconds'|'seasons'>) {
 return `${durationLabel(crop.growthSeconds)} com rega em dia · ${crop.seasons} temporada(s)`;
}

function forecastSuffix(timestamp?:number) {
 if(timestamp===undefined||!Number.isFinite(timestamp)||Math.abs(timestamp)>8.64e15)return '';
 return ` Previsão: ${new Intl.DateTimeFormat('pt-BR',{day:'2-digit',month:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).format(timestamp)}.`;
}

export function cropTimingText(tile:Pick<Tile,'state'|'watered'|'dry'|'paused'|'weeds'|'insects'|'nominalRemainingSeconds'|'remainingSeconds'>,estimatedReadyAt?:number,estimatedNominalReadyAt?:number) {
 if(tile.state==='READY'||tile.state==='DEAD')return {nominal:'',condition:'',estimate:''};
 const paused=tile.paused||tile.weeds||tile.insects>0,unwatered=tile.watered===false;
 const nominal=`${paused?'Após remover as pragas, com rega em dia':'Com rega em dia e sem pragas'}: ${durationLabel(Math.max(1,tile.nominalRemainingSeconds))} restantes`;
 const condition=paused?'Crescimento pausado. Remova as pragas para continuar.':unwatered?'Regue para iniciar o crescimento.':tile.dry?'Solo seco: crescimento à metade da velocidade. Regue para acelerar.':'Mantenha a rega em dia para evitar atrasos.';
 if(paused||unwatered)return {nominal,condition,estimate:''};
 const nominalForecast=forecastSuffix(estimatedNominalReadyAt);
 return {nominal:nominal+(nominalForecast?'.'+nominalForecast:''),condition,estimate:`Sem nova rega e sem pragas: ${durationLabel(Math.max(1,tile.remainingSeconds))} restantes.${forecastSuffix(estimatedReadyAt)}`};
}

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

export function fertilizerDuration(seconds:number) {
 const minutes=Math.max(1,Math.ceil(seconds/60)),hours=Math.floor(minutes/60),rest=minutes%60;
 return [hours?`${hours} ${hours===1?'hora':'horas'}`:'',rest?`${rest} ${rest===1?'minuto':'minutos'}`:''].filter(Boolean).join(' e ');
}
