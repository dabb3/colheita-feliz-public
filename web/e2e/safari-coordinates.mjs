export function viewportPointToScreen(point, viewportRect, screenInfo) {
  if (typeof point?.x !== 'number' || typeof point?.y !== 'number') {
    throw new TypeError('Touch coordinates must be numeric.');
  }
  if (typeof viewportRect?.left !== 'number' || typeof viewportRect?.top !== 'number') {
    throw new TypeError('Viewport offsets must be numeric.');
  }
  const values = [point?.x, point?.y, viewportRect?.left, viewportRect?.top, screenInfo?.scale,
    screenInfo?.safeAreaInsets?.left, screenInfo?.safeAreaInsets?.top,
    screenInfo?.coordinateCorrection?.x, screenInfo?.coordinateCorrection?.y].filter(value => value !== undefined);
  if (!values.every(Number.isFinite)) throw new TypeError('Touch coordinates and viewport offsets must be finite.');
  if (screenInfo.scale <= 0) throw new RangeError('Device screen scale must be positive.');
  if (viewportRect.width !== undefined && viewportRect.width <= 0) throw new RangeError('Viewport width must be positive.');
  if (viewportRect.height !== undefined && viewportRect.height <= 0) throw new RangeError('Viewport height must be positive.');

  const screenPoint = {
    x: Math.round(point.x + Math.max(viewportRect.left / screenInfo.scale, screenInfo.safeAreaInsets?.left ?? 0) + (screenInfo.coordinateCorrection?.x ?? 0)),
    y: Math.round(point.y + Math.max(viewportRect.top / screenInfo.scale, screenInfo.safeAreaInsets?.top ?? 0) + (screenInfo.coordinateCorrection?.y ?? 0)),
  };
  if (!Number.isFinite(screenPoint.x) || !Number.isFinite(screenPoint.y)) throw new TypeError('Mapped touch coordinates must be finite.');
  return screenPoint;
}
