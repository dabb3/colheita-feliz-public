import test from 'node:test';
import assert from 'node:assert/strict';
import {socialCooldown,mergeSocialCooldowns} from '../src/social-cooldowns.ts';

const now=Date.parse('2026-11-21T12:00:00Z');
const ready={stealNextAt:null,insectsNextAt:null,weedsNextAt:null};
test('social timers are independent and release only at the exact server deadline',()=>{
 const timers={...ready,insectsNextAt:'2026-11-21T15:00:00Z'};
 assert.equal(socialCooldown('STEAL',timers,now).available,true);
 assert.equal(socialCooldown('SPREAD_WEEDS',timers,now).available,true);
 assert.equal(socialCooldown('SPREAD_INSECTS',timers,now).label,'3h 00min');
 assert.equal(socialCooldown('SPREAD_INSECTS',timers,now+10799999).available,false);
 assert.equal(socialCooldown('SPREAD_INSECTS',timers,now+10799999).label,'01s');
 assert.equal(socialCooldown('SPREAD_INSECTS',timers,now+10800000).available,true);
 assert.equal(socialCooldown('SPREAD_INSECTS',timers,now+10800001).label,'Pronto');
});
test('missing, malformed, unsynchronized and pending data never claim availability',()=>{
 for(const timers of [undefined,{...ready,insectsNextAt:'invalid'},{...ready,insectsNextAt:undefined}])assert.equal(socialCooldown('SPREAD_INSECTS',timers,now).label,'Verificando…');
 assert.equal(socialCooldown('STEAL',ready,NaN).available,false);
 assert.equal(socialCooldown('STEAL',ready,now,true).label,'Pendente');
 assert.match(socialCooldown('STEAL',ready,now,true).message,/resultado/);
 assert.equal(socialCooldown('STEAL',{...ready,stealNextAt:new Date(now+59000).toISOString()},now).label,'59s');
 assert.equal(socialCooldown('STEAL',{...ready,stealNextAt:new Date(now+2700000).toISOString()},now).label,'45min');
});
test('a stale snapshot cannot shorten an already observed deadline',()=>{
 const recent={...ready,stealNextAt:'2026-11-21T15:00:00Z'};
 assert.deepEqual(mergeSocialCooldowns(recent,ready),recent);
 assert.deepEqual(mergeSocialCooldowns(ready,recent),recent);
 assert.equal(mergeSocialCooldowns(recent,undefined),undefined,'old API remains unknown');
});
