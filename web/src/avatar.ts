export const CLASSIC_AVATAR='/assets/classic/icons/avatar_masculino.png';
export type AvatarChoice='classic'|'google';
interface StorageLike {getItem(key:string):string|null;setItem(key:string,value:string):void;}
export function googleAvatar(value:unknown):string|undefined {
 if(typeof value!=='string')return;
 try{const url=new URL(value);if(url.protocol==='https:'&&url.hostname==='lh3.googleusercontent.com'&&!url.username&&!url.password&&!url.port)return url.href;}catch{/* Missing or invalid provider photo uses the original avatar. */}
}
export function avatarSource(choice:AvatarChoice,photo?:string){return choice==='google'?googleAvatar(photo)??CLASSIC_AVATAR:CLASSIC_AVATAR;}
export function readAvatar(storage:StorageLike|undefined,user:string):AvatarChoice {
 try{return storage?.getItem(`colheita:avatar:${user}`)==='google'?'google':'classic';}catch{return 'classic';}
}
export function saveAvatar(storage:StorageLike|undefined,user:string,choice:AvatarChoice){
 try{storage?.setItem(`colheita:avatar:${user}`,choice);}catch{/* A browser preference must never prevent playing. */}
}
