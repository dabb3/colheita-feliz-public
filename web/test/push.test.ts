import test from 'node:test';
import assert from 'node:assert/strict';
import {changeBinding, acceptsPush} from '../src/push-state.ts';
import {testMessage} from '../src/push.ts';
test('logout offline keeps revocation and rejects old binding',()=>{let s=changeBinding(undefined,{account:'a',bindingId:'one',endpoint:'https://push',deviceToken:'token'});s=changeBinding(s);assert.equal(s.active,undefined);assert.equal(s.pending.length,1);assert.equal(acceptsPush(s,{type:'TEST',bindingId:'one'}),false);});
test('account change preserves device proof and increases revision',()=>{const a=changeBinding(undefined,{account:'a',bindingId:'one',endpoint:'https://push',deviceToken:'token'});const b=changeBinding(a,{account:'b',bindingId:'two',endpoint:'https://push',deviceToken:'token'});assert.equal(b.revision,2);assert.equal(b.pending[0].deviceToken,'token');assert.equal(acceptsPush(b,{type:'TEST',bindingId:'one'}),false);assert.equal(acceptsPush(b,{type:'TEST',bindingId:'two'}),true);assert.notEqual(a.revision,b.revision);});
test('only provider acceptance claims sending, never receipt',()=>{assert.equal(testMessage('accepted'),'Enviado ao serviço de notificações');for(const status of ['pending','unavailable','expired'])assert.doesNotMatch(testMessage(status),/Enviado|recebid/i);});
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {ApiClient,ApiError} from '../src/api.ts';
const tick=()=>new Promise(resolve=>setImmediate(resolve));
function harness(){
 let state={revision:1,account:'a',pending:[]},permissionCalls=0,subscribeCalls=0,closed=0;
 const make=(tag="div")=>({tag,children:[],append(...items){this.children.push(...items);},setAttribute(){}}),parent=make();
 const registration={getNotifications:async()=>[{close(){closed++;}}],pushManager:{getSubscription:async()=>null,subscribe:async options=>{subscribeCalls++;assert.equal(options.userVisibleOnly,true);return{endpoint:'https://push',toJSON:()=>({endpoint:'https://push',keys:{p256dh:'key',auth:'auth'}})};}}};
 const ctx={ApiError,document:{createElement:make,createTextNode:t=>t},indexedDB:{},Notification:{permission:'default',requestPermission(){permissionCalls++;return Promise.resolve('granted');}},PushManager:class{},navigator:{serviceWorker:{register:async()=>registration,ready:Promise.resolve(registration),getRegistration:async()=>registration}},isSecureContext:true,crypto:globalThis.crypto,btoa,atob,Uint8Array,Date,setTimeout(){},changeBinding,pushState:async update=>{if(update)state=update(state);return structuredClone(state);}};
 let source=readFileSync(new URL('../src/push.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');source+='\nglobalThis.h={renderPushSettings,suspendPush,connectPush,reconcilePush};';runInNewContext(stripTypeScriptTypes(source),ctx);
 return {h:ctx.h,parent,ctx,read:()=>state,write:s=>{state=s;},permissionCalls:()=>permissionCalls,subscribeCalls:()=>subscribeCalls,closed:()=>closed,buttons:()=>parent.children[0].children.filter(n=>typeof n==='object'&&n.tag==='button')};
}
test('unconfigured settings do not request permission and disable activation',async()=>{const x=harness();x.h.renderPushSettings(x.parent,'a',{push:async()=>({enabled:false})});await tick();assert.equal(x.permissionCalls(),0);assert.equal(x.buttons()[0].disabled,true);});
test('only an explicit activation click asks permission, and logout while permission waits cannot restore binding',async()=>{const x=harness();let resolve;x.ctx.Notification.requestPermission=()=>new Promise(r=>resolve=r);x.h.renderPushSettings(x.parent,'a',{push:async()=>({enabled:true,publicKey:'AA'})});await tick();assert.equal(resolve,undefined);const operation=x.buttons()[0].onclick();await tick();await x.h.suspendPush();resolve('granted');await operation;assert.equal(x.read().active,undefined);assert.equal(x.subscribeCalls(),0);});
test('late server response after offline logout cannot reinstall binding and keeps revocation',async()=>{const x=harness();let complete;const sent=[];const api={push:async(op,body)=>{sent.push([op,body]);if(op==='config')return{enabled:true,publicKey:'AA'};if(op==='subscribe')return new Promise(r=>complete=r);throw new Error('offline');}};x.h.renderPushSettings(x.parent,'a',api);await tick();const operation=x.buttons()[0].onclick();await tick();assert.equal(x.read().active.account,'a');await x.h.suspendPush();complete({status:'subscribed'});await operation;assert.equal(x.read().active,undefined);assert.equal(x.read().pending.length,1);assert.equal(x.closed(),1);assert.equal(x.read().pending[0].deviceToken,sent.find(([op])=>op==='subscribe')[1].deviceToken);});
test('another tab account switch invalidates a waiting activation and reconciles old proofs',async()=>{const x=harness();let resolve;x.ctx.Notification.requestPermission=()=>new Promise(r=>resolve=r);const api={push:async()=>({enabled:true,publicKey:'AA'})};x.h.renderPushSettings(x.parent,'a',api);await tick();const operation=x.buttons()[0].onclick();await tick();await x.h.connectPush('b',api);resolve('granted');await operation;assert.equal(x.read().account,'b');assert.equal(x.read().active,undefined);assert.equal(x.subscribeCalls(),0);});
test('push transport does not adopt leases, mutate pending state, retry, or follow redirects',async()=>{const calls=[],storage={getItem:()=>null,setItem(){throw Error('unexpected');},removeItem(){throw Error('unexpected');}};const api=new ApiClient('https://api.invalid','a',async()=>'private',storage,async(url,options)=>{calls.push({url,options});return new Response('{"enabled":false,"requestLease":{"requestId":"ignored"}}');});await api.push('config');await api.push('revoke',{endpoint:'private'});assert.equal(api.pending,null);assert.equal(calls.length,2);assert.equal(calls[1].options.redirect,'error');assert.equal(calls[1].options.cache,'no-store');assert.equal(calls[1].options.method,'POST');assert.ok(calls[1].options.signal);});
test('worker rejects old bindings after offline logout and only opens its own origin',async()=>{
 const handlers={},shown=[],opened=[],notifications=[];let active={revision:2,account:'a',pending:[],active:{account:'a',bindingId:'current'}};
 const ctx={pushState:async()=>active,acceptsPush,URL,location:{origin:'https://game.invalid'},registration:{showNotification:async(title,options)=>shown.push([title,options]),getNotifications:async()=>notifications},clients:{matchAll:async()=>[],openWindow:async url=>opened.push(url)},addEventListener:(type,handler)=>handlers[type]=handler};
 let source=readFileSync(new URL('../src/push-worker.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'');runInNewContext(stripTypeScriptTypes(source),ctx);
 assert.equal(handlers.fetch,undefined);
 async function push(payload){let work;handlers.push({data:{json:()=>payload},waitUntil:p=>work=p});await work;}
 await push({type:'TEST',bindingId:'old',title:'Private',url:'https://evil.invalid'});assert.equal(shown.length,0);
 await push({type:'TEST',bindingId:'current',title:'Private',url:'https://evil.invalid'});assert.equal(shown.length,1);assert.equal(shown[0][0],'Colheita Feliz');assert.doesNotMatch(JSON.stringify(shown),/Private|evil/);
 active={revision:3,pending:[]};await push({type:'TEST',bindingId:'current'});assert.equal(shown.length,1);
 let work;handlers.notificationclick({notification:{close(){}},waitUntil:p=>work=p});await work;assert.deepEqual(opened,['https://game.invalid/']);
});
test('legacy worker migrates to crop-ready only after explicit consent and logout suppresses it',async()=>{
 const handlers={},shown=[];let state={revision:2,account:'a',pending:[],active:{account:'a',bindingId:'current'}};
 const ctx={pushState:async()=>state,acceptsPush,URL,location:{origin:'https://game.invalid'},registration:{showNotification:async(...args)=>shown.push(args),getNotifications:async()=>[]},clients:{matchAll:async()=>[],openWindow:async()=>{}},addEventListener:(type,handler)=>handlers[type]=handler};
 const source=readFileSync(new URL('../src/push-worker.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'');runInNewContext(stripTypeScriptTypes(source),ctx);
 async function push(payload){let work;handlers.push({data:{json:()=>payload},waitUntil:p=>work=p});await work;}
 await push({type:'CROP_READY',bindingId:'current'});assert.equal(shown.length,0);
 state={...state,active:{...state.active,plants:true}};await push({type:'CROP_READY',bindingId:'current'});assert.equal(shown.length,1);
 state=changeBinding(state);await push({type:'CROP_READY',bindingId:'current'});assert.equal(shown.length,1);
});
test('IndexedDB read-modify-write intents serialize revisions and preserve pending revocations',async()=>{
 let saved,tail=Promise.resolve();
 const indexedDB={open(){const request={};queueMicrotask(()=>{request.result={close(){},transaction(){const tx={},getRequest={};let done;const wait=tail;tail=new Promise(resolve=>done=resolve);const store={get(){wait.then(()=>{getRequest.result=structuredClone(saved);getRequest.onsuccess();queueMicrotask(()=>{tx.oncomplete();done();});});return getRequest;},put(value){saved=structuredClone(value);}};tx.objectStore=()=>store;return tx;}};request.onsuccess();});return request;}};
 const ctx={indexedDB};let source=readFileSync(new URL('../src/push-state.ts',import.meta.url),'utf8').replace(/^export /mg,'');source+='\nglobalThis.h={pushState,changeBinding};';runInNewContext(stripTypeScriptTypes(source),ctx);
 await ctx.h.pushState(s=>ctx.h.changeBinding(s,{account:'a',bindingId:'old',endpoint:'https://push',deviceToken:'private'}));
 await Promise.all(Array.from({length:8},()=>ctx.h.pushState(s=>ctx.h.changeBinding(s))));const state=await ctx.h.pushState();assert.equal(state.revision,9);assert.equal(state.pending.length,1);assert.equal(state.pending[0].deviceRevision,2);assert.equal(state.active,undefined);
});
test('test button debounces repeated clicks for a minute and distinguishes provider acceptance',async()=>{const x=harness();x.write({revision:3,account:'a',deviceToken:'private',active:{account:'a',bindingId:'current',endpoint:'https://push',deviceToken:'private'},pending:[]});let tests=0;const api={push:async op=>{if(op==='config')return{enabled:true,publicKey:'AA'};if(op==='test'){tests++;return{status:'accepted'};}}};x.h.renderPushSettings(x.parent,'a',api);await tick();const testButton=x.buttons().find(n=>n.textContent==='Enviar notificação de teste');await testButton.onclick();await testButton.onclick();assert.equal(tests,1);assert.equal(testButton.disabled,true);const text=x.parent.children[0].children.map(n=>n.textContent).join(' ');assert.match(text,/Enviado ao serviço de notificações/);assert.doesNotMatch(text,/recebida|recebeu/);});
test('disable then re-enable in the same panel keeps proof and creates a newer binding',async()=>{const x=harness(),sent=[];const api={push:async(op,body)=>{sent.push([op,body]);return op==='config'?{enabled:true,publicKey:'AA'}:{};}};x.h.renderPushSettings(x.parent,'a',api);await tick();const [activate,disable]=x.buttons();await activate.onclick();const first=structuredClone(x.read());assert.ok(first.active);await disable.onclick();assert.equal(x.read().active,undefined);await activate.onclick();assert.ok(x.read().active);assert.equal(x.read().active.deviceToken,first.active.deviceToken);assert.notEqual(x.read().active.bindingId,first.active.bindingId);assert.ok(x.read().revision>first.revision);assert.equal(sent.filter(([op])=>op==='subscribe').length,2);});
test('a stale config callback cannot enable an old account permission request',async()=>{const x=harness();let resolve;const api={push:async op=>op==='config'?new Promise(r=>resolve=r):{}};x.h.renderPushSettings(x.parent,'a',api);assert.equal(x.buttons()[0].disabled,true);await x.h.connectPush('b',api);resolve({enabled:true,publicKey:'AA'});await tick();assert.equal(x.buttons()[0].disabled,true);await x.buttons()[0].onclick();assert.equal(x.permissionCalls(),0);assert.equal(x.read().account,'b');});
test('a stale enabled panel may ask site permission but cannot bind the old account',async()=>{const x=harness(),api={push:async()=>({enabled:true,publicKey:'AA'})};x.h.renderPushSettings(x.parent,'a',api);await tick();await x.h.connectPush('b',api);await x.buttons()[0].onclick();assert.equal(x.permissionCalls(),1);assert.equal(x.subscribeCalls(),0);assert.equal(x.read().account,'b');});
test('expired browser activation does not request permission or subscribe',async()=>{const x=harness();x.ctx.navigator.userActivation={isActive:false};x.h.renderPushSettings(x.parent,'a',{push:async()=>({enabled:true,publicKey:'AA'})});await tick();await x.buttons()[0].onclick();assert.equal(x.permissionCalls(),0);assert.equal(x.subscribeCalls(),0);assert.match(x.parent.children[0].children.map(n=>n.textContent).join(' '),/Toque novamente/);});
test('a stale disable control cannot clear the new account binding',async()=>{const x=harness(),api={push:async()=>({enabled:true,publicKey:'AA'})};x.h.renderPushSettings(x.parent,'a',api);await tick();x.write({revision:5,account:'b',pending:[],active:{account:'b',bindingId:'new',endpoint:'https://push',deviceToken:'private'}});await x.buttons()[1].onclick();assert.equal(x.read().active.bindingId,'new');assert.equal(x.read().account,'b');});
test('offline disable still offers reactivation and keeps revocation pending',async()=>{const x=harness();let offline=false;const api={push:async(op)=>{if(op==='config')return{enabled:true,publicKey:'AA'};if(offline)throw Error('offline');return{};}};x.h.renderPushSettings(x.parent,'a',api);await tick();const [activate,disable]=x.buttons();await activate.onclick();offline=true;await disable.onclick();assert.equal(x.read().active,undefined);assert.equal(x.read().account,'a');assert.equal(x.read().pending.length,1);assert.equal(activate.hidden,false);assert.equal(activate.disabled,false);offline=false;await activate.onclick();assert.ok(x.read().active);assert.equal(x.read().pending.length,0);});
test('push deadline covers a stalled auth token and prevents a late send',async t=>{t.mock.timers.enable({apis:['setTimeout']});let resolveToken,sends=0,result='waiting';const api=new ApiClient('https://api.invalid','a',()=>new Promise(r=>resolveToken=r),{getItem:()=>null},async()=>{sends++;return new Response('{}');});const work=api.push('config').then(()=>result='success',()=>result='timeout');t.mock.timers.tick(15_001);await tick();assert.equal(result,'timeout');resolveToken('private');await work;await Promise.resolve();assert.equal(sends,0);});
test('push deadline covers a stalled response body',async t=>{t.mock.timers.enable({apis:['setTimeout']});let result='waiting';const api=new ApiClient('https://api.invalid','a',async()=>'private',{getItem:()=>null},async()=>({ok:true,status:200,json:()=>new Promise(()=>{})}));const work=api.push('config').then(()=>result='success',()=>result='timeout');await Promise.resolve();await Promise.resolve();t.mock.timers.tick(15_001);await tick();assert.equal(result,'timeout');await work;});
test('local suspension completes before a stalled remote revocation or auth signout',async()=>{const x=harness();x.write({revision:3,account:'a',pending:[],active:{account:'a',bindingId:'old',endpoint:'https://push',deviceToken:'private'}});let revoked=false,signedOut=false,finished=false;const api={push:async()=>{revoked=true;return new Promise(()=>{});}};const logout=(async()=>{await x.h.suspendPush(api);signedOut=true;finished=true;})();await tick();assert.equal(finished,true);assert.equal(signedOut,true);assert.equal(revoked,true);assert.equal(x.read().active,undefined);assert.equal(x.closed(),1);assert.equal(x.read().pending.length,1);await logout;});
test('push deadline also bounds a stalled fetch without retry',async t=>{t.mock.timers.enable({apis:['setTimeout']});let sends=0,signal,result='waiting';const api=new ApiClient('https://api.invalid','a',async()=>'private',{getItem:()=>null},async(_url,options)=>{sends++;signal=options.signal;return new Promise(()=>{});});const work=api.push('revoke',{}).then(()=>result='success',()=>result='timeout');await tick();t.mock.timers.tick(15_001);await tick();assert.equal(result,'timeout');assert.equal(signal.aborted,true);assert.equal(sends,1);await work;});
test('a failed suppression write unregisters the worker and closes notifications before logout continues',async()=>{const x=harness();let unregistered=0,closed=0,signOut=0,remote=0;const original=x.ctx.pushState;x.ctx.pushState=async update=>{if(update)throw Error('storage unavailable');return original();};x.ctx.navigator.serviceWorker.getRegistration=async()=>({unregister:async()=>{unregistered++;return true;},getNotifications:async()=>[{close(){closed++;}}]});await x.h.suspendPush({push:async()=>{remote++;}}).catch(()=>{});signOut++;assert.equal(unregistered,1);assert.equal(closed,1);assert.equal(signOut,1);assert.equal(remote,0);});
test('reconnecting after worker fallback reconciles the recovered stale binding',async()=>{const x=harness();x.write({revision:3,account:'a',pending:[],active:{account:'a',bindingId:'old',endpoint:'https://push',deviceToken:'private'}});x.ctx.navigator.serviceWorker.getRegistration=async()=>undefined;const revoked=[];await x.h.connectPush('a',{push:async(op,body)=>revoked.push([op,body])});assert.equal(x.read().active,undefined);assert.equal(x.read().account,'a');assert.equal(revoked.length,1);assert.equal(revoked[0][0],'revoke');assert.equal(revoked[0][1].deviceToken,'private');assert.equal(x.read().pending.length,0);});
for(const mode of ['disabled','offline'])test(`an active device can be disabled while configuration is ${mode}`,async()=>{const x=harness();x.write({revision:3,account:'a',pending:[],active:{account:'a',bindingId:'old',endpoint:'https://push',deviceToken:'private'}});const sent=[];const api={push:async op=>{sent.push(op);if(mode==='offline')throw Error('offline');return{enabled:false};}};x.h.renderPushSettings(x.parent,'a',api);await tick();const [activate,disable,testButton]=x.buttons();assert.equal(disable.hidden,false);assert.equal(activate.disabled,true);assert.equal(testButton.hidden,true);await testButton.onclick();assert.doesNotMatch(x.parent.children[0].children.map(n=>n.textContent).join(' '),/Notificações ativadas neste/);await disable.onclick();assert.equal(x.read().active,undefined);assert.equal(sent.includes('test'),false);assert.equal(x.permissionCalls(),0);});

for(const status of [400,403,404,409])test('definitive revoke '+status+' drops only rejected intent and continues activation',async()=>{const x=harness(),calls=[];x.write({revision:3,account:'a',pending:[{endpoint:'https://bad',deviceToken:'bad',deviceRevision:2},{endpoint:'https://good',deviceToken:'good',deviceRevision:3}]});const api={push:async(op,body)=>{calls.push([op,body]);if(op==='config')return{enabled:true,publicKey:'AA'};if(op==='revoke'&&body.endpoint==='https://bad')throw new ApiError(status,'rejected');return{};}};x.h.renderPushSettings(x.parent,'a',api);await tick();await x.buttons()[0].onclick();assert.equal(x.read().pending.length,0);assert.ok(x.read().active);assert.equal(calls.filter(([op])=>op==='revoke').length,2);assert.equal(calls.filter(([op])=>op==='subscribe').length,1);});
for(const status of [401,429,500,503,'network'])test('transient revoke '+status+' preserves queue and stops without retry',async()=>{const x=harness(),pending=[{endpoint:'https://bad',deviceToken:'bad',deviceRevision:2},{endpoint:'https://good',deviceToken:'good',deviceRevision:3}];x.write({revision:3,account:'a',pending});let calls=0;await assert.rejects(x.h.reconcilePush('a',{push:async()=>{calls++;throw status==='network'?new Error('offline'):new ApiError(status,'failed');}}));assert.deepEqual(x.read().pending,pending);assert.equal(calls,1);});
test('revoke rejection preserves concurrent newer proof and current binding',async()=>{const x=harness(),old={endpoint:'https://push',deviceToken:'old',deviceRevision:2};x.write({revision:2,account:'a',pending:[old]});let reject;const work=x.h.reconcilePush('a',{push:()=>new Promise((_,r)=>reject=r)});await tick();const newer={endpoint:old.endpoint,deviceToken:'new',deviceRevision:3},sameRevision={...old,deviceToken:'different'};x.write({revision:4,account:'b',active:{account:'b',endpoint:old.endpoint,bindingId:'new',deviceToken:'new'},pending:[old,newer,sameRevision]});reject(new ApiError(403,'failed'));await work;assert.deepEqual(x.read().pending,[newer,sameRevision]);assert.equal(x.read().active.bindingId,'new');});
test('activation requests site permission synchronously before IndexedDB await',async()=>{const x=harness();x.h.renderPushSettings(x.parent,'a',{push:async()=>({enabled:true,publicKey:'AA'})});await tick();const original=x.ctx.pushState;let release;const gate=new Promise(r=>release=r);x.ctx.pushState=async update=>{await gate;return original(update);};const work=x.buttons()[0].onclick();assert.equal(x.permissionCalls(),1);release();await work;});
test('denied site permission leaves binding and revision unchanged',async()=>{const x=harness();x.write({revision:3,account:'a',pending:[],deviceToken:'proof',active:{account:'a',bindingId:'current',endpoint:'https://push',deviceToken:'proof'}});x.h.renderPushSettings(x.parent,'a',{push:async()=>({enabled:true,publicKey:'AA'})});await tick();const initial=structuredClone(x.read());x.ctx.Notification.requestPermission=()=>Promise.resolve('denied');await x.buttons()[0].onclick();assert.deepEqual(x.read(),initial);});
for(const status of [400,403,409])test('rejected subscribe '+status+' releases invalid endpoint for next explicit click',async()=>{const x=harness();let unsubscribed=0;const registration=await x.ctx.navigator.serviceWorker.getRegistration();const subscription={endpoint:'https://invalid',toJSON:()=>({keys:{p256dh:'key',auth:'auth'}}),unsubscribe:async()=>{unsubscribed++;return true;}};registration.pushManager.getSubscription=async()=>subscription;let sent=0;const api={push:async op=>{if(op==='config')return{enabled:true,publicKey:'AA'};if(op==='subscribe'){sent++;throw new ApiError(status,'invalid proof');}return{};}};x.h.renderPushSettings(x.parent,'a',api);await tick();await x.buttons()[0].onclick();assert.equal(unsubscribed,1);assert.equal(sent,1);assert.equal(x.subscribeCalls(),0);assert.equal(x.read().active,undefined);});

test('another tab changing stored account while site permission waits cannot reserve the old account',async()=>{const x=harness();let resolve;x.ctx.Notification.requestPermission=()=>new Promise(r=>resolve=r);x.h.renderPushSettings(x.parent,'a',{push:async()=>({enabled:true,publicKey:'AA'})});await tick();const work=x.buttons()[0].onclick();const next={revision:6,account:'b',pending:[],active:{account:'b',endpoint:'https://other',bindingId:'new',deviceToken:'other'}};x.write(next);resolve('granted');await work;assert.deepEqual(x.read(),next);assert.equal(x.subscribeCalls(),0);});
for(const status of [401,429,500])test('transient subscribe '+status+' keeps browser subscription for reconnect',async()=>{const x=harness();let unsubscribed=0;const registration=await x.ctx.navigator.serviceWorker.getRegistration();registration.pushManager.getSubscription=async()=>({endpoint:'https://push',toJSON:()=>({keys:{}}),unsubscribe:async()=>{unsubscribed++;}});x.h.renderPushSettings(x.parent,'a',{push:async op=>{if(op==='config')return{enabled:true,publicKey:'AA'};throw new ApiError(status,'temporary');}});await tick();await x.buttons()[0].onclick();assert.equal(unsubscribed,0);assert.equal(x.read().pending.length,1);});
test('rejected old subscribe cannot unsubscribe or clear a newer account binding',async()=>{const x=harness();let reject,unsubscribed=0;const registration=await x.ctx.navigator.serviceWorker.getRegistration();registration.pushManager.getSubscription=async()=>({endpoint:'https://push',toJSON:()=>({keys:{}}),unsubscribe:async()=>{unsubscribed++;}});x.h.renderPushSettings(x.parent,'a',{push:async op=>op==='config'?{enabled:true,publicKey:'AA'}:new Promise((_,r)=>reject=r)});await tick();const work=x.buttons()[0].onclick();await tick();const next={revision:9,account:'b',pending:[{endpoint:'https://keep',deviceToken:'proof',deviceRevision:8}],deviceToken:'new',active:{account:'b',endpoint:'https://push',bindingId:'new',deviceToken:'new'}};x.write(next);reject(new ApiError(403,'proof changed'));await work;assert.deepEqual(x.read(),next);assert.equal(unsubscribed,0);});
test('a rejected endpoint is replaced only on the next explicit activation',async()=>{const x=harness();let current={endpoint:'https://invalid',toJSON:()=>({keys:{}}),unsubscribe:async()=>{current=null;return true;}},sends=0;const registration=await x.ctx.navigator.serviceWorker.getRegistration();registration.pushManager.getSubscription=async()=>current;x.h.renderPushSettings(x.parent,'a',{push:async op=>{if(op==='config')return{enabled:true,publicKey:'AA'};if(op==='subscribe'&&++sends===1)throw new ApiError(403,'proof changed');return{};}});await tick();await x.buttons()[0].onclick();assert.equal(x.subscribeCalls(),0);assert.equal(current,null);await x.buttons()[0].onclick();assert.equal(x.subscribeCalls(),1);assert.equal(sends,2);assert.equal(x.read().active.endpoint,'https://push');assert.equal(x.read().pending.length,0);});

test('failed browser unsubscribe keeps local suppression and reports a recoverable failure',async()=>{const x=harness();const registration=await x.ctx.navigator.serviceWorker.getRegistration();registration.pushManager.getSubscription=async()=>({endpoint:'https://invalid',toJSON:()=>({keys:{}}),unsubscribe:async()=>{throw Error('browser unavailable');}});x.h.renderPushSettings(x.parent,'a',{push:async op=>{if(op==='config')return{enabled:true,publicKey:'AA'};throw new ApiError(403,'proof rejected');}});await tick();await assert.doesNotReject(x.buttons()[0].onclick());assert.equal(x.read().active,undefined);assert.equal(x.read().pending.length,1);assert.equal(x.read().pending[0].endpoint,'https://invalid');assert.equal(x.buttons()[0].disabled,false);assert.match(x.parent.children[0].children.map(n=>n.textContent).join(' '),/Não foi possível concluir/);});

test('crop ready requires the current account binding and respects the local plants preference',()=>{
 const active={account:'a',bindingId:'current',endpoint:'https://push',deviceToken:'private',plants:true};
 const state={revision:3,account:'a',pending:[],active};
 assert.equal(acceptsPush(state,{type:'CROP_READY',bindingId:'current'}),true);
 assert.equal(acceptsPush({...state,active:{...active,plants:false}},{type:'CROP_READY',bindingId:'current'}),false);
 assert.equal(acceptsPush({...state,account:'b'},{type:'CROP_READY',bindingId:'current'}),false);
 assert.equal(acceptsPush(changeBinding(state),{type:'CROP_READY',bindingId:'current'}),false);
 assert.equal(acceptsPush(state,{type:'UNKNOWN',bindingId:'current'}),false);
 assert.equal(acceptsPush({...state,active:{...active,plants:false}},{type:'TEST',bindingId:'current'}),true);
});
test('social push types require their own confirmed category',()=>{
 const state={revision:1,account:'a',pending:[],active:{account:'a',bindingId:'b',endpoint:'https://push',deviceToken:'d',plants:true,animals:false,thefts:true,pests:false}};
 assert.equal(acceptsPush(state,{type:'ANIMAL_READY',bindingId:'b'}),false);
 assert.equal(acceptsPush(state,{type:'THEFT',bindingId:'b'}),true);
 assert.equal(acceptsPush(state,{type:'PEST',bindingId:'b'}),false);
 assert.equal(acceptsPush({...state,active:{...state.active,animals:true,pests:true}},{type:'ANIMAL_READY',bindingId:'b'}),true);
 assert.equal(acceptsPush({...state,active:{...state.active,animals:true,pests:true}},{type:'PEST',bindingId:'b'}),true);
});

function boundHarness(){const x=harness();x.write({revision:3,account:'a',deviceToken:'private',pending:[],active:{account:'a',bindingId:'current',endpoint:'https://push',deviceToken:'private'}});return x;}
const plantsInput=x=>x.parent.children.at(-1).children.find(n=>n.tag==='label').children[0];
const panelText=x=>x.parent.children.at(-1).children.map(n=>n.textContent).join(' ');
function preferencesApi(calls=[],plants=false){return{push:async(op,body)=>{calls.push([op,body]);if(op==='config')return{enabled:true,publicKey:'AA',plantEventsEnabled:true};if(op==='preferences'){if(typeof body.plants==='boolean')plants=body.plants;return{bindingId:body.bindingId,deviceRevision:body.deviceRevision,plants};}return{};}};}

test('plants restores the device preference on reopening settings and leaves other types disabled',async()=>{
 const x=boundHarness(),calls=[],api=preferencesApi(calls,false);x.h.renderPushSettings(x.parent,'a',api);await tick();
 const plants=plantsInput(x);assert.equal(plants.checked,false);assert.equal(plants.disabled,false);
 for(const row of x.parent.children[0].children.filter(n=>n.tag==='label').slice(1))assert.equal(row.children[0].disabled,true);
 plants.checked=true;await plants.onchange();assert.equal(x.read().revision,4);assert.equal(x.read().active.plants,true);assert.equal(x.read().active.deviceToken,'private');assert.equal(x.read().pending.length,0);
 assert.deepEqual(calls.filter(([op])=>op==='preferences').map(([,body])=>JSON.parse(JSON.stringify(body))),[{endpoint:'https://push',deviceToken:'private',bindingId:'current',deviceRevision:3},{endpoint:'https://push',deviceToken:'private',bindingId:'current',deviceRevision:4,plants:true}]);
 x.h.renderPushSettings(x.parent,'a',api);await tick();assert.equal(plantsInput(x).checked,true);assert.equal(plantsInput(x).disabled,false);
 assert.match(panelText(x),/aproximados/);
});

test('plants update is reserved once and a late reply cannot change a new account',async()=>{
 const x=boundHarness(),calls=[];let finish;const base=preferencesApi(calls,false),api={push:async(op,body)=>op==='preferences'&&typeof body.plants==='boolean'?new Promise(r=>{calls.push([op,body]);finish=r;}):base.push(op,body)};
 x.h.renderPushSettings(x.parent,'a',api);await tick();const plants=plantsInput(x);plants.checked=true;const work=plants.onchange();await tick();await plants.onchange();
 assert.equal(calls.filter(([op,b])=>op==='preferences'&&'plants'in b).length,1);assert.equal(x.read().revision,4);
 await x.h.connectPush('b',api);const current=structuredClone(x.read());finish({bindingId:'current',deviceRevision:4,plants:true});await work;assert.deepEqual(structuredClone(x.read()),current);assert.equal(plants.disabled,true);
});

test('uncertain plants update keeps a retriable revision and logout revokes a newer revision',async()=>{
 const x=boundHarness(),calls=[];let fail=true;const base=preferencesApi(calls,true),api={push:async(op,body)=>{if(op==='preferences'&&typeof body.plants==='boolean'&&fail){calls.push([op,body]);throw Error('offline');}return base.push(op,body);}};
 x.h.renderPushSettings(x.parent,'a',api);await tick();const plants=plantsInput(x);plants.checked=false;await plants.onchange();
 assert.equal(x.read().revision,4);assert.equal(x.read().active.plants,false);assert.match(panelText(x),/tente novamente|Repetir/);
 const retry=x.buttons().find(n=>n.textContent==='Repetir alteração de Plantas');assert.ok(retry);assert.equal(retry.hidden,false);fail=false;await retry.onclick();
 assert.equal(x.read().revision,4);assert.equal(x.read().active.plantsPending,undefined);assert.equal(plants.checked,false);
 assert.deepEqual(calls.filter(([op,b])=>op==='preferences'&&'plants'in b).map(([,b])=>b.deviceRevision),[4,4]);
 await x.h.suspendPush();assert.equal(x.read().pending[0].deviceRevision,5);assert.equal(acceptsPush(x.read(),{type:'CROP_READY',bindingId:'current'}),false);
});

for(const mode of ['unconfigured','offline','plants-disabled'])test('plants cannot update when config is '+mode,async()=>{
 const x=boundHarness(),sent=[];x.h.renderPushSettings(x.parent,'a',{push:async(op)=>{sent.push(op);if(mode==='offline')throw Error('offline');return mode==='unconfigured'?{enabled:false}:{enabled:true,publicKey:'AA',plantEventsEnabled:false};}});await tick();
 assert.equal(plantsInput(x).disabled,true);assert.equal(sent.includes('preferences'),false);assert.equal(x.read().revision,3);assert.equal(x.permissionCalls(),0);
});

test('late preference read cannot overwrite a newer binding revision',async()=>{
 const x=boundHarness();let complete;x.h.renderPushSettings(x.parent,'a',{push:async op=>op==='config'?{enabled:true,publicKey:'AA',plantEventsEnabled:true}:new Promise(r=>complete=r)});await tick();
 x.write({...x.read(),revision:4,active:{...x.read().active,plants:true}});complete({bindingId:'current',deviceRevision:3,plants:false});await tick();assert.equal(x.read().active.plants,true);assert.equal(plantsInput(x).disabled,true);
});

test('worker uses a fixed crop message and closes it when binding disappears during display',async()=>{
 const handlers={},shown=[],closed=[];let active={revision:2,account:'a',pending:[],active:{account:'a',bindingId:'current',plants:true}},remove=false;
 const ctx={pushState:async()=>active,acceptsPush,URL,location:{origin:'https://game.invalid'},registration:{showNotification:async(title,options)=>{shown.push([title,options]);if(remove)active=changeBinding(active);},getNotifications:async options=>[{close(){closed.push(options.tag);}}]},clients:{matchAll:async()=>[],openWindow:async()=>{}},addEventListener:(type,handler)=>handlers[type]=handler};
 let source=readFileSync(new URL('../src/push-worker.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'');runInNewContext(stripTypeScriptTypes(source),ctx);
 async function push(payload){let work;handlers.push({data:{json:()=>payload},waitUntil:p=>work=p});await work;}
 await push({type:'CROP_READY',bindingId:'current',body:'private',url:'https://evil.invalid'});assert.equal(shown.length,1);assert.equal(shown[0][1].body,'Há plantas prontas para colher na sua fazenda. Toque para voltar ao jogo.');assert.equal(shown[0][1].tag,'current');assert.doesNotMatch(JSON.stringify(shown),/private|evil/);
 await push({type:'INVALID',bindingId:'current'});await push(null);await push({type:'CROP_READY'});assert.equal(shown.length,1);
 remove=true;await push({type:'CROP_READY',bindingId:'current'});assert.deepEqual(closed,['current']);
});

test('malformed persisted bindings cannot accept a push with missing identity',()=>{
 assert.equal(acceptsPush({revision:1,pending:[],active:{}},{type:'TEST'}),false);
 assert.equal(acceptsPush({revision:1,pending:[],account:'a',active:{account:'a'}},{type:'CROP_READY'}),false);
});

test('reopened settings retries an uncertain plants write without inventing a new revision',async()=>{
 const x=boundHarness();x.write({...x.read(),revision:4,active:{...x.read().active,plants:false,plantsPending:true}});const calls=[],api=preferencesApi(calls,true);
 x.h.renderPushSettings(x.parent,'a',api);await tick();assert.equal(plantsInput(x).disabled,true);assert.equal(plantsInput(x).checked,false);assert.equal(calls.filter(([op])=>op==='preferences').length,0);
 const retry=x.buttons().find(n=>n.textContent==='Repetir alteração de Plantas');assert.equal(retry.hidden,false);await retry.onclick();assert.equal(x.read().revision,4);assert.equal(x.read().active.plantsPending,undefined);assert.equal(x.read().active.plants,false);assert.equal(plantsInput(x).disabled,false);
 assert.equal(calls.find(([op])=>op==='preferences')[1].deviceRevision,4);
});

for(const mode of ['rejected','wrong-binding','wrong-revision','wrong-value'])test('plants '+mode+' response cannot confirm the reserved intent',async()=>{
 const x=boundHarness(),base=preferencesApi([],true),api={push:async(op,body)=>{if(op!=='preferences'||typeof body.plants!=='boolean')return base.push(op,body);if(mode==='rejected')throw new ApiError(403,'denied');return{bindingId:mode==='wrong-binding'?'other':body.bindingId,deviceRevision:body.deviceRevision+(mode==='wrong-revision'?1:0),plants:mode==='wrong-value'?!body.plants:body.plants};}};
 x.h.renderPushSettings(x.parent,'a',api);await tick();const plants=plantsInput(x);plants.checked=false;await plants.onchange();assert.equal(x.read().revision,4);assert.equal(x.read().active.plantsPending,true);assert.equal(x.read().active.plants,false);assert.equal(plants.disabled,true);assert.match(panelText(x),/tente novamente/);
 await x.buttons()[1].onclick();assert.equal(x.read().active,undefined);assert.equal(x.buttons()[0].disabled,false);
});

test('a second settings panel cannot overwrite an in-flight plants intention',async()=>{
 const x=boundHarness(),base=preferencesApi([],true);let complete,writes=0;const api={push:async(op,body)=>op==='preferences'&&typeof body.plants==='boolean'?(writes++,new Promise(r=>complete=r)):base.push(op,body)};
 x.h.renderPushSettings(x.parent,'a',api);await tick();const first=plantsInput(x);x.h.renderPushSettings(x.parent,'a',api);await tick();const second=plantsInput(x);
 first.checked=false;const work=first.onchange();await tick();second.checked=true;await second.onchange();assert.equal(writes,1);assert.equal(x.read().revision,4);assert.equal(x.read().active.plants,false);
 complete({bindingId:'current',deviceRevision:4,plants:false});await work;assert.equal(x.read().active.plants,false);
});

test('plants read failure offers a bounded explicit reload and does not reserve a revision',async()=>{
 const x=boundHarness(),base=preferencesApi([],false);let offline=true,calls=0;const api={push:async(op,body)=>{if(op==='preferences'){calls++;if(offline)throw Error('offline');}return base.push(op,body);}};
 x.h.renderPushSettings(x.parent,'a',api);await tick();assert.equal(calls,1);assert.equal(x.read().revision,3);assert.equal(plantsInput(x).disabled,true);assert.match(panelText(x),/tente novamente/);
 offline=false;const reload=x.buttons().find(n=>n.textContent==='Recarregar preferência de Plantas');await reload.onclick();assert.equal(calls,2);assert.equal(x.read().revision,3);assert.equal(plantsInput(x).checked,false);assert.equal(plantsInput(x).disabled,false);
});

test('reloading plants after a read error ignores double clicks',async()=>{
 const x=boundHarness(),base=preferencesApi([],false);let attempts=0,finish;const api={push:async(op,body)=>{if(op==='preferences'){attempts++;if(attempts===1)throw Error('offline');return new Promise(r=>finish=()=>r({bindingId:body.bindingId,deviceRevision:body.deviceRevision,plants:false}));}return base.push(op,body);}};
 x.h.renderPushSettings(x.parent,'a',api);await tick();const reload=x.buttons().find(n=>n.textContent==='Recarregar preferência de Plantas');const first=reload.onclick(),second=reload.onclick();await tick();assert.equal(attempts,2);finish();await Promise.all([first,second]);assert.equal(plantsInput(x).disabled,false);
});

test('successful activation restores the disable control and offline revoke keeps its recovery message',async()=>{
 const x=harness();let offline=false;const api={push:async op=>{if(op==='config')return{enabled:true,publicKey:'AA',plantEventsEnabled:true};if(offline)throw Error('offline');return{};}};
 x.h.renderPushSettings(x.parent,'a',api);await tick();await x.buttons()[0].onclick();assert.equal(x.buttons()[1].disabled,false);offline=true;await x.buttons()[1].onclick();assert.equal(x.read().active,undefined);assert.equal(x.buttons()[0].disabled,false);assert.match(panelText(x),/vinculação será reconciliada/);
});

test('a PR80 binding permits tests but cannot display crops without an explicit boolean opt-in',async()=>{
 const handlers={},shown=[];let state;
 const ctx={pushState:async()=>state,acceptsPush,URL,location:{origin:'https://game.invalid'},registration:{showNotification:async(title,options)=>shown.push([title,options]),getNotifications:async()=>[]},clients:{matchAll:async()=>[],openWindow:async()=>{}},addEventListener:(type,handler)=>handlers[type]=handler};
 const source=readFileSync(new URL('../src/push-worker.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'');runInNewContext(stripTypeScriptTypes(source),ctx);
 async function push(type){let work;handlers.push({data:{json:()=>({type,bindingId:'current'})},waitUntil:p=>work=p});await work;}
 for(const plants of [undefined,false,null,0,1,'true',{},[]]){
  state={revision:3,account:'a',pending:[],active:{account:'a',bindingId:'current',...(plants===undefined?{}:{plants})}};
  const before=shown.length;await push('CROP_READY');assert.equal(shown.length,before,'crop suppressed for '+JSON.stringify(plants));await push('TEST');assert.equal(shown.length,before+1);
 }
 state.active.plants=true;await push('CROP_READY');assert.equal(shown.at(-1)[1].body,'Há plantas prontas para colher na sua fazenda. Toque para voltar ao jogo.');
});

test('opening PR80 settings reads effective false without opting in or activating the device',async()=>{
 const x=boundHarness(),calls=[];let complete;
 x.h.renderPushSettings(x.parent,'a',{push:async(op,body)=>{calls.push([op,body]);if(op==='config')return{enabled:true,publicKey:'AA',plantEventsEnabled:true};return new Promise(r=>complete=()=>r({bindingId:'current',deviceRevision:3,plants:false}));}});
 assert.equal(plantsInput(x).checked,false);await tick();assert.equal(plantsInput(x).checked,false);assert.equal(plantsInput(x).disabled,true);
 complete();await tick();assert.equal(plantsInput(x).checked,false);assert.equal(plantsInput(x).disabled,false);assert.equal(x.read().active.plants,false);assert.equal(x.read().revision,3);
 assert.deepEqual(calls.map(([op])=>op),['config','preferences']);assert.equal('plants'in calls[1][1],false);assert.equal(x.permissionCalls(),0);assert.equal(x.subscribeCalls(),0);
});

test('activation and a test leave plants off until the checkbox explicitly opts in',async()=>{
 const x=harness(),calls=[],api=preferencesApi(calls,false);x.h.renderPushSettings(x.parent,'a',api);
 assert.equal(plantsInput(x).checked,false);await tick();await x.buttons()[0].onclick();
 const subscribe=calls.find(([op])=>op==='subscribe')[1];assert.deepEqual(JSON.parse(JSON.stringify(subscribe.preferences)),{plants:false,animals:false,thefts:false,pests:false});assert.equal(x.read().active.plants,false);assert.equal(plantsInput(x).checked,false);assert.equal(plantsInput(x).disabled,false);
 await x.buttons().find(n=>n.textContent==='Enviar notificação de teste').onclick();assert.equal(x.read().active.plants,false);assert.equal(calls.filter(([op])=>op==='preferences').length,0);
 const revision=x.read().revision,plants=plantsInput(x);plants.checked=true;await plants.onchange();assert.equal(x.read().revision,revision+1);assert.equal(x.read().active.plants,true);
 const writes=calls.filter(([op])=>op==='preferences');assert.equal(writes.length,1);assert.equal(writes[0][1].plants,true);assert.equal(writes[0][1].deviceRevision,revision+1);
 x.h.renderPushSettings(x.parent,'a',api);await tick();assert.equal(plantsInput(x).checked,true);assert.equal(plantsInput(x).disabled,false);
 const buttons=x.parent.children.at(-1).children.filter(n=>typeof n==='object'&&n.tag==='button');await buttons[1].onclick();await buttons[0].onclick();assert.equal(x.read().active.plants,false);assert.equal(plantsInput(x).checked,false);assert.equal(calls.filter(([op])=>op==='subscribe').at(-1)[1].preferences.plants,false);
});

test('unavailable notification types remain unchecked and disabled',async()=>{
 const x=harness();x.h.renderPushSettings(x.parent,'a',preferencesApi());await tick();
 for(const row of x.parent.children[0].children.filter(n=>n.tag==='label')){assert.equal(row.children[0].checked,false);assert.equal(row.children[0].disabled,true);}
});

for(const plants of [undefined,false,null,0,1,'true',{},[]])test('settings fail closed for persisted plants '+JSON.stringify(plants),async()=>{
 const x=boundHarness();if(plants!==undefined)x.write({...x.read(),active:{...x.read().active,plants}});
 x.h.renderPushSettings(x.parent,'a',{push:async()=>{throw Error('offline');}});await tick();assert.equal(plantsInput(x).checked,false);assert.equal(plantsInput(x).disabled,true);assert.equal(acceptsPush(x.read(),{type:'CROP_READY',bindingId:'current'}),false);
});

test('a lost opt-in response repeats the explicit intent at exactly the same revision',async()=>{
 const x=boundHarness(),calls=[],base=preferencesApi(calls,false);let lost=true;
 const api={push:async(op,body)=>{const result=await base.push(op,body);if(op==='preferences'&&body.plants===true&&lost){lost=false;throw Error('response lost');}return result;}};
 x.h.renderPushSettings(x.parent,'a',api);await tick();const plants=plantsInput(x);plants.checked=true;await plants.onchange();assert.equal(x.read().active.plantsPending,true);assert.equal(x.read().revision,4);
 await x.buttons().find(n=>n.textContent==='Repetir alteração de Plantas').onclick();assert.equal(x.read().active.plants,true);assert.equal(x.read().active.plantsPending,undefined);assert.equal(x.read().revision,4);
 assert.deepEqual(calls.filter(([op,b])=>op==='preferences'&&'plants'in b).map(([,b])=>[b.deviceRevision,b.plants]),[[4,true],[4,true]]);
});
