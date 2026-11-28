export function requireReleasedPanPointerPair(events) {
  if (!Array.isArray(events)) throw new TypeError('Released-pan DOM events must be an array.');

  const downs = events.filter(event => event.type === 'pointerdown');
  if (downs.length !== 1 || downs[0].pointerType !== 'touch') {
    throw new Error(`Released-pan must observe exactly one touch pointerdown: ${JSON.stringify(events)}`);
  }

  const pointerDown = downs[0];
  if (!Number.isSafeInteger(pointerDown.pointerId)) {
    throw new Error(`Released-pan pointerdown must have a numeric DOM pointerId: ${JSON.stringify(pointerDown)}`);
  }

  const pointerUp = events.find(event => event.type === 'pointerup'
    && event.pointerType === 'touch'
    && event.pointerId === pointerDown.pointerId);
  if (!pointerUp) {
    throw new Error(`Released-pan is missing pointerup for DOM pointerId ${pointerDown.pointerId}: ${JSON.stringify(events)}`);
  }

  if (events.some(event => event.type === 'pointercancel')) {
    throw new Error(`W3C Release Actions must not produce pointercancel in released-pan: ${JSON.stringify(events)}`);
  }

  return { pointerDown, pointerUp };
}
