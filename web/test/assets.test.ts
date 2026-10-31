import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync} from 'node:fs';
import {animalPath,animalLayout,animalProductPath} from '../src/presentation.ts';
test('all animal poses map to files shipped with the desktop',()=>{
 for(const id of Object.keys(animalLayout)){
  if(id==='abelha')continue;
  for(const stage of ['BABY','YOUNG','ADULT'])for(const sleep of [false,true])for(const walking of id==='pavao'?[false]:[false,true]){
   const path=animalPath(id,stage,sleep,walking,false);
   assert.ok(existsSync(new URL('../../assets/'+path,import.meta.url)),path);
  }
 }
});
test('generic feed marker uses an asset shipped with the desktop',()=>{
 assert.ok(existsSync(new URL('../../assets/classic/icons/racao-generica.png',import.meta.url)));
});
test('ready animal product markers use desktop assets',()=>{
 for(const id of ['galinha','pato','ovelha','vaca'])assert.ok(existsSync(new URL('../../assets/'+animalProductPath(id),import.meta.url)));
});
test('bee hive atlas used by the desktop is present for both scene sprites',()=>{
 assert.ok(existsSync(new URL('../../assets/classic/animals/abelha/colmeia-abelha-atlas.png',import.meta.url)));
});
test('animated weed and insect frames use desktop assets',()=>{
 for(const frame of [1,2,3,4]){
  assert.ok(existsSync(new URL(`../../assets/classic/effects/erva_daninha_${frame}.png`,import.meta.url)));
 }
 for(const frame of [1,2,3])assert.ok(existsSync(new URL(`../../assets/classic/effects/verme_${frame}.png`,import.meta.url)));
});
test('music loop and action sound cues exist locally',()=>{
 for(const name of ['farm_loop.mp3',...['click_button','plant','water','harvest','buy','sell','weed','levelup'].map(id=>`${id}.wav`)])assert.ok(existsSync(new URL(`../../assets/sounds/${name}`,import.meta.url)),name);
});
