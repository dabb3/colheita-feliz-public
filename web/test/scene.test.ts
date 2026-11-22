import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {createHash} from 'node:crypto';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as presentation from '../src/presentation.ts';

test('launch icons and original cleaning sprays retain their approved bytes',()=>{
 const icons={
  'effects/erva-nova.png':'74e984e5ae559d9d97e4be778a9e50445f5321c4a6c2255f72a702f90f7150bc',
  'effects/minhoca-nova.png':'d53850ad0720a0d55bc8d9fee74cbbacfbb9250397c70940409d9b8c10f8942b',
  'icons/hud/herbicida-ui.png':'798872d29c791e6458628dc0b7be7a653264b3a52df7f9b3a50ad33f93e6b61b',
  'icons/hud/pesticida-ui.png':'63e23413ad00bb13ab59095fee52dfdd4c3990244ea53fdf5912c11fd80b6836',
 };
 for(const [path,hash] of Object.entries(icons))assert.equal(createHash('sha256').update(readFileSync(new URL('../../assets/classic/'+path,import.meta.url))).digest('hex'),hash,path);
});

test('pests stay small, separated and rooted through growth, arrival, removal and reduced motion',()=>{
 const pending=[],drawn=[],stack=[];let now=1000,reduced=false,pose={x:0,y:0,angle:0,scale:1};
 const context={clearRect(){},fillRect(){},beginPath(){},moveTo(){},lineTo(){},closePath(){},stroke(){},fill(){},ellipse(){},fillText(){},
  save(){stack.push({...pose});},restore(){pose=stack.pop();},translate(x,y){pose.x+=x;pose.y+=y;},rotate(a){pose.angle+=a;},scale(x){pose.scale*=x;},
  drawImage(image,...args){drawn.push({path:image.src,args,pose:{...pose}});},
  createRadialGradient(){return{addColorStop(){}};},getImageData(x,y,w,h){return{data:new Uint8ClampedArray(w*h*4).fill(255)};}};
 const environment={...presentation,periodAt:()=>'day',performance:{now:()=>now},AbortController,
  window:{requestAnimationFrame:()=>1,cancelAnimationFrame(){},matchMedia:()=>({matches:reduced})},document:{createElement:()=>({getContext:()=>context})},
  Image:class{width=100;height=60;src;constructor(){pending.push(this);}}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',environment);
 const scene=new environment.Scene({getContext:()=>context,addEventListener(){}},{crops:[{id:'nabo',spriteStem:'nabo'}],animals:[]},()=>{},()=>{},()=>{});
 const tile={column:0,row:0,unlocked:true,state:'GROWING',cropId:'nabo',stage:1,weeds:true,insects:3};
 const update=t=>{drawn.length=0;scene.update({owner:"pest-review",own:true,tiles:[t],animals:[]});while(pending.length)pending.shift().onload();drawn.length=0;scene.update({owner:"pest-review",own:true,tiles:[t],animals:[]});};
 update(tile);
 scene.sprites.get('classic/effects/minhoca-nova.png:true:').footCenterX=0.938596;
 for(const stage of [1,3,5]) for(const count of [1,2,3]) for(const time of [2000,2750,3500]) {
  now=time;update({...tile,stage,insects:count});
  const worms=drawn.filter(d=>d.path?.endsWith('minhoca-nova.png')),weed=drawn.find(d=>d.path?.endsWith('erva-nova.png'));
  assert.equal(worms.length,count);assert.equal(weed.args.at(-2),27);
  assert.deepEqual([weed.pose.x,weed.pose.y],[550,397],'weed rotates around its fixed soil base');
  assert.equal(weed.args.at(-3)+weed.args.at(-1),0,'trimmed weed bottom is the local rotation origin');
  for(const [i,worm] of worms.entries()) {
   assert.equal(worm.args.at(-2),23);
   assert.equal(worm.args.at(-4),-11.5,"asymmetric source is centered by its visible bounds");
   assert.ok(worm.pose.y+worm.args.at(-3)>weed.pose.y+2,"worm silhouette clears the weed base");
   const dx=worm.pose.x-585,dy=worm.pose.y-400;
   assert.ok((Math.abs(dx)+12)/85+(dy+2)/36<1,'worm fits inside the tile diamond');
   if(i)assert.ok(worm.pose.x-worms[i-1].pose.x>=23,'worm silhouettes cannot overlap');
  }
 }
 now=5000;update({...tile,weeds:false,insects:0});now=6000;update(tile);
 assert.ok(scene.pestEntrance(0,0,'spread-weed',false)<1);
 now=6150;const middle=scene.pestEntrance(0,0,'spread-weed',false);assert.ok(middle>.28&&middle<1);
 now=6300;assert.equal(scene.pestEntrance(0,0,'spread-weed',false),1);
 reduced=true;now=7000;update({...tile,weeds:false,insects:0});update(tile);
 for(const d of drawn.filter(d=>/erva-nova|minhoca-nova/.test(d.path)))assert.deepEqual([d.pose.angle,d.pose.scale],[0,1]);
 assert.equal(scene.pestEntrance(0,0,'spread-pest',true),1);
 update({...tile,weeds:false,insects:0});assert.ok(!drawn.some(d=>/erva-nova|minhoca-nova/.test(d.path)));
 assert.ok(scene.tileEffects.some(e=>e.kind==='weed'),'existing cleaning effect remains');
 scene.close();
});

test('scene draws stage-sized plants, real pests, ready props and trimmed hive without economic calls',()=>{
 const drawn=[],texts=[],pending=[],images=[],events=new Map(),selected=[],hovered=[];
 let tick,now=0,timers=0,ellipses=0,period='day',christmas=false,canInspect=true;const periods=[],tilesSelected=[];const cancelled=[],windowEvents=new Map();
 const context={clearRect(){},fillRect(){},save(){},restore(){},translate(){},scale(){},rotate(){},beginPath(){},moveTo(){},lineTo(){},closePath(){},stroke(){},fill(){},
  drawImage(image,...args){drawn.push({path:image.src,args});},fillText(text){texts.push(text);},ellipse(){ellipses++;},
  createRadialGradient(){return{addColorStop(){}};},getImageData(x,y,w,h){const data=new Uint8ClampedArray(w*h*4);for(let row=0;row<h;row++)for(let col=0;col<w;col++)if(row<h*.88||col<w*.25)data[(row*w+col)*4+3]=255;return{data};}};
 const canvas={width:1600,height:900,getContext:()=>context,getBoundingClientRect:()=>({left:0,top:0,width:1600,height:900}),addEventListener(name,handler){events.set(name,handler);}};
 const environment={...presentation,periodAt:()=>period,christmasActive:()=>christmas,performance:{now:()=>now},window:{requestAnimationFrame:fn=>{tick=fn;return ++timers;},cancelAnimationFrame:id=>cancelled.push(id),addEventListener(name,handler){windowEvents.set(name,handler);}},AbortController,document:{hidden:false,createElement:()=>({getContext:()=>context})},
  Image:class{width=100;height=200;onload;onerror;src;constructor(){pending.push(this);images.push(this);}}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',environment);
 const scene=new environment.Scene(canvas,{crops:[{id:'nabo',spriteStem:'nabo'}],animals:[{id:'vaca',kind:'PRODUCER'},{id:'abelha',kind:'PROTECTOR'},{id:'cachorro-robo',kind:'PROTECTOR'}]},(tile,pointer)=>tilesSelected.push({tile,pointer}),(id,pointerType)=>selected.push({id,pointerType}),tile=>hovered.push(tile),value=>periods.push(value),()=>{},()=>{},()=>canInspect);
 const tile={column:0,row:0,unlocked:true,state:'READY',cropId:'nabo',stage:3,weeds:true,insects:2};
 const farm={owner:'alice',own:true,nextPlot:{column:1,row:0},tiles:[tile,{column:0,row:1,unlocked:true,state:'GRASS'},{column:1,row:0,unlocked:false,state:'GRASS',requiredLevel:2,price:50,currency:'GOLD',purchasable:true}],animals:[{id:'vaca',stage:'ADULT',status:'READY'},{id:'abelha',stage:'ADULT',status:'PRODUCING'},{id:'cachorro-robo',stage:'ADULT',status:'PRODUCING'}]};
 scene.update(farm);
 while(pending.length){const image=pending.shift();if(image.src.includes('atlas')){image.width=400;image.height=100;}if(image.src.endsWith('cachorro-andando.png'))image.onerror();else image.onload();}
 drawn.length=0;scene.update(farm);
 assert.ok(drawn.some(d=>d.path?.endsWith('cachorro.png')&&d.args.at(-2)===110),'idle dog remains visible at normal width when the walking reference fails');
 assert.equal(images.filter(image=>image.src.endsWith('cachorro-andando.png')).length,1,'failed reference is not requested again');
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('nabo_3.png')&&d.args.at(-1)===80&&d.args.at(-2)===40));
 const lastTerrain=drawn.findLastIndex(d=>typeof d.path==='string'&&/terrain\/(grama-default|terra-molhada)\.png$/.test(d.path));
 const firstPlant=drawn.findIndex(d=>typeof d.path==='string'&&d.path.endsWith('nabo_3.png'));
 assert.ok(firstPlant>lastTerrain,'all terrain must be drawn before any plant');
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('erva-nova.png')&&d.args.at(-2)===27));
 assert.ok(drawn.filter(d=>d.path?.endsWith('minhoca-nova.png')).every(d=>d.args.at(-2)===23));
 assert.ok(drawn.filter(d=>typeof d.path==='string'&&d.path.endsWith('minhoca-nova.png')).length>=2,'two worms rendered individually');
 assert.ok(drawn.some(d=>typeof d.path==='string'&&d.path.endsWith('balde-vaca.png')));
 assert.ok(images.some(image=>image.src.endsWith('colmeia-abelha-atlas.png')));
 assert.ok(drawn.some(d=>d.args.at(-2)===156&&d.args.at(-1)===78));
 events.get('click')({clientX:1515,clientY:400});
 assert.deepEqual(selected,[{id:'vaca',pointerType:'mouse'}]);
 // iOS Safari can emit mouse/zero-detail click after a real touch pointerdown.
 events.get('pointerdown')({clientX:1515,clientY:400,pointerType:'touch'});
 events.get('click')({clientX:1515,clientY:400,pointerType:'mouse',detail:0});
 assert.deepEqual(selected.at(-1),{id:'vaca',pointerType:'touch'},'iOS tap keeps its touch origin');
 events.get('click')({clientX:1515,clientY:400,pointerType:'mouse',detail:0});
 assert.equal(selected.at(-1).pointerType,'mouse','the next click cannot reuse a stale touch');
 events.get('pointerdown')({clientX:1515,clientY:400,pointerType:'touch'});
 events.get('pointercancel')({});
 events.get('click')({clientX:1515,clientY:400,pointerType:'mouse'});
 assert.equal(selected.at(-1).pointerType,'mouse','cancelled touches cannot affect a later click');
 events.get('pointerdown')({clientX:700,clientY:400,pointerType:'touch'});
 events.get('click')({clientX:1515,clientY:400,pointerType:'mouse'});
 assert.equal(selected.at(-1).pointerType,'mouse','a touch elsewhere cannot hijack a mouse click');
 now=2000;
 events.get('pointerdown')({clientX:1515,clientY:400,pointerType:'touch'});
 now=4000;
 events.get('click')({clientX:1515,clientY:400,pointerType:'mouse'});
 assert.equal(selected.at(-1).pointerType,'mouse','a delayed unrelated click cannot inherit a touch');
 events.get('click')({clientX:1515,clientY:400,pointerType:'touch',detail:0});
 assert.equal(selected.at(-1).pointerType,'touch','native Android touch remains a touch even with detail zero');
 events.get('pointerdown')({clientX:1515,clientY:400,pointerType:'mouse'});
 events.get('click')({clientX:1515,clientY:400,pointerType:'mouse',detail:1});
 assert.equal(selected.at(-1).pointerType,'mouse','desktop mouse remains unchanged');
 const selectedBeforeDrag=tilesSelected.length,drag={pointerId:9,pointerType:'mouse',clientX:670,clientY:436};
 events.get('pointerdown')(drag);events.get('pointermove')({...drag,clientX:700,clientY:450});
 windowEvents.get('pointerup')({...drag,clientX:700,clientY:450});
 let prevented=false,stopped=false;
 events.get('click')({clientX:700,clientY:450,pointerType:'mouse',detail:1,preventDefault(){prevented=true;},stopImmediatePropagation(){stopped=true;}});
 assert.ok(prevented&&stopped,'a drag release cannot select a locked plot');assert.equal(tilesSelected.length,selectedBeforeDrag);
 events.get('pointerdown')({pointerId:10,pointerType:'mouse',clientX:670,clientY:436});
 events.get('click')({clientX:670,clientY:436,pointerType:'mouse',detail:1});
 assert.equal(tilesSelected.at(-1).tile.unlocked,false,'a later real click still selects the locked plot');
 events.get('pointerdown')({clientX:585,clientY:400,pointerType:'touch'});
 events.get('click')({clientX:585,clientY:400,pointerType:'mouse',detail:0});
 assert.equal(tilesSelected.at(-1).pointer,'touch','iOS synthesised click also preserves tile tap origin');
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
 assert.deepEqual(drawn.find(d=>d.path?.endsWith('/background-night.png')).args,[0,0,1600,900],'the whole background fills the world, without the old bottom crop');
 scene.update({...farm,activeBackgroundId:'savana'});while(pending.length)pending.shift().onload();drawn.length=0;scene.update({...farm,activeBackgroundId:'savana'});
 assert.ok(drawn.some(d=>d.path?.endsWith('/background-savana-noite.png')),'the farm on screen draws its own pack');
 assert.ok(!drawn.some(d=>d.path?.endsWith('/background-night.png')),'a loaded pack replaces the classic art');
 drawn.length=0;scene.update({...farm,activeBackgroundId:'background-natal'});
 assert.ok(drawn.some(d=>d.path?.endsWith('/background-night.png')),'an unknown pack falls back to the classic one');
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


