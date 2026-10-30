import test from 'node:test';
import assert from 'node:assert/strict';
import {periodAt,tileCenter,pickTile,animalPath,animalLayout,cropSize,animalWidth,productPath,terrainSize,peacockWidth,animalMotion,animalFlip,poseWidth} from '../src/presentation.ts';

test('plant dimensions match desktop stage heights and preserve aspect',()=>{
 for(let stage=1;stage<=5;stage++)assert.deepEqual(cropSize(stage,1.5),{width:(32+stage*16)*1.5,height:32+stage*16});
 assert.deepEqual(terrainSize,{width:188,height:100});
});

test('animal growth changes visible size without moving the ground anchor',()=>{
 assert.equal(animalWidth('porco','BABY'),125*.65);
 assert.equal(animalWidth('porco','YOUNG'),125*.85);
 assert.equal(animalWidth('porco','ADULT'),125);
});

test('only ready producers display collection props, never a second hive',()=>{
 for(const id of Object.keys(animalLayout))assert.equal(productPath(id,'PRODUCING'),undefined);
 assert.equal(productPath('vaca','READY'),'classic/animals/vaca/balde-vaca.png');
 assert.equal(productPath('galinha','READY'),productPath('pato','READY'));
 assert.equal(productPath('abelha','READY'),undefined);
 assert.equal(productPath('pavao','READY'),undefined);
 assert.equal(productPath('cavalo','READY'),undefined);
});
test('day periods use local minute boundaries',()=>{
 for(const [h,m,expected] of [[5,59,'night'],[6,0,'day'],[16,59,'day'],[17,0,'sunset'],[18,30,'sunset'],[18,31,'night']]) assert.equal(periodAt(new Date(2026,9,29,h,m)),expected);
});
test('all 30 tile centers roundtrip and clear the bottom toolbar',()=>{
 for(let r=0;r<5;r++) for(let c=0;c<6;c++) {
  const p=tileCenter(c,r); assert.deepEqual(pickTile(p.x,p.y),{column:c,row:r}); assert.ok(p.y+36<790);
 }
 assert.equal(pickTile(0,0),null);
});
test('pigeon stays static and peacock alternates only feather pose',()=>{
 assert.match(animalPath('pombo','ADULT',false,true),/pomba-adulta.png$/);
 assert.match(animalPath('pavao','ADULT',false,true),/pavao-aberto.png$/);
 assert.match(animalPath('pavao','ADULT',true,true),/pavao-dormindo.png$/);
});

test('peacock poses preserve visible height and aspect',()=>{assert.equal(peacockWidth(80,.4),32);assert.equal(peacockWidth(80,1.5),120);});

test('walks stay bounded and alternate frames only while translating, including horse',()=>{
 for(const id of ['vaca','cavalo','burro','cabra','galinha']){
  let previous=animalMotion(id,0,false),moved=false,rested=false;
  for(let seconds=.05;seconds<20;seconds+=.05){const motion=animalMotion(id,seconds,false),[x,y]=animalLayout[id],radius=id==='cavalo'?36:20;
   assert.ok(Math.abs(motion.x-x)<=radius+.001);assert.ok(Math.abs(motion.y-y)<=radius*.35+.001);
   const distance=Math.hypot(motion.x-previous.x,motion.y-previous.y);assert.ok(distance<=18*.05+.001);
   if(motion.walking){moved ||= distance>0;if(distance>.001)assert.equal(motion.facingRight,motion.x>previous.x);}
   else{rested=true;assert.equal(motion.alternate,false);}
   assert.equal(animalMotion(id,seconds,true).walking,false);previous=motion;
  }
  assert.ok(moved&&rested,id);
 }
});

test('static species never roam and facing uses original sprite orientation',()=>{
 for(const id of ['pombo','pavao','abelha'])for(const seconds of [0,4,12]){
  const motion=animalMotion(id,seconds,false);assert.equal(motion.x,animalLayout[id][0]);assert.equal(motion.y,animalLayout[id][1]);assert.equal(motion.walking,false);
 }
 assert.equal(animalFlip('cavalo',true),false);assert.equal(animalFlip('cavalo',false),true);
 assert.equal(animalFlip('vaca',true),true);assert.equal(animalFlip('vaca',false),false);
 assert.equal(poseWidth(100,2,3),150);
 assert.ok(animalPath('cavalo','ADULT',false,false,true).endsWith('cavalo-andando.png'));
});
