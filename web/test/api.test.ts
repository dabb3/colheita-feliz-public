import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiClient, acceptFarm} from '../src/api.ts';

function memory() { const entries = new Map(); return { getItem: k => entries.get(k) ?? null, setItem: (k,v) => entries.set(k,v), removeItem: k => entries.delete(k) }; }
test('unknown purchase result preserves request across reload and blocks new commands', async () => {
 const storage = memory(); const bodies = []; let online = false;
 const send = async (_url, init) => { bodies.push(init.body); if (!online) throw new TypeError('offline'); return new Response(JSON.stringify({farm:{owner:'alice',revision:'2'}})); };
 const api = new ApiClient('http://localhost', 'alice', async () => 'token', storage, send);
 await assert.rejects(api.mutate('/v1/me/actions',{action:'BUY_SEED',expectedRevision:'1',itemId:'nabo',currency:'GOLD'}));
 assert.ok(api.pending);
 await assert.rejects(api.mutate('/v1/me/actions',{action:'BUY_SEED'}),/pendente/);
 online = true;
 const restored = new ApiClient('http://localhost','alice',async ()=>'token',storage,send);
 await restored.retry(); assert.equal(bodies[0],bodies[1]); assert.equal(restored.pending,null);
 assert.equal(new ApiClient('http://localhost','bob',async ()=>'token',storage,send).pending,null);
});
test('known rejection clears pending but unavailable response preserves it',async()=>{
 const storage=memory(); let status=409;
 const api=new ApiClient('http://localhost','alice',async ()=>'token',storage,async()=>new Response('{"error":"revisão"}',{status}));
 await assert.rejects(api.mutate('/v1/me/actions',{})); assert.equal(api.pending,null);
 status=503; await assert.rejects(api.mutate('/v1/me/actions',{})); assert.ok(api.pending);
});
test('late replay cannot replace a newer farm even beyond safe JS integer',()=>{
 const current={owner:'alice',revision:'9007199254740993'};
 assert.equal(acceptFarm(current,{owner:'alice',revision:'9007199254740992'}),current);
 assert.equal(acceptFarm(current,{owner:'alice',revision:'9007199254740994'}).revision,'9007199254740994');
});

test('fetch is called without binding API instance as browser receiver',async()=>{
 const api=new ApiClient('http://localhost','alice',async ()=>'token',memory(),async function(){assert.equal(this,undefined);return new Response('{}');});
 await api.get('/health');
});
