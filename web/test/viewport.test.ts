import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as presentation from '../src/presentation.ts';

test('the complete world fits and all plot centers roundtrip across window shapes',()=>{
 for(const [width,height]of [[390,844],[1024,600],[1280,1024],[1366,768],[2560,1080]]){
  const transform=presentation.sceneTransform(width,height);
  for(const point of [{x:0,y:0},{x:1600,y:900},...Array.from({length:30},(_,i)=>presentation.tileCenter(i%6,Math.floor(i/6)))]){
   const screen=presentation.worldToScreen(point,transform);
   assert.ok(screen.x>=-1e-8&&screen.x<=width+1e-8);
   assert.ok(screen.y>=-1e-8&&screen.y<=height+1e-8);
   const world=presentation.screenToWorld(screen,transform)!;
   assert.ok(Math.abs(world.x-point.x)<1e-8&&Math.abs(world.y-point.y)<1e-8);
  }
  assert.equal(presentation.screenToWorld({x:-1,y:-1},transform),null);
 }
});

test('letterbox space and a collapsed canvas cannot produce a world point',()=>{
 const transform=presentation.sceneTransform(390,844);
 assert.equal(presentation.screenToWorld({x:195,y:50},transform),null);
 assert.equal(presentation.screenToWorld({x:195,y:800},transform),null);
 assert.equal(presentation.screenToWorld({x:0,y:0},presentation.sceneTransform(0,0)),null);
});

test('points just inside every diamond edge retain the same plot after projection',()=>{
 for(const [width,height]of [[390,844],[1024,600],[1280,1024],[1366,768],[2560,1080]]){
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

test('canvas clicks and hover keep their plot after resize and ignore the surrounding space',()=>{
 const handlers=new Map(),selected=[],hovered=[];
 let bounds={left:10,top:20,width:390,height:844};
 const canvas={width:0,height:0,getContext:()=>({}),getBoundingClientRect:()=>bounds,addEventListener:(name,handler)=>handlers.set(name,handler)};
 const context={...presentation,window:{requestAnimationFrame:()=>1,cancelAnimationFrame(){}},document:{hidden:true},AbortController};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',context);
 const scene=new context.Scene(canvas,{},tile=>selected.push([tile.column,tile.row]),()=>{},tile=>hovered.push(tile));
 const tiles=Array.from({length:30},(_,i)=>({column:i%6,row:Math.floor(i/6)}));
 scene.update({owner:'synthetic',tiles,animals:[]});
 // At 390 × 844, contain fits the world into 390 × 219.375, centered vertically.
 handlers.get('click')({clientX:10+585*390/1600,clientY:20+(844-900*390/1600)/2+400*390/1600});
 assert.deepEqual(selected,[[0,0]]);
 handlers.get('pointermove')({clientX:10+585*390/1600,clientY:20+(844-900*390/1600)/2+400*390/1600});
 assert.equal(hovered.at(-1),tiles[0]);
 handlers.get('click')({clientX:205,clientY:40});
 handlers.get('pointermove')({clientX:205,clientY:40});
 assert.equal(selected.length,1);assert.equal(hovered.at(-1),null);
 bounds={left:0,top:0,width:1600,height:900};
 handlers.get('click')({clientX:670,clientY:724});
 assert.deepEqual(selected,[[0,0],[5,4]]);
 scene.close();
});
