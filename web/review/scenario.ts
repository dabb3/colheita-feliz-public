import { cameraLayout } from "../src/mobile.ts";
import {renderEvents} from '../src/events.ts';
import {renderNotifications} from '../src/notifications.ts';
import { Scene } from "../src/scene.ts";
import { GameAudio } from "../src/audio.ts";
import { animalLayout, tileCenter } from "../src/presentation.ts";
import { renderWallet, renderMarket } from "../src/ui.ts";
import {
  renderDeposit,
  renderPlantHealth,
  renderAnimal,
  renderMissions,
  renderFriends,
  renderAudioSettings,
} from "../src/ui-panels.ts";
import type { Farm, Catalog, Tile } from "../src/types.ts";
import crops from "../../assets/data/crops.json";
import animals from "../../assets/data/animals.json";
import fertilizers from "../../assets/data/fertilizers.json";

// Isolated presentation fixtures: no authentication, network API, save or economic command.
const prices = (values: Record<string, number | undefined>) =>
  Object.fromEntries(
    Object.entries(values)
      .filter(([, value]) => value !== undefined)
      .map(([key, value]) => [key, value!]),
  );
const catalog: Catalog = {
  crops: crops.map((crop) => ({
    ...crop,
    acquisitionSource: crop.acquisitionSource === "LOGIN_REWARD" ? "LOGIN_REWARD" as const : "SHOP" as const,
    seedPrices: prices(crop.seedPrices),
  })),
  animals: animals.map((animal) => ({
    ...animal,
    prices: prices(animal.prices),
  })),
  fertilizers: fertilizers.map((f) => ({ ...f, prices: prices(f.prices) })),
  feedPrices: { producer: 120, growth: 80 },
  vip: { greenPrice: 0 },
};
const params = new URLSearchParams(location.search),
  period = params.get("period") ?? "day";
const stage = params.get("stage") ?? "ADULT";
const canvas = document.getElementById("farm") as HTMLCanvasElement;
const audio = new GameAudio();
audio.setActive(true);
const scene = new Scene(
  canvas,
  catalog,
  (tile) => {
    document.getElementById("notice")!.textContent =
      `Terra ${tile.column + 1}, ${tile.row + 1}`;
  },
  animalPanel,
  health,
);
const tiles = Array.from({ length: 30 }, (_, i) => {
  const row = Math.floor(i / 6),
    column = i % 6,
    stage = row + 1,
    crop = crops[column];
  return {
    column,
    row,
    unlocked: column < 5,
    state: column === 4 && !params.has("pests") ? "DEAD" : stage === 5 ? "READY" : "GROWING",
    cropId: column < 5 ? crop.id : undefined,
    stage,
    season: 1,
    seasons: crop.seasons,
    watered: true,
    dry: column === 1,
    paused: false,
    weeds: params.has("pests") ? column === 0 || column === 4 : column === 2,
    insects: params.has("pests") ? (column === 4 ? 3 : column >= 1 && column <= 3 ? column : 0) : column === 3 ? (row % 3) + 1 : 0,
    fertilized: false,
    protected: false,
    harvestRevision: "1",
    remainingSeconds: ((crop.growthSeconds * (5 - stage)) / 4) * 1.6,
    nominalRemainingSeconds: (crop.growthSeconds * (5 - stage)) / 4,
  };
});
const farm = {
  owner: "review",
  name: "Fazenda de demonstração",
  level: 2,
  currentXp: "8",
  xpToNext: "28",
  coins: "500",
  greenCoins: "5",
  revision: "1",
  serverTime: new Date().toISOString(),
  own: true,
  tiles,
  seedCounts: { milho: 3, nabo: 1 },
  feed: { producer: 2, growth: 1 },
  fertilizers: { comum: 4 },
  vipActive: false,
  produceLots: [
    { itemId: "milho", currency: "GOLD", quantity: 2 },
    { itemId: "milho", currency: "GREEN", quantity: 1 },
    {
      itemId: crops.find((c) => c.vipOnly)!.id,
      currency: "GREEN",
      quantity: 1,
      vipOnly: true,
    },
  ],
  depositTotals: { GOLD: 160, GREEN: 20 },
  missions: [
    {
      id: "plant",
      title: "Cultivar todos os dias",
      green: 1,
      progress: 1,
      target: 1,
      done: true,
    },
    {
      id: "collect",
      title: "Recolher a produção",
      green: 1,
      progress: 0,
      target: 1,
      done: false,
    },
  ],
  animals: Object.keys(animalLayout).map((id) => ({
    id,
    stage,
    status: params.get("hungry") === "1" ? "HUNGRY" :
      id === "abelha" ||
      catalog.animals.find((a) => a.id === id)?.kind === "PROTECTOR" ||
      stage !== "ADULT"
        ? "PRODUCING"
        : "READY",
    remainingSeconds: 0,
  })),
  nextPlot: { column: 5, row: 0 },
} as Farm;
scene.update(farm);
if (params.has("pests") && params.has("camera")) {
  const viewport = document.getElementById("scene")!;
  const width = window.innerWidth, height = window.innerHeight;
  const layout = cameraLayout(width, height, { zoom: 1.4, centerX: params.get("camera") === "right" ? 830 : 600, centerY: 555 });
  viewport.style.cssText = `position:fixed;inset:0;width:${width}px;height:${height}px;overflow:hidden;z-index:100`;
  canvas.style.cssText = `position:absolute;transform:none;max-width:none;width:${layout.width}px;height:${layout.height}px;left:${layout.x}px;top:${layout.y}px`;
}
renderWallet(document.getElementById("wallet")!, farm);

