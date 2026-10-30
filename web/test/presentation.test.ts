import test from 'node:test';
import assert from 'node:assert/strict';
import {periodAt,tileCenter,pickTile,animalPath} from '../src/presentation.ts';
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
