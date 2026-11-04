import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as presentation from '../src/presentation.ts';

test('the farm uses one uniform scale and centers the full world at review viewport sizes',()=>{
 for(const [width,height]of [[390,844],[1024,600],[1366,768],[2560,1080]]){
  const transform=presentation.sceneTransform(width,height);
  assert.equal(transform.scaleX,transform.scaleY);
  assert.equal(transform.offsetX,(width-1600*transform.scaleX)/2);
  assert.equal(transform.offsetY,(height-900*transform.scaleY)/2);
  for(const point of [{x:0,y:0},{x:1600,y:900},...Array.from({length:30},(_,i)=>presentation.tileCenter(i%6,Math.floor(i/6)))]){
   const screen=presentation.worldToScreen(point,transform);
   assert.ok(screen.x>=transform.offsetX-1e-8&&screen.x<=width-transform.offsetX+1e-8);
   assert.ok(screen.y>=transform.offsetY-1e-8&&screen.y<=height-transform.offsetY+1e-8);
   const world=presentation.screenToWorld(screen,transform)!;
   assert.ok(Math.abs(world.x-point.x)<1e-8&&Math.abs(world.y-point.y)<1e-8);
  }
  assert.equal(presentation.screenToWorld({x:-1,y:-1},transform),null);
 }
});

test('letterbox space does not map to the world and a collapsed canvas cannot produce a point',()=>{
 const transform=presentation.sceneTransform(390,844);
 const top=presentation.screenToWorld({x:195,y:transform.offsetY},transform)!,bottom=presentation.screenToWorld({x:195,y:transform.offsetY+900*transform.scaleY},transform)!;
 assert.equal(top.x,800);assert.equal(top.y,0);
 assert.equal(bottom.x,800);assert.equal(bottom.y,900);
 assert.equal(presentation.screenToWorld({x:195,y:100},transform),null);
 assert.equal(presentation.screenToWorld({x:0,y:0},presentation.sceneTransform(0,0)),null);
});

test('crop HUD timing matches server projections for watered, dry, unwatered, paused, and ready crops',()=>{
 const tile={state:'GROWING',watered:true,dry:false,paused:false};
 assert.equal(presentation.cropRemainingAfter(tile,120,10),110);
 assert.equal(presentation.cropNominalRemainingAfter(tile,120,10),110);
 assert.equal(presentation.cropRemainingAfter({...tile,dry:true},220,10),210);
 assert.equal(presentation.cropNominalRemainingAfter({...tile,dry:true},120,10),115);
 assert.equal(presentation.cropRemainingAfter({...tile,watered:false,dry:true},120,10),120);
 assert.equal(presentation.cropNominalRemainingAfter({...tile,watered:false,dry:true},120,10),120);
 assert.equal(presentation.cropRemainingAfter({...tile,paused:true},220,10),220);
 assert.equal(presentation.cropNominalRemainingAfter({...tile,paused:true},120,10),120);
 assert.equal(presentation.cropRemainingAfter({...tile,state:'READY'},0,10),0);
 assert.equal(presentation.cropNominalRemainingAfter({...tile,state:'READY'},0,10),0);
});

test('points just inside every diamond edge retain the same plot after projection',()=>{
 for(const [width,height]of [[390,844],[1024,600],[1366,768],[2560,1080]]){
  const transform=presentation.sceneTransform(width,height);
  for(let row=0;row<5;row++)for(let column=0;column<6;column++){
   const center=presentation.tileCenter(column,row);
   for(const [dx,dy]of [[84,0],[-84,0],[0,35],[0,-35]]){
    const screen=presentation.worldToScreen({x:center.x+dx,y:center.y+dy},transform);
    const point=presentation.screenToWorld(screen,transform)!;
    assert.deepEqual(presentation.pickTile(point.x,point.y),{column,row});
   }
  }
 }
});

test('canvas clicks and hover share the uniform transform and ignore letterbox space',()=>{
 const handlers=new Map(),selected=[],hovered=[],timers=new Map(),sounds=[];
 let timerId=0;
 let bounds={left:10,top:312.3125,width:390,height:219.375};
 const canvas={width:0,height:0,getContext:()=>({}),getBoundingClientRect:()=>bounds,addEventListener:(name,handler)=>handlers.set(name,handler)};
 const context={...presentation,window:{requestAnimationFrame:()=>1,cancelAnimationFrame(){},setTimeout(callback,delay){assert.equal(delay,500);timers.set(++timerId,callback);return timerId;},clearTimeout(id){timers.delete(id);}},document:{hidden:true},AbortController,performance:{now:()=>0}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',context);
 const scene=new context.Scene(canvas,{},tile=>selected.push([tile.column,tile.row]),()=>{},tile=>hovered.push(tile),undefined,undefined,id=>sounds.push(id));
 const tiles=Array.from({length:30},(_,i)=>({column:i%6,row:Math.floor(i/6)}));
 scene.update({owner:'synthetic',tiles,animals:[]});
 const transform=presentation.sceneTransform(bounds.width,bounds.height),screen=presentation.worldToScreen(presentation.tileCenter(0,0),transform);
 handlers.get('click')({clientX:bounds.left+screen.x,clientY:bounds.top+screen.y});
 assert.deepEqual(selected,[[0,0]]);
 handlers.get('pointermove')({clientX:bounds.left+screen.x,clientY:bounds.top+screen.y});
 assert.equal(hovered.at(-1),tiles[0]);
 handlers.get('click')({clientX:10+195,clientY:bounds.top-1});
 handlers.get('pointermove')({clientX:10+195,clientY:bounds.top-1});
 assert.equal(selected.length,1);assert.equal(hovered.at(-1),null);
 bounds={left:0,top:0,width:1600,height:900};
 handlers.get('click')({clientX:670,clientY:724});
 assert.deepEqual(selected,[[0,0],[5,4]]);
 scene.update({owner:'synthetic',tiles,animals:[{id:'abelha'}]});
 const point={clientX:1210,clientY:800,pointerType:'mouse'};
 handlers.get('pointermove')(point);assert.equal(timers.size,1);assert.deepEqual(sounds,[]);
 handlers.get('pointerleave')();assert.equal(timers.size,0);
 handlers.get('pointermove')(point);assert.equal(timers.size,1);
 const [id,fire]=timers.entries().next().value;timers.delete(id);fire();
 handlers.get('pointermove')(point);assert.equal(timers.size,0);assert.deepEqual(sounds,['abelha']);
 scene.close();
});
