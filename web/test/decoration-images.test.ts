import test from 'node:test';
import assert from 'node:assert/strict';
import {DecorationImageCache, LatestDecorationPreview} from '../src/decoration-images.ts';

function deferred() {
  let resolve!: () => void, reject!: (error: Error) => void;
  const promise = new Promise<void>((yes, no) => { resolve = yes; reject = no; });
  return {promise, resolve, reject};
}

function fakeFactory() {
  const created: {src: string; naturalWidth: number; decode(): Promise<void>; task: ReturnType<typeof deferred>}[] = [];
  const create = () => {
    const task = deferred();
    const image = {src: '', naturalWidth: 1478, decode: () => task.promise, task};
    created.push(image);
    return image;
  };
  return {created, create};
}

async function turn() { await Promise.resolve(); await Promise.resolve(); }

test('same URL shares in-flight decoding and stays cached for repeated visits', async () => {
  const {created, create} = fakeFactory(), cache = new DecorationImageCache(create, 4);
  const a = cache.load('/assets/inverno-noite.png'), b = cache.load('/assets/inverno-noite.png');
  assert.strictEqual(a, b);
  await turn();
  assert.equal(created.length, 1);
  assert.equal(created[0].src, '/assets/inverno-noite.png');
  created[0].task.resolve();
  assert.strictEqual(await a, await cache.load('/assets/inverno-noite.png'));
  assert.equal(created.length, 1);
});

test('preloads neighboring previews, but caps the decoded-image cache', async () => {
  const {created, create} = fakeFactory(), cache = new DecorationImageCache(create, 2);
  cache.preload(['/a', '/a', '/b']);
  await turn();
  assert.equal(created.length, 2);
  created[0].task.resolve(); created[1].task.resolve();
  await turn();
  const latest = cache.load('/c'); await turn();
  created[2].task.resolve(); await latest;
  const reused = cache.load('/b'); await reused;
  assert.equal(created.length, 3, 'recent neighbor retained in bounded cache');
  const first = cache.load('/a'); await turn();
  assert.equal(created.length, 4, 'older preview evicted to limit memory');
  created[3].task.resolve(); await first;
});

test('rapid navigation and stale load failures cannot overwrite the latest selection', async () => {
  const {created, create} = fakeFactory(), preview = new LatestDecorationPreview(new DecorationImageCache(create));
  const visible: string[] = [], errors: string[] = [];
  const first = preview.show('/night', img => visible.push(img.src), () => errors.push('night'));
  const second = preview.show('/day', img => visible.push(img.src), () => errors.push('day'));
  await turn();
  created[1].task.resolve(); await second;
  created[0].task.resolve(); await first;
  assert.deepEqual(visible, ['/day']);
  assert.deepEqual(errors, []);

  const stale = preview.show('/bad', img => visible.push(img.src), () => errors.push('bad'));
  const fresh = preview.show('/good', img => visible.push(img.src), () => errors.push('good'));
  await turn();
  created[2].task.reject(new Error('network error'));
  await stale;
  created[3].task.resolve(); await fresh;
  assert.deepEqual(visible, ['/day', '/good']);
  assert.deepEqual(errors, []);
});

test('failed or empty images can be retried without caching the error', async () => {
  const {created, create} = fakeFactory(), cache = new DecorationImageCache(create);
  const first = cache.load('/fail'); await turn();
  created[0].task.reject(new Error('broken file'));
  await assert.rejects(first);
  const second = cache.load('/fail'); await turn();
  assert.equal(created.length, 2);
  created[1].task.resolve(); await second;

  const empty = cache.load('/empty'); await turn();
  created[2].naturalWidth = 0; created[2].task.resolve();
  await assert.rejects(empty, /without dimensions/);
  const again = cache.load('/empty'); await turn();
  assert.equal(created.length, 4);
  created[3].task.resolve(); await again;
});

test('cancel prevents late completion after the panel was replaced', async () => {
  const {created, create} = fakeFactory(), preview = new LatestDecorationPreview(new DecorationImageCache(create));
  let replaced = false;
  const running = preview.show('/pending', () => { replaced = true; }, () => { replaced = true; });
  await turn(); preview.cancel(); created[0].task.resolve(); await running;
  assert.equal(replaced, false);
});
