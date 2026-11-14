import test from 'node:test';
import assert from 'node:assert/strict';
import {cameraLayout,canvasResolution,coverScale,initialCamera,zoomAround} from '../src/mobile.ts';

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
