import assert from 'node:assert/strict';
import test from 'node:test';
import { viewportPointToScreen } from './safari-coordinates.mjs';

test('adds the iOS status-bar origin in CSS points using the device scale', () => {
  assert.deepEqual(
    viewportPointToScreen({ x: 354, y: 371 }, { left: 0, top: 177 }, { scale: 3 }),
    { x: 354, y: 430 },
  );
});

test('converts native viewport offsets before adding CSS viewport coordinates', () => {
  assert.deepEqual(
    viewportPointToScreen({ x: 12, y: 20 }, { left: 20, top: 96 }, { scale: 2 }),
    { x: 22, y: 68 },
  );
});

test('rejects invalid screen scale and non-finite coordinates', () => {
  assert.throws(() => viewportPointToScreen({ x: 1, y: 2 }, { left: 0, top: 0 }, { scale: 0 }), /scale/);
  assert.throws(() => viewportPointToScreen({ x: Number.NaN, y: 2 }, { left: 0, top: 0 }, { scale: 2 }), /coordinate/);
  assert.throws(() => viewportPointToScreen({ x: 1, y: 2 }, { left: 0, top: 0, width: 0 }, { scale: 2 }), /width/);
});
