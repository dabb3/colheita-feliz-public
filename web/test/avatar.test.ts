import test from 'node:test';
import assert from 'node:assert/strict';
import * as avatar from '../src/avatar.ts';

test('Google avatar accepts only the Google picture host over HTTPS',()=>{
 const photo='https://lh3.googleusercontent.com/a/example=s96-c';
 assert.equal(avatar.googleAvatar(photo),photo);
 for(const value of [undefined,null,123,'javascript:alert(1)','http://lh3.googleusercontent.com/a','https://lh3.googleusercontent.com.evil.test/a','https://user:secret@lh3.googleusercontent.com/a','data:image/png;base64,x'])assert.equal(avatar.googleAvatar(value),undefined);
});

test('avatar preference is scoped to the account and stores only the choice',()=>{
 const values=new Map(),storage={getItem:key=>values.get(key)??null,setItem:(key,value)=>values.set(key,value)};
 assert.equal(avatar.readAvatar(storage,'alice'),'classic');
 avatar.saveAvatar(storage,'alice','google');
 assert.equal(avatar.readAvatar(storage,'alice'),'google');assert.equal(avatar.readAvatar(storage,'bob'),'classic');
 assert.deepEqual([...values.values()],['google']);
 assert.equal(avatar.avatarSource('google',undefined),avatar.CLASSIC_AVATAR);
});

test('unavailable browser storage never prevents the game from displaying its avatar',()=>{
 const storage={getItem(){throw Error('blocked');},setItem(){throw Error('blocked');}};
 assert.equal(avatar.readAvatar(storage,'alice'),'classic');
 assert.doesNotThrow(()=>avatar.saveAvatar(storage,'alice','google'));
});
