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
 assert.equal(notificationCount(items),1);const root=make('div'),reads=[];let all=0,cleared=0;const dismissed=[];ctx.render(root,items,id=>reads.push(id),()=>all++,id=>dismissed.push(id),()=>cleared++);
 const nodes=n=>[n,...n.children.flatMap(nodes)];const flat=nodes(root);assert.ok(flat.some(n=>n.textContent==='<João> furtou 1 Morango.'));
 flat.find(n=>n.textContent==='Marcar como lida').onclick();root.children[0].onclick();assert.deepEqual(reads,['one']);assert.equal(all,1);
 const dismiss=flat.filter(n=>n.textContent==='×');assert.equal(dismiss.length,1);dismiss[0].onclick();assert.deepEqual(dismissed,['two']);flat.find(n=>n.textContent==='Limpar lidas').onclick();assert.equal(cleared,1);assert.equal(items[0].read,false);assert.ok(flat.find(n=>n.textContent==='Lida').disabled);
 const empty=make('div');ctx.render(empty,[],()=>{},()=>{});assert.ok(empty.children[0].disabled);
});
test('late crop notices separate maturity, protection and generation and support legacy entries',()=>{
 const make=tag=>({tag,children:[],textContent:'',append(...nodes){this.children.push(...nodes);},setAttribute(){}});
 const ctx={document:{createElement:make}};
 const load=name=>readFileSync(new URL('../src/'+name,import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(load('ui.ts')+'\n'+load('notifications.ts'))+'\nglobalThis.render=renderNotifications;',ctx);
 const createdAt='2026-11-08T14:53:07.606Z',maturedAt='2026-11-08T14:07:20.730Z',ownerExclusiveUntil='2026-11-08T14:12:20.730Z';
 const base={id:'crop',type:'CROP_READY',message:'Ervilha: planta pronta para colher.',createdAt,read:false};
 const nodes=node=>[node,...node.children.flatMap(nodes)];
 for(const metadata of [{maturedAt,ownerExclusiveUntil},{},{maturedAt:'invalid',ownerExclusiveUntil:'invalid'}]){
  const root=make('div');ctx.render(root,[{...base,metadata}],()=>{},()=>{});
  const times=nodes(root).filter(node=>node.tag==='time'),hasTiming=metadata.maturedAt===maturedAt;
  assert.deepEqual(times.map(node=>node.dateTime),hasTiming?[maturedAt,ownerExclusiveUntil,createdAt]:[createdAt]);
  assert.match(times.at(-1).textContent,/^Aviso gerado:/);
  if(hasTiming){assert.equal(times[0].textContent,`Maturou: ${new Date(maturedAt).toLocaleString('pt-BR')}`);assert.match(times[1].textContent,/^Proteção até:/);}
 }
});
