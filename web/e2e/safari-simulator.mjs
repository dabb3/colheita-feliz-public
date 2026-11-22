import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdir, writeFile } from 'node:fs/promises';
import { resolve } from 'node:path';

const appium = process.env.APPIUM_URL ?? 'http://127.0.0.1:4723';
const appUrl = process.env.E2E_APP_URL ?? 'http://127.0.0.1:5173';
const artifacts = resolve(process.env.SAFARI_ARTIFACT_DIR ?? 'test-results/safari-simulator');
await mkdir(artifacts, { recursive: true });

function simctl(args, options = {}) {
  return execFileSync('xcrun', ['simctl', ...args], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'], ...options }).trim();
}

function compatibleIosMajors() {
  const sdks = execFileSync('xcodebuild', ['-showsdks'], { encoding: 'utf8' });
  const majors = new Set([...sdks.matchAll(/-sdk iphonesimulator(\d+)/g)].map(match => Number(match[1])));
  if (!majors.size) throw new Error('The selected Xcode exposes no iOS Simulator SDK.');
  return majors;
}

function chooseSimulator() {
  const inventory = JSON.parse(simctl(['list', 'devices', 'available', '--json']));
  const supportedMajors = compatibleIosMajors();
  const candidates = Object.entries(inventory.devices).flatMap(([runtime, devices]) => {
    const version = runtime.match(/iOS-(\d+)-(\d+)/);
    if (!version || !supportedMajors.has(Number(version[1]))) return [];
    return devices.filter(device => device.name.startsWith('iPhone ')).map(device => ({
      ...device,
      runtime,
      version: `${version[1]}.${version[2]}`,
      versionKey: Number(version[1]) * 100 + Number(version[2]),
    }));
  }).sort((a, b) => b.versionKey - a.versionKey || a.name.localeCompare(b.name));
  if (!candidates.length) throw new Error(`No available iPhone Simulator matches the selected Xcode SDKs (iOS ${[...supportedMajors].join(', ')}).`);
  return candidates[0];
}

if (process.argv.includes('--list')) {
  const device = chooseSimulator();
  console.log(`Available Safari target: ${device.name} · iOS ${device.version} · ${device.udid}`);
  process.exit(0);
}

async function request(path, method = 'GET', body) {
  const response = await fetch(`${appium}${path}`, {
    method,
    headers: body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const result = await response.json();
  if (!response.ok || result.value?.error) throw new Error(`${method} ${path}: ${JSON.stringify(result.value ?? result)}`);
  return result.value ?? result;
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

  const plot = await execute("const c=document.querySelector('#farm').getBoundingClientRect(); return {x:Math.round(c.left+c.width*1010/1600),y:Math.round(c.top+c.height*580/900)}");
  await call('/actions', 'POST', { actions: [{ type: 'pointer', id: 'finger', parameters: { pointerType: 'touch' }, actions: [
    { type: 'pointerMove', duration: 0, x: plot.value.x, y: plot.value.y },
    { type: 'pointerDown', button: 0 },
    { type: 'pause', duration: 100 },
    { type: 'pointerUp', button: 0 },
  ] }] });
  await call('/actions', 'DELETE');
  await waitFor(async () => (await execute("return document.querySelector('#modal-title').textContent === 'Comprar terra' && document.querySelector('#modal').open")).value, 'touch opening the plot panel');
  await screenshot('plot-touch');
  await click('#close-modal');

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