const modal = document.getElementById("modal") as HTMLDialogElement;
function openPanel(title: string, draw: (root: HTMLElement) => void) {
  document.getElementById("modal-title")!.textContent = title;
  document.getElementById("plant-health")!.hidden = true;
  const content = document.getElementById("modal-content")!;
  content.replaceChildren();
  draw(content);
  if (!modal.open) modal.showModal();
}
function demonstration() {
  document.getElementById("notice")!.textContent =
    "Demonstração: nenhuma ação foi enviada.";
}
function animalPanel(id: string) {
  openPanel("Animal", (root) =>
    renderAnimal(root, catalog, farm, id, true, demonstration),
  );
}
function deposit(category = "products") {
  openPanel("Depósito", (root) =>
    renderDeposit(root, catalog, farm, category, deposit, demonstration),
  );
}
function health(tile: Tile | null) {
  const panel = document.getElementById("plant-health")!;
  renderPlantHealth(panel, catalog, modal.open ? null : tile, farm.serverTime);
  if (tile) {
    const p = tileCenter(tile.column, tile.row);
    panel.style.setProperty(
      "--health-x",
      `${Math.max(12, Math.min(88, p.x / 16))}%`,
    );
    panel.style.setProperty("--health-y", `${(p.y - 45) / 9}%`);
  }
}
function market(category = "crops", seedGroup: "common" | "vip" = "common") {
  document.getElementById("modal-title")!.textContent = "Loja";
  document.getElementById("plant-health")!.hidden = true;
  const content = document.getElementById("modal-content")!;
  content.replaceChildren();
  const tabs = document.createElement("div");
  tabs.className = "tabs";
  content.append(tabs);
  for (const [id, label] of [
    ["crops", "Sementes"],
    ["animals", "Animais"],
    ["fertilizers", "Fertilizantes"],
  ]) {
    const button = document.createElement("button");
    button.textContent = label;
    button.setAttribute("aria-pressed", String(id === category));
    button.onclick = () => market(id);
    tabs.append(button);
  }
  const body = document.createElement("div");
  content.append(body);
  renderMarket(
    body,
    catalog,
    farm,
    category,
    seedGroup,
    (next) => market(category, next),
    () => {
      document.getElementById("notice")!.textContent =
        "Demonstração: nenhuma compra foi enviada.";
    },
  );
  if (!modal.open) modal.showModal();
}
document.getElementById("shop")!.onclick = () => market();
document.getElementById("deposit")!.onclick = () => deposit();
document.getElementById("missions")!.onclick = () =>
  openPanel("Tarefas diárias", (root) => renderMissions(root, farm));
