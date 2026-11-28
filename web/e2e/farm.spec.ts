import {test, expect, type Page} from '@playwright/test';
import {readFileSync} from 'node:fs';

const data = (name: string) => JSON.parse(readFileSync(new URL(`../../assets/data/${name}.json`, import.meta.url), 'utf8'));

// Exercise the built client and real assets without production credentials or services.
async function farmApi(page: Page) {
  const farm = {
    owner: 'alice', name: 'Alice', own: true, revision: '1', serverTime: '2026-11-21T15:00:00Z',
    coins: '500', greenCoins: '5', level: 1, currentXp: '0', xpToNext: '25', avatarSelected: true,
    seedCounts: {nabo: 2}, fertilizers: {}, feed: {}, animals: [], missions: [],
    activeBackgroundId: 'inverno', ownedBackgroundIds: ['default', 'inverno', 'outono'],
    tiles: [{column: 0, row: 0, unlocked: true, state: 'GRASS', stage: 0, season: 1, seasons: 1,
      watered: false, dry: false, paused: false, weeds: false, insects: 0, fertilized: false,
      harvestRevision: '1', remainingSeconds: 0, nominalRemainingSeconds: 0}],
  };
  const bodies: string[] = [], receipts = new Map();
  let ids = 0, loseNextResponse = false;
  await page.route('http://127.0.0.1:8080/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    const json = (body: unknown) => route.fulfill({json: body});
    if (path === '/health') return json({development: true});
    if (path === '/v1/catalog') return json({crops: data('crops'), animals: data('animals'),
      fertilizers: data('fertilizers'), feedPrices: {producer: 120, growth: 80}, vip: {greenPrice: 100}});
    if (path === '/v1/request-id') return json({requestId: `browser-receipt-${++ids}`});
    if (path === '/v1/me/farm') return json(farm);
    if (path === '/v1/me/actions') {
      const body = request.postData()!; bodies.push(body);
      const command = JSON.parse(body);
      expect(request.headers().authorization).toBe('Bearer dev-alice');
      if (receipts.has(command.requestId)) return json(receipts.get(command.requestId));
      expect(command.expectedRevision).toBe(farm.revision);
      const tile = farm.tiles[0];
      if (command.action === 'PLOW') { expect(tile.state).toBe('GRASS'); tile.state = 'PLOWED'; }
      else if (command.action === 'PLANT') {
        expect(tile.state).toBe('PLOWED'); expect(command.itemId).toBe('nabo');
        Object.assign(tile, {state: 'GROWING', cropId: 'nabo', stage: 1}); farm.seedCounts.nabo--;
      } else if (command.action === 'WATER') { expect(tile.state).toBe('GROWING'); tile.watered = true; }
      else if (command.action === 'EQUIP_BACKGROUND') farm.activeBackgroundId = command.itemId;
      else throw new Error(`Unexpected command: ${command.action}`);
      farm.revision = String(Number(farm.revision) + 1);
      const reply = structuredClone({farm, result: {success: true, message: 'Ação concluída.'}});
      receipts.set(command.requestId, reply);
      if (loseNextResponse) { loseNextResponse = false; return route.abort('failed'); }
      return json(reply);
    }
    // Push capabilities must not contact any real service during browser tests.
    if (path.startsWith('/v1/push/')) return route.fulfill({status: 503, json: {error: 'Disabled in browser tests'}});
    throw new Error(`Unexpected API request: ${path}`);
  });
  return {farm, bodies, loseResponse() { loseNextResponse = true; }};
}

async function login(page: Page) {
  await page.goto('/', {waitUntil: 'domcontentloaded'});
  await page.getByRole('button', {name: 'Testar como Alice'}).click();
  await expect(page.locator('#game')).toBeVisible();
  await expect(page.locator('#wallet')).toContainText('Alice');
}

async function touchPlot(page: Page) {
  const point = await page.locator('#farm').evaluate(canvas => {
    const rect = canvas.getBoundingClientRect();
    return {x: rect.left + 585 * rect.width / 1600, y: rect.top + 400 * rect.height / 900};
  });
  await page.touchscreen.tap(point.x, point.y);
}

