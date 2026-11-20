import test from 'node:test';
import assert from 'node:assert/strict';
import {existsSync,readFileSync} from 'node:fs';
import {BACKGROUND_PACKS,backgroundPack,backgroundPath} from '../src/background-catalog.ts';
import {backgroundArtPath} from '../src/presentation.ts';

test('catalog has the free classic pack plus ten paid packs, 33 real files and no Christmas art',()=>{
 assert.equal(BACKGROUND_PACKS.length,11);assert.equal(BACKGROUND_PACKS[0].id,'default');
 const files=BACKGROUND_PACKS.flatMap(pack=>Object.values(pack.files));
 assert.equal(files.length,33);assert.equal(new Set(files).size,33);
 for(const file of files)assert.ok(existsSync(new URL(`../../assets/classic/background/${file}`,import.meta.url)),file);
 assert.ok(!files.some(file=>file.includes('natal')));
 assert.equal(backgroundPack('inverno').files.day,'backgound-inverno.png','keeps the real file name with the typo');
});

test('client and server list the same paid packs',()=>{
 const java=readFileSync(new URL('../../server/src/main/java/br/com/colheitafeliz/server/Backgrounds.java',import.meta.url),'utf8');
 const paid=[...java.match(/PAID = List\.of\(([^)]*)\)/s)[1].matchAll(/"([a-z_]+)"/g)].map(match=>match[1]);
 assert.deepEqual(BACKGROUND_PACKS.slice(1).map(pack=>pack.id),paid);
});

test('background path follows pack and period and falls back to the classic pack',()=>{
 assert.equal(backgroundArtPath('sunset','savana'),'classic/background/background-savana-tarde.png');
 assert.equal(backgroundArtPath('night','mundo_dos_doces'),'classic/background/background_mundo_dos_doces_noite_real.png');
 for(const id of [undefined,null,'','natal','../x'])assert.equal(backgroundPath(id,'day'),'classic/background/background.png');
});
