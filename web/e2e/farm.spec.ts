import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  page.on('pageerror', error => test.info().annotations.push({ type: 'pageerror', description: error.message }));
  await page.goto('/');
  await page.getByRole('button', { name: 'Testar como Alice' }).click();
  await expect(page.locator('#game')).toBeVisible();
  await expect(page.locator('#farm')).toBeVisible();
});

test.afterEach(async () => {
  const errors = test.info().annotations.filter(annotation => annotation.type === 'pageerror');
  expect(errors, 'unexpected browser exceptions').toEqual([]);
});

test('login opens the synthetic farm and loads a bounded canvas', async ({ page }, testInfo) => {
  const canvas = page.locator('#farm');
  if (testInfo.project.name === 'webkit-iphone') {
    await expect(page.locator('#camera-controls')).toBeVisible();
    await expect.poll(() => canvas.evaluate(node => node.width)).toBeLessThanOrEqual(3200);
    await expect.poll(() => canvas.evaluate(node => node.height)).toBeLessThanOrEqual(1800);
  } else {
    await expect(canvas).toHaveJSProperty('width', 1600);
    await expect(canvas).toHaveJSProperty('height', 900);
    await expect(page.locator('#camera-controls')).toBeHidden();
  }
});

test('mobile touch selects a locked plot and opens the purchase panel', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'webkit-iphone', 'Touch profile runs in the iPhone project.');
  const canvas = page.locator('#farm');
  const bounds = await canvas.boundingBox();
  expect(bounds).not.toBeNull();
  await page.touchscreen.tap(bounds!.x + bounds!.width * 1010 / 1600, bounds!.y + bounds!.height * 580 / 900);
  await expect(page.locator('#modal')).toHaveJSProperty('open', true);
  await expect(page.locator('#modal-title')).toHaveText('Comprar terra');
});

test('dragging the camera does not activate a plot and a later touch still works', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'webkit-iphone', 'Touch profile runs in the iPhone project.');
  await page.addInitScript(() => {
    const original = window.fetch.bind(window);
    (window as typeof window & { farmActions: number }).farmActions = 0;
    window.fetch = (input, init) => {
      if (new URL(typeof input === 'string' ? input : input instanceof Request ? input.url : input.href).pathname === '/v1/me/actions' && init?.method === 'POST') {
        (window as typeof window & { farmActions: number }).farmActions++;
        return Promise.resolve(new Response(JSON.stringify({ error: 'Blocked by E2E safety guard' }), { status: 409, headers: { 'Content-Type': 'application/json' } }));
      }
      return original(input, init);
    };
  });
  await page.reload();
  await expect(page.locator('#game')).toBeVisible();
  await page.getByRole('button', { name: 'Arar / limpar' }).click();
  const canvas = page.locator('#farm');
  const bounds = await canvas.boundingBox();
  expect(bounds).not.toBeNull();
  const start = { x: bounds!.x + bounds!.width * 585 / 1600, y: bounds!.y + bounds!.height * 400 / 900 };
  const end = { x: bounds!.x + bounds!.width * 670 / 1600, y: bounds!.y + bounds!.height * 436 / 900 };
  await page.mouse.move(start.x, start.y);
  await page.mouse.down();
  await page.mouse.move(end.x, end.y, { steps: 8 });
  await page.mouse.up();
  expect(await page.evaluate(() => (window as typeof window & { farmActions: number }).farmActions)).toBe(0);
  await page.getByRole('button', { name: 'Selecionar' }).click();
  await page.touchscreen.tap(bounds!.x + bounds!.width * 1010 / 1600, bounds!.y + bounds!.height * 580 / 900);
  await expect(page.locator('#modal-title')).toHaveText('Comprar terra');
});

test('mobile zoom changes the canvas scale without hiding controls', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'webkit-iphone', 'Touch profile runs in the iPhone project.');
  const canvas = page.locator('#farm');
  const before = await canvas.evaluate(node => getComputedStyle(node).width);
  await page.getByRole('button', { name: 'Aproximar' }).click();
  await expect.poll(() => canvas.evaluate(node => getComputedStyle(node).width)).not.toBe(before);
  await expect(page.getByRole('button', { name: 'Afastar' })).toBeVisible();
});

test('shop remains visible and scrollable in portrait and landscape', async ({ page }) => {
  for (const viewport of [{ width: 390, height: 844 }, { width: 844, height: 390 }]) {
    await page.setViewportSize(viewport);
    await expect.poll(() => page.evaluate(() => ({ width: window.visualViewport?.width, height: window.visualViewport?.height }))).toEqual(viewport);
    if (viewport.width < viewport.height) {
      await page.locator('#more').click();
      await page.locator('#shop').click();
    } else {
      await page.locator('#shop').click();
    }
    await expect(page.locator('#modal')).toHaveJSProperty('open', true);
    await expect(page.locator('#modal-title')).toHaveText('Loja');
    const dialog = await page.locator('#modal').boundingBox();
    expect(dialog).not.toBeNull();
    expect(dialog!.y).toBeGreaterThanOrEqual(0);
    expect(dialog!.y + dialog!.height).toBeLessThanOrEqual(viewport.height + 1);
    const content = page.locator('#modal-content');
    await expect(content).toBeVisible();
    const scrollHeight = await content.evaluate(node => node.scrollHeight);
    const clientHeight = await content.evaluate(node => node.clientHeight);
    expect(scrollHeight).toBeGreaterThan(clientHeight);
    await content.evaluate(node => { node.scrollTop = node.scrollHeight; });
    await expect.poll(() => content.evaluate(node => node.scrollTop)).toBeGreaterThan(0);
    await page.locator('#close-modal').click();
  }
});

test('critical login, farm, zoom, shop and deposit views produce review screenshots', async ({ page }, testInfo) => {
  await page.goto('/');
  await page.screenshot({ path: testInfo.outputPath('login.png'), animations: 'disabled' });
  await page.getByRole('button', { name: 'Testar como Alice' }).click();
  await expect(page.locator('#game')).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath('farm.png'), animations: 'disabled' });
  if (testInfo.project.name === 'webkit-iphone') {
    await page.getByRole('button', { name: 'Aproximar' }).click();
    await page.screenshot({ path: testInfo.outputPath('farm-zoom.png'), animations: 'disabled' });
    await page.locator('#more').click();
    await page.screenshot({ path: testInfo.outputPath('more.png'), animations: 'disabled' });
    await page.locator('#shop').click();
  } else {
    await page.locator('#shop').click();
  }
  await expect(page.locator('#modal-title')).toHaveText('Loja');
  await page.screenshot({ path: testInfo.outputPath('shop.png'), animations: 'disabled' });
  await page.locator('#close-modal').click();
  await page.locator('#deposit').click();
  await expect(page.locator('#modal-title')).toHaveText('Depósito');
  await page.screenshot({ path: testInfo.outputPath('deposit.png'), animations: 'disabled' });
});
