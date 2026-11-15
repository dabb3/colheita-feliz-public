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

/** World point currently shown at `screen`. */
export function worldAt(width: number, height: number, camera: Camera, screen: {x: number; y: number}) {
  const layout = cameraLayout(width, height, camera);
  return {x: (screen.x - layout.x) / layout.scale, y: (screen.y - layout.y) / layout.scale};
}

/** Camera that shows `world` at `screen` with the given zoom. */
export function anchorCamera(width: number, height: number, world: {x: number; y: number}, zoom: number, screen: {x: number; y: number}): Camera {
  const scale = coverScale(width, height) * Math.min(MAX_ZOOM, Math.max(1, zoom));
  return {zoom, centerX: world.x + (width / 2 - screen.x) / scale, centerY: world.y + (height / 2 - screen.y) / scale};
}

/** Keeps the world point under `screen` fixed while changing the zoom. */
export function zoomAround(width: number, height: number, camera: Camera, zoom: number, screen: {x: number; y: number}): Camera {
  return anchorCamera(width, height, worldAt(width, height, camera, screen), zoom, screen);
}

/** Backing-store multiplier: sharper sprites when zoomed, capped for phone GPUs. */
export function canvasResolution(scale: number, devicePixelRatio: number) {
  return Math.min(2, Math.max(1, Math.ceil(scale * devicePixelRatio * 2) / 2));
}

let resyncCamera = () => {};
/** Restores the camera after something else reset the canvas (e.g. a new Scene after login). */
export function syncMobileCamera() {
  resyncCamera();
}

export interface CameraElements {
  scene: HTMLElement; canvas: HTMLCanvasElement; controls: HTMLElement;
  zoomIn: HTMLButtonElement; zoomOut: HTMLButtonElement;
  /** Receives pointer releases outside the scene (the window in the browser). */
  root: EventTarget;
}

export function setupCamera(media: MediaQueryList, {scene, canvas, controls, zoomIn, zoomOut, root}: CameraElements) {
  let camera: Camera | undefined, size = {width: 0, height: 0}, suppressUntil = 0, moved = false;
  const pointers = new Map<number, {x: number; y: number}>();
  let start: {x: number; y: number} | undefined;
  let pinch: {distance: number; zoom: number; anchor: {x: number; y: number}} | undefined;
  const middle = () => {
    const [a, b] = [...pointers.values()];
    return {x: (a.x + b.x) / 2, y: (a.y + b.y) / 2, distance: Math.hypot(a.x - b.x, a.y - b.y)};
  };

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
    const resolution = canvasResolution(layout.scale, globalThis.devicePixelRatio || 1);
    if (canvas.width !== Math.round(WORLD_WIDTH * resolution)) {
      canvas.width = Math.round(WORLD_WIDTH * resolution);
      canvas.height = Math.round(WORLD_HEIGHT * resolution);
    }
    canvas.style.width = `${layout.width}px`;
    canvas.style.height = `${layout.height}px`;
    canvas.style.transform = `translate(${layout.x}px,${layout.y}px)`;
    zoomIn.disabled = layout.camera.zoom >= MAX_ZOOM;
    zoomOut.disabled = layout.camera.zoom <= 1;
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
      const {x, y, distance} = middle();
      // The world point under the initial finger midpoint follows the fingers: pinch zooms and pans together.
      pinch = {distance: distance || 1, zoom: camera.zoom, anchor: worldAt(size.width, size.height, camera, {x, y})};
      moved = true;
    }
  });
  scene.addEventListener('pointermove', event => {
    const previous = pointers.get(event.pointerId);
    if (!media.matches || !previous || !camera) return;
    const point = local(event);
    pointers.set(event.pointerId, point);
    if (pointers.size >= 2 && pinch) {
      const {x, y, distance} = middle();
      camera = anchorCamera(size.width, size.height, pinch.anchor, pinch.zoom * distance / pinch.distance, {x, y});
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
  // Released outside the scene (mouse or pen) still frees the pointer; the guard above swallows any click.
  root.addEventListener('pointerup', release as EventListener);
  root.addEventListener('pointercancel', release as EventListener);
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
  zoomIn.addEventListener('click', () => zoomBy(1.25));
  zoomOut.addEventListener('click', () => zoomBy(1 / 1.25));
  new ResizeObserver(apply).observe(scene);
  media.addEventListener('change', sync);
  resyncCamera = apply;
  sync();
}

function setupMoreMenu(media: MediaQueryList) {
  const more = document.getElementById('more') as HTMLButtonElement | null, menu = document.getElementById('more-menu');
  if (!more || !menu) return;
  const moved = ['notifications', 'friends', 'settings', 'calendar'].map(id => document.getElementById(id)).filter((node): node is HTMLElement => !!node);
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
  const escape = (event: KeyboardEvent) => {if (event.key === 'Escape' && !menu.hidden) {event.stopPropagation(); close(true);}};
  menu.addEventListener('keydown', escape);
  more.addEventListener('keydown', escape);
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
  const element = (id: string) => document.getElementById(id);
  const [scene, canvas, controls, zoomIn, zoomOut] = ['scene', 'farm', 'camera-controls', 'zoom-in', 'zoom-out'].map(element);
  if (scene && canvas && controls && zoomIn && zoomOut) {
    setupCamera(portrait, {
      scene, controls, canvas: canvas as HTMLCanvasElement,
      zoomIn: zoomIn as HTMLButtonElement, zoomOut: zoomOut as HTMLButtonElement, root: window,
    });
  }
}

if (typeof window !== 'undefined' && typeof document !== 'undefined') setupMobileLayout();
