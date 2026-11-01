export const CLASSIC_AVATAR='/assets/classic/icons/avatar_masculino.png';
export const GAME_AVATARS=[
 {id:'classic',label:'Masculino 1',file:'avatar_masculino.png'},
 {id:'male-2',label:'Masculino 2',file:'avatar_masculino2.png'},
 {id:'male-3',label:'Masculino 3',file:'avatar_masculino3.png'},
 {id:'female-1',label:'Feminino 1',file:'avatar_feminino.png'},
 {id:'female-2',label:'Feminino 2',file:'avatar_feminino2.png'},
 {id:'female-3',label:'Feminino 3',file:'avatar_feminino3.png'},
] as const;
export type AvatarChoice=typeof GAME_AVATARS[number]['id']|'google';
interface StorageLike {getItem(key:string):string|null;setItem(key:string,value:string):void;}
export function googleAvatar(value:unknown):string|undefined {
 if(typeof value!=='string')return;
 try{const url=new URL(value);if(url.protocol==='https:'&&url.hostname==='lh3.googleusercontent.com'&&!url.username&&!url.password&&!url.port)return url.href;}catch{/* Missing or invalid provider photo uses the original avatar. */}
}
export function avatarSource(choice:AvatarChoice,photo?:string){return choice==='google'?googleAvatar(photo)??CLASSIC_AVATAR:`/assets/classic/icons/${GAME_AVATARS.find(avatar=>avatar.id===choice)?.file??'avatar_masculino.png'}`;}
export function readAvatar(storage:StorageLike|undefined,user:string):AvatarChoice {
 try{const choice=storage?.getItem(`colheita:avatar:${user}`);return choice==='google'?'google':GAME_AVATARS.find(avatar=>avatar.id===choice)?.id??'classic';}catch{return 'classic';}
}
export function saveAvatar(storage:StorageLike|undefined,user:string,choice:AvatarChoice){
 try{storage?.setItem(`colheita:avatar:${user}`,choice);}catch{/* A browser preference must never prevent playing. */}
}
