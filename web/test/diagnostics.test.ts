import test from 'node:test';
import assert from 'node:assert/strict';
import {runDiagnostics} from '../src/diagnostics.ts';
import {ApiClient} from '../src/api.ts';

const reply=(body,status=200)=>new Response(JSON.stringify(body),{status,headers:{'Content-Type':'application/json'}});
test('a received HTML or malformed JSON response is not classified as a network failure',async()=>{
 const result=await runDiagnostics({base:'https://api.example',token:async()=>'secret',pending:false,browser:'Chrome/140',timeoutMs:100,send:async()=>new Response('<html>private proxy error</html>')});
 assert.equal(result.health.status,'invalid-response');assert.equal(result.auth.status,'invalid-response');assert.doesNotMatch(result.report,/private proxy/);
});
test('diagnostics uses only independent GETs, sanitizes reports and estimates clock offset',async()=>{
 const calls=[];let time=1000;
 const result=await runDiagnostics({base:'https://api.example',token:async()=>'secret-token',pending:true,browser:'Chrome/140',version:'abcdef1',now:()=>time,timeoutMs:100,
  send:async(url,options)=>{calls.push({url,options});time+=20;return reply(url.endsWith('/health')?{ok:true}:{ok:true,serverTime:new Date(1110).toISOString(),originAllowed:true,private:'secret-token'});}});
 assert.deepEqual(calls.map(c=>c.url),['https://api.example/health','https://api.example/v1/diagnostics']);
 assert.ok(calls.every(c=>c.options.method==='GET'&&c.options.cache==='no-store'&&!c.options.body));
 assert.equal(result.auth.status,'ok');assert.equal(result.clockOffsetMs,80);
 assert.match(result.report,/preservada/i);assert.doesNotMatch(result.report,/secret-token|private|Authorization|Bearer/);
});
test('failed health still allows auth check; no raw error or body escapes',async()=>{
 const result=await runDiagnostics({base:'https://api.example',token:async()=>'token',pending:false,browser:'Chrome/140',version:'v',timeoutMs:100,send:async url=>{if(url.endsWith('/health'))throw new Error('private-user-token');return reply({error:'private-body'},401);}});
 assert.equal(result.health.status,'network');assert.equal(result.auth.status,'http-401');assert.doesNotMatch(result.report,/private/);
});
test('timeout bounds a stalled token without sending a late authenticated request',async()=>{
 let resolveToken;let requests=0;
 const token=new Promise<string>(resolve=>{resolveToken=resolve;});
 const result=await runDiagnostics({base:'https://api.example',token:()=>token,pending:true,browser:'Firefox/140',timeoutMs:5,send:async()=>{requests++;return reply({ok:true});}});
 assert.equal(result.auth.status,'timeout');resolveToken('secret');await new Promise(r=>setTimeout(r,0));assert.equal(requests,1);
});
test('missing local session is distinct from a failed connection',async()=>{
 let requests=0;
 const result=await runDiagnostics({base:'https://api.example',token:async()=>{throw new Error('private session details');},pending:true,browser:'Chrome/140',send:async()=>{requests++;return reply({ok:true});}});
 assert.equal(requests,1);assert.equal(result.auth.status,'session-unavailable');assert.doesNotMatch(result.report,/private session details/);
});
test('diagnosing preserves pending storage byte-for-byte and never requests a command id',async()=>{
 const pending=JSON.stringify({path:'/v1/me/actions',body:'{"requestId":"private-id","action":"WATER"}'});
 const storage={getItem:()=>pending,setItem(){assert.fail('write');},removeItem(){assert.fail('remove');}};
 const client=new ApiClient('https://api.example','private-owner',async()=>'secret',storage,async url=>{assert.ok(!url.includes('request-id'));return reply(url.endsWith('/health')?{ok:true}:{ok:true,originAllowed:true,serverTime:new Date().toISOString()});});
 const before=JSON.stringify(client.pending);const result=await client.diagnose({browser:'Chrome/140'});
 assert.equal(JSON.stringify(client.pending),before);assert.equal(client.busy,false);assert.doesNotMatch(result.report,/private-id|private-owner|secret/);
});