test('background transitions retain the last image while both replacements load or fail',()=>{
 const pending=[],drawn=[];let period='day';
 const context={clearRect(){drawn.length=0;},fillRect(){},save(){},restore(){},translate(){},rotate(){},drawImage(image){drawn.push(image.src);}};
 const environment={...presentation,periodAt:()=>period,performance:{now:()=>0},AbortController,console:{warn(){}},
  window:{requestAnimationFrame:()=>1,cancelAnimationFrame(){}},document:{hidden:false},
  Image:class{width=1600;height=900;src;constructor(){pending.push(this);}}};
 const source=readFileSync(new URL('../src/scene.ts',import.meta.url),'utf8').replace(/^import\b[\s\S]*?;\s*/gm,'').replace('export class Scene','class Scene');
 runInNewContext(stripTypeScriptTypes(source)+'\nglobalThis.Scene=Scene;',environment);
 const scene=new environment.Scene({width:1600,getContext:()=>context,addEventListener(){}},{animals:[],crops:[]},()=>{},()=>{},()=>{});
 const farm={owner:'alice',activeBackgroundId:'inverno',tiles:[],animals:[]};scene.update(farm);
 const day=pending.find(image=>image.src.endsWith('/backgound-inverno.png'));day.onload();
 assert.ok(drawn.includes(day.src));
 period='sunset';scene.update({...farm,activeBackgroundId:'outono'});
 assert.ok(drawn.includes(day.src),'keep the last loaded art instead of the green fill');
 const requested=pending.find(image=>image.src.endsWith('/background-outono-tarde.png'));
 requested.onerror();assert.ok(drawn.includes(day.src),'a failed replacement cannot erase the last background');
 const fallback=pending.find(image=>image.src.endsWith('/background-sunset.png'));fallback.onload();
 assert.ok(drawn.includes(fallback.src));assert.ok(!drawn.includes(day.src));
 period='night';scene.update({...farm,activeBackgroundId:'savana'});
 const stale=pending.find(image=>image.src.endsWith('/background-savana-noite.png'));
 scene.update({...farm,activeBackgroundId:'inverno'});
 const current=pending.find(image=>image.src.endsWith('/background-inverno-noite.png'));current.onload();stale.onload();
 assert.ok(drawn.includes(current.src));assert.ok(!drawn.includes(stale.src),'late completion must not replace the selected pack');scene.close();
});
