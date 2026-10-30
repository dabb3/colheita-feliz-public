import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync,statSync} from 'node:fs';
const initial=['fence/trilho.png','fence/mourao.png','terrain/grama-default.png','terrain/placa-terreno.png','buildings/main-house.png','buildings/dog-house.png','icons/adubo-comum.png',...['pa-ui','saco-ui','regador-ui','mao-restaurada','herbicida-ui','pesticida-ui'].map(n=>`icons/hud/${n}.png`)];
function file(name){const optimized=new URL('../public/assets/classic/'+name,import.meta.url);return existsSync(optimized)?optimized:new URL('../../assets/classic/'+name,import.meta.url);}
test('initial farm assets fit 9 MB leaving room for scripts and API under 10 MB',()=>{
 for(const period of ['','-sunset','-night']){
  const size=[...initial,`background/background${period}.png`].reduce((sum,name)=>sum+statSync(file(name)).size,0);
  assert.ok(size<9_000_000,`${period||'day'}: ${size} bytes`);
 }
});
test('web fertilizer icons are bounded to their displayed scale',()=>{
 for(const id of ['comum','rapido','super','amor']){const png=readFileSync(file(`icons/adubo-${id}.png`));assert.ok(Math.max(png.readUInt32BE(16),png.readUInt32BE(20))<=96);}
});
