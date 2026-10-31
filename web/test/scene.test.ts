import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as presentation from '../src/presentation.ts';

test('scene draws stage-sized plants, real pests, ready props and trimmed hive without economic calls',()=>{
 const drawn=[],texts=[],pending=[],events=new Map(),selected=[],hovered=[];
 let tick,visibility,now=0,timers=0;const cleared=[];
 const context={clearRect(){},fillRect(){},save(){},restore(){},translate(){},scale(){},rotate(){},beginPath(){},moveTo(){},lineTo(){},closePath(){},stroke(){},ellipse(){},fill(){},
  drawImage(image,...args){drawn.push({path:image.src,args});},fillText(text){texts.push(text);},
  createRadialGradient(){return{addColorStop(){}};},getImageData(x,y,w,h){const data=new Uint8ClampedArray(w*h*4);for(let i=3;i<data.length;i+=4)data[i]=255;return{data};}};
 const canvas={width:1600,height:900,getContext:()=>context,getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler){events.set(name,handler);}};
 const environment={...presentation,periodAt:()=> 'day',performance:{now:()=>now},window:{setInterval:fn=>{tick=fn;return ++timers;}},clearInterval:id=>cleared.push(id),AbortController,document:{hidden:false,addEventListener:(event,fn)=>{visibility=fn;},createElement:()=>({getContext:()=>context})},
  Image:class{width=100;height=200;onload;src;constructor(){pending.push(this);}}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import .*$/mg,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',environment);
 const scene=new environment.Scene(canvas,{crops:[{id:'nabo',spriteStem:'nabo'}]},()=>{},id=>selected.push(id),tile=>hovered.push(tile));
 const tile={column:0,row:0,unlocked:true,state:'READY',cropId:'nabo',stage:3,weeds:true,insects:2};
 scene.update({own:true,nextPlot:{column:1,row:0},tiles:[tile],animals:[{id:'vaca',stage:'ADULT',status:'READY'},{id:'abelha',stage:'ADULT',status:'PRODUCING'}]});
 while(pending.length){const image=pending.shift();if(image.src.includes('atlas')){image.width=400;image.height=100;}image.onload();}
 assert.ok(drawn.some(d=>d.path.endsWith('nabo_3.png')&&d.args.at(-1)===80&&d.args.at(-2)===40));
 assert.ok(drawn.some(d=>/erva_daninha_[1-4]\.png$/.test(d.path)));
 assert.ok(drawn.some(d=>d.path.endsWith('verme_2.png')));
 assert.ok(drawn.some(d=>d.path.endsWith('balde-vaca.png')));
 assert.ok(drawn.some(d=>d.path.endsWith('colmeia-abelha-atlas.png')&&d.args[0]===0&&d.args.at(-2)===156&&d.args.at(-1)===78));
 assert.ok(texts.includes('50 verdes'));
 events.get('click')({clientX:1553,clientY:457});
 assert.deepEqual(selected,['vaca']);
 events.get('pointermove')({clientX:585,clientY:400});assert.equal(hovered.at(-1).state,'READY');
 drawn.length=0;
 scene.update({tiles:[{...tile,state:'DEAD'}],animals:[{id:'vaca',stage:'ADULT',status:'PRODUCING'}]});
 assert.equal(hovered.at(-1).state,'DEAD');
 while(pending.length)pending.shift().onload();
 assert.ok(drawn.some(d=>d.path.endsWith('planta-morta.png')&&d.args.at(-2)===86));
 assert.ok(!drawn.some(d=>d.path.endsWith('balde-vaca.png')));
 scene.effect('WATER',0,0);texts.length=0;tick();assert.ok(texts.includes('Regado'));
 environment.document.hidden=true;visibility();assert.ok(cleared.includes(1));
 environment.document.hidden=false;now=10000;visibility();tick();assert.equal(timers,2);assert.ok(texts.includes('Regado'));
 scene.close();
 const count=drawn.length;tick();assert.equal(drawn.length,count);
});
