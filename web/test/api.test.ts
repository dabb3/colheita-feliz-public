import test from 'node:test';
import assert from 'node:assert/strict';
import {ApiClient, acceptFarm} from '../src/api.ts';

function memory() { const entries = new Map(); return { getItem: k => entries.get(k) ?? null, setItem: (k,v) => entries.set(k,v), removeItem: k => entries.delete(k) }; }
test('a diagnostic logger failure cannot turn a confirmed action into an unknown result',async()=>{
 const previous=console.debug;console.debug=()=>{throw new Error('logger unavailable');};
 try{
  const api=new ApiClient('https://example.invalid','alice',async()=>'token',memory(),async(url)=>new Response(
   url.endsWith('/v1/request-id')?'{"requestId":"id"}':'{"result":{"success":true}}',{headers:{'Server-Timing':'auth;dur=1'}}));
  assert.equal((await api.mutate('/v1/me/actions',{action:'PLOW'})).result.success,true);assert.equal(api.pending,null);
 }finally{console.debug=previous;}
});
test('timing diagnostics contain durations and action but no credentials, request body or player id',async()=>{
 const logs=[],previous=console.debug;console.debug=(...args)=>logs.push(args);
 try{
  const api=new ApiClient('https://example.invalid','private-user',async()=>'private-token',memory(),async(url)=>
   new Response(url.endsWith('/v1/request-id')?'{"requestId":"private-request"}':'{}',{headers:{'Server-Timing':'auth;dur=20, db;dur=100'}}));
  await api.mutate('/v1/farms/00000000-0000-0000-0000-000000000002/visit-actions',{action:'LOVE',itemId:'amor'});
  const output=JSON.stringify(logs);
  assert.match(output,/LOVE/);assert.match(output,/auth;dur=20/);assert.match(output,/durationMs/);
  for(const secret of ['private-token','private-user','private-request','00000000-0000-0000-0000-000000000002','itemId'])assert.equal(output.includes(secret),false);
 }finally{console.debug=previous;}
});
test('a prepared server identifier removes issuance from the action critical path and is single use',async()=>{
 const calls=[],bodies=[];let ids=0;
 const api=new ApiClient('http://localhost','alice',async()=>'token',memory(),async(url,init)=>{
  calls.push(url);if(url.endsWith('/v1/request-id'))return new Response(JSON.stringify({requestId:`id-${++ids}`}));
  bodies.push(JSON.parse(init.body));return new Response('{}');
 });
 await Promise.all([api.prepareRequestId(),api.prepareRequestId()]);assert.equal(ids,1);
 calls.length=0;await api.mutate('/v1/me/actions',{action:'PLOW'});
 assert.deepEqual(calls,['http://localhost/v1/me/actions']);assert.equal(bodies[0].requestId,'id-1');
 await api.mutate('/v1/me/actions',{action:'WATER'});assert.equal(ids,2);assert.equal(bodies[1].requestId,'id-2');
});
test('failed preparation can be retried without creating an unknown action',async()=>{
 let fail=true;
 const api=new ApiClient('http://localhost','alice',async()=>'token',memory(),async()=>{
  if(fail)throw new TypeError('offline');return new Response('{"requestId":"fresh"}');
 });
 await assert.rejects(api.prepareRequestId());assert.equal(api.pending,null);
 fail=false;await api.mutate('/v1/me/actions',{action:'PLOW'});assert.equal(api.pending,null);
});
test('an old prepared id is discarded and an unknown POST retry keeps the exact id',async()=>{
 let now=0,ids=0,fail=true;const bodies=[];
 const api=new ApiClient('http://localhost','alice',async()=>'token',memory(),async(url,init)=>{
  if(url.endsWith('/v1/request-id'))return new Response(JSON.stringify({requestId:`id-${++ids}`}));
  bodies.push(init.body);if(fail)throw new TypeError('offline');return new Response('{}');
 },()=>now);
 await api.prepareRequestId();now=31_000;
 await assert.rejects(api.mutate('/v1/me/actions',{action:'PLOW'}));assert.equal(ids,2);
 now=100_000;await api.prepareRequestId();assert.equal(ids,2);
 fail=false;await api.retry();assert.equal(ids,2);assert.equal(bodies[0],bodies[1]);
});
test('unknown purchase result preserves request across reload and blocks new commands', async () => {
 const storage = memory(); const bodies = []; let online = false;
 const send = async (_url, init) => { if (_url.endsWith('/v1/request-id')) return new Response(JSON.stringify({requestId:'019a0000-0000-7000-8000-000000000001'})); bodies.push(init.body); if (!online) throw new TypeError('offline'); return new Response(JSON.stringify({farm:{owner:'alice',revision:'2'}})); };
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
 const api=new ApiClient('http://localhost','alice',async ()=>'token',storage,async(url)=>url.endsWith('/v1/request-id')?new Response(JSON.stringify({requestId:'019a0000-0000-7000-8000-000000000001'})):new Response('{"error":"revisão"}',{status}));
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

test('request uses server id, guards concurrent issuance, and expires without automatic resubmission',async()=>{
 let release;const lease=new Promise(resolve=>release=resolve);let posts=0,leases=0;const bodies=[];
 const api=new ApiClient('http://localhost','alice',async ()=>'token',memory(),async(url,init)=>{
  if(url.endsWith('/v1/request-id')){leases++;await lease;return new Response(JSON.stringify({requestId:'019a0000-0000-7000-8000-000000000001'}));}
  posts++;bodies.push(init.body);return new Response('{"error":"expired"}',{status:410});
 });
 const attempt=api.mutate('/v1/me/actions',{action:'PLOW'});
 await assert.rejects(api.mutate('/v1/me/actions',{action:'PLOW'}),/pendente/);
 release();await assert.rejects(attempt);
 assert.equal(JSON.parse(bodies[0]).requestId,'019a0000-0000-7000-8000-000000000001');
 assert.equal(api.pending,null);assert.equal(leases,1);assert.equal(posts,1);
});
