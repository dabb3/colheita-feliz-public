import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync,existsSync,statSync} from 'node:fs';
const initial=['fence/trilho.png','fence/mourao.png','terrain/grama-default.png','terrain/placa-terreno.png','buildings/main-house.png','buildings/dog-house.png','icons/adubo-comum.png',...['avatar_masculino','amigos','cursor-action','moeda-comum','moeda-verde','icon-shop','deposit-icon'].map(n=>`icons/${n}.png`),...['pa-ui','saco-ui','regador-ui','mao-restaurada','herbicida-ui','pesticida-ui'].map(n=>`icons/hud/${n}.png`)];
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

test('HUD artwork is optimized for its visible size',()=>{
 for(const id of ['avatar_masculino','moeda-comum','moeda-verde','icon-shop','deposit-icon']){
  const png=readFileSync(file(`icons/${id}.png`));assert.ok(Math.max(png.readUInt32BE(16),png.readUInt32BE(20))<=128,id);
 }
});

test('new friend and game-avatar artwork is optimized for its visible size',()=>{
 for(const id of ['amigos','avatar_masculino2','avatar_masculino3','avatar_feminino','avatar_feminino2','avatar_feminino3']){
  const png=readFileSync(file(`icons/${id}.png`));assert.ok(Math.max(png.readUInt32BE(16),png.readUInt32BE(20))<=128,id);
 }
});

test('mouse pointer is a cropped 32px asset separate from the toolbar cursor',()=>{
 const png=readFileSync(new URL('../public/assets/classic/icons/cursor-action.png',import.meta.url));
 assert.equal(png.readUInt32BE(16),32);assert.equal(png.readUInt32BE(20),32);
 assert.ok(existsSync(new URL('../../assets/classic/icons/hud/cursor-ui.png',import.meta.url)));
});
