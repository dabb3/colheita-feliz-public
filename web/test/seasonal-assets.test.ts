import test from 'node:test';
import assert from 'node:assert/strict';
import {seedArtPath,christmasActive,backgroundArtPath,musicArtPath} from '../src/presentation.ts';

test('Mini-Bis seed uses its supplied name and regular seed paths remain unchanged',()=>{
 assert.equal(seedArtPath('mini-bis'),'classic/crops/seeds/semente-mini-bis.png');
 assert.equal(seedArtPath('coco'),'classic/crops/seeds/semente_coco.png');
 assert.equal(seedArtPath('batata-doce'),'classic/crops/seeds/semente_batata-doce.png');
});
test('farm always uses the original day, sunset and night backgrounds',()=>{
 assert.equal(backgroundArtPath('day'),'classic/background/background.png');
 assert.equal(backgroundArtPath('sunset'),'classic/background/background-sunset.png');
 assert.equal(backgroundArtPath('night'),'classic/background/background-night.png');
});
test('Christmas music ends at midnight São Paulo including an open night session',()=>{
 const before=new Date('2027-01-01T02:59:59.999Z'),after=new Date('2027-01-01T03:00:00Z');
 assert.equal(christmasActive(before),true);assert.equal(christmasActive(after),false);
 assert.equal(musicArtPath('day',before),'musica-evento-natal-dia-e-por-do-sol.mp3');assert.equal(musicArtPath('sunset',before),musicArtPath('day',before));
 assert.equal(musicArtPath('night',before),'musica-evento-natal-noite.mp3');assert.equal(musicArtPath('night',after),'noite.mp3');assert.equal(musicArtPath('day',after),'farm_loop.mp3');
});
