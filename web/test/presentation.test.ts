import test from 'node:test';
import assert from 'node:assert/strict';
import {periodAt,tileCenter,pickTile,animalPath,animalLayout,cropSize,animalWidth,productPath,terrainSize,peacockWidth} from '../src/presentation.ts';

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
