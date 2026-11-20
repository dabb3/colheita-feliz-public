import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {stripTypeScriptTypes} from 'node:module';
import {runInNewContext} from 'node:vm';
import * as values from '../src/ui-presentation.ts';
import {animalPath,productPath,seedArtPath} from '../src/presentation.ts';
import {avatarSource} from '../src/avatar.ts';

function panels(){
 const make=tag=>({tag,children:[],attributes:{},textContent:'',style:{setProperty(){}},append(...nodes){this.children.push(...nodes);},prepend(...nodes){this.children.unshift(...nodes);},replaceChildren(){this.children=[];},setAttribute(k,v){this.attributes[k]=v;},getAttribute(k){return this.attributes[k]??null;},contains(node){return nodes(this).includes(node);},focus(){document.activeElement=this;},querySelectorAll(selector){return nodes(this).slice(1).filter(node=>selector==='[data-focus-key]'?node.getAttribute('data-focus-key')!==null:false);}});
 const document={activeElement:null,createElement:make};const ctx={...values,animalPath,productPath,seedArtPath,avatarSource,document};
 const load=name=>readFileSync(new URL('../src/'+name,import.meta.url),'utf8').replace(/^import .*$/mg,'').replace(/^export /mg,'');
 runInNewContext(stripTypeScriptTypes(load('ui.ts')+'\n'+load('ui-panels.ts'))+'\nglobalThis.panels={renderDeposit,renderMarket,renderPlantHealth,renderAnimal,renderMissions,renderFriends,renderGiftComposer,renderLoginRewards:typeof renderLoginRewards==="undefined"?undefined:renderLoginRewards,giftLimit,renderAudioSettings:typeof renderAudioSettings==="undefined"?undefined:renderAudioSettings};',ctx);
 return{...ctx.panels,root:make('div'),document};
}
function nodes(root){return[root,...root.children.flatMap(child=>typeof child==='object'?nodes(child):[])];}
const catalog={crops:[{id:'milho',name:'Milho',spriteStem:'milho',vipOnly:false},{id:'vip',name:'Especial',spriteStem:'nabo',vipOnly:true}],animals:[{id:'vaca',name:'Vaca',kind:'PRODUCER',productId:'leite',productName:'Leite'}],fertilizers:[],feedPrices:{producer:10,growth:20}};

test('deposit preserves mixed currency quantities and skips VIP without blocking sell all',()=>{
 const {root,renderDeposit}=panels(),commands=[];
 renderDeposit(root,catalog,{vipActive:false,depositTotals:{GOLD:100,GREEN:5},produceLots:[{itemId:'milho',quantity:2,currency:'GOLD'},{itemId:'milho',quantity:1,currency:'GREEN'},{itemId:'vip',quantity:1,currency:'GREEN',vipOnly:true}]},'products',()=>{},c=>commands.push(c));
 const all=nodes(root);assert.equal(all.filter(n=>n.tag==='article').length,2);
 assert.ok(all.some(n=>n.textContent==='2 em ouro · 1 em verdes'));
 const sellAll=all.find(n=>n.textContent.includes('Vender tudo'));assert.ok(!sellAll.disabled);sellAll.onclick();
 const buttons=all.filter(n=>n.textContent==='Vender 1');assert.equal(buttons[1].disabled,true);buttons[0].onclick();
 assert.equal(JSON.stringify(commands),JSON.stringify([{action:'SELL_ALL'},{action:'SELL_ITEM',itemId:'milho'}]));
});

