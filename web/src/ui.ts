import type {Farm,Catalog,CommandFields} from './types.ts';
import {animalPath} from './presentation.ts';
import {xpPercent,purchaseLock} from './ui-presentation.ts';

export function text(tag:string,value:string,className='') {
 const node=document.createElement(tag);node.textContent=value;node.className=className;return node;
}
export function image(path:string,className='') {
 const node=document.createElement('img');node.src='/assets/'+path;node.alt='';node.className=className;return node;
}
export function control(label:string,action:()=>void) {
 const node=text('button',label) as HTMLButtonElement;node.type='button';node.setAttribute('data-focus-key',label);node.onclick=action;return node;
}

export function renderWallet(root:HTMLElement,farm:Farm,visitedName?:string) {
 root.replaceChildren();
 const avatar=image('classic/icons/avatar_masculino.png','avatar');
 const info=document.createElement('div');info.className='wallet-info';
 const heading=document.createElement('div');heading.className='farm-heading';
 const name=text('strong',visitedName?`Visitando ${visitedName}`:farm.name);name.title=name.textContent??'';
 heading.append(name,text('span',String(farm.level??1),'level-badge'));
 const progress=document.createElement('progress');progress.max=100;progress.value=xpPercent(farm.currentXp,farm.xpToNext);
 progress.setAttribute('aria-label',`Experiência: ${farm.currentXp??0} de ${farm.xpToNext??0} XP`);
 const xp=text('small',`${farm.currentXp??0} / ${farm.xpToNext??0} XP`,'xp-label');
 const coins=document.createElement('div');coins.className='balances';
 for(const [icon,value,label]of [['moeda-comum',farm.coins??'0','ouro'],['moeda-verde',farm.greenCoins??'0','verdes']]){
  const row=document.createElement('div');row.setAttribute('aria-label',`${value} ${label}`);
  row.append(image(`classic/icons/${icon}.png`),text('strong',value));coins.append(row);
 }
 info.append(heading,progress,xp,coins);root.append(avatar,info);
}

export function renderMarket(root:HTMLElement,catalog:Catalog,farm:Farm|undefined,category:string,seedGroup:'common'|'vip',onSeedGroup:(group:'common'|'vip')=>void,onBuy:(fields:CommandFields)=>void) {
 root.replaceChildren();
 if(category==='crops'){
  const subtabs=document.createElement('div');subtabs.className='tabs seed-tabs';
  for(const [id,label]of [['common','Comuns'],['vip','VIP']] as const){const tab=control(label,()=>onSeedGroup(id));tab.className='shop-tab';tab.setAttribute('aria-pressed',String(id===seedGroup));subtabs.append(tab);}
  root.append(subtabs);
 }
 const entries=category==='crops'?catalog.crops.filter(c=>c.vipOnly===(seedGroup==='vip')).map(c=>({id:c.id,name:c.name,image:`classic/crops/plants_phase_one_to_five/${c.spriteStem}_5.png`,
  details:`${c.growthSeconds/3600} h · ${c.seasons} temporada(s)`,prices:c.seedPrices,level:c.requiredLevel,vip:c.vipOnly,action:'BUY_SEED',owned:false})):
  category==='animals'?catalog.animals.map(a=>({id:a.id,name:a.name,image:a.id==='abelha'?'classic/animals/abelha/abelha.png':animalPath(a.id,'ADULT',false,false,false),
   details:a.productName,prices:a.prices,level:a.requiredLevel,vip:false,action:'BUY_ANIMAL',owned:!!farm?.animals.some(animal=>animal.id===a.id)})):
  catalog.fertilizers.map(f=>({id:f.id,name:f.name,image:`classic/icons/adubo-${f.id}.png`,details:`Reduz ${f.reductionSeconds/60} min${f.friendsOnly?' · em amigos':''}`,
   prices:f.prices,level:f.requiredLevel,vip:false,action:'BUY_FERTILIZER',owned:false}));
 const scroll=document.createElement('div');scroll.className='market-scroll';const grid=document.createElement('div');grid.className='cards market-cards';scroll.append(grid);root.append(scroll);
 for(const item of entries){
  const card=document.createElement('article');card.className='card market-card';
  const lock=purchaseLock(farm?.level??0,item.level,item.vip,!!farm?.vipActive,item.owned);
  if(lock)card.className+=' locked';
  const art=image(item.image,'market-art');art.loading='lazy';
  card.append(text('h3',item.name),art,text('p',item.details),text('small',`Nível ${item.level}${item.vip?' · VIP':''}`));
  if(lock)card.append(text('span',lock,'lock-badge'));
  for(const [currency,price]of Object.entries(item.prices)){
   const label=currency==='GOLD'?'ouro':'verdes',buy=control(`Comprar · ${price}`,()=>onBuy({action:item.action,itemId:item.id,currency}));
   buy.disabled=!!lock;buy.setAttribute('aria-label',`Comprar ${item.name} por ${price} ${label}`);buy.title=lock??`${price} ${label}`;
   buy.append(image(`classic/icons/moeda-${currency==='GOLD'?'comum':'verde'}.png`,'price-coin'));card.append(buy);
  }
  grid.append(card);
 }
 if(!entries.length)scroll.append(text('p',category==='crops'&&seedGroup==='vip'?'Nenhuma semente VIP disponível.':'Nenhum item nesta categoria.','empty-state'));
}
