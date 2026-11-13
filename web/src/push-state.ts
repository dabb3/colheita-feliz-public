export interface Binding {account:string;bindingId:string;endpoint:string;deviceToken:string}
export interface Revocation {endpoint:string;deviceToken:string;deviceRevision:number}
export interface PushState {revision:number;account?:string;deviceToken?:string;active?:Binding;pending:Revocation[]}
export function changeBinding(previous:PushState|undefined,active?:Binding):PushState {
 const s=previous??{revision:0,pending:[]};
 if(!Number.isSafeInteger(s.revision+1))throw new Error('Revisão indisponível.');
 const revision=s.revision+1,pending=[...s.pending];
 if(s.active)pending.push({endpoint:s.active.endpoint,deviceToken:s.active.deviceToken,deviceRevision:revision});
 return {revision,account:s.account,deviceToken:active?.deviceToken??s.deviceToken,active,pending};
}
export function acceptsPush(state:PushState|undefined,payload:{type?:string;bindingId?:string}):boolean{return payload.type==='TEST'&&!!state?.active&&state.active.bindingId===payload.bindingId;}
export async function pushState(update?:(state:PushState|undefined)=>PushState):Promise<PushState|undefined>{
 return new Promise((resolve,reject)=>{
  const open=indexedDB.open('colheita-device-push',1);
  open.onupgradeneeded=()=>open.result.createObjectStore('state');
  open.onerror=()=>reject(open.error);
  open.onsuccess=()=>{const db=open.result,tx=db.transaction('state',update?'readwrite':'readonly'),store=tx.objectStore('state'),read=store.get('device');let result:PushState|undefined;
   read.onsuccess=()=>{try{result=update?update(read.result):read.result;if(update)store.put(result,'device');}catch(error){tx.abort();reject(error);}};
   tx.oncomplete=()=>{db.close();resolve(result);};tx.onabort=tx.onerror=()=>{db.close();reject(tx.error??new Error('Armazenamento indisponível.'));};
  };
 });
}
