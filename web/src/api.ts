import type {Farm, Reply, CommandFields} from './types.ts';

interface StorageLike {getItem(key:string):string|null;setItem(key:string,value:string):void;removeItem(key:string):void;}
interface Pending {path:string;body:string;}
export class ApiError extends Error {
 readonly status:number;
 constructor(status:number,message:string) {super(message);this.status=status;}
}
export function acceptFarm<T extends {owner:string;revision:string}>(current:T|undefined,incoming:T):T {
 return current?.owner===incoming.owner && BigInt(current.revision)>BigInt(incoming.revision) ? current : incoming;
}
export class ApiClient {
 private readonly key:string;
 private readonly base:string;
 private readonly token:()=>Promise<string>;
 private readonly storage:StorageLike;
 private readonly send:typeof fetch;
 pending:Pending|null;
 busy=false;
 private lease?:{started:number;promise:Promise<{requestId:string}>};
 private readonly now:()=>number;
 constructor(base:string,user:string,token:()=>Promise<string>,storage:StorageLike,send:typeof fetch=fetch,now:()=>number=()=>performance.now()) {
  this.base=base;this.token=token;this.storage=storage;this.send=send;
  this.key=`colheita:pending:${base}:${user}`;
  const saved=storage.getItem(this.key);
  this.pending=saved ? JSON.parse(saved) : null;
  this.now=now;
 }
 async prepareRequestId():Promise<void> {if(!this.pending)await this.requestId();}
 private requestId():Promise<{requestId:string}> {
  // Keep at most one server-issued id, briefly: long-hidden tabs must acquire a fresh one.
  if(this.lease&&this.now()-this.lease.started<30_000)return this.lease.promise;
  const lease={started:this.now(),promise:this.get<{requestId:string}>('/v1/request-id')};
  this.lease=lease;
  void lease.promise.catch(()=>{if(this.lease===lease)this.lease=undefined;});
  return lease.promise;
 }
 async get<T>(path:string):Promise<T> {return this.request(path);}
 async mutate(path:string,fields:CommandFields):Promise<Reply> {
  if(this.pending || this.busy) throw new Error('Resolva a ação pendente antes de continuar.');
  this.busy=true;
  try {
   const reservation=this.requestId(),lease=await reservation;
   if(this.lease?.promise===reservation)this.lease=undefined;
   const pending={path,body:JSON.stringify({...fields,requestId:lease.requestId})};
   // Persist before sending: a reload after an unknown result must reuse this UUID.
   this.storage.setItem(this.key,JSON.stringify(pending)); this.pending=pending;
  } finally {this.busy=false;}
  return this.retry();
 }
 async retry():Promise<Reply> {
  if(!this.pending || this.busy) throw new Error('Não há ação pendente disponível.');
  this.busy=true;
  try {
   const response=await this.request<Reply>(this.pending.path,this.pending.body);
   this.clear();return response;
  } catch(error) {
   if(error instanceof ApiError && [400,403,404,409,410,413,415,429].includes(error.status)) this.clear();
   throw error;
  } finally {this.busy=false;}
 }
 private clear() {this.storage.removeItem(this.key);this.pending=null;}
 private async request<T>(path:string,body?:string):Promise<T> {
  const started=this.now();
  const token=await this.token();
  const send=this.send;
  const response=await send(this.base+path,{method:body?'POST':'GET',
   headers:{Authorization:`Bearer ${token}`,...(body?{'Content-Type':'application/json'}:{})},body,
   signal:AbortSignal.timeout(65_000),cache:'no-store'});
  const json=await response.json();
  const serverTiming=response.headers.get('Server-Timing');
  if(serverTiming)try{
   const route=path.replace(/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi,':farm');
   const candidate=body?JSON.parse(body).action:undefined,action=typeof candidate==='string'&&/^[A-Z_]{1,32}$/.test(candidate)?candidate:undefined;
   console.debug('Colheita API timing',JSON.stringify({route,action,durationMs:Math.round((this.now()-started)*100)/100,serverTiming}));
  }catch{/* Diagnostics must never change the outcome of a confirmed action. */}
  if(!response.ok) throw new ApiError(response.status,json.error??'Não foi possível concluir.');
  const lease=json?.requestLease,serverDate=Date.parse(response.headers.get('Date')??'');
  if(typeof lease?.requestId==='string'&&/^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(lease.requestId)
   &&typeof lease.expiresAt==='string'&&Number.isFinite(serverDate)&&Date.parse(lease.expiresAt)>serverDate+30_000){
   this.lease={started:this.now(),promise:Promise.resolve({requestId:lease.requestId})};
  }
  return json as T;
 }
}
