import test from 'node:test';
import assert from 'node:assert/strict';
import {xpPercent,pageItems,purchaseLock} from '../src/ui-presentation.ts';

test('XP progress keeps integer precision beyond the safe Number range',()=>{
 assert.equal(xpPercent('900719925474099300','1801439850948198600'),50);
 assert.equal(xpPercent('0.5','25'),2);
 assert.equal(xpPercent('0','25'),0);
 assert.equal(xpPercent('25','0'),100);
 assert.equal(xpPercent(undefined,undefined),0);
});

test('market pagination bounds first, last and empty pages without losing entries',()=>{
 const items=Array.from({length:23},(_,i)=>i);
 assert.deepEqual(pageItems(items,1).items,[10,11,12,13,14,15,16,17,18,19]);
 assert.equal(pageItems(items,99).page,2);
 assert.deepEqual(pageItems(items,99).items,[20,21,22]);
 assert.equal(pageItems(items,-1).page,0);
 assert.deepEqual(pageItems([],0),{items:[],page:0,pages:1,total:0});
});

test('purchase locks explain ownership, level and VIP without changing prices',()=>{
 assert.equal(purchaseLock(2,3,false,false,false),'Nível 3');
 assert.equal(purchaseLock(3,3,true,false,false),'Exige VIP');
 assert.equal(purchaseLock(3,3,true,true,false),undefined);
 assert.equal(purchaseLock(3,3,false,false,true),'Já comprado');
});
