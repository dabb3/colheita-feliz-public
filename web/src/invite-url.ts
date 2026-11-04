/** Lê o convite de `?invite=` e devolve o endereço sem ele (null quando não havia convite). */
export function takeInvite(href:string):{invite:string|null;cleaned:string|null}{
 const url=new URL(href),invite=url.searchParams.get('invite');
 if(!invite)return{invite:null,cleaned:null};
 url.searchParams.delete('invite');
 return{invite,cleaned:url.href};
}
