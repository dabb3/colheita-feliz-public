const number = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 });
const state = new URLSearchParams(location.search).get("state") ?? "items";
const content = document.getElementById("modal-content");
const modal = document.getElementById("modal");
const items = state === "empty" ? [] : state === "vip"
  ? [
      { id: "nabo", name: "Nabo", count: 3, cost: 300, vip: false },
      { id: "tomate", name: "Tomate", count: 4, cost: 950, vip: true },
    ]
  : [
      { id: "tomate", name: "Tomate", count: 4, cost: 950, vip: false },
      { id: "nabo", name: "Nabo", count: 3, cost: 300, vip: false },
      { id: "cenoura", name: "Cenoura", count: 2, cost: 350, vip: false },
    ];
const feed = state === "empty" ? [] : [{ id: "producer", count: 3 }, { id: "growth", count: 2 }];
const multiplier = 1.15;
const total = items.filter((item) => !item.vip || state !== "vip")
  .reduce((sum, item) => sum + item.count * item.cost * multiplier, 0);

function img(path) {
  const image = document.createElement("img");
  image.src = `/assets/${path}`;
  image.alt = "";
  return image;
}

const summary = document.createElement("section");
summary.className = "deposit-summary-line";
const gold = document.createElement("div");
gold.className = "deposit-total";
gold.append(document.createTextNode("Total: "), Object.assign(document.createElement("strong"), { textContent: number.format(total) }), img("classic/icons/moeda-comum.png"));
summary.append(gold);
if (state !== "empty") {
  const green = document.createElement("div");
  green.className = "deposit-green";
  green.append(document.createTextNode("12"), img("classic/icons/moeda-verde.png"));
  summary.append(green);
}
const sellAll = document.createElement("button");
sellAll.className = "deposit-sell-all";
sellAll.textContent = "Vender tudo";
sellAll.disabled = !items.length || (state === "vip" && items.every((item) => item.vip));
summary.append(sellAll);
content.append(summary);

const section = document.createElement("section");
section.className = "deposit-group";
const title = document.createElement("h3");
title.textContent = "Colheitas";
section.append(title);
if (!items.length) {
  const empty = document.createElement("p");
  empty.className = "deposit-harvest-empty";
  empty.textContent = "Nada colhido ainda. Suas colheitas aparecem aqui. 🌾";
  section.append(empty);
}
for (const item of items) {
  const row = document.createElement("article");
  row.className = `deposit-item${item.vip && state === "vip" ? " is-vip-locked" : ""}`;
  row.dataset.productId = item.id;
  const art = document.createElement("div");
  art.className = "deposit-artwork";
  art.append(img(`classic/crops/plants_phase_one_to_five/${item.id}_5.png`));
  const badge = document.createElement("span");
  badge.className = "deposit-quantity";
  badge.textContent = `×${item.count}`;
  art.append(badge);
  row.append(art);
  const details = document.createElement("div");
  details.className = "deposit-item-details";
  const name = document.createElement("strong");
  name.textContent = item.name;
  details.append(name);
  if (item.vip && state === "vip") {
    const lock = document.createElement("small");
    lock.className = "deposit-vip-note";
    lock.textContent = "🔒 Venda exige VIP";
    details.append(lock);
  } else {
    const price = document.createElement("div");
    price.className = "deposit-price";
    const line = document.createElement("span");
    line.append(document.createTextNode(`${item.count} × ${number.format(item.cost * multiplier)} = ${number.format(item.count * item.cost * multiplier)} `), img("classic/icons/moeda-comum.png"));
    price.append(line);
    details.append(price);
  }
  row.append(details);
  const actions = document.createElement("div");
  actions.className = "deposit-item-actions";
  const locked = item.vip && state === "vip";
  for (const label of item.count === 1 ? ["Vender"] : ["Vender 1", `Vender ${item.count}`]) {
    const button = document.createElement("button");
    button.textContent = label;
    button.disabled = locked;
    button.setAttribute("aria-disabled", String(locked));
    button.title = locked ? "Venda exige VIP" : label;
    const count = label === "Vender" || label === "Vender 1" ? 1 : item.count;
    button.setAttribute("aria-label", `Vender ${count} ${item.name} por ${number.format(item.cost * multiplier * count)} ouro`);
    actions.append(button);
  }
  row.append(actions);
  section.append(row);
}
content.append(section);

const feedSection = document.createElement("section");
feedSection.className = "deposit-feed";
const feedTitle = document.createElement("h3");
feedTitle.textContent = "Ração armazenada";
feedSection.append(feedTitle);
if (feed.length) {
  for (const item of feed) {
    const row = document.createElement("article");
    row.className = "deposit-item deposit-feed-row";
    const art = document.createElement("div");
    art.className = "deposit-artwork";
    art.append(img("classic/icons/racao-generica.png"));
    const badge = document.createElement("span");
    badge.className = "deposit-quantity";
    badge.textContent = `×${item.count}`;
    art.append(badge);
    const details = document.createElement("div");
    details.className = "deposit-item-details";
    const name = document.createElement("strong");
    name.textContent = item.id === "producer" ? "Ração de produção" : "Ração de crescimento";
    details.append(name, Object.assign(document.createElement("span"), { textContent: `${item.count} unidades armazenadas` }));
    row.append(art, details);
    feedSection.append(row);
  }
} else {
  const empty = document.createElement("p");
  empty.className = "deposit-feed-empty";
  empty.append(document.createTextNode("Nenhuma ração armazenada · "));
  const store = document.createElement("button");
  store.className = "deposit-store-link";
  store.type = "button";
  store.textContent = "Ver na Loja ›";
  empty.append(store);
  feedSection.append(empty);
}
content.append(feedSection);
modal.showModal();
