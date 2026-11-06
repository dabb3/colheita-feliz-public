import test from 'node:test';
import assert from 'node:assert/strict';
import {animalSaleOffer,animalOwnedSale} from '../src/presentation.ts';
import type {AnimalType} from '../src/types.ts';

test('animal offer shows authoritative catalog quotes for common and VIP without multiplying principal',()=>{
 const type:AnimalType={id:'vaca',name:'Vaca',kind:'PRODUCER',requiredLevel:48,prices:{GOLD:220000,GREEN:180},productId:'leite',productName:'Leite',salePrices:{GOLD:{commonTenths:2281600,vipTenths:2301400},GREEN:{commonTenths:1826,vipTenths:1840}}};
 assert.equal(animalSaleOffer(type,false),'Venda: 228160,0 ouro · 182,6 verdes');
 assert.equal(animalSaleOffer(type,true),'Venda: 230140,0 ouro · 184,0 verdes');
 assert.equal(animalSaleOffer({...type,kind:'PROTECTOR',salePrices:undefined},true),'Sem revenda');
});
test('owned animal uses server quote, including legacy sale and no private fields on a visit',()=>{
 const animal={id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0,saleQuote:'225.0',saleCurrency:'GREEN',economyVersion:0};
 assert.equal(animalOwnedSale(animal),'Venda: 225,0 verdes');
 assert.equal(animalOwnedSale({...animal,saleQuote:'184.0',economyVersion:2}),'Venda: 184,0 verdes');
 assert.equal(animalOwnedSale({id:'vaca',stage:'ADULT',status:'READY',remainingSeconds:0}),'');
});
