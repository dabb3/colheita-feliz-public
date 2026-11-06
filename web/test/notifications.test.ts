import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import {notificationCount,PROTECTED_ICON} from '../src/notifications.ts';

test('bell sits before shop and every protection icon uses the lock asset',()=>{
 const html=readFileSync(new URL('../index.html',import.meta.url),'utf8');assert.ok(html.indexOf('id="notifications"')<html.indexOf('id="shop"'));
 assert.equal(PROTECTED_ICON,'/assets/classic/icons/icone-cadeado.png');
 const main=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8');assert.ok(main.includes("'Protegida',PROTECTED_ICON"));
 assert.ok(!main.includes("'Protegida','/assets/classic/animals/abelha"));
});
test('notification list renders safely, supports read actions and marks all without mutating server state',()=>{
 const make=tag=>({tag,children:[],attributes:{},textContent:'',append(...v){this.children.push(...v);},setAttribute(k,v){this.attributes[k]=v;}});
 const ctx={document:{createElement:make}};
 const load=name=>readFileSync(new URL('../src/'+name,import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(load('ui.ts')+'\n'+load('notifications.ts'))+'\nglobalThis.render=renderNotifications;',ctx);
 const items=[{id:'one',type:'THEFT',message:'<João> furtou 1 Morango.',createdAt:'2026-11-06T12:00:00Z',read:false,metadata:{}},{id:'two',type:'VIP_EXPIRED',message:'VIP expirou',createdAt:'2026-11-06T11:00:00Z',read:true,metadata:{}}];
 assert.equal(notificationCount(items),1);const root=make('div'),reads=[];let all=0;ctx.render(root,items,id=>reads.push(id),()=>all++);
 const nodes=n=>[n,...n.children.flatMap(nodes)];const flat=nodes(root);assert.ok(flat.some(n=>n.textContent==='<João> furtou 1 Morango.'));
 flat.find(n=>n.textContent==='Marcar como lida').onclick();root.children[0].onclick();assert.deepEqual(reads,['one']);assert.equal(all,1);
 assert.equal(items[0].read,false);assert.ok(flat.find(n=>n.textContent==='Lida').disabled);
 const empty=make('div');ctx.render(empty,[],()=>{},()=>{});assert.ok(empty.children[0].disabled);
});
