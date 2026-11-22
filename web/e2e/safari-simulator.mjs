import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { request as httpRequest } from 'node:http';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { viewportPointToScreen } from './safari-coordinates.mjs';

const appium = process.env.APPIUM_URL ?? 'http://127.0.0.1:4723';
const appUrl = process.env.E2E_APP_URL ?? 'http://127.0.0.1:5173';
const artifacts = resolve(process.env.SAFARI_ARTIFACT_DIR ?? 'test-results/safari-simulator');
await mkdir(artifacts, { recursive: true });

function simctl(args, options = {}) {
  return execFileSync('xcrun', ['simctl', ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], ...options }).trim();
}

function compatibleIosVersions() {
  const sdks = execFileSync('xcodebuild', ['-showsdks'], { encoding: 'utf8' });
  const versions = new Set([...sdks.matchAll(/-sdk iphonesimulator(\d+\.\d+)/g)].map(match => match[1]));
  if (!versions.size) throw new Error('The selected Xcode exposes no iOS Simulator SDK.');
  return versions;
}

function chooseSimulator() {
  const inventory = JSON.parse(simctl(['list', 'devices', 'available', '--json']));
  const supportedVersions = compatibleIosVersions();
  const candidates = Object.entries(inventory.devices).flatMap(([runtime, devices]) => {
    const version = runtime.match(/iOS-(\d+)-(\d+)/);
    const versionName = version ? `${version[1]}.${version[2]}` : null;
    if (!versionName || !supportedVersions.has(versionName)) return [];
    return devices.filter(device => device.name.startsWith('iPhone ')).map(device => ({
      ...device,
      runtime,
      version: versionName,
      versionKey: Number(version[1]) * 100 + Number(version[2]),
    }));
  }).sort((a, b) => b.versionKey - a.versionKey || a.name.localeCompare(b.name));
  if (!candidates.length) throw new Error(`No available iPhone Simulator matches the selected Xcode SDKs (iOS ${[...supportedVersions].join(', ')}).`);
  return candidates[0];
}

if (process.argv.includes('--list')) {
  const device = chooseSimulator();
  console.log(`Available Safari target: ${device.name} · iOS ${device.version} · ${device.udid}`);
  process.exit(0);
}

async function request(path, method = 'GET', body) {
  const timeoutMs = 600_000;
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
        if ((response.statusCode ?? 500) >= 400 || result.value?.error) {
          reject(new Error(`${method} ${path}: ${JSON.stringify(result.value ?? result)}`));
          return;
        }
        resolve(result.value ?? result);
      });
    });
    req.setTimeout(timeoutMs, () => req.destroy(new Error(`Timed out after ${timeoutMs}ms: ${method} ${path}`)));
    req.on('error', reject);
    if (body !== undefined) req.write(JSON.stringify(body));
    req.end();
  });
}

async function waitFor(check, label, timeout = 30_000) {
  const deadline = Date.now() + timeout;
  let last;
  while (Date.now() < deadline) {
    try {
      last = await check();
      if (last) return last;
    } catch (error) {
      last = error.message;
    }
    await new Promise(resolveWait => setTimeout(resolveWait, 250));
  }
  throw new Error(`Timed out waiting for ${label}: ${last ?? 'not ready'}`);
}