document.getElementById("friends")!.onclick = () =>
  openPanel("Amigos — demonstração", (root) =>
    renderFriends(
      root,
      [
        { id: "review-bob", name: "Fazenda do amigo",avatar:{id:"female-2"} },
        { id: "review-ana", name: "Fazenda da amiga" },
      ],
      demonstration,
    ),
  );
document.getElementById("settings")!.onclick = () =>
  openPanel("Ajustes de áudio", (root) =>
    renderAudioSettings(root, audio.settings, (channel, enabled, volume) => {
      audio.unlock();
      if (channel === "music") audio.setMusic(enabled);
      else audio.setEffects(enabled);
      audio.setVolume(channel, volume);
    }),
  );
document.addEventListener("pointerdown", () => audio.unlock(), { once: true });
document.addEventListener("keydown", () => audio.unlock(), { once: true });
document.addEventListener("visibilitychange", () =>
  audio.setActive(!document.hidden),
);
document.querySelectorAll<HTMLButtonElement>("[data-effect]").forEach(
  (button) =>
    (button.onclick = () => {
      const action = button.dataset.effect!;
      audio.unlock();
      audio.play(action);
      demonstration();
    }),
);
document.getElementById("close-modal")!.onclick = () => modal.close();
document
  .querySelectorAll<HTMLButtonElement>("[data-tool]")
  .forEach(
    (button) =>
      (button.onclick = () =>
        button.setAttribute(
          "aria-pressed",
          String(button.getAttribute("aria-pressed") !== "true"),
        )),
  );
window.addEventListener(
  "pagehide",
  () => {
    scene.close();
    audio.close();
  },
  { once: true },
);

if(params.get('panel')==='fertilizers')market('fertilizers');
if(params.get('panel')==='notifications')openPanel('Notificações',(root)=>renderNotifications(root,[
 ...[false,true].map(read=>({id:`review-crop-${read}`,type:'CROP_READY' as const,message:'Ervilha da segunda temporada: planta pronta para colher após uma longa ausência do proprietário.',createdAt:'2026-11-08T14:53:07.606Z',read,metadata:{maturedAt:'2026-11-08T14:07:20.730Z',ownerExclusiveUntil:'2026-11-08T14:12:20.730Z'}})),
 {id:'review-unread',type:'THEFT',message:'Amigo furtou 1 Morango.',createdAt:'2026-11-06T12:00:00Z',read:false,metadata:{}},
 {id:'review-read',type:'CHRISTMAS_GIFT',message:'Presente de Natal recebido.',createdAt:'2026-11-06T11:00:00Z',read:true,metadata:{}}
],demonstration,demonstration,demonstration,demonstration));

if(params.get('panel')==='events') {
 const active=params.get('state')==='active',now=active?'2026-11-22T15:00:00Z':'2026-11-21T15:00:00Z';
 openPanel('Eventos',(root)=>renderEvents(root,{id:'sunday-harvest-2026-11-22',state:active?'ACTIVE':'UPCOMING',multiplier:2,startsAt:'2026-11-22T03:00:00Z',endsAt:'2026-11-23T03:00:00Z',nextStartsAt:active?'2026-11-29T03:00:00Z':'2026-11-22T03:00:00Z',nextEndsAt:active?'2026-11-30T03:00:00Z':'2026-11-23T03:00:00Z',cropIds:catalog.crops.filter(c=>!c.vipOnly&&c.acquisitionSource!=='LOGIN_REWARD').map(c=>c.id),timezone:'America/Sao_Paulo'},catalog,Date.parse(now)));
}