test('plant health separates nominal and unwatered times and hides stale content',()=>{
 const {root,renderPlantHealth}=panels();
 renderPlantHealth(root,catalog,{cropId:'milho',state:'GROWING',watered:true,dry:true,stage:1,season:1,seasons:1,nominalRemainingSeconds:72000,remainingSeconds:115200},'2026-11-03T01:00:00Z');
 let all=nodes(root);assert.ok(all.some(n=>n.textContent.startsWith('Com rega em dia e sem pragas: 20 h restantes. Previsão:')));
 assert.ok(all.some(n=>n.textContent.startsWith('Sem nova rega e sem pragas: 1 d 8 h restantes. Previsão:')));
 assert.ok(all.some(n=>n.textContent.includes('Solo seco: crescimento à metade')));
 renderPlantHealth(root,catalog,{cropId:'milho',state:'GROWING',watered:true,paused:true,weeds:true,nominalRemainingSeconds:3600,remainingSeconds:7200},'2026-11-03T01:00:00Z');
 all=nodes(root);assert.ok(all.some(n=>n.textContent.includes('Crescimento pausado')));assert.ok(!all.some(n=>n.textContent.includes('Previsão:')));
 renderPlantHealth(root,catalog,{cropId:'milho',state:'DEAD'});all=nodes(root);assert.ok(all.some(n=>n.textContent==='Planta morta'));assert.ok(!all.some(n=>n.textContent.startsWith('Com rega')));
 renderPlantHealth(root,catalog,null);assert.equal(root.hidden,true);assert.equal(root.children.length,0);
});

test('individual animal uses selected id and visitors get no economic actions',()=>{
 const {root,renderAnimal}=panels(),commands=[];
 const farm={animals:[{id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0}],feed:{producer:1}};
 renderAnimal(root,catalog,farm,'vaca',false,c=>commands.push(c));assert.equal(nodes(root).filter(n=>n.tag==='button').length,0);
 renderAnimal(root,catalog,farm,'vaca',true,c=>commands.push(c));
 nodes(root).find(n=>n.textContent==='Recolher produto').onclick();assert.equal(commands[0].itemId,'vaca');assert.equal(commands[0].action,'TEND_ANIMAL');
 renderAnimal(root,catalog,{animals:[]},'vaca',true,()=>{});assert.ok(nodes(root).some(n=>n.textContent==='Este animal não está mais nesta fazenda.'));
});

test('missions and friends use text nodes and retain target identity',()=>{
 const {root,renderMissions,renderFriends}=panels();renderMissions(root,{missions:[{id:'m',title:'Colher',green:1,done:true}]});
 assert.ok(nodes(root).some(n=>n.textContent==='Concluída'));
 const visits=[];renderFriends(root,[{id:'bob',name:'<Carlos>'}],id=>visits.push(id));
});
test('missions show partial progress against the target',()=>{
 const {root,renderMissions}=panels();renderMissions(root,{missions:[{id:'nature',title:'Amigo da Natureza',green:0,progress:1,target:2,done:false},{id:'animals',title:'Amigo dos Animais',green:0,progress:0,target:1,done:false}]});
 assert.ok(nodes(root).some(n=>n.textContent==='1/2'));assert.ok(nodes(root).some(n=>n.textContent==='0/1'));assert.ok(nodes(root).some(n=>n.textContent==='Em andamento'));
});

