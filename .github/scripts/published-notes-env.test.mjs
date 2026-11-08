import {test} from 'node:test';
import assert from 'node:assert/strict';
import {mkdtempSync, writeFileSync, unlinkSync, rmdirSync} from 'node:fs';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const script=fileURLToPath(new URL('./published-notes-env.mjs',import.meta.url));
const valid={version:'another-release-v2',prNumber:123,items:['Novidade publicada.']};
function run(value,raw=false){
  const dir=mkdtempSync(join(tmpdir(),'colheita-notes-'));
  const path=join(dir,'notes.json');
  try{
    if(value!==undefined)writeFileSync(path,raw?value:JSON.stringify(value));
    return spawnSync(process.execPath,[script,path],{encoding:'utf8'});
  }finally{
    if(value!==undefined)unlinkSync(path);
    rmdirSync(dir);
  }
}
test('metadata comes from the supplied version and real PR without hardcoded release values',()=>{
  const result=run(valid);
  assert.equal(result.status,0,result.stderr);
  assert.equal(result.stdout,'--env\nPUBLISHED_UPDATE_NOTES_VERSION=another-release-v2\n--env\nPUBLISHED_UPDATE_NOTES_PR=123\n');
});
for(const [name,value] of Object.entries({
  'missing file':undefined,'malformed JSON':'{',
  'null PR':{...valid,prNumber:null},'string PR':{...valid,prNumber:'123'},
  'fractional PR':{...valid,prNumber:1.5},'zero PR':{...valid,prNumber:0},
  'invalid version':{...valid,version:'bad\n--env OTHER=value'},
  'empty copy':{...valid,items:[]},'blank copy':{...valid,items:[' ']},
  'nonstring copy':{...valid,items:[123]},'long copy':{...valid,items:['x'.repeat(601)]},
  'too many items':{...valid,items:Array(31).fill('Novidade')}
}))test(`rejects ${name} without emitting deployment arguments`,()=>{
  const result=run(value,name==='malformed JSON');
  assert.equal(result.status,1);
  assert.equal(result.stdout,'');
  assert.match(result.stderr,/Invalid update notes manifest/);
});
