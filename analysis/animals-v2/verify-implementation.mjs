import fs from 'node:fs';
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
const baseline='be6e985';
const original=file=>execFileSync('git',['show',`${baseline}:${file}`],{encoding:'utf8'});
function csv(file){const [head,...lines]=fs.readFileSync(file,'utf8').trim().split(/\r?\n/);const keys=head.split(',');return lines.map(line=>Object.fromEntries(line.split(',').map((v,i)=>[keys[i],v])));}
const gold=csv('analysis/animals-v2/results/current-proposed.csv');
const green=csv('analysis/animals-v2/green-calibration/prices.csv');
const current=JSON.parse(fs.readFileSync('assets/data/animals.json','utf8'));
const before=JSON.parse(original('assets/data/animals.json'));
const tenths=s=>{const [whole,fraction='']=s.split('.');assert.ok(fraction.length<=1);return BigInt(whole)*10n+BigInt(fraction||'0');};
let checked=0;
const rows=['animal,currency,profile,approved_tenths,implemented_tenths'];
for(const animal of current){
 const unchanged={...animal};delete unchanged.salePrices;
 assert.deepEqual(unchanged,before.find(a=>a.id===animal.id),animal.id+' non-sale definition');
 if(animal.kind==='PROTECTOR'){assert.equal(animal.salePrices,undefined);continue;}
 const g=gold.find(a=>a.animal===animal.id),v=green.find(a=>a.animal===animal.id);assert.ok(g&&v);
 for(const currency of ['GOLD','GREEN'])for(const profile of ['common','vip']){
  const approved=currency==='GOLD'?g[`proposed_${profile}_gold`]:v[`B_${profile}`];
  const actual=animal.salePrices[currency][`${profile}Tenths`];
  assert.equal(BigInt(actual),tenths(approved),animal.id+currency+profile);
  rows.push([animal.id,currency,profile,tenths(approved),actual].join(','));checked++;
 }
}
assert.equal(checked,40);assert.equal(current.length,15);
for(const file of ['assets/data/crops.json','assets/data/progression.json','assets/data/fertilizers.json'])
 assert.deepEqual(JSON.parse(fs.readFileSync(file,'utf8')),JSON.parse(original(file)),file);
const rule=fs.readFileSync('docs/game-rule.md','utf8');
for(const animal of current.filter(a=>a.kind!=='PROTECTOR')){
 const line=rule.split(/\r?\n/).find(l=>l.startsWith(`| ${animal.name} |`));assert.ok(line,animal.name);
 const cells=line.split('|');
 for(const [index,profile] of [[5,'common'],[6,'vip']]){
  const expected=['GOLD','GREEN'].map(c=>(animal.salePrices[c][`${profile}Tenths`]/10).toLocaleString('pt-BR',{minimumFractionDigits:1,maximumFractionDigits:1})).join(' / ');
  assert.equal(cells[index].trim(),expected,animal.id+' documentation');
 }
}
fs.mkdirSync('build/reports/animal-v2',{recursive:true});
fs.writeFileSync('build/reports/animal-v2/approved-vs-implemented.csv',rows.join('\n')+'\n');
fs.writeFileSync('analysis/animals-v2/implemented-prices.csv',rows.join('\n')+'\n');
console.log('OK: all 40 approved prices match catalog and docs; all 15 animal definitions, purchases and protected catalogs preserved.');
