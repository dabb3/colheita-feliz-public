import test from 'node:test';
import assert from 'node:assert/strict';
import {GameAudio} from '../src/audio.ts';

function fixture(stored=null){
 const clips=[],writes=[];let now=0;
 const audio=new GameAudio(path=>{const clip={path,paused:true,currentTime:0,volume:0,loop:false,plays:0,play(){this.paused=false;this.plays++;return Promise.resolve();},pause(){this.paused=true;}};clips.push(clip);return clip;}, {getItem:()=>stored,setItem:(key,value)=>writes.push(value)},()=>now);
 return{audio,clips,writes,time:value=>{now=value;}};
}
test('audio waits for a gesture and respects independent music and effects settings',()=>{
 const {audio,clips}=fixture();audio.setActive(true);audio.setEffects(true);audio.play('WATER');assert.equal(clips.length,0);
 audio.unlock();audio.play('WATER');assert.equal(clips[0].path,'/assets/sounds/water.wav');assert.equal(clips[0].plays,1);
 audio.setMusic(true);const music=clips.find(c=>c.path.endsWith('.mp3'));assert.equal(music.loop,true);
 audio.setEffects(false);assert.equal(clips[0].paused,true);assert.equal(music.paused,false);
 audio.setActive(false);assert.ok(clips.every(c=>c.paused));audio.close();audio.unlock();assert.ok(clips.every(c=>c.paused));
});
test('effects limit overlap, debounce repeated cues and bound volumes',()=>{
 const {audio,clips,time,writes}=fixture();audio.setActive(true);audio.unlock();audio.setEffects(true);
 audio.play('WATER');audio.play('WATER');assert.equal(clips[0].plays,1);
 audio.play('PLANT');audio.play('HARVEST');audio.play('BUY');assert.equal(clips.filter(c=>!c.paused).length,3);
 clips[0].pause();time(200);audio.play('WATER');assert.equal(clips[0].plays,2);
 audio.setVolume('effects',5);assert.equal(audio.settings.effectsVolume,1);assert.equal(clips[0].volume,1);
 audio.setVolume('music',-1);assert.equal(audio.settings.musicVolume,0);assert.ok(writes.length>0);
});
test('bad storage and autoplay failures do not break game actions',async()=>{
 const audio=new GameAudio(()=>({paused:true,currentTime:0,volume:0,play:()=>Promise.reject(new Error('autoplay')),pause(){}}),{getItem(){throw new Error('blocked');},setItem(){throw new Error('blocked');}});
 audio.setActive(true);audio.unlock();audio.setEffects(true);audio.play('LEVEL_UP');await Promise.resolve();audio.close();
 assert.equal(audio.settings.effects,true);
});
