import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';

// Execute the production positioning function against real browser geometry contracts.
const source=readFileSync(new URL('../src/main.ts',import.meta.url),'utf8');
function place({game,canvas,viewport,tools,point,height=290,header={top:0,height:0}}){
 const panel={className:'crop-tooltip',style:{setProperty(){}},dataset:{},getBoundingClientRect(){return {height:Math.min(height,parseFloat(this.style.maxHeight)||Infinity)};}};
 const context={element:id=>id==='farm'?{getBoundingClientRect:()=>canvas}:id==='tools-bar'?{getBoundingClientRect:()=>tools}:id==='farm-header'?{getBoundingClientRect:()=>header}:{hidden:true,getBoundingClientRect:()=>({top:0,height:0})},window:{innerWidth:viewport.width,innerHeight:viewport.height,visualViewport:viewport,matchMedia:()=>({matches:viewport.width<=1100})},document:{documentElement:{clientWidth:viewport.width,clientHeight:viewport.height}},worldToScreen:p=>p,sceneTransform:()=>({})};
 runInNewContext(stripTypeScriptTypes(source.slice(source.indexOf('function positionHealth('),source.indexOf('function scheduleHealthClock(')))+'\nglobalThis.positionHealth=positionHealth;',context);
 context.positionHealth(panel,{getBoundingClientRect:()=>game},point);
 return {left:parseFloat(panel.style.left),top:parseFloat(panel.style.top),height:Math.min(height,parseFloat(panel.style.maxHeight)||Infinity),width:parseFloat(panel.style.width)};
}
for(const width of [320,390,739,1366])test(`plant popup stays in visible space above tools at width ${width}`,()=>{
 for(const point of [{x:20,y:50},{x:width-20,y:550},{x:width/2,y:850}]){
  const layout=place({game:{left:0,top:0,width,height:1000},canvas:{left:0,top:0,width,height:1000},viewport:{offsetLeft:0,offsetTop:80,width,height:700},tools:{top:620,bottom:1000},point});
  assert.ok(layout.top>=92,JSON.stringify(layout));
  assert.ok(layout.top+layout.height<=608,JSON.stringify(layout));
  assert.ok(layout.left>=12&&layout.left+layout.width<=width-12,JSON.stringify(layout));
 }
});
test('friends cards wrap names and actions instead of overflowing narrow dialogs',()=>{
 const css=readFileSync(new URL('../style.css',import.meta.url),'utf8');
 assert.match(css,/\.friend-card\s*\{[^}]*flex-wrap\s*:\s*wrap/s);
 assert.match(css,/\.friend-card strong\s*\{[^}]*overflow-wrap\s*:\s*anywhere/s);
 assert.match(css,/\.friend-card[^}]*button\s*\{[^}]*min-width\s*:\s*0/s);
});

test('zoomed popup never covers the mobile header menu',()=>{
 const layout=place({game:{left:0,top:0,width:390,height:844},canvas:{left:-250,top:200,width:900,height:506},viewport:{offsetLeft:0,offsetTop:0,width:390,height:844},header:{top:0,height:220,bottom:220},tools:{top:660},point:{x:430,y:280},height:223});
 assert.ok(layout.top>=232,JSON.stringify(layout));assert.ok(layout.top+layout.height<=648);
});
