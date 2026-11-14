// Mobile portrait layout: farm camera (pinch/drag/zoom buttons) and the "Mais" menu.
// Desktop and landscape keep the original fixed 16:9 scene and the full menu bar.
const WORLD_WIDTH = 1600, WORLD_HEIGHT = 900;
const PLOTS = {left: 160, right: 1095, top: 364, bottom: 760};
const MAX_ZOOM = 2.5, TAP_SLOP = 10, CLICK_GUARD_MS = 450;

export interface Camera {zoom: number; centerX: number; centerY: number}
export interface CameraLayout {scale: number; width: number; height: number; x: number; y: number; camera: Camera}

/** Smallest scale that fills the viewport, so the farm never shows empty bands. */
export function coverScale(width: number, height: number) {
  return Math.max(width / WORLD_WIDTH, height / WORLD_HEIGHT);
}

/** Clamps zoom and pan so the canvas always covers the viewport. */
export function cameraLayout(width: number, height: number, camera: Camera): CameraLayout {
  const zoom = Math.min(MAX_ZOOM, Math.max(1, camera.zoom));
  const scale = coverScale(width, height) * zoom;
  const canvasWidth = WORLD_WIDTH * scale, canvasHeight = WORLD_HEIGHT * scale;
  const x = Math.min(0, Math.max(width - canvasWidth, width / 2 - camera.centerX * scale));
  const y = Math.min(0, Math.max(height - canvasHeight, height / 2 - camera.centerY * scale));
  return {
    scale, width: canvasWidth, height: canvasHeight, x, y,
    camera: {zoom, centerX: (width / 2 - x) / scale, centerY: (height / 2 - y) / scale},
  };
}

/** Initial framing: centered on the plots, zoomed in only when they would look small. */
export function initialCamera(width: number, height: number): Camera {
  const plotsScale = (width * 0.98) / (PLOTS.right - PLOTS.left);
  return {
    zoom: Math.max(1, plotsScale / coverScale(width, height)),
    centerX: (PLOTS.left + PLOTS.right) / 2,
    centerY: (PLOTS.top + PLOTS.bottom) / 2,
  };
}

/** Keeps the world point under `screen` fixed while changing the zoom. */
export function zoomAround(width: number, height: number, camera: Camera, zoom: number, screen: {x: number; y: number}): Camera {
  const before = cameraLayout(width, height, camera);
  const worldX = (screen.x - before.x) / before.scale, worldY = (screen.y - before.y) / before.scale;
  const scale = coverScale(width, height) * Math.min(MAX_ZOOM, Math.max(1, zoom));
  return {zoom, centerX: worldX + (width / 2 - screen.x) / scale, centerY: worldY + (height / 2 - screen.y) / scale};
}

/** Backing-store multiplier: sharper sprites when zoomed, capped for phone GPUs. */
export function canvasResolution(scale: number, devicePixelRatio: number) {
  return Math.min(2, Math.max(1, Math.ceil(scale * devicePixelRatio * 2) / 2));
}

