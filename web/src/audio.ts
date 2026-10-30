type Clip=Pick<HTMLAudioElement,'play'|'pause'|'paused'|'loop'|'volume'|'currentTime'>;
type Settings={effects:boolean;music:boolean;effectsVolume:number;musicVolume:number};
const files:Record<string,string>={PLOW:'click_button',PLANT:'plant',WATER:'water',HARVEST:'harvest',BUY:'buy',SELL:'sell',WEED:'weed',LEVEL_UP:'levelup'};

export class GameAudio {
 private config:Settings={effects:false,music:false,effectsVolume:.65,musicVolume:.3};
 private factory:(path:string)=>Clip;
 private storage?:Pick<Storage,'getItem'|'setItem'>;
 private now:()=>number;
 private effects=new Map<string,Clip>();
 private lastPlayed=new Map<string,number>();
 private music?:Clip;
 private active=false;
 private unlocked=false;
 private closed=false;
 constructor(factory:(path:string)=>Clip=path=>new Audio(path),storage?:Pick<Storage,'getItem'|'setItem'>,now:()=>number=()=>performance.now()){
  this.factory=factory;this.now=now;
  try{this.storage=storage??localStorage;const saved=JSON.parse(this.storage.getItem('colheita:audio')??'{}');
   for(const key of ['effects','music'] as const)if(typeof saved[key]==='boolean')this.config[key]=saved[key];
   for(const key of ['effectsVolume','musicVolume'] as const)if(typeof saved[key]==='number'&&Number.isFinite(saved[key]))this.config[key]=Math.max(0,Math.min(1,saved[key]));
  }catch{/* Audio preferences are optional when storage is blocked. */}
 }
 get settings(){return{...this.config};}
 unlock(){if(this.closed)return;this.unlocked=true;this.apply();}
 setActive(active:boolean){if(this.closed)return;this.active=active;this.apply();}
 setEffects(enabled:boolean){this.config.effects=enabled;this.save();this.apply();}
 setMusic(enabled:boolean){this.config.music=enabled;this.save();this.apply();}
 setVolume(channel:'effects'|'music',volume:number){
  if(!Number.isFinite(volume))return;this.config[channel==='effects'?'effectsVolume':'musicVolume']=Math.max(0,Math.min(1,volume));
  for(const clip of this.effects.values())clip.volume=this.config.effectsVolume;this.save();this.apply();
 }
 play(cue:string){
  if(this.closed||!this.active||!this.unlocked||!this.config.effects||!files[cue])return;
  const now=this.now();if(now-(this.lastPlayed.get(cue)??-Infinity)<150)return;
  if([...this.effects.values()].filter(clip=>!clip.paused).length>=3)return;
  try{let clip=this.effects.get(cue);if(!clip){clip=this.factory(`/assets/sounds/${files[cue]}.wav`);this.effects.set(cue,clip);}
   clip.volume=this.config.effectsVolume;clip.currentTime=0;this.lastPlayed.set(cue,now);this.start(clip);
  }catch{/* Missing or blocked audio must not reject a confirmed game action. */}
 }
 close(){this.active=false;this.apply();this.closed=true;this.effects.clear();this.music=undefined;}
 private start(clip:Clip){try{void Promise.resolve(clip.play()).catch(()=>{});}catch{/* Autoplay can be denied synchronously too. */}}
 private apply(){
  if(this.closed||!this.active||!this.unlocked){this.music?.pause();for(const clip of this.effects.values())clip.pause();return;}
  if(!this.config.effects)for(const clip of this.effects.values())clip.pause();
  if(!this.config.music){this.music?.pause();return;}
  try{if(!this.music){this.music=this.factory('/assets/sounds/farm_loop.mp3');this.music.loop=true;}
   this.music.volume=this.config.musicVolume;if(this.music.paused)this.start(this.music);
  }catch{/* The game remains usable without a music device or decoder. */}
 }
 private save(){try{this.storage?.setItem('colheita:audio',JSON.stringify(this.config));}catch{/* Keep the current session settings in memory. */}}
}
