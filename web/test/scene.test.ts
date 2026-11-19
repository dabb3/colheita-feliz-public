import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as presentation from '../src/presentation.ts';

test('scene draws stage-sized plants, real pests, ready props and trimmed hive without economic calls',()=>{
 const drawn=[],texts=[],pending=[],images=[],events=new Map(),selected=[],hovered=[];
 let tick,now=0,timers=0,ellipses=0,period='day',christmas=false,canInspect=true;const periods=[],tilesSelected=[];const cancelled=[];
 const context={clearRect(){},fillRect(){},save(){},restore(){},translate(){},scale(){},rotate(){},beginPath(){},moveTo(){},lineTo(){},closePath(){},stroke(){},fill(){},
  drawImage(image,...args){drawn.push({path:image.src,args});},fillText(text){texts.push(text);},ellipse(){ellipses++;},
  createRadialGradient(){return{addColorStop(){}};},getImageData(x,y,w,h){const data=new Uint8ClampedArray(w*h*4);for(let row=0;row<h;row++)for(let col=0;col<w;col++)if(row<h*.88||col<w*.25)data[(row*w+col)*4+3]=255;return{data};}};
 const canvas={width:1600,height:900,getContext:()=>context,getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler){events.set(name,handler);}};
 const environment={...presentation,periodAt:()=>period,christmasActive:()=>christmas,performance:{now:()=>now},window:{requestAnimationFrame:fn=>{tick=fn;return ++timers;},cancelAnimationFrame:id=>cancelled.push(id)},AbortController,document:{hidden:false,createElement:()=>({getContext:()=>context})},
  Image:class{width=100;height=200;onload;onerror;src;constructor(){pending.push(this);images.push(this);}}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',environment);
 const scene=new environment.Scene(canvas,{crops:[{id:'nabo',spriteStem:'nabo'}],animals:[{id:'vaca',kind:'PRODUCER'},{id:'abelha',kind:'PROTECTOR'},{id:'cachorro-robo',kind:'PROTECTOR'}]},(tile,pointer)=>tilesSelected.push({tile,pointer}),id=>selected.push(id),tile=>hovered.push(tile),value=>periods.push(value),()=>{},()=>{},()=>canInspect);
 const tile={column:0,row:0,unlocked:true,state:'READY',cropId:'nabo',stage:3,weeds:true,insects:2};
 const farm={owner:'alice',own:true,nextPlot:{column:1,row:0},tiles:[tile,{column:0,row:1,unlocked:true,state:'GRASS'}],animals:[{id:'vaca',stage:'ADULT',status:'READY'},{id:'abelha',stage:'ADULT',status:'PRODUCING'},{id:'cachorro-robo',stage:'ADULT',status:'PRODUCING'}]};
 scene.update(farm);
 while(pending.length){const image=pending.shift();if(image.src.includes('atlas')){image.width=400;image.height=100;}if(image.src.endsWith('cachorro-andando.png'))image.onerror();else image.onload();}
 drawn.length=0;scene.update(farm);
 assert.ok(drawn.some(d=>d.path?.endsWith('cachorro.png')&&d.args.at(-2)===110),'idle dog remains visible at normal width when the walking reference fails');
 assert.equal(images.filter(image=>image.src.endsWith('cachorro-andando.png')).length,1,'failed reference is not requested again');
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('nabo_3.png')&&d.args.at(-1)===80&&d.args.at(-2)===40));
 const lastTerrain=drawn.findLastIndex(d=>typeof d.path==='string'&&/terrain\/(grama-default|terra-molhada)\.png$/.test(d.path));
 const firstPlant=drawn.findIndex(d=>typeof d.path==='string'&&d.path.endsWith('nabo_3.png'));
 assert.ok(firstPlant>lastTerrain,'all terrain must be drawn before any plant');
 assert.ok(drawn.some(d=>typeof d.path==='string'&&/erva_daninha_[1-4]\.png$/.test(d.path)));
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('verme_2.png')));
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('balde-vaca.png')));
 assert.ok(images.some(image=>image.src.endsWith('colmeia-abelha-atlas.png')));
 assert.ok(drawn.some(d=>d.args.at(-2)===156&&d.args.at(-1)===78));
 events.get('click')({clientX:1515,clientY:400});
 assert.deepEqual(selected,['vaca']);
 events.get('pointermove')({clientX:585,clientY:400});assert.equal(hovered.at(-1).state,'READY');
 canInspect=false;
 for(const pointerType of ['mouse','touch']){
  events.get('pointermove')({clientX:585,clientY:400,pointerType});
  assert.equal(hovered.at(-1),null,'tools must not send plant hover to the popup controller');
  events.get('click')({clientX:585,clientY:400,pointerType});
  assert.equal(tilesSelected.at(-1).tile,tile,'tool clicks still reach their normal action');
  assert.equal(tilesSelected.at(-1).pointer,pointerType);
 }
 canInspect=true;
 drawn.length=0;
 const beforeEffects=ellipses;now=100;scene.update({owner:'alice',tiles:[{...tile,state:'DEAD',weeds:false}],animals:[{id:'vaca',stage:'ADULT',status:'PRODUCING'}]});
 while(pending.length)pending.shift().onload();
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('planta-morta.png')&&d.args.at(-2)===86));
 const dead=drawn.findLast(d=>typeof d.path==='string'&&d.path.endsWith('planta-morta.png'));
 const [x,y,width,height]=dead.args.slice(-4),center=presentation.tileCenter(tile.column,tile.row);
 assert.equal(x+width/2,center.x,'dead leaves use the full trimmed artwork center, not the asymmetric bottom band');
 assert.equal(y+height,center.y+10,'dead leaves rest ten units below the tile center, like Desktop');
 assert.ok(!drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('balde-vaca.png')));
 assert.ok(ellipses>beforeEffects);
 events.get('pointermove')({clientX:585,clientY:400});assert.equal(hovered.at(-1).state,'DEAD');
 now=10000;tick(33);assert.equal(timers,2);
 assert.deepEqual(periods,['day']);period='night';scene.update(farm);assert.deepEqual(periods,['day','night']);scene.update(farm);assert.deepEqual(periods,['day','night']);period='day';scene.update(farm);assert.deepEqual(periods,['day','night','day']);
 const backgrounds=images.map(image=>image.src).filter(src=>src.includes('/background/'));
 assert.ok(['background.png','background-night.png'].every(name=>backgrounds.some(src=>src.endsWith('/'+name))),'open session switches between the original backgrounds');
 assert.ok(!backgrounds.some(src=>src.includes('natal')),'Christmas backgrounds are never requested');
 // Christmas starts and ends at midnight in São Paulo: the period stays night, yet the music must be synced again.
 period='night';scene.update(farm);periods.length=0;
 christmas=true;scene.update(farm);scene.update(farm);assert.deepEqual(periods,['night'],'event start resyncs the music once');
 christmas=false;scene.update(farm);assert.deepEqual(periods,['night','night'],'event end resyncs the music');
 christmas=true;scene.update(farm);while(pending.length)pending.shift().onload();drawn.length=0;scene.update(farm);
 assert.ok(drawn.some(d=>d.path?.endsWith('/background-night.png'))&&!drawn.some(d=>d.path?.includes('natal')),'the night background stays original');
 christmas=false;period='day';scene.update(farm);
 scene.update({...farm,tiles:[{...tile,protected:true}]});while(pending.length)pending.shift().onload();
 drawn.length=0;scene.update({...farm,tiles:[{...tile,protected:true}]});
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('icone-cadeado.png')),'owner sees protected plots');
 drawn.length=0;scene.update({...farm,own:false,tiles:[{...tile,protected:true}]});
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('nabo_3.png')));
 assert.ok(!drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('icone-cadeado.png')),'visitors never see protected plots');
 scene.close();
 assert.deepEqual(cancelled,[2]);
});

