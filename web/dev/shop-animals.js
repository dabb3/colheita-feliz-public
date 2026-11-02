const content = document.getElementById("modal-content");
const modal = document.getElementById("modal");
const state = new URLSearchParams(location.search).get("state") ?? "shop";
const panel = document.createElement("div");
panel.className = "shop-panel";
panel.id = "shop-panel";
panel.setAttribute("role", "tabpanel");
content.append(panel);

function tab(list, id, label, selected, iconPath) {
  const button = document.createElement("button");
  button.className = `shop-tab${id === "owned" ? " owned-animals-tab" : ""}`;
  button.type = "button";
  button.id = `shop-tab-${id}`;
  button.setAttribute("role", "tab");
  button.setAttribute("aria-selected", String(selected));
  button.setAttribute("aria-controls", "shop-panel");
  button.tabIndex = selected ? 0 : -1;
  if (iconPath) {
    const icon = document.createElement("img");
    icon.src = `/assets/${iconPath}`;
    icon.alt = "";
    icon.setAttribute("aria-hidden", "true");
    button.append(icon);
  }
  button.append(document.createTextNode(label));
  list.append(button);
}

const tabs = document.createElement("div");
tabs.className = "tabs shop-tabs";
tabs.setAttribute("role", "tablist");
tabs.setAttribute("aria-label", "Seções da Loja");
tabs.setAttribute("aria-orientation", "horizontal");
content.insertBefore(tabs, panel);
const feedDemo = state === "feed-zero" || state === "feed-stock";
const category = state === "shop" ? "crops" : feedDemo ? "animals" : "owned";
for (const [id, label] of [["crops", "Sementes"], ["animals", "Animais"], ["fertilizers", "Fertilizantes"], ["vip", "VIP"], ["owned", "Meus animais"]])
  tab(tabs, id, label, category === id, id === "owned" ? "classic/icons/icone-minha-fazenda.png" : undefined);
panel.setAttribute("aria-labelledby", `shop-tab-${category}`);