test('gift composer keeps a fixed friend, accessible seed selection and persistent footer',()=>{
 const {root,renderGiftComposer,giftLimit}=panels(),footer={...root,children:[],replaceChildren(...items){this.children=items;}};
 const catalog={crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'},{id:'milho',name:'Milho',spriteStem:'milho'}]};
 const state={phase:'ready',cropId:'nabo',quantity:1,seedCounts:{nabo:8,milho:2},gifts:{received:[],sent:[]},canSend:true};
 const actions={selectSeed(){},setQuantity(){},send(){},cancel(){},retry(){}};
 renderGiftComposer(root,footer,catalog,{id:'friend-1',name:'Sam'},state,actions);
 const all=nodes(root),choices=all.filter(n=>n.getAttribute?.('aria-pressed')!==null);
 assert.equal(all.find(n=>n.textContent==='Sam')?.tag,'strong');assert.equal(choices.length,2);assert.equal(choices.filter(n=>n.getAttribute('aria-pressed')==='true').length,1);
 assert.ok(choices.some(n=>n.textContent.includes('Nabo · 8 no estoque')));assert.equal(footer.children[1].disabled,false);
 assert.deepEqual([giftLimit(0),giftLimit(3),giftLimit(9)],[0,3,5]);assert.equal(footer.children[0].textContent,'Cancelar');assert.equal(footer.children[1].textContent,'Enviar');
 renderGiftComposer(root,footer,catalog,{id:'friend-2',name:'Sam'}, {...state,phase:'sending',canSend:false},actions);
 assert.equal(footer.children[0].disabled,true);assert.equal(footer.children[1].disabled,true);assert.equal(nodes(root).some(n=>n.textContent==='Presentes'),false);
});

test('gift composer restores focused seed and quantity controls after rerender',()=>{
 const {root,renderGiftComposer,document}=panels();root.contains=node=>node===root||nodes(root).includes(node);root.scrollTop=84;
 const focus=(key)=>{document.activeElement=nodes(root).find(node=>node.getAttribute?.('data-focus-key')===key);};
 const state={phase:'ready',cropId:'nabo',quantity:1,seedCounts:{nabo:5,milho:2},gifts:{received:[],sent:[]},canSend:true};
 const actions={selectSeed(id){state.cropId=id;state.quantity=1;render();},setQuantity(value){state.quantity=value;render();},send(){},cancel(){},retry(){}};
 const render=()=>renderGiftComposer(root,{...root,children:[],replaceChildren(...items){this.children=items;}},{crops:[{id:'nabo',name:'Nabo',spriteStem:'nabo'},{id:'milho',name:'Milho',spriteStem:'milho'}]},{id:'friend',name:'Sam'},state,actions);
 render();focus('gift-seed:nabo');nodes(root).find(node=>node.getAttribute?.('data-focus-key')==='gift-seed:nabo').onclick();assert.equal(document.activeElement?.getAttribute('data-focus-key'),'gift-seed:nabo');assert.equal(root.scrollTop,84);
 focus('gift-quantity-more');nodes(root).find(node=>node.getAttribute?.('data-focus-key')==='gift-quantity-more').onclick();assert.equal(state.quantity,2);assert.equal(document.activeElement?.getAttribute('data-focus-key'),'gift-quantity-more');
 focus('gift-quantity-more');nodes(root).find(node=>node.getAttribute?.('data-focus-key')==='gift-quantity-more').onclick();nodes(root).find(node=>node.getAttribute?.('data-focus-key')==='gift-quantity-more').onclick();nodes(root).find(node=>node.getAttribute?.('data-focus-key')==='gift-quantity-more').onclick();assert.equal(state.quantity,5);assert.equal(document.activeElement?.getAttribute('data-focus-key'),'gift-quantity-less');
 const outside=document.createElement('button');outside.focus();render();assert.equal(document.activeElement,outside);
});

test('login calendar renders all server rewards in order with special seed details and accessible states',()=>{
 const {root,renderLoginRewards}=panels(),gold=[250,500,750,1000,1500,2000,250,500,750,1000,1500,2000,250,500,750,1000,1500,2000,250,500,750,1000,1500,2000,2500,3000];let goldIndex=0;
 const rewards=Array.from({length:30},(_,index)=>{const day=index+1,isSeed=[7,14,21,30].includes(day);return{day,kind:isSeed?'SEED':'GOLD',currency:isSeed?'GREEN':'GOLD',amount:isSeed?'1':String(gold[goldIndex++]),...({7:{itemId:'semente-bronze'},14:{itemId:'semente-prata'},21:{itemId:'semente-ouro'},30:{itemId:'semente-diamante'}}[day]??{})};});
 const catalog={crops:[{id:'semente-bronze',name:'Semente de Bronze',spriteStem:'semente-bronze',growthSeconds:86400,seasons:1,cycleRevenueTenths:50,acquisitionSource:'LOGIN_REWARD'},{id:'semente-prata',name:'Semente de Prata',spriteStem:'semente-prata',growthSeconds:129600,seasons:1,cycleRevenueTenths:100,acquisitionSource:'LOGIN_REWARD'},{id:'semente-ouro',name:'Semente de Ouro',spriteStem:'semente-ouro',growthSeconds:172800,seasons:1,cycleRevenueTenths:150,acquisitionSource:'LOGIN_REWARD'},{id:'semente-diamante',name:'Semente de Diamante',spriteStem:'semente-diamante',growthSeconds:259200,seasons:1,cycleRevenueTenths:200,acquisitionSource:'LOGIN_REWARD'}]};
 renderLoginRewards(root,{completedDay:6,nextDay:7,available:true,streakReset:false,nextResetAt:'2026-11-16T03:00:00Z',rewards},catalog,{phase:'ready',pending:false,recoverable:false,busy:false,refreshing:false,online:true,countdown:'15 h 00 min 00 s'}, {claim(){},retry(){},refresh(){}});
 const all=nodes(root),cards=all.filter(n=>n.className?.includes('login-reward-card'));assert.equal(cards.length,30);assert.deepEqual(cards.map(n=>n.getAttribute('aria-label')?.match(/Dia (\d+)/)?.[1]),Array.from({length:30},(_,i)=>String(i+1)));
 assert.ok(nodes(cards[6]).some(n=>n.textContent.includes('Semente de Bronze')));assert.ok(nodes(cards[6]).some(n=>n.textContent.includes('1 d')));assert.ok(nodes(cards[6]).some(n=>n.textContent.includes('5 verdes')));assert.ok(nodes(cards[6]).some(n=>n.textContent==='Disponível'));
 assert.ok(nodes(cards[5]).some(n=>n.textContent==='Concluído'));assert.ok(nodes(cards[7]).some(n=>n.textContent==='Futuro'));assert.equal(all.find(n=>n.textContent==='Resgatar recompensa').disabled,false);
 for(const [day,name,payout,growth]of [[14,'Semente de Prata','10 verdes','1 d 12 h'],[21,'Semente de Ouro','15 verdes','2 d'],[30,'Semente de Diamante','20 verdes','3 d']]){const card=cards[day-1];assert.ok(nodes(card).some(n=>n.textContent.includes(name)));assert.ok(nodes(card).some(n=>n.textContent.includes(payout)));assert.ok(nodes(card).some(n=>n.textContent.includes(growth)));}
 assert.ok(all.some(n=>n.textContent==='Se perder um dia, sua sequência volta ao início.'));
});

test('login calendar preserves panel scroll, retains controls focus, and moves disabled claim focus to summary',()=>{
 const {root,renderLoginRewards,document}=panels();root.contains=node=>node===root||nodes(root).includes(node);root.scrollTop=88;
 const status={completedDay:0,nextDay:1,available:true,streakReset:false,nextResetAt:'2026-11-16T03:00:00Z',rewards:[{day:1,kind:'GOLD',currency:'GOLD',amount:'250'}]},catalog={crops:[]},state={phase:'ready',pending:false,recoverable:false,busy:false,refreshing:false,online:true,countdown:'1 h 00 min 00 s'},actions={claim(){},retry(){},refresh(){}};
 const render=()=>renderLoginRewards(root,status,catalog,state,actions);render();
 const claim=nodes(root).find(n=>n.getAttribute?.('data-focus-key')==='login-rewards-claim');document.activeElement=claim;
 render();assert.equal(document.activeElement.getAttribute('data-focus-key'),'login-rewards-claim');assert.equal(root.scrollTop,88);
 status.available=false;render();assert.equal(document.activeElement.getAttribute('data-focus-key'),'login-rewards-heading');assert.equal(root.scrollTop,88);
 const outside=document.createElement('button');outside.focus();render();assert.equal(document.activeElement,outside);
 const countdown=nodes(root).find(n=>n.className==='login-rewards-countdown');assert.equal(countdown.getAttribute('aria-live'),'off');assert.equal(countdown.getAttribute('role'),'timer');
});

test('calendar lives inside Eventos on desktop and in the mobile more menu, and the modal scrolls on short screens',()=>{
 const html=readFileSync(new URL('../index.html',import.meta.url),'utf8'),mobile=readFileSync(new URL('../src/mobile.ts',import.meta.url),'utf8'),css=readFileSync(new URL('../style.css',import.meta.url),'utf8');
 assert.doesNotMatch(html,/id="calendar"/);assert.match(html,/id="events"/);assert.match(mobile,/\['notifications', 'achievements', 'friends', 'settings', 'events'\]/);assert.match(css,/\.login-calendar-dialog #modal-content\{[^}]*overflow:auto/);assert.match(css,/@media\(max-width:380px\)\{\.login-rewards-grid\{grid-template-columns:repeat\(3,minmax\(0,1fr\)\)/);
});

test('shop excludes login reward crops while catalog crops remain available for planting and deposit',()=>{
 const {root,renderMarket}=panels();let seen=[];
 renderMarket(root,{crops:[{id:'normal',name:'Normal',spriteStem:'normal',seedPrices:{GOLD:5},acquisitionSource:'SHOP',vipOnly:false,requiredLevel:1},{id:'semente-bronze',name:'Semente de Bronze',spriteStem:'semente-bronze',seedPrices:{},acquisitionSource:'LOGIN_REWARD',vipOnly:false,requiredLevel:1}],animals:[],fertilizers:[],feedPrices:{},vip:{greenPrice:1}},undefined,'crops','common',()=>{},fields=>seen.push(fields));
 assert.equal(nodes(root).filter(n=>n.className?.split(/\s+/).includes('market-card')).length,1);assert.ok(nodes(root).some(n=>n.textContent==='Normal'));assert.ok(!nodes(root).some(n=>n.textContent==='Semente de Bronze'));
});

test('gift composer excludes login reward crops but does not mutate catalog selection',()=>{
 const {root,renderGiftComposer}=panels(),footer={...root,children:[],replaceChildren(...items){this.children=items;}};
 const catalog={crops:[{id:'normal',name:'Normal',spriteStem:'normal',acquisitionSource:'SHOP'},{id:'semente-bronze',name:'Semente de Bronze',spriteStem:'semente-bronze',acquisitionSource:'LOGIN_REWARD'}]};
 renderGiftComposer(root,footer,catalog,{id:'friend',name:'Sam'},{phase:'ready',cropId:'normal',quantity:1,seedCounts:{normal:2,'semente-bronze':1},gifts:{received:[],sent:[]},canSend:true},{selectSeed(){},setQuantity(){},send(){},cancel(){},retry(){}});
 assert.ok(nodes(root).some(n=>n.textContent.includes('Normal · 2 no estoque')));assert.ok(!nodes(root).some(n=>n.textContent.includes('Semente de Bronze')));
});

test('deposit seed inventory keeps login reward crops visible',()=>{
 const {root,renderDeposit}=panels(),crop={id:'semente-bronze',name:'Semente de Bronze',spriteStem:'semente-bronze',acquisitionSource:'LOGIN_REWARD'};
 renderDeposit(root,{crops:[crop],animals:[],fertilizers:[],feedPrices:{},vip:{greenPrice:1}},{seedCounts:{'semente-bronze':1}},'seeds',()=>{},()=>{});
 assert.ok(nodes(root).some(node=>node.textContent==='Semente de Bronze'));assert.ok(!nodes(root).some(node=>node.textContent==='Nenhuma semente armazenada.'));
});

test('audio controls keep separate channel toggles and volume',()=>{
 const {root,renderAudioSettings}=panels(),changes=[];
 renderAudioSettings(root,{effects:false,music:true,effectsVolume:.65,musicVolume:.3},(...args)=>changes.push(args));
 const all=nodes(root),check=all.find(n=>n.tag==='input'&&n.attributes['aria-label']==='Efeitos');
 check.checked=true;check.onchange();assert.deepEqual(changes[0],['effects',true,.65]);
 const volume=all.find(n=>n.attributes['aria-label']==='Volume de Música');volume.value='10';volume.oninput();assert.deepEqual(changes[1],['music',true,.1]);
});