test('hungry animals get a red silhouette halo instead of the tiny feed icon, without leaking canvas state',()=>{
 const drawn=[],images=[],pending=[],stack=[];let reduced=false;
 const context={shadowColor:'rgba(0, 0, 0, 0)',shadowBlur:0,shadowOffsetX:0,shadowOffsetY:0,setTransform(){},clearRect(){},fillRect(){},translate(){},scale(){},rotate(){},beginPath(){},moveTo(){},lineTo(){},closePath(){},stroke(){},fill(){},ellipse(){},fillText(){},
  save(){stack.push({shadowColor:this.shadowColor,shadowBlur:this.shadowBlur});},restore(){Object.assign(this,stack.pop());},
  drawImage(image,...args){drawn.push({path:image.src??'',shadow:this.shadowColor,blur:this.shadowBlur,args});},
  createRadialGradient(){return{addColorStop(){}};},getImageData(x,y,w,h){const data=new Uint8ClampedArray(w*h*4);for(let row=0;row<h;row++)for(let col=0;col<w;col++)if(row<h*.88||col<w*.25)data[(row*w+col)*4+3]=255;return{data};}};
 const canvas={width:3200,height:1800,getContext:()=>context,getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(){}};
 const environment={...presentation,periodAt:()=>'day',performance:{now:()=>0},window:{requestAnimationFrame:()=>1,cancelAnimationFrame(){},matchMedia:query=>({matches:reduced&&query.includes('reduce')})},AbortController,document:{hidden:false,createElement:()=>({getContext:()=>context})},
  Image:class{width=100;height=200;onload;onerror;src;constructor(){pending.push(this);images.push(this);}}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',environment);
 const scene=new environment.Scene(canvas,{crops:[],animals:[{id:'vaca',kind:'PRODUCER'},{id:'abelha',kind:'PROTECTOR'}]},()=>{},()=>{},()=>{},()=>{},()=>{},()=>{},()=>true);
 canvas.width=3200; // the mobile camera doubles the backing store
 const farm={owner:'alice',own:true,tiles:[],animals:[{id:'vaca',stage:'ADULT',status:'HUNGRY'},{id:'abelha',stage:'ADULT',status:'HUNGRY'},{id:'porco',stage:'ADULT',status:'READY'}]};
 scene.update(farm);while(pending.length){const image=pending.shift();if(image.src.includes('atlas')){image.width=400;image.height=100;}image.onload();}
 drawn.length=0;scene.update(farm);
 const red=draw=>draw.shadow.startsWith('rgba(255, 32, 32');
 const cow=drawn.filter(d=>/vaca\/vaca[^/]*\.png$/.test(d.path)),hive=drawn.filter(d=>d.path===''),pig=drawn.filter(d=>/porco\/porco[^/]*\.png$/.test(d.path));
 assert.ok(cow.length>0&&cow.every(red),'hungry cow is drawn with the red halo');assert.ok(hive.some(red),'hungry hive (atlas frame) gets the halo too');assert.ok(drawn.filter(red).every(d=>d.path===''||/vaca\//.test(d.path)),'only the hungry animals glow');
 assert.ok(pig.length>0&&pig.every(d=>!red(d)),'a fed animal has no halo');
 assert.ok(cow.every(d=>d.blur>=28),'blur follows the doubled backing store');
 assert.ok(!images.some(image=>image.src.endsWith('racao-generica.png')),'the tiny feed icon is gone');
 assert.equal(stack.length,0);assert.equal(context.shadowColor,'rgba(0, 0, 0, 0)','canvas state is restored for the next objects');assert.equal(context.shadowBlur,0);
 const others=drawn.filter(d=>d.path!==''&&!/vaca\//.test(d.path));assert.ok(others.length>0&&others.every(d=>!red(d)),'no other sprite inherits the halo');
 reduced=true;drawn.length=0;scene.update(farm);const still=drawn.filter(d=>/vaca\/vaca[^/]*\.png$/.test(d.path)).map(d=>d.blur);
 drawn.length=0;scene.update(farm);assert.deepEqual(drawn.filter(d=>/vaca\/vaca[^/]*\.png$/.test(d.path)).map(d=>d.blur),still,'reduced motion keeps the halo still');
});