if (feedDemo) {
  const scroll = document.createElement("div");
  scroll.className = "market-scroll";
  panel.append(scroll);
  const market = document.createElement("section");
  market.className = "animal-feed-market";
  market.append(Object.assign(document.createElement("h3"), { textContent: "Rações" }));
  const stocks = state === "feed-stock" ? { producer: 3, growth: 2 } : { producer: 0, growth: 0 };
  for (const feed of [
    { id: "production", name: "Ração de produção", badge: "🥚", description: "Mantém a produção ativa por até 8 horas.", stock: stocks.producer, price: 120 },
    { id: "growth", name: "Ração de crescimento", badge: "🌱", description: "Mantém o crescimento ativo por até 12 horas.", stock: stocks.growth, price: 80 },
  ]) {
    const card = document.createElement("article");
    card.className = `animal-feed-option feed-${feed.id === "production" ? "production" : "growth"}`;
    const main = document.createElement("div");
    main.className = "animal-feed-main";
    const icon = document.createElement("span");
    icon.className = `feed-icon feed-icon-large feed-icon-${feed.id === "production" ? "production" : "growth"}`;
    const image = document.createElement("img");
    image.src = "/assets/classic/icons/racao-generica.png";
    image.alt = "";
    icon.append(image, Object.assign(document.createElement("span"), { className: "feed-icon-badge", textContent: feed.badge }));
    const details = document.createElement("div");
    details.className = "animal-feed-description";
    details.append(Object.assign(document.createElement("strong"), { textContent: feed.name }), Object.assign(document.createElement("span"), { textContent: feed.description }));
    const stock = Object.assign(document.createElement("span"), { className: "deposit-quantity animal-feed-stock", textContent: `×${feed.stock}` });
    main.append(icon, details, stock);
    const controls = document.createElement("div");
    controls.className = "animal-feed-controls";
    const selector = document.createElement("div");
    selector.className = "animal-feed-quantity";
    const count = Object.assign(document.createElement("output"), { className: "animal-feed-quantity-value", value: "1" });
    const decrease = document.createElement("button");
    decrease.className = "animal-feed-stepper";
    decrease.textContent = "−";
    decrease.setAttribute("aria-label", `Diminuir quantidade de ${feed.name.toLocaleLowerCase("pt-BR")}`);
    const increase = document.createElement("button");
    increase.className = "animal-feed-stepper";
    increase.textContent = "+";
    increase.setAttribute("aria-label", `Aumentar quantidade de ${feed.name.toLocaleLowerCase("pt-BR")}`);
    let quantity = 1;
    selector.append(decrease, count, increase);
    controls.append(selector);
    const buy = document.createElement("button");
    buy.className = "animal-feed-buy";
    const coin = document.createElement("img");
    coin.src = "/assets/classic/icons/moeda-comum.png";
    coin.alt = "";
    function update() {
      count.value = String(quantity);
      decrease.disabled = quantity === 1;
      buy.replaceChildren(document.createTextNode(`Comprar · ${new Intl.NumberFormat("pt-BR").format(feed.price * quantity)} `), coin);
      buy.setAttribute("aria-label", `Comprar ${quantity} ${feed.name.toLocaleLowerCase("pt-BR")} por ${feed.price * quantity} ouro`);
    }
    decrease.addEventListener("click", () => { quantity = Math.max(1, quantity - 1); update(); });
    increase.addEventListener("click", () => { quantity += 1; update(); });
    buy.addEventListener("click", () => { feed.stock += quantity; stock.textContent = `×${feed.stock}`; stock.classList.remove("feed-stock-pulse"); void stock.offsetWidth; stock.classList.add("feed-stock-pulse"); });
    update();
    controls.append(buy);
    card.append(main, controls);
    market.append(card);
  }
  scroll.append(market);
} else if (state === "shop") {
  const groups = document.createElement("div");
  groups.className = "tabs seed-tabs";
  groups.setAttribute("role", "tablist");
  groups.setAttribute("aria-label", "Tipo de semente");
  for (const [id, label] of [["common", "Comuns"], ["vip", "VIP"]]) tab(groups, id, label, id === "common");
  panel.append(groups);
  const scroll = document.createElement("div");
  scroll.className = "market-scroll";
  panel.append(scroll);
  const cards = document.createElement("div");
  cards.className = "cards market-cards";
  scroll.append(cards);
  for (const [name, art, level, price] of [["Nabo", "nabo", 1, 300], ["Cenoura", "cenoura", 1, 350], ["Milho", "milho", 2, 500], ["Batata", "batata", 3, 650], ["Tomate", "tomate", 5, 950]]) {
    const card = document.createElement("article");
    card.className = "card market-card";
    const title = document.createElement("h3");
    title.textContent = name;
    const image = document.createElement("img");
    image.className = "market-art";
    image.src = `/assets/classic/crops/plants_phase_one_to_five/${art}_5.png`;
    image.alt = "";
    card.append(title, image, Object.assign(document.createElement("p"), { textContent: "Crescimento · 1 temporada" }), Object.assign(document.createElement("small"), { textContent: `Nível ${level}` }));
    const buy = document.createElement("button");
    buy.className = "market-buy";
    buy.textContent = `Comprar · ${price} `;
    const coin = document.createElement("img");
    coin.className = "price-coin";
    coin.src = "/assets/classic/icons/moeda-comum.png";
    coin.alt = "";
    buy.append(coin);
    card.append(buy);
    cards.append(card);
  }
} else {
  const feedSummary = document.createElement("section");
  feedSummary.className = "owned-feed-summary";
  feedSummary.append(Object.assign(document.createElement("strong"), { textContent: "Ração: 1" }), Object.assign(document.createElement("span"), { textContent: "Produção 1 · Crescimento 0" }));
  const buyFeed = document.createElement("button");
  buyFeed.className = "owned-feed-link";
  buyFeed.textContent = "Comprar ração";
  feedSummary.append(buyFeed);
  panel.append(feedSummary);

  if (state === "empty") {
    const empty = document.createElement("div");
    empty.className = "owned-empty";
    const art = document.createElement("img");
    art.src = "/assets/classic/animals/galinha/galinha.png";
    art.alt = "";
    art.setAttribute("aria-hidden", "true");
    empty.append(art, Object.assign(document.createElement("h3"), { textContent: "Você ainda não tem animais" }), Object.assign(document.createElement("p"), { textContent: "Eles produzem ovo, mel, ovo de pato e pena, entre outros produtos." }));
    const link = document.createElement("button");
    link.className = "owned-empty-shop";
    link.textContent = "Ver animais à venda";
    empty.append(link);
    panel.append(empty);
  } else {
    const animals = [
      { id: "galinha", name: "Galinha", stage: "ADULT", state: "READY", label: "Pronto para coletar", progress: 100, time: "Pronto", bar: "Produção", action: "Coletar", art: "classic/animals/galinha/galinha.png" },
      { id: "ovelha", name: "Ovelha", stage: "ADULT", state: "PRODUCING", label: "Produzindo", progress: 63, time: "7 h 26 min restantes", bar: "Produção", action: "", art: "classic/animals/ovelha/ovelha.png" },
      { id: "porco", name: "Porco", stage: "YOUNG", state: "HUNGRY", label: "Com fome", progress: 42, time: "13 h 50 min restantes", bar: "Crescimento", action: "Alimentar", art: "classic/animals/porco/porco-jovem.png" },
    ];
    const list = document.createElement("div");
    list.className = "owned-animal-list";
    for (const animal of animals) {
      const card = document.createElement("article");
      card.className = "owned-animal-card";
      const image = document.createElement("img");
      image.className = "owned-animal-art";
      image.src = `/assets/${animal.art}`;
      image.alt = "";
      const details = document.createElement("div");
      details.className = "owned-animal-details";
      const heading = document.createElement("div");
      heading.className = "owned-animal-heading";
      heading.append(Object.assign(document.createElement("strong"), { textContent: animal.name }), Object.assign(document.createElement("span"), { className: "owned-animal-stage", textContent: animal.stage === "YOUNG" ? "Jovem" : "Adulto" }), Object.assign(document.createElement("span"), { className: `animal-state animal-state-${animal.state.toLowerCase()}`, textContent: animal.label }));
      details.append(heading);
      const caption = document.createElement("div");
      caption.className = "crop-progress-caption";
      caption.append(Object.assign(document.createElement("span"), { textContent: animal.bar }), Object.assign(document.createElement("strong"), { textContent: animal.time }));
      const track = document.createElement("div");
      track.className = `crop-growth-track${animal.progress === 100 ? " is-ready" : ""}`;
      track.setAttribute("role", "progressbar");
      track.setAttribute("aria-valuenow", String(animal.progress));
      track.setAttribute("aria-valuemin", "0");
      track.setAttribute("aria-valuemax", "100");
      track.setAttribute("aria-label", `${animal.bar}: ${animal.progress}%`);
      const fill = document.createElement("span");
      fill.className = "crop-growth-fill";
      fill.style.width = `${animal.progress}%`;
      track.append(fill);
      details.append(caption, track);
      const actions = document.createElement("div");
      actions.className = "owned-animal-actions";
      if (animal.action) {
        const button = document.createElement("button");
        button.textContent = animal.action;
        button.disabled = animal.action === "Alimentar";
        if (button.disabled) {
          button.setAttribute("aria-disabled", "true");
          button.title = "Sem ração. Compre na Loja, aba Animais.";
          actions.append(button, Object.assign(document.createElement("small"), { className: "owned-feed-warning", textContent: "Sem ração para alimentar." }));
        } else actions.append(button);
      }
      card.append(image, details, actions);
      list.append(card);
    }
    panel.append(list);
  }
}
modal.showModal();
