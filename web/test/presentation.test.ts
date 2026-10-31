import test from 'node:test';
import assert from 'node:assert/strict';
import {periodAt,tileCenter,pickTile,animalPath,tileArtRect,backgroundBounds,animalShadowRect,spriteDrawRect,measureSpriteFootprint,animalMotion,beeOffset,animalFlip,animalStatusText,animalActionLabel,animalLayer,animalInsidePen,animationFrame,cropHeight,toolShortcutOrder,gameShortcut,shortcutPanelState,tileEffect,toolbarTrayToggle,tileCommand,cropTooltipModel,untilUtcMidnight,cropGroups,soundForAction,animalProductPath,showAnimalProduct,actionRewardModel,animalClickCommand} from '../src/presentation.ts';
test('day periods use local minute boundaries',()=>{
 for(const [h,m,expected] of [[5,59,'night'],[6,0,'day'],[16,59,'day'],[17,0,'sunset'],[18,30,'sunset'],[18,31,'night']]) assert.equal(periodAt(new Date(2026,9,29,h,m)),expected);
});
test('all 30 tile centers roundtrip and clear the bottom toolbar',()=>{
 for(let r=0;r<5;r++) for(let c=0;c<6;c++) {
  const p=tileCenter(c,r); assert.deepEqual(pickTile(p.x,p.y),{column:c,row:r}); assert.ok(p.y+36<790);
 }
 assert.equal(pickTile(0,0),null);
});
test('tile art uses the desktop footprint centered on the iso grid',()=>{
 assert.deepEqual(tileArtRect(0,0),{x:491,y:350,width:188,height:100});
 assert.deepEqual(tileArtRect(5,4),{x:576,y:674,width:188,height:100});
});
test('background covers the viewport without stretching and stays top aligned',()=>{
 const bounds=backgroundBounds(1600,900,1478,1064);
 assert.deepEqual(bounds,{x:0,y:0,width:1600,height:1600*1064/1478});
});
test('animal shadow is centered on its ground anchor and scales with the animal',()=>{
 assert.deepEqual(animalShadowRect(100,500,100,.5),{x:83.75,y:500,rx:16.25,ry:2.6});
});
test('trimmed sprites fill the desktop width and use the ground support anchor',()=>{
 assert.deepEqual(spriteDrawRect(100,300,200,20,10,80,70,.1,.4),{x:20,y:142.5,width:200,height:175});
});
test('sprite footprint matches visible bounds and the bottom support band',()=>{
 const alpha=[[0,0,0,0,0],[0,1,1,1,0],[0,1,1,0,0],[0,1,1,0,0],[0,0,1,1,0],[0,0,0,0,0],[0,0,0,0,0]];
 assert.deepEqual(measureSpriteFootprint(5,7,(x,y)=>alpha[y][x]*255),{left:1,top:1,width:3,height:4,footBottom:0,footCenterX:2/3,footWidth:2/3});
});
test('pigeon stays static and peacock alternates only feather pose',()=>{
 assert.match(animalPath('pombo','ADULT',false,false,false),/pomba-adulta.png$/);
 assert.match(animalPath('pavao','ADULT',false,false,true),/pavao-aberto.png$/);
 assert.match(animalPath('pavao','ADULT',true,false,true),/pavao-dormindo.png$/);
});
test('bee flight positions match desktop phase and ellipse',()=>{
 assert.deepEqual(beeOffset(0,0),{x:0,y:12});
 assert.deepEqual(beeOffset(1,0),{x:Math.sin(1.7)*28,y:Math.cos(1.7*1.3)*12});
});
test('walking and sleeping animal poses use desktop asset names',()=>{
 assert.match(animalPath('galinha','ADULT',false,true,false),/galinha-andando.png$/);
 assert.match(animalPath('coelho','ADULT',true,false,false),/coelho-adulto-dormindo.png$/);
 assert.deepEqual(animalMotion('pombo',10),{dx:0,dy:0,walking:false,facingRight:false,open:false});
 assert.ok(Math.abs(animalMotion('cavalo',2).dx)<=27);
 assert.equal(animalFlip('cavalo',true),false);
 assert.equal(animalFlip('galinha',true),true);
});
test('animal walks pause and choose varied destinations within the desktop home radius',()=>{
 assert.deepEqual(animalMotion('galinha',0),{dx:0,dy:0,walking:false,facingRight:false,open:false});
 const samples=Array.from({length:161},(_,i)=>animalMotion('galinha',i/2));
 assert.ok(samples.some(motion=>motion.walking));
 assert.ok(samples.some(motion=>!motion.walking));
 assert.ok(samples.every(motion=>Math.hypot(motion.dx,motion.dy)<=20));
 assert.notDeepEqual(animalMotion('galinha',10),animalMotion('galinha',20));
 assert.deepEqual(animalMotion('galinha',4,true),{...animalMotion('galinha',4),dx:0,dy:0,walking:false,open:false});
});
test('animal detail status distinguishes hunger, protection, production and ready items',()=>{
 assert.equal(animalStatusText('HUNGRY','PRODUCER',5400),'Com fome');
 assert.equal(animalStatusText('PRODUCING','PROTECTOR',5400),'Protegendo');
 assert.equal(animalStatusText('PRODUCING','PRODUCER',5400),'Produzindo · 1 h 30 min');
 assert.equal(animalStatusText('PRODUCING','GROWING',5400),'Crescendo · 1 h 30 min');
 assert.equal(animalStatusText('READY','PRODUCER',0),'Pronto para recolher');
 assert.equal(animalStatusText('READY','GROWING',0),'Adulto pronto para venda');
});
test('animal actions only appear when server state allows feeding, collecting or selling',()=>{
 assert.equal(animalActionLabel('HUNGRY','PRODUCER','ADULT'),'Alimentar');
 assert.equal(animalActionLabel('READY','PRODUCER','ADULT'),'Recolher produto');
 assert.equal(animalActionLabel('READY','GROWING','ADULT'),'Vender adulto');
 assert.equal(animalActionLabel('PRODUCING','PRODUCER','ADULT'),null);
 assert.equal(animalActionLabel('READY','GROWING','YOUNG'),null);
});
test('clicking own animals runs the desktop contextual action; visits stay read-only',()=>{
 assert.deepEqual(animalClickCommand('galinha',false),{action:'TEND_ANIMAL',itemId:'galinha'});
 assert.equal(animalClickCommand('galinha',true),null);
});
test('animal layers follow the desktop fence and terrain draw order',()=>{
 assert.equal(animalLayer('vaca'),'back');
 assert.equal(animalLayer('pavao'),'back');
 assert.equal(animalLayer('cavalo'),'front');
 assert.equal(animalLayer('pombo'),'front');
 assert.equal(animalInsidePen('galinha'),true);
 assert.equal(animalInsidePen('pavao'),false);
});
test('plant effect frames and crop height match desktop timing and scale',()=>{
 assert.equal(animationFrame(.159,4,.16),0);
 assert.equal(animationFrame(.16,4,.16),1);
 assert.equal(animationFrame(.64,4,.16),0);
 assert.equal(cropHeight(1),48);
 assert.equal(cropHeight(5),112);
});
test('number shortcuts match desktop toolbar order',()=>{
 assert.deepEqual(toolShortcutOrder,['CURSOR','PLOW','PLANT','WATER','WEED','PESTICIDE','HARVEST','FERTILIZE']);
});
test('keyboard shortcuts cover desktop panels and settings',()=>{
 assert.deepEqual(gameShortcut('1'),{action:'tool',tool:'CURSOR'});
 assert.deepEqual(gameShortcut('8'),{action:'tool',tool:'FERTILIZE'});
 assert.deepEqual(gameShortcut('q'),{action:'missions'});
 assert.deepEqual(gameShortcut('M'),{action:'music'});
 assert.deepEqual(gameShortcut('n'),{action:'effects'});
 assert.deepEqual(gameShortcut('b'),{action:'shop'});
 assert.deepEqual(gameShortcut('v'),{action:'deposit'});
 assert.equal(gameShortcut('1',true),undefined);
});
test('panel shortcuts open another panel and close the same panel on repeat',()=>{
 assert.equal(shortcutPanelState(false,'','Loja'),'open');
 assert.equal(shortcutPanelState(true,'Depósito','Loja'),'open');
 assert.equal(shortcutPanelState(true,'Loja','Loja'),'close');
});
test('tile transitions identify successful actions for short visual effects',()=>{
 const tile={state:'PLOWED',dry:false,cropId:'nabo',weeds:false,insects:0,fertilized:false};
 assert.equal(tileEffect({...tile,state:'GRASS'},tile),'plow');
 assert.equal(tileEffect({...tile,cropId:undefined},tile),'plant');
 assert.equal(tileEffect({...tile,dry:true},tile),'water');
 assert.equal(tileEffect({...tile,state:'READY'}, {...tile,cropId:undefined}),'harvest');
 assert.equal(tileEffect({...tile,weeds:true},tile),'weed');
 assert.equal(tileEffect({...tile,insects:2},tile),'pest');
 assert.equal(tileEffect({...tile,fertilized:false},{...tile,fertilized:true}),'fertilize');
 assert.equal(tileEffect(tile,tile),null);
});
test('seed and fertilizer tools toggle their desktop-style trays',()=>{
 assert.equal(toolbarTrayToggle('CURSOR','PLANT',false),true);
 assert.equal(toolbarTrayToggle('PLANT','PLANT',true),false);
 assert.equal(toolbarTrayToggle('PLANT','PLANT',false),true);
 assert.equal(toolbarTrayToggle('PLANT','WATER',true),false);
 assert.equal(toolbarTrayToggle('CURSOR','LOVE',false),true);
});
test('each toolbar tool sends the selected seed, fertilizer, and visit revision',()=>{
 const tile={column:2,row:1,harvestRevision:'17'} as import('../src/types.ts').Tile;
 assert.deepEqual(tileCommand('PLANT',tile,'nabo','super'),{action:'PLANT',column:2,row:1,itemId:'nabo'});
 assert.deepEqual(tileCommand('FERTILIZE',tile,'nabo','super'),{action:'FERTILIZE',column:2,row:1,itemId:'super'});
 assert.deepEqual(tileCommand('WATER',tile,'nabo','super'),{action:'WATER',column:2,row:1});
 assert.deepEqual(tileCommand('STEAL',tile,'nabo','amor',true),{action:'STEAL',column:2,row:1,harvestRevision:'17'});
 assert.deepEqual(tileCommand('LOVE',tile,'nabo','amor',true),{action:'LOVE',column:2,row:1,harvestRevision:'17',itemId:'amor'});
});
test('crop tooltip reports desktop-style season, growth, and field conditions',()=>{
 const tile={state:'GROWING',season:2,seasons:4,nominalRemainingSeconds:3600,remainingSeconds:900,dry:true,paused:true,weeds:true,insects:2,protected:true} as import('../src/types.ts').Tile;
 assert.deepEqual(cropTooltipModel(tile,'Nabo',false,false),{
  title:'Nabo',seasonText:'2 / 4 · 1 unidade',seasonProgress:.5,
  growthText:'Pausado por erva e insetos',growthProgress:.75,chips:['PRAGA','2 VERMES','SECA','PROTEGIDA']
 });
});
test('daily mission reset countdown follows UTC midnight',()=>{
 assert.equal(untilUtcMidnight('2026-10-30T23:30:00Z'),'30 min');
 assert.equal(untilUtcMidnight('2026-10-30T12:00:00Z'),'12 h');
});
test('seed catalog separates common and VIP crops in desktop order',()=>{
 const groups=cropGroups([{id:'a',vipOnly:true},{id:'b',vipOnly:false},{id:'c',vipOnly:true}] as import('../src/types.ts').Crop[]);
 assert.deepEqual(groups,{common:[{id:'b',vipOnly:false}],vip:[{id:'a',vipOnly:true},{id:'c',vipOnly:true}]});
});
test('web action sounds map to desktop cues',()=>{
 assert.equal(soundForAction('PLOW'),'click_button');
 assert.equal(soundForAction('WATER'),'water');
 assert.equal(soundForAction('FERTILIZE'),'plant');
 assert.equal(soundForAction('TEND_ANIMAL',true),'harvest');
 assert.equal(soundForAction('TEND_ANIMAL',false),'plant');
 assert.equal(soundForAction('STEAL'),'harvest');
 assert.equal(soundForAction('BUY_PLOT'),'buy');
 assert.equal(soundForAction('SELL_ITEM'),'sell');
 assert.equal(soundForAction('UNKNOWN'),undefined);
});
test('ready producer animals show the desktop product asset',()=>{
 assert.equal(showAnimalProduct('READY','PRODUCER','galinha'),true);
 assert.equal(showAnimalProduct('PRODUCING','PRODUCER','galinha'),false);
 assert.equal(showAnimalProduct('READY','GROWING','coelho'),false);
 assert.equal(showAnimalProduct('READY','PRODUCER','abelha'),false);
 assert.equal(animalProductPath('galinha'),'classic/animals/ninho-geral/ninho-com-ovos.png');
 assert.equal(animalProductPath('ovelha'),'classic/animals/ovelha/la-ovelha.png');
 assert.equal(animalProductPath('vaca'),'classic/animals/vaca/balde-vaca.png');
});
test('action reward toast reports wallet, XP, and level changes',()=>{
 const before={coins:'100',greenCoins:'4',currentXp:'10',level:1};
 assert.deepEqual(actionRewardModel(before,{coins:'120',greenCoins:'6',currentXp:'15',level:1}),{coins:20,greens:2,xp:5,level:1,leveledUp:false});
 assert.deepEqual(actionRewardModel(before,{coins:'100',greenCoins:'4',currentXp:'0',level:2}),{coins:0,greens:0,xp:0,level:2,leveledUp:true});
});