let sessionId;
let simulator;
let startedSimulator = false;
const result = { appUrl, device: null, screenshots: [], javascriptErrors: [] };
try {
  simulator = chooseSimulator();
  result.device = { name: simulator.name, runtime: simulator.runtime, udid: simulator.udid };
  console.log(`Safari Simulator: ${simulator.name} · iOS ${simulator.version} · ${simulator.udid}`);
  if (simulator.state !== 'Booted' && simulator.state !== 'Booting') {
    simctl(['boot', simulator.udid]);
    startedSimulator = true;
  }
  execFileSync('open', ['-a', 'Simulator', '--args', '-CurrentDeviceUDID', simulator.udid]);
  simctl(['bootstatus', simulator.udid, '-b']);
  await waitFor(() => request('/status').then(status => status.ready === true), 'Appium server', 60_000);

  const created = await request('/session', 'POST', { capabilities: { alwaysMatch: {
    platformName: 'iOS',
    'appium:automationName': 'XCUITest',
    'appium:deviceName': simulator.name,
    'appium:platformVersion': simulator.version,
    'appium:udid': simulator.udid,
    browserName: 'Safari',
    'appium:newCommandTimeout': 120,
    'appium:wdaLaunchTimeout': 300_000,
    'appium:webviewConnectTimeout': 30_000,
    'appium:showXcodeLog': true,
  } } });
  sessionId = created.sessionId ?? created.value?.sessionId;
  assert.ok(sessionId, 'Appium returned a W3C session id');
  const call = (path, method = 'GET', body) => request(`/session/${sessionId}${path}`, method, body);
  const execute = async (script, args = []) => ({ value: await call('/execute/sync', 'POST', { script, args }) });
  const find = async selector => {
    const found = await call('/element', 'POST', { using: 'css selector', value: selector });
    const elementId = found['element-6066-11e4-a52e-4f735466cecf'] ?? found.ELEMENT;
    assert.ok(elementId, `Safari found ${selector}`);
    return elementId;
  };
  const click = async selector => call(`/element/${await find(selector)}/click`, 'POST');
  const screenGeometry = async () => {
    const [viewportRect, screenInfo, browser] = await Promise.all([
      call('/execute/sync', 'POST', { script: 'mobile: viewportRect', args: [] }),
      call('/execute/sync', 'POST', { script: 'mobile: deviceScreenInfo', args: [] }),
      execute("const c=document.querySelector('#farm'),r=c.getBoundingClientRect(); return {inner:{width:innerWidth,height:innerHeight},visual:{width:visualViewport?.width,height:visualViewport?.height,scale:visualViewport?.scale,offsetLeft:visualViewport?.offsetLeft,offsetTop:visualViewport?.offsetTop},canvas:{left:r.left,top:r.top,width:r.width,height:r.height},hit:document.elementFromPoint(r.left+r.width/2,r.top+r.height/2)?.tagName}"),
    ]);
    return { viewportRect, screenInfo, browser: browser.value };
  };
  const touch = async (x, y, finger = 'finger') => {
    const geometry = await screenGeometry();
    const screenPoint = viewportPointToScreen({ x, y }, geometry.viewportRect, geometry.screenInfo);
    await call('/actions', 'POST', { actions: [{ type: 'pointer', id: finger, parameters: { pointerType: 'touch' }, actions: [
      { type: 'pointerMove', duration: 0, x: screenPoint.x, y: screenPoint.y },
      { type: 'pointerDown', button: 0 },
      { type: 'pause', duration: 100 },
      { type: 'pointerUp', button: 0 },
    ] }] });
    await call('/actions', 'DELETE');
    return { screenPoint, geometry };
  };
  const screenshot = async name => {
    const data = await call('/screenshot');
    const file = resolve(artifacts, `${name}.png`);
    await writeFile(file, Buffer.from(data, 'base64'));
    result.screenshots.push(file);
  };
  const visible = selector => execute('const e=document.querySelector(arguments[0]); return !!e && !e.hidden && e.getBoundingClientRect().width>0', [selector]);

  await call('/url', 'POST', { url: appUrl });
  await waitFor(async () => (await visible('#login .login-test-button')).value, 'synthetic login');
  await execute("window.__iosTestErrors=[]; window.addEventListener('error', event=>window.__iosTestErrors.push(event.message));");
  await screenshot('login');
  await click('#login .login-test-button');
  await waitFor(async () => (await visible('#game')).value, 'farm after synthetic login');
  await waitFor(async () => (await execute("return document.querySelector('#farm').width > 0 && document.querySelector('#farm').height > 0")).value, 'farm canvas');
  await screenshot('farm');

  const plot = await execute("const canvas=document.querySelector('#farm'),c=canvas.getBoundingClientRect(),x=Math.round(c.left+c.width*1010/1600),y=Math.round(c.top+c.height*580/900); window.__iosPointerEvents=[]; for(const type of ['pointerdown','pointerup','pointercancel','click']) canvas.addEventListener(type,e=>window.__iosPointerEvents.push({type:e.type,pointerType:e.pointerType,pointerId:e.pointerId,clientX:e.clientX,clientY:e.clientY}),true); return {x,y,rect:{left:c.left,top:c.top,width:c.width,height:c.height},viewport:{width:innerWidth,height:innerHeight,visualWidth:visualViewport?.width,visualHeight:visualViewport?.height,visualScale:visualViewport?.scale,offsetTop:visualViewport?.offsetTop},hit:document.elementFromPoint(x,y)?.tagName}");
  result.touchDiagnostics = plot.value;
  result.touchDiagnostics.mapping = await touch(plot.value.x, plot.value.y);
  result.touchDiagnostics.events = (await execute('return window.__iosPointerEvents')).value;
  const plotPanelOpen = async () => (await execute("return document.querySelector('#modal-title').textContent === 'Comprar terra' && document.querySelector('#modal').open")).value;
  await waitFor(plotPanelOpen, 'touch opening the plot panel');
  const observed = result.touchDiagnostics.events.find(event => event.type === 'pointerup');
  assert.ok(observed, 'Safari reports the native touch pointerup');
  assert.ok(Math.abs(observed.clientX - plot.value.x) <= 1 && Math.abs(observed.clientY - plot.value.y) <= 1,
    `mapped touch reaches intended CSS point: intended=${plot.value.x},${plot.value.y} observed=${observed.clientX},${observed.clientY}`);
  await screenshot('plot-touch');
  await click('#close-modal');

  // Drive actual W3C touch input through the farm camera, then verify a tap still selects a safe panel.
  const beforeGestureActions = (await execute("return window.__farmTestActions ?? 0")).value;
  await call('/execute/sync', 'POST', { script: "window.__farmTestActions=0; const f=window.fetch; window.fetch=(...a)=>{if(String(a[0]).includes('/v1/me/actions')) window.__farmTestActions++; return f(...a)}", args: [] });
  const canvasRect = (await execute("const r=document.querySelector('#farm').getBoundingClientRect(); return {left:r.left,top:r.top,width:r.width,height:r.height}")).value;
  const center = { x: canvasRect.left + canvasRect.width * .72, y: canvasRect.top + canvasRect.height * .58 };
  const panStart = await screenGeometry();
  const start = viewportPointToScreen(center, panStart.viewportRect, panStart.screenInfo);
  const endCss = { x: center.x - 42, y: center.y + 28 };
  const end = viewportPointToScreen(endCss, panStart.viewportRect, panStart.screenInfo);
  await call('/actions', 'POST', { actions: [{ type: 'pointer', id: 'pan', parameters: { pointerType: 'touch' }, actions: [
    { type: 'pointerMove', duration: 0, x: start.x, y: start.y }, { type: 'pointerDown', button: 0 },
    { type: 'pointerMove', duration: 450, x: end.x, y: end.y }, { type: 'pointerUp', button: 0 },
  ] }] }); await call('/actions', 'DELETE');
  const pinchStartCss = { x: center.x - 35, y: center.y }, pinchEndCss = { x: center.x + 35, y: center.y };
  const pinchStart = viewportPointToScreen(pinchStartCss, panStart.viewportRect, panStart.screenInfo);
  const pinchEnd = viewportPointToScreen(pinchEndCss, panStart.viewportRect, panStart.screenInfo);
  await call('/actions', 'POST', { actions: [
    { type: 'pointer', id: 'pinch-left', parameters: { pointerType: 'touch' }, actions: [
      { type: 'pointerMove', duration: 0, x: pinchStart.x, y: pinchStart.y }, { type: 'pointerDown', button: 0 },
      { type: 'pause', duration: 100 }, { type: 'pointerMove', duration: 450, x: pinchStart.x - 22, y: pinchStart.y }, { type: 'pointerUp', button: 0 },
    ] },
    { type: 'pointer', id: 'pinch-right', parameters: { pointerType: 'touch' }, actions: [
      { type: 'pointerMove', duration: 0, x: pinchEnd.x, y: pinchEnd.y }, { type: 'pointerDown', button: 0 },
      { type: 'pause', duration: 100 }, { type: 'pointerMove', duration: 450, x: pinchEnd.x + 22, y: pinchEnd.y }, { type: 'pointerUp', button: 0 },
    ] },
  ] }); await call('/actions', 'DELETE');
  const interruptedStart = viewportPointToScreen(center, panStart.viewportRect, panStart.screenInfo);
  await call('/actions', 'POST', { actions: [{ type: 'pointer', id: 'interrupted-pan', parameters: { pointerType: 'touch' }, actions: [
    { type: 'pointerMove', duration: 0, x: interruptedStart.x, y: interruptedStart.y },
    { type: 'pointerDown', button: 0 }, { type: 'pointerMove', duration: 150, x: interruptedStart.x - 18, y: interruptedStart.y + 12 },
  ] }] });
  await call('/actions', 'DELETE');
  await click('#tools button[aria-label="Selecionar"]');
  await touch(center.x, center.y);
  result.cameraGestures = { pan: { start, end }, pinch: { start: [pinchStart, pinchEnd], spread: 44 }, apiActionsBefore: beforeGestureActions, apiActionsAfter: (await execute('return window.__farmTestActions')).value };
  assert.equal(result.cameraGestures.apiActionsAfter, 0, 'camera and follow-up selection touch do not send farm actions');
  if ((await execute("return document.querySelector('#modal').open")).value) await click('#close-modal');
  await click('#zoom-out');
  assert.equal((await execute("return document.querySelector('#modal').open")).value, false, 'zoom control remains usable after gestures');
  await click('#more');
  assert.ok((await execute("return document.querySelector('#more-menu')?.getBoundingClientRect().width > 0")).value, 'navigation panel remains usable after interrupted gesture');
  await click('#more');

  const beforeZoom = await execute("return getComputedStyle(document.querySelector('#farm')).width");
  await click('#zoom-in');
  await waitFor(async () => (await execute("return getComputedStyle(document.querySelector('#farm')).width !== arguments[0]", [beforeZoom.value])).value, 'zoom control');
  await screenshot('farm-zoom');
  await click('#more');
  await screenshot('more-menu');
  await click('#shop');
  await waitFor(async () => (await execute("return document.querySelector('#modal').open && document.querySelector('#modal-title').textContent === 'Loja'")).value, 'shop panel');
  await screenshot('shop');
  const dialog = await execute("const r=document.querySelector('#modal').getBoundingClientRect(); return {top:r.top,bottom:r.bottom,height:innerHeight}");
  assert.ok(dialog.value.top >= 0 && dialog.value.bottom <= dialog.value.height + 1, `shop dialog fits viewport: ${JSON.stringify(dialog.value)}`);

  await call('/orientation', 'POST', { orientation: 'LANDSCAPE' });
  await waitFor(async () => (await execute('return innerWidth > innerHeight')).value, 'landscape orientation');
  const scrolling = await execute("const e=document.querySelector('.market-scroll'); return e.scrollHeight >= e.clientHeight");
  assert.equal(scrolling.value, true, 'shop content has a scroll container');
  const scrollHeight = await execute("return document.querySelector('.market-scroll').scrollHeight");
  const clientHeight = await execute("return document.querySelector('.market-scroll').clientHeight");
  assert.ok(scrollHeight.value > clientHeight.value, 'shop content overflows its scroll container');
  await execute("const e=document.querySelector('.market-scroll'); e.scrollTop=e.scrollHeight");
  await waitFor(async () => (await execute("return document.querySelector('.market-scroll').scrollTop > 0")).value, 'scrolling shop content');
  await execute("document.querySelector('.market-scroll').scrollTop=0");
  const landscapeDialog = await execute("const r=document.querySelector('#modal').getBoundingClientRect(); return {top:r.top,bottom:r.bottom,height:innerHeight}");
  assert.ok(landscapeDialog.value.top >= 0 && landscapeDialog.value.bottom <= landscapeDialog.value.height + 1, `landscape shop dialog fits viewport: ${JSON.stringify(landscapeDialog.value)}`);
  await screenshot('shop-landscape');
  await call('/orientation', 'POST', { orientation: 'PORTRAIT' });
  await waitFor(async () => (await execute('return innerHeight > innerWidth')).value, 'portrait orientation restored');

  const errorsBeforeReload = (await execute('return window.__iosTestErrors ?? []')).value;
  await call('/refresh', 'POST');
  await waitFor(async () => (await visible('#game')).value, 'farm after Safari reload');
  await execute("window.__iosTestErrors=[]; window.addEventListener('error', event=>window.__iosTestErrors.push(event.message));");
  await screenshot('farm-after-reload');
  result.javascriptErrors = [...errorsBeforeReload, ...(await execute('return window.__iosTestErrors ?? []')).value];
  assert.deepEqual(result.javascriptErrors, [], 'no unexpected JavaScript exceptions');
  console.log('Safari iOS Simulator flows passed.');
} catch (error) {
  result.error = error.message;
  if (sessionId) {
    try {
      const data = await request(`/session/${sessionId}/screenshot`);
      const path = resolve(artifacts, 'failure.png');
      await writeFile(path, Buffer.from(data, 'base64'));
      result.screenshots.push(path);
    } catch { /* Keep the original failure if Safari cannot capture a screenshot. */ }
  }
  throw error;
} finally {
  if (sessionId) try { await request(`/session/${sessionId}`, 'DELETE'); } catch { /* Keep session cleanup best-effort after test failure. */ }
  if (simulator) {
    try {
      const logs = simctl(['spawn', simulator.udid, 'log', 'show', '--last', '10m', '--style', 'compact']);
      await writeFile(resolve(artifacts, 'simulator.log'), logs);
    } catch { /* Simulator logs are supplementary when the runtime exits early. */ }
    if (startedSimulator) try { simctl(['shutdown', simulator.udid]); } catch { /* The runtime may already be stopped. */ }
  }
  await writeFile(resolve(artifacts, 'result.json'), JSON.stringify(result, null, 2));
}
