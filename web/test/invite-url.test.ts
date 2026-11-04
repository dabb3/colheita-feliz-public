import test from 'node:test';
import assert from 'node:assert/strict';
import {takeInvite} from '../src/invite-url.ts';

test('invite is taken from the address and removed from it',()=>{
 assert.deepEqual(takeInvite('https://x/?invite=abc'),{invite:'abc',cleaned:'https://x/'});
});
test('other parameters and the hash survive, including the PKCE code',()=>{
 assert.deepEqual(takeInvite('https://x/?code=k&invite=abc#h'),{invite:'abc',cleaned:'https://x/?code=k#h'});
});
test('an address without invite is left alone',()=>{
 assert.deepEqual(takeInvite('https://x/?a=1'),{invite:null,cleaned:null});
});
test('an empty invite is ignored and left in place',()=>{
 assert.deepEqual(takeInvite('https://x/?invite='),{invite:null,cleaned:null});
});
