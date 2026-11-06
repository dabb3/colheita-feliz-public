import test from 'node:test';
import assert from 'node:assert/strict';
import {seedArtPath,christmasActive,backgroundArtPath,musicArtPath} from '../src/presentation.ts';

test('Mini-Bis seed uses its supplied name and regular seed paths remain unchanged',()=>{
 assert.equal(seedArtPath('mini-bis'),'classic/crops/seeds/semente-mini-bis.png');
 assert.equal(seedArtPath('coco'),'classic/crops/seeds/semente_coco.png');
 assert.equal(seedArtPath('batata-doce'),'classic/crops/seeds/semente_batata-doce.png');
});
test('Christmas theme ends at midnight São Paulo including an open night session',()=>{
 const before=new Date('2027-01-01T02:59:59.999Z'),after=new Date('2027-01-01T03:00:00Z');
 assert.equal(christmasActive(before),true);assert.equal(christmasActive(after),false);
 assert.equal(backgroundArtPath('day',before),'classic/background/background-natal-dia.png');
 assert.equal(backgroundArtPath('sunset',before),'classic/background/background-natal-por-do-sol.png');
 assert.equal(backgroundArtPath('night',before),'classic/background/background-natal-noite.png');
 for(const period of ['day','sunset','night'] as const)assert.equal(backgroundArtPath(period,after),`classic/background/background${period==='day'?'':'-'+period}.png`);
 assert.equal(musicArtPath('day',before),'musica-evento-natal-dia-e-por-do-sol.mp3');assert.equal(musicArtPath('sunset',before),musicArtPath('day',before));
 assert.equal(musicArtPath('night',before),'musica-evento-natal-noite.mp3');assert.equal(musicArtPath('night',after),'noite.mp3');assert.equal(musicArtPath('day',after),'farm_loop.mp3');
});