test('development login opens the actual farm without a pending action', async ({page}) => {
  await farmApi(page); await login(page);
  await expect(page.locator('#tools').getByRole('button', {name: 'Selecionar', exact: true})).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('#pending')).toBeHidden();
});

test('mobile touch can plow, plant and water the same plot', async ({browser}) => {
  const context = await browser.newContext({viewport: {width: 390, height: 844}, hasTouch: true, isMobile: true});
  try {
    const page = await context.newPage(), api = await farmApi(page); await login(page);
    await page.getByRole('button', {name: 'Arar / limpar', exact: true}).tap(); await touchPlot(page);
    await expect.poll(() => api.farm.revision).toBe('2');
    await expect(page.locator('#pending')).toBeHidden();
    await page.getByRole('button', {name: 'Plantar', exact: true}).tap();
    await page.getByRole('button', {name: 'Nabo, 2 sementes', exact: true}).tap(); await touchPlot(page);
    await expect.poll(() => api.farm.revision).toBe('3');
    await expect(page.locator('#pending')).toBeHidden();
    await page.getByRole('button', {name: 'Regar', exact: true}).tap(); await touchPlot(page);
    await expect.poll(() => api.farm.revision).toBe('4');
    await expect(page.locator('#pending')).toBeHidden();
    expect(api.farm.tiles[0].watered).toBe(true);
    expect(api.bodies.map(body => JSON.parse(body).action)).toEqual(['PLOW', 'PLANT', 'WATER']);
    await page.screenshot({path: test.info().outputPath('mobile-watered.png')});
  } finally { await context.close(); }
});

test('lost action response survives reload and replays the same receipt once', async ({page}, testInfo) => {
  const api = await farmApi(page); await login(page); api.loseResponse();
  await page.getByRole('button', {name: 'Arar / limpar', exact: true}).click();
  if (testInfo.project.name === 'webkit-iphone' || testInfo.project.name.startsWith('webkit-mobile-')) await touchPlot(page);
  else {
    const canvas = page.locator('#farm');
    await canvas.click({position: await canvas.evaluate(node => ({x: 585 * node.clientWidth / 1600, y: 400 * node.clientHeight / 900}))});
  }
  await expect(page.locator('#pending')).toBeVisible();
  expect(api.farm.revision).toBe('2');
  await page.reload(); await expect(page.locator('#game')).toBeVisible();
  await page.getByRole('button', {name: 'Confirmar ação pendente'}).click();
  await expect(page.locator('#pending')).toBeHidden();
  expect(api.bodies).toHaveLength(2); expect(api.bodies[1]).toBe(api.bodies[0]);
  expect(api.farm.revision).toBe('2');
});

test('period and pack changes retain the previous background until the fallback loads', async ({page}, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium-desktop', 'This background animation check uses the desktop toolbar layout.');
  await page.clock.install({time: new Date('2026-11-21T15:00:00Z')});
  const api = await farmApi(page);
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/assets/classic/background/*', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('background-sunset.png') || path.endsWith('background-inverno-tarde.png')) { await gate; await route.continue(); }
    else if (path.endsWith('background-outono-tarde.png')) await route.abort('failed');
    else await route.continue();
  });
  try {
    const winterLoaded = page.waitForResponse('**/backgound-inverno.png');
    await login(page); await (await winterLoaded).finished();
    const pixel = () => page.locator('#farm').evaluate((canvas: HTMLCanvasElement) =>
      Array.from(canvas.getContext('2d')!.getImageData(10, 10, 1, 1).data));
    await expect.poll(pixel).not.toEqual([132, 186, 48, 255]);
    // Wait for the equipped winter art, not the classic image that may arrive first.
    await page.evaluate(() => new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
    const previous = await pixel();
    const requested = page.waitForRequest('**/background-sunset.png');
    await page.clock.setSystemTime(new Date('2026-11-21T20:00:00Z')); await requested;
    await page.locator('#decorations').click(); await page.getByRole('button', {name: 'Próximo fundo'}).click();
    await page.getByRole('button', {name: 'Aplicar', exact: true}).click();
    await expect.poll(() => api.farm.activeBackgroundId).toBe('outono');
    await page.getByRole('button', {name: 'Fechar', exact: true}).click();
    expect(await pixel()).toEqual(previous);
    await page.screenshot({path: test.info().outputPath('background-loading.png')});
    release(); await expect.poll(pixel).not.toEqual(previous);
  } finally { release(); }
});


