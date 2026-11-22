export function viewportPointToScreen(point, viewportRect, screenInfo) {
  const values = [point?.x, point?.y, viewportRect?.left, viewportRect?.top, screenInfo?.scale];
  if (!values.every(Number.isFinite)) throw new TypeError('Touch coordinates and viewport offsets must be finite.');
  if (screenInfo.scale <= 0) throw new RangeError('Device screen scale must be positive.');
  if (viewportRect.width !== undefined && viewportRect.width <= 0) throw new RangeError('Viewport width must be positive.');
  if (viewportRect.height !== undefined && viewportRect.height <= 0) throw new RangeError('Viewport height must be positive.');

  return {
    x: Math.round(point.x + viewportRect.left / screenInfo.scale),
    y: Math.round(point.y + viewportRect.top / screenInfo.scale),
  };
}
