import test from 'node:test';
import assert from 'node:assert/strict';
import {eventStatus,eventXp,eventBoosted} from '../src/events.ts';
const event={id:'sunday-harvest-2026-11-22',state:'ACTIVE',multiplier:2,startsAt:'2026-11-22T03:00:00Z',endsAt:'2026-11-23T03:00:00Z',nextStartsAt:'2026-11-29T03:00:00Z',nextEndsAt:'2026-11-30T03:00:00Z',cropIds:['nabo','cenoura'],timezone:'America/Sao_Paulo'};
test('event display follows server window and announces the next Sunday after expiry',()=>{
 assert.equal(eventStatus(event,Date.parse('2026-11-22T02:59:59Z')).phase,'upcoming');
 assert.equal(eventStatus(event,Date.parse(event.startsAt)).phase,'active');
 const ended=eventStatus(event,Date.parse(event.endsAt));assert.equal(ended.phase,'upcoming');assert.equal(ended.target,event.nextStartsAt);
 assert.equal(eventStatus(event,Date.parse(event.startsAt)).seconds,86400);
 assert.equal(eventStatus(event,Date.parse('2026-11-22T03:00:10Z')).seconds,86390);
 assert.equal(eventStatus(undefined,NaN).phase,'unavailable');
 assert.equal(eventStatus({...event,endsAt:'invalid'},0).phase,'unavailable');
});
test('event badges and first-season XP only use the eligible IDs from the server',()=>{
 assert.equal(eventBoosted(event,'nabo',Date.parse(event.startsAt)),true);
 assert.equal(eventBoosted(event,'bronze',Date.parse(event.startsAt)),false);
 assert.equal(eventBoosted(event,'nabo',Date.parse(event.endsAt)),false);
 assert.deepEqual(eventXp(36000,2),{normal:'15',boosted:'30'});
 assert.deepEqual(eventXp(54000,2),{normal:'22,5',boosted:'45'});
});

test('stale projection still recognizes its advertised next Sunday and expires safely afterwards',()=>{
 assert.equal(eventStatus(event,Date.parse(event.nextStartsAt)).phase,'active');
 assert.equal(eventStatus(event,Date.parse(event.nextStartsAt)).target,event.nextEndsAt);
 assert.equal(eventBoosted(event,'nabo',Date.parse(event.nextStartsAt)),true);
 assert.equal(eventStatus(event,Date.parse(event.nextEndsAt)).phase,'unavailable');
});