test('first entry uses a quiet placeholder until any background is ready', async ({page}, testInfo) => {
  test.skip(testInfo.project.name !== 'chromium-desktop', 'This background loading check is covered in the desktop Chromium project.');
  await page.clock.install({time: new Date('2026-11-21T15:00:00Z')});
  const api = await farmApi(page);
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/assets/classic/background/*', async route => { await gate; await route.continue(); });
  try {
    await login(page);
    const pixel = () => page.locator('#farm').evaluate((canvas: HTMLCanvasElement) =>
      Array.from(canvas.getContext('2d')!.getImageData(10, 10, 1, 1).data));
    expect(await pixel()).toEqual([231, 227, 216, 255]);
    release(); await expect.poll(pixel).not.toEqual([231, 227, 216, 255]);
  } finally { release(); }
});

test('deterministic mobile visual baselines cover farm, zoom, shop, deposit and plant popup', async ({page}, testInfo) => {
  test.skip(testInfo.project.name !== 'webkit-mobile-large', 'Visual references use the pinned large mobile WebKit profile.');
  await page.clock.install({time: new Date('2026-11-21T15:00:00Z')});
  const api = await farmApi(page);
  await login(page);
  await page.evaluate(async () => {
    await document.fonts.ready;
    await Promise.all([...document.images].map(image => image.decode().catch(() => undefined)));
    await new Promise<void>(resolve => requestAnimationFrame(() => requestAnimationFrame(() => resolve())));
  });
  await page.clock.pauseAt(new Date('2026-11-21T15:00:10Z'));
  // Permit tiny cross-run rasterization noise while keeping the per-screen diff below 0.03%.
  const screenshot = {animations: 'disabled' as const, threshold: 0.1, maxDiffPixels: 100};
  await expect(page).toHaveScreenshot('farm-mobile-large.png', screenshot);
  await page.getByRole('button', {name: 'Aproximar'}).tap();
  await expect(page).toHaveScreenshot('farm-zoom-mobile-large.png', screenshot);
  await page.locator('#more').tap();
  await expect(page).toHaveScreenshot('more-mobile-large.png', screenshot);
  await page.locator('#shop').tap();
  await expect(page.locator('#modal-title')).toHaveText('Loja');
  await expect(page).toHaveScreenshot('shop-mobile-large.png', screenshot);
  await page.locator('#close-modal').tap();
  await page.locator('#deposit').tap();
  await expect(page.locator('#modal-title')).toHaveText('Depósito');
  await expect(page).toHaveScreenshot('deposit-mobile-large.png', screenshot);
  await page.locator('#close-modal').tap();
  await expect(page.locator('#modal')).toHaveJSProperty('open', false);
  Object.assign(api.farm.tiles[0], {state: 'GROWING', cropId: 'nabo', stage: 1, watered: true,
    remainingSeconds: 900, nominalRemainingSeconds: 900});
  await page.reload(); await expect(page.locator('#game')).toBeVisible();
  await page.clock.runFor(100);
  await page.waitForLoadState('networkidle');
  await page.evaluate(() => { window.scrollTo(0, 0); document.querySelector('#game')?.scrollTo(0, 0); });
  await page.getByRole('button', {name: 'Selecionar', exact: true}).tap();
  await touchPlot(page);
  await expect(page.locator('#plant-health')).toBeVisible();
  await expect(page).toHaveScreenshot('plant-popup-mobile-large.png', screenshot);
});
