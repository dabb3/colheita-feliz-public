import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync} from 'node:fs';
import {animalPath,animalLayout,productPath} from '../src/presentation.ts';

test('scene props and pest frames use existing desktop art',()=>{
 const paths=Object.keys(animalLayout).map(id=>productPath(id,'READY')).filter(Boolean);
 for(let i=1;i<=4;i++)paths.push(`classic/effects/erva_daninha_${i}.png`);
 for(let i=1;i<=3;i++)paths.push(`classic/effects/verme_${i}.png`);
 for(const path of paths)assert.ok(existsSync(new URL('../../assets/'+path,import.meta.url)),path);
});
test('all animal poses map to files shipped with the desktop',()=>{
 for(const id of Object.keys(animalLayout)){
  if(id==='abelha')continue;
  for(const stage of ['BABY','YOUNG','ADULT'])for(const sleep of [false,true])for(const walking of [false,true]){
   const path=animalPath(id,stage,sleep,false,walking);
   assert.ok(existsSync(new URL('../../assets/'+path,import.meta.url)),path);
  }
 }
});

test('all desktop audio cues and music are available locally',()=>{
 for(const name of ['farm_loop.mp3',...['click_button','plant','water','harvest','buy','sell','weed','levelup'].map(id=>id+'.wav')])assert.ok(existsSync(new URL('../../assets/sounds/'+name,import.meta.url)),name);
});
