import test from 'node:test';
import assert from 'node:assert/strict';
import {anchorCamera,cameraLayout,canvasResolution,coverScale,initialCamera,setupCamera,setupMobileLayout,setMoreMenuVisiting,syncMobileCamera,worldAt,zoomAround} from '../src/mobile.ts';

test('the mobile camera always covers the viewport without empty bands',()=>{
 for(const [width,height]of [[360,420],[390,480],[430,560],[340,700]]){
  for(const camera of [{zoom:1,centerX:0,centerY:0},{zoom:9,centerX:1600,centerY:900},{zoom:0.2,centerX:800,centerY:450}]){
   const layout=cameraLayout(width,height,camera);
   assert.ok(layout.camera.zoom>=1&&layout.camera.zoom<=2.5);
   assert.ok(layout.x<=0&&layout.y<=0);
   assert.ok(layout.x+layout.width>=width-1e-6&&layout.y+layout.height>=height-1e-6);
   assert.ok(Math.abs(layout.width/layout.height-16/9)<1e-9,"sprites keep their proportions");
  }
 }
});

test('initial framing centers the plots and never starts below cover scale',()=>{
 const camera=initialCamera(390,480),layout=cameraLayout(390,480,camera);
 assert.ok(layout.scale>=coverScale(390,480));
 const plotsCenter=(627.5*layout.scale)+layout.x;
 assert.ok(Math.abs(plotsCenter-195)<1,'plots are horizontally centered');
});

test('pinch zoom keeps the world point under the fingers fixed',()=>{
 const before={zoom:1.2,centerX:700,centerY:500},finger={x:120,y:200};
 const a=cameraLayout(390,480,before),world={x:(finger.x-a.x)/a.scale,y:(finger.y-a.y)/a.scale};
 const b=cameraLayout(390,480,zoomAround(390,480,before,2,finger));
 assert.ok(Math.abs(world.x*b.scale+b.x-finger.x)<1e-6);
 assert.ok(Math.abs(world.y*b.scale+b.y-finger.y)<1e-6);
});

test('the backing store grows with zoom but stays capped for phones',()=>{
 assert.equal(canvasResolution(0.25,1),1);
 assert.equal(canvasResolution(0.5,3),1.5);
 assert.equal(canvasResolution(1.25,3),2);
});

test('pinch moving both fingers zooms and pans together',()=>{
 const camera={zoom:1.2,centerX:700,centerY:500},start={x:150,y:220},end={x:230,y:160};
 const anchor=worldAt(390,480,camera,start),moved=cameraLayout(390,480,anchorCamera(390,480,anchor,1.8,end));
 assert.ok(Math.abs(anchor.x*moved.scale+moved.x-end.x)<1e-6,'world point follows the fingers horizontally');
 assert.ok(Math.abs(anchor.y*moved.scale+moved.y-end.y)<1e-6,'world point follows the fingers vertically');
 assert.ok(Math.abs(moved.camera.zoom-1.8)<1e-9);
});

function harness(){
 globalThis.ResizeObserver??=class{observe(){}};
 globalThis.devicePixelRatio=3;
 const scene=Object.assign(new EventTarget(),{classList:{add(){},remove(){}},contains:()=>true,getBoundingClientRect:()=>({left:0,top:0,width:390,height:480})});
 const canvas=Object.assign(new EventTarget(),{width:1600,height:900,style:{removeProperty(){}}});
 const button=()=>Object.assign(new EventTarget(),{disabled:false});
 const root=new EventTarget(),media=Object.assign(new EventTarget(),{matches:true});
 setupCamera(media,{scene,canvas,controls:{hidden:true,contains:()=>false},zoomIn:button(),zoomOut:button(),root});
 const pointer=(target,type,pointerId,clientX,clientY)=>target.dispatchEvent(Object.assign(new Event(type),{pointerId,clientX,clientY}));
 const click=()=>{const event=new Event('click',{cancelable:true});scene.dispatchEvent(event);return event.defaultPrevented;};
 return{scene,canvas,root,pointer,click};
}

