import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { test } from 'node:test';
import { createWebDriverRequester, requireTouchPoint } from '../e2e/safari-webdriver.mjs';

async function withServer(handler, callback) {
  const server = createServer(handler);
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const address = server.address();
  try {
    await callback(`http://127.0.0.1:${address.port}`);
  } finally {
    server.close();
    await once(server, 'close');
  }
}

test('WebDriver requester preserves an explicit null value', async () => {
  await withServer((_request, response) => response.end(JSON.stringify({ value: null, sessionId: 's' })), async url => {
    assert.equal(await createWebDriverRequester(url)('/session/s/execute/sync'), null);
  });
});

test('WebDriver requester unwraps valid W3C value objects', async () => {
  await withServer((_request, response) => response.end(JSON.stringify({ value: { x: 12, y: 34 }, sessionId: 's' })), async url => {
    assert.deepEqual(await createWebDriverRequester(url)('/session/s/execute/sync'), { x: 12, y: 34 });
  });
});

test('WebDriver requester rejects WebDriver protocol errors', async () => {
  await withServer((_request, response) => response.end(JSON.stringify({ value: { error: 'invalid argument', message: 'bad coordinates' } })), async url => {
    await assert.rejects(createWebDriverRequester(url)('/session/s/actions', 'POST'), /invalid argument.*bad coordinates/);
  });
});

test('touch point validation requires observed touch with finite numeric coordinates', () => {
  assert.deepEqual(requireTouchPoint({ pointerType: 'touch', x: 12, y: 34 }, 'calibration'), { x: 12, y: 34, pointerType: 'touch' });
  for (const event of [null, { pointerType: 'mouse', x: 1, y: 2 }, { pointerType: 'touch', x: null, y: 2 }, { pointerType: 'touch', x: NaN, y: 2 }, { pointerType: 'touch', x: 1, y: Infinity }]) {
    assert.throws(() => requireTouchPoint(event, 'calibration'), /finite numeric coordinates/);
  }
});
