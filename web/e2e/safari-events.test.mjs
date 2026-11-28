import assert from 'node:assert/strict';
import test from 'node:test';
import { requireReleasedPanPointerPair } from './safari-events.mjs';

test('released-pan requires a touch pointerup for its own DOM pointerId', () => {
  const events = [
    { type: 'pointerdown', pointerType: 'touch', pointerId: 31 },
    { type: 'pointerup', pointerType: 'touch', pointerId: 31 },
  ];
  assert.deepEqual(requireReleasedPanPointerPair(events), { pointerDown: events[0], pointerUp: events[1] });
});

test('released-pan fails explicitly when its matching pointerup is absent', () => {
  assert.throws(() => requireReleasedPanPointerPair([
    { type: 'pointerdown', pointerType: 'touch', pointerId: 31 },
    { type: 'pointerup', pointerType: 'touch', pointerId: 30 },
  ]), /missing pointerup for DOM pointerId 31/);
});

test('released-pan rejects pointercancel and does not confuse WebDriver action ids with DOM ids', () => {
  assert.throws(() => requireReleasedPanPointerPair([
    { type: 'pointerdown', pointerType: 'touch', pointerId: 31, actionId: 'released-pan' },
    { type: 'pointercancel', pointerType: 'touch', pointerId: 31 },
    { type: 'pointerup', pointerType: 'touch', pointerId: 31 },
  ]), /must not produce pointercancel/);
});