test('a drag or pinch never reaches the farm, a tap does',()=>{
 const {scene,root,pointer,click}=harness();
 pointer(scene,'pointerdown',1,100,100);pointer(root,'pointerup',1,102,101);
 assert.equal(click(),false,'tap executes the tool');
 pointer(scene,'pointerdown',1,100,100);pointer(scene,'pointermove',1,160,140);pointer(root,'pointerup',1,160,140);
 assert.equal(click(),true,'drag click is swallowed');
 pointer(scene,'pointerdown',1,100,100);pointer(scene,'pointerdown',2,200,200);pointer(scene,'pointermove',2,240,240);
 pointer(root,'pointerup',1,100,100);pointer(root,'pointerup',2,240,240);
 assert.equal(click(),true,'pinch click is swallowed');
});

test('a pointer released outside the scene does not stick',()=>{
 const {scene,canvas,root,pointer}=harness();
 pointer(scene,'pointerdown',1,100,100);pointer(root,'pointerup',1,500,500);
 pointer(scene,'pointerdown',2,100,100);
 const before=canvas.style.transform;pointer(scene,'pointermove',2,160,100);
 assert.notEqual(canvas.style.transform,before,'next finger pans instead of starting a pinch with a stale pointer');
});

test('the camera restores the sharp backing store after a new Scene resets the canvas',()=>{
 const {canvas}=harness();
 const sharp=canvas.width;assert.ok(sharp>1600);
 canvas.width=1600;canvas.height=900;
 syncMobileCamera();
 assert.equal(canvas.width,sharp);
});

test('portrait visit keeps Amigos in the bar while Mais is hidden, and puts it back afterwards',()=>{
 // Minimal DOM: nodes live in parent child lists so "before"/"after" keep the real order.
 const node=(id,tag='button')=>({id,tag,hidden:false,parent:null,attributes:{},listeners:{},
  setAttribute(key,value){this.attributes[key]=String(value);},addEventListener(name,handler){this.listeners[name]=handler;},focus(){},contains(target){return target===this||(this.children??[]).includes(target);},
  remove(){if(this.parent){this.parent.children.splice(this.parent.children.indexOf(this),1);this.parent=null;}},
  before(other){other.remove();other.parent=this.parent;this.parent.children.splice(this.parent.children.indexOf(this),0,other);},
  after(other){other.remove();other.parent=this.parent;this.parent.children.splice(this.parent.children.indexOf(this)+1,0,other);},
  append(...others){for(const other of others){other.remove();other.parent=this;(this.children??=[]).push(other);}},
  querySelector(){return null;}});
 const bar=node('farm-menu','nav'),menu=node('more-menu','div'),more=node('more'),ids=['notifications','shop','friends','home','settings','events'];
 const byId=new Map([[bar.id,bar],[menu.id,menu],[more.id,more]]);bar.append(...ids.map(id=>{const button=node(id);byId.set(id,button);return button;}),more,menu);
 const media={matches:true,addEventListener(){}};const saved={window:globalThis.window,document:globalThis.document,ResizeObserver:globalThis.ResizeObserver};
 globalThis.window={matchMedia:()=>media};globalThis.document={getElementById:id=>byId.get(id)??null,createComment:id=>node('#'+id,'comment'),addEventListener(){}};
 try{
  setupMobileLayout();
  assert.equal(byId.get('friends').parent,menu,'portrait moves Amigos into Mais');
  menu.hidden=false;setMoreMenuVisiting(true);
  assert.equal(byId.get('friends').parent,bar,'Amigos stays reachable in the bar during a visit');assert.equal(menu.hidden,true,'the open Mais menu closes');
  assert.ok(['notifications','settings','events'].every(id=>byId.get(id).parent===menu));
  setMoreMenuVisiting(false);assert.equal(byId.get('friends').parent,menu);
  assert.deepEqual(menu.children.map(child=>child.id),['notifications','friends','settings','events'],'original order is kept');
 }finally{setMoreMenuVisiting(false);Object.assign(globalThis,saved);}
});