function setupCamera(media: MediaQueryList) {
  const scene = document.getElementById('scene'), canvas = document.getElementById('farm') as HTMLCanvasElement | null;
  const controls = document.getElementById('camera-controls');
  if (!scene || !canvas || !controls) return;
  let camera: Camera | undefined, size = {width: 0, height: 0}, suppressUntil = 0, moved = false;
  const pointers = new Map<number, {x: number; y: number}>();
  let start: {x: number; y: number} | undefined, pinch: {distance: number; zoom: number} | undefined;

  const local = (event: PointerEvent | WheelEvent) => {
    const box = scene.getBoundingClientRect();
    return {x: event.clientX - box.left, y: event.clientY - box.top};
  };
  const apply = () => {
    if (!media.matches) return;
    const box = scene.getBoundingClientRect();
    if (box.width < 1 || box.height < 1) return;
    const resized = box.width !== size.width || box.height !== size.height;
    size = {width: box.width, height: box.height};
    camera ??= initialCamera(size.width, size.height);
    const layout = cameraLayout(size.width, size.height, camera);
    camera = layout.camera;
    const resolution = canvasResolution(layout.scale, window.devicePixelRatio || 1);
    if (canvas.width !== Math.round(WORLD_WIDTH * resolution)) {
      canvas.width = Math.round(WORLD_WIDTH * resolution);
      canvas.height = Math.round(WORLD_HEIGHT * resolution);
    }
    canvas.style.width = `${layout.width}px`;
    canvas.style.height = `${layout.height}px`;
    canvas.style.transform = `translate(${layout.x}px,${layout.y}px)`;
    (document.getElementById('zoom-in') as HTMLButtonElement).disabled = layout.camera.zoom >= MAX_ZOOM;
    (document.getElementById('zoom-out') as HTMLButtonElement).disabled = layout.camera.zoom <= 1;
    if (!resized) canvas.dispatchEvent(new Event('camerachange'));
  };
  const reset = () => {
    scene.classList.remove('camera');
    controls.hidden = true;
    canvas.style.removeProperty('width');
    canvas.style.removeProperty('height');
    canvas.style.removeProperty('transform');
    canvas.width = WORLD_WIDTH;
    canvas.height = WORLD_HEIGHT;
    camera = undefined;
    pointers.clear();
  };
  const sync = () => {
    if (!media.matches) {reset(); return;}
    scene.classList.add('camera');
    controls.hidden = false;
    apply();
  };
  const zoomBy = (factor: number, at?: {x: number; y: number}) => {
    if (!camera) return;
    camera = zoomAround(size.width, size.height, camera, camera.zoom * factor, at ?? {x: size.width / 2, y: size.height / 2});
    apply();
  };

  scene.addEventListener('pointerdown', event => {
    if (!media.matches || controls.contains(event.target as Node)) return;
    pointers.set(event.pointerId, local(event));
    if (pointers.size === 1) {moved = false; start = local(event); pinch = undefined;}
    if (pointers.size === 2 && camera) {
      const [a, b] = [...pointers.values()];
      pinch = {distance: Math.hypot(a.x - b.x, a.y - b.y) || 1, zoom: camera.zoom};
      moved = true;
    }
  });
  scene.addEventListener('pointermove', event => {
    const previous = pointers.get(event.pointerId);
    if (!media.matches || !previous || !camera) return;
    const point = local(event);
    pointers.set(event.pointerId, point);
    if (pointers.size >= 2 && pinch) {
      const [a, b] = [...pointers.values()];
      const middle = {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2};
      camera = zoomAround(size.width, size.height, camera, pinch.zoom * Math.hypot(a.x - b.x, a.y - b.y) / pinch.distance, middle);
      apply();
      return;
    }
    if (!moved && start && Math.hypot(point.x - start.x, point.y - start.y) < TAP_SLOP) return;
    moved = true;
    const scale = cameraLayout(size.width, size.height, camera).scale;
    camera = {...camera, centerX: camera.centerX - (point.x - previous.x) / scale, centerY: camera.centerY - (point.y - previous.y) / scale};
    apply();
  });
  const release = (event: PointerEvent) => {
    if (!pointers.delete(event.pointerId)) return;
    if (moved) suppressUntil = performance.now() + CLICK_GUARD_MS;
    if (pointers.size < 2) pinch = undefined;
    if (pointers.size === 0) start = undefined;
  };
  scene.addEventListener('pointerup', release);
  scene.addEventListener('pointercancel', release);
  // Navigation gestures never reach the farm: the click that ends a drag or pinch is swallowed.
  scene.addEventListener('click', event => {
    if (performance.now() < suppressUntil && !controls.contains(event.target as Node)) {
      event.stopPropagation();
      event.preventDefault();
    }
    suppressUntil = 0;
  }, {capture: true});
  scene.addEventListener('wheel', event => {
    if (!media.matches) return;
    event.preventDefault();
    zoomBy(event.deltaY < 0 ? 1.15 : 1 / 1.15, local(event));
  }, {passive: false});
  document.getElementById('zoom-in')?.addEventListener('click', () => zoomBy(1.25));
  document.getElementById('zoom-out')?.addEventListener('click', () => zoomBy(1 / 1.25));
  new ResizeObserver(apply).observe(scene);
  media.addEventListener('change', sync);
  sync();
}

function setupMoreMenu(media: MediaQueryList) {
  const more = document.getElementById('more') as HTMLButtonElement | null, menu = document.getElementById('more-menu');
  if (!more || !menu) return;
  const moved = ['notifications', 'friends', 'settings'].map(id => document.getElementById(id)).filter((node): node is HTMLElement => !!node);
  // Placeholders keep the desktop order when the buttons return to the bar.
  const anchors = moved.map(node => {const anchor = document.createComment(node.id); node.before(anchor); return anchor;});
  const close = (focus = false) => {
    menu.hidden = true;
    more.setAttribute('aria-expanded', 'false');
    if (focus) more.focus();
  };
  const sync = () => {
    if (media.matches) menu.append(...moved);
    else {moved.forEach((node, index) => anchors[index].after(node)); close();}
  };
  more.addEventListener('click', () => {
    const open = menu.hidden;
    menu.hidden = !open;
    more.setAttribute('aria-expanded', String(open));
    if (open) (menu.querySelector('button:not([hidden])') as HTMLButtonElement | null)?.focus();
  });
  menu.addEventListener('click', event => {if ((event.target as HTMLElement).closest('button')) close();});
  menu.addEventListener('keydown', event => {if (event.key === 'Escape') {event.stopPropagation(); close(true);}});
  document.addEventListener('pointerdown', event => {
    if (!menu.hidden && !menu.contains(event.target as Node) && !more.contains(event.target as Node)) close();
  });
  media.addEventListener('change', sync);
  sync();
}

export function setupMobileLayout() {
  if (typeof window.matchMedia !== 'function') return;
  const portrait = window.matchMedia('(max-width:700px) and (orientation:portrait)');
  setupMoreMenu(portrait);
  setupCamera(portrait);
}

if (typeof window !== 'undefined' && typeof document !== 'undefined') setupMobileLayout();
