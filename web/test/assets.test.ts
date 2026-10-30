import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync} from 'node:fs';
import {animalPath,animalLayout} from '../src/presentation.ts';
test('all animal poses map to files shipped with the desktop',()=>{
 for(const id of Object.keys(animalLayout)){
  if(id==='abelha')continue;
  for(const stage of ['BABY','YOUNG','ADULT'])for(const sleep of [false,true]){
   const path=animalPath(id,stage,sleep,false);
   assert.ok(existsSync(new URL('../../assets/'+path,import.meta.url)),path);
  }
 }
});
