import type {Farm,Catalog,CommandFields} from './types.ts';
import {animalPath} from './presentation.ts';
import {xpPercent,pageItems,purchaseLock} from './ui-presentation.ts';

function text(tag:string,value:string,className='') {
 const node=document.createElement(tag);node.textContent=value;node.className=className;return node;
}
function image(path:string,className='') {
 const node=document.createElement('img');node.src='/assets/'+path;node.alt='';node.className=className;return node;
}
function control(label:string,action:()=>void) {
 const node=text('button',label) as HTMLButtonElement;node.type='button';node.onclick=action;return node;
}

export function focusMarketPage(root:HTMLElement,forward:boolean) {
 const buttons=root.querySelectorAll<HTMLButtonElement>('.market-pages button');
 const target=buttons[forward?1:0];(target?.disabled?buttons[forward?0:1]:target)?.focus();
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

export function renderMarket(root:HTMLElement,catalog:Catalog,farm:Farm|undefined,category:string,requested:number,onPage:(page:number)=>void,onBuy:(fields:CommandFields)=>void) {
 root.replaceChildren();
 const entries=category==='crops'?catalog.crops.map(c=>({id:c.id,name:c.name,image:`classic/crops/plants_phase_one_to_five/${c.spriteStem}_5.png`,
  details:`${c.growthSeconds/3600} h · ${c.seasons} temporada(s)`,prices:c.seedPrices,level:c.requiredLevel,vip:c.vipOnly,action:'BUY_SEED',owned:false})):
  category==='animals'?catalog.animals.map(a=>({id:a.id,name:a.name,image:a.id==='abelha'?'classic/animals/abelha/abelha.png':animalPath(a.id,'ADULT',false,false),
   details:a.productName,prices:a.prices,level:a.requiredLevel,vip:false,action:'BUY_ANIMAL',owned:!!farm?.animals.some(animal=>animal.id===a.id)})):
  catalog.fertilizers.map(f=>({id:f.id,name:f.name,image:`classic/icons/adubo-${f.id}.png`,details:`Reduz ${f.reductionSeconds/60} min${f.friendsOnly?' · em amigos':''}`,
   prices:f.prices,level:f.requiredLevel,vip:false,action:'BUY_FERTILIZER',owned:false}));
 const page=pageItems(entries,requested);
 const navigation=document.createElement('nav');navigation.className='market-pages';navigation.setAttribute('aria-label','Páginas da loja');
 const previous=control('‹',()=>onPage(page.page-1));previous.disabled=page.page===0;previous.setAttribute('aria-label','Página anterior');
 const next=control('›',()=>onPage(page.page+1));next.disabled=page.page===page.pages-1;next.setAttribute('aria-label','Próxima página');
 navigation.append(previous,text('span',`${page.page+1} / ${page.pages}`),next,text('small',`${page.total} itens`));root.append(navigation);
 const grid=document.createElement('div');grid.className='cards market-cards';root.append(grid);
 for(const item of page.items){
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
 if(!entries.length)root.append(text('p','Nenhum item nesta categoria.','empty-state'));
}
