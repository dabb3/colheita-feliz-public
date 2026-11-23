import { request as httpRequest } from 'node:http';

export function createWebDriverRequester(appium, timeoutMs = 600_000) {
  return function request(path, method = 'GET', body) {
    return new Promise((resolve, reject) => {
      const req = httpRequest(new URL(`${appium}${path}`), {
        method,
        headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
      }, response => {
        let payload = '';
        response.setEncoding('utf8');
        response.on('data', chunk => { payload += chunk; });
        response.on('end', () => {
          let result;
          try {
            result = JSON.parse(payload);
          } catch (error) {
            reject(error);
            return;
          }
          const hasW3cValue = result !== null && typeof result === 'object' && Object.hasOwn(result, 'value');
          const value = hasW3cValue ? result.value : result;
          if ((response.statusCode ?? 500) >= 400 || value?.error) {
            reject(new Error(`${method} ${path}: ${JSON.stringify(value)}`));
            return;
          }
          resolve(value);
        });
      });
      req.setTimeout(timeoutMs, () => req.destroy(new Error(`Timed out after ${timeoutMs}ms: ${method} ${path}`)));
      req.on('error', reject);
      if (body !== undefined) req.write(JSON.stringify(body));
      req.end();
    });
  };
}

export function requireTouchPoint(event, label) {
  if (event?.pointerType !== 'touch') {
    throw new Error(`${label} requires an observed touch event with finite numeric coordinates: ${JSON.stringify(event)}`);
  }
  const point = requireFinitePoint(event, label);
  return { ...point, pointerType: event.pointerType };
}

export function requireFinitePoint(point, label) {
  if (typeof point?.x !== 'number' || typeof point?.y !== 'number'
    || !Number.isFinite(point.x) || !Number.isFinite(point.y)) {
    throw new Error(`${label} requires finite numeric coordinates: ${JSON.stringify(point)}`);
  }
  return { x: point.x, y: point.y };
}
