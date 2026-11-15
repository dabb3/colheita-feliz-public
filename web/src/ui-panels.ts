import type { Farm, Catalog, Tile, CommandFields, Friend, Gifts } from "./types.ts";
import { text, image, control, renderDailyAllowance,friendAvatar,vipBadge } from "./ui.ts";
import { animalPath, productPath } from "./presentation.ts";
import { durationLabel, animalCare, cropTimingText } from "./ui-presentation.ts";

function inventoryCard(name: string, path: string, count: number) {
  const card = document.createElement("article");
  card.className = "card inventory-card";
  const art = image(path);
  art.loading = "lazy";
  card.append(text("h3", name), art, text("strong", `${count} unidade(s)`));
  return card;
}

export function renderDeposit(
  root: HTMLElement,
  catalog: Catalog,
  farm: Farm,
  category: string,
  onCategory: (category: string) => void,
  onAction: (fields: CommandFields) => void,
) {
  root.replaceChildren();
  const tabs = document.createElement("div");
  tabs.className = "tabs";
  root.append(tabs);
  for (const [id, label] of [
    ["products", "Produtos"],
    ["seeds", "Sementes"],
    ["supplies", "Insumos"],
  ]) {
    const tab = control(label, () => onCategory(id));
    tab.setAttribute("aria-pressed", String(id === category));
    tabs.append(tab);
  }
  if (category === "products") {
    const overview = document.createElement("section");
    overview.className = "deposit-overview";
    overview.append(text("h3", "Você recebe ao vender tudo"));
    const totals = farm.depositTotals ?? {},
      summary = document.createElement("div");
    summary.className = "deposit-summary";
    let total = 0;
    for (const [currency, label, icon] of [
      ["GOLD", "Ouro", "moeda-comum"],
      ["GREEN", "Verdes", "moeda-verde"],
    ]) {
      const amount = totals[currency] ?? 0;
      total += Number(amount) || 0;
      const item = document.createElement("div");
      item.className = "deposit-currency";
      item.append(
        image(`classic/icons/${icon}.png`),
        text("span", label),
        text("strong", String(amount)),
      );
      summary.append(item);
    }
    overview.append(summary);
    if (total > 0) {
      const value = Object.entries(totals)
        .filter(([, amount]) => Number(amount) > 0)
        .map(
          ([currency, amount]) =>
            `${amount} ${currency === "GOLD" ? "ouro" : "verdes"}`,
        )
        .join(" + ");
      const sell = control(`Vender tudo · ${value}`, () =>
        onAction({ action: "SELL_ALL" }),
      );
      sell.className = "deposit-sell-all";
      overview.append(sell);
    }
    root.append(
      overview,
      text(
        "p",
        "Produtos VIP sem assinatura ficam bloqueados. Cada lote preserva sua moeda de aquisição.",
        "muted",
      ),
    );
    const groups = new Map<string, NonNullable<Farm["produceLots"]>>();
    for (const lot of farm.produceLots ?? [])
      groups.set(lot.itemId, [...(groups.get(lot.itemId) ?? []), lot]);
    let count = 0;
    for (const [kind, label] of [
      ["crop", "Colheitas"],
      ["animal", "Produtos dos animais"],
      ["other", "Outros produtos"],
    ] as const) {
      const items = [...groups].filter(([id]) => {
        const crop = catalog.crops.some((item) => item.id === id),
          animal = catalog.animals.some((item) => item.productId === id);
        return kind === "crop"
          ? crop
          : kind === "animal"
            ? animal
            : !crop && !animal;
      });
      if (!items.length) continue;
      const section = document.createElement("section");
      section.className = "deposit-group";
      section.append(text("h3", label));
      const grid = document.createElement("div");
      grid.className = "cards inventory-cards";
      section.append(grid);
      for (const [id, lots] of items) {
        count += 1;
        const crop = catalog.crops.find((item) => item.id === id),
          animal = catalog.animals.find((item) => item.productId === id);
        const art = crop
          ? `classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`
          : animal
            ? (productPath(animal.id, "READY") ??
              animalPath(animal.id, "ADULT", false, false, false))
            : "classic/icons/deposit-icon.png";
        const quantity = lots.reduce((sum, lot) => sum + lot.quantity, 0),
          card = inventoryCard(
            crop?.name ?? animal?.productName ?? id,
            art,
            quantity,
          );
        const currencies = new Map<string, number>();
        for (const lot of lots)
          currencies.set(
            lot.currency,
            (currencies.get(lot.currency) ?? 0) + lot.quantity,
          );
        card.append(
          text(
            "p",
            [...currencies]
              .map(
                ([currency, amount]) =>
                  `${amount} em ${currency === "GOLD" ? "ouro" : "verdes"}`,
              )
              .join(" · "),
          ),
        );
        const blocked = (lot: (typeof lots)[number]) =>
            (lot.vipOnly ?? crop?.vipOnly) && !farm.vipActive,
          sellable = lots.some((lot) => !blocked(lot));
        if (lots.some(blocked))
          card.append(text("small", "Lotes VIP bloqueados", "status-badge"));
        const actions = document.createElement("div");
        actions.className = "deposit-sell-actions";
        const one = control("Vender 1", () =>
          onAction({ action: "SELL_ITEM", itemId: id }),
        );
        one.disabled = !sellable;
        one.setAttribute(
          "aria-label",
          `Vender 1 ${crop?.name ?? animal?.productName ?? id}`,
        );
        const all = control("Vender tudo", () =>
          onAction({ action: "SELL_ITEM_ALL", itemId: id }),
        );
        all.disabled = !sellable;
        all.setAttribute(
          "aria-label",
          `Vender todo o estoque de ${crop?.name ?? animal?.productName ?? id}`,
        );
        actions.append(one, all);
        card.append(actions);
        grid.append(card);
      }
      root.append(section);
    }
    if (!count)
      root.append(
        text(
          "p",
          "Seu depósito está vazio. Colha uma plantação ou recolha a produção dos animais.",
          "empty-state",
        ),
      );
  } else if (category === "seeds") {
    const grid = document.createElement("div");
    grid.className = "cards inventory-cards";
    for (const crop of catalog.crops) {
      const count = farm.seedCounts?.[crop.id] ?? 0;
      if (count > 0)
        grid.append(
          inventoryCard(
            crop.name,
            `classic/crops/plants_phase_one_to_five/${crop.spriteStem}_5.png`,
            count,
          ),
        );
    }
    root.append(grid);
    if (!grid.children.length)
      root.append(text("p", "Nenhuma semente armazenada.", "empty-state"));
  } else {
    const grid = document.createElement("div");
    grid.className = "cards inventory-cards";
    for (const fertilizer of catalog.fertilizers) {
      const count = farm.fertilizers?.[fertilizer.id] ?? 0;
      if (count > 0)
        grid.append(
          inventoryCard(
            fertilizer.name,
            `classic/icons/adubo-${fertilizer.id}.png`,
            count,
          ),
        );
    }
    for (const [id, count] of Object.entries(farm.feed ?? {}))
      if (count > 0)
        grid.append(
          inventoryCard(
            `Ração de ${id === "producer" ? "produção" : "crescimento"}`,
            "classic/icons/racao-generica.png",
            count,
          ),
        );
    root.append(grid);
    if (!grid.children.length)
      root.append(text("p", "Nenhum insumo armazenado.", "empty-state"));
  }
}

export function renderPlantHealth(
  root: HTMLElement,
  catalog: Catalog,
  tile: Tile | null,
  serverTime?: string,
) {
  root.replaceChildren();
  root.hidden = !tile?.cropId;
  if (!tile?.cropId) return;
  const crop = catalog.crops.find((c) => c.id === tile.cropId);
  root.append(text("strong", crop?.name ?? tile.cropId));
  if (tile.state === "DEAD") {
    root.append(text("p", "Planta morta", "health-state"));
    return;
  }
  root.append(
    text(
      "p",
      `Temporada ${tile.season} / ${tile.seasons} · estágio ${tile.stage} / ${crop?.stages ?? 5}`,
      "muted",
    ),
  );
  if (tile.state === "READY")
    root.append(text("p", "Pronta para colher", "health-state"));
  else {
    const serverAt = Date.parse(serverTime ?? "");
    const timing = cropTimingText(tile, serverAt + tile.remainingSeconds * 1000, serverAt + tile.nominalRemainingSeconds * 1000);
    for (const line of [timing.nominal, timing.condition, timing.estimate])
      if (line) root.append(text("p", line, "muted"));
  }
  if(tile.protected)root.append(image("classic/icons/icone-cadeado.png","protection-icon"));
  const conditions = [
    tile.dry ? "Precisa de água" : "",
    tile.paused ? "Crescimento pausado" : "",
    tile.weeds ? "Ervas daninhas" : "",
    tile.insects ? `${tile.insects} inseto(s)` : "",
    tile.protected ? "Protegida contra furto" : "",
  ].filter(Boolean);
  root.append(
    text("span", conditions.join(" · ") || "Saudável", "status-badge"),
  );
}

export function renderAnimal(
  root: HTMLElement,
  catalog: Catalog,
  farm: Farm,
  id: string,
  canTend: boolean,
  onAction: (fields: CommandFields) => void,
) {
  root.replaceChildren();
  const animal = farm.animals.find((a) => a.id === id),
    type = catalog.animals.find((a) => a.id === id);
  if (!animal || !type) {
    root.append(text("p", "Este animal não está mais nesta fazenda."));
    return;
  }
  const card = inventoryCard(
    type.name,
    animalPath(id, animal.stage, false, false, false),
    1,
  );
  root.append(card);
  const stages: Record<string, string> = {
    BABY: "Filhote",
    YOUNG: "Jovem",
    ADULT: "Adulto",
  };
  const status =
    animal.status === "HUNGRY"
      ? "Com fome"
      : animal.status === "READY"
        ? type.kind === "GROWING"
          ? "Adulto disponível para venda"
          : "Produto pronto"
        : type.kind === "PROTECTOR"
          ? "Protegendo a fazenda"
          : type.kind === "PRODUCER"
            ? "Produzindo"
            : "Em crescimento";
  card.append(text("p", `${stages[animal.stage] ?? animal.stage} · ${status}`));
  if (animal.remainingSeconds > 0)
    card.append(
      text("p", `Tempo restante: ${durationLabel(animal.remainingSeconds)}`),
    );
  if (!canTend) {
    card.append(
      text(
        "p",
        "Animal da fazenda visitada. Cuidados disponíveis apenas para o dono.",
        "muted",
      ),
    );
    return;
  }
  const care = animalCare(animal, type),
    feedId = type.kind === "PRODUCER" ? "producer" : "growth";
  const count =
    (farm.feed?.[feedId] ?? 0) +
    (type.kind === "PROTECTOR" ? (farm.feed?.producer ?? 0) : 0);
  if (care) {
    const button = control(care.label, () =>
      onAction({ action: care.action, itemId: id }),
    );
    button.disabled = care.label === "Alimentar" && count === 0;
    card.append(button);
  }
  if (type.kind === "PRODUCER" && animal.status === "READY")
    card.append(
      text("small", "Recolher entrega uma unidade e remove o animal.", "muted"),
    );
  card.append(text("p", `Ração disponível: ${count}`));
  if (catalog.feedPrices[feedId] !== undefined)
    card.append(
      control(`Comprar ração · ${catalog.feedPrices[feedId]} ouro`, () =>
        onAction({ action: "BUY_FEED", itemId: feedId }),
      ),
    );
}

export function renderMissions(root: HTMLElement, farm: Farm) {
  root.replaceChildren();
  renderDailyAllowance(root, farm.dailyAllowance, () => {});
  const missions = farm.missions ?? [];
  root.append(
    text(
      "p",
      `${missions.filter((m) => m.done).length} / ${missions.length} tarefas concluídas`,
      "muted",
    ),
  );
  const grid = document.createElement("div");
  grid.className = "cards mission-cards";
  root.append(grid);
  for (const mission of missions) {
    const card = document.createElement("article");
    card.className = "card mission-card";
    card.append(
      text("h3", mission.title),
      text(
        "strong",
        mission.done ? "Concluída" : "Em andamento",
        "status-badge",
      ),
      text("p", `${mission.progress ?? 0}/${mission.target ?? 1}`, "mission-progress"),
      text("p", `${mission.green} moeda(s) verde(s)`),
    );
    grid.append(card);
  }
  root.append(
    text(
      "p",
      "As recompensas são creditadas pelo servidor ao concluir as tarefas.",
      "muted",
    ),
  );
  root.append(
    text(
      "p",
      farm.vipActive ? `VIP ativo até ${farm.vipExpiresAt}` : "VIP inativo.",
      "muted",
    ),
  );
}

export function renderFriends(
  root: HTMLElement,
  friends: Friend[],
  onVisit: (id: string) => void,
) {
  root.replaceChildren();
  root.className = "friend-list";
  for (const friend of friends) {
    const row = document.createElement("article");
    row.className = "friend-card";
    row.append(
      friendAvatar(friend),
      text("strong", friend.name),
      control(`Visitar ${friend.name}`, () => onVisit(friend.id)),
    );
    if(friend.vipActive)row.children[1].append(vipBadge());
    root.append(row);
  }
  if (!friends.length)
    root.append(
      text(
        "p",
        "Nenhum amigo ainda. Crie um convite e compartilhe o link.",
        "empty-state",
      ),
    );
}

export function giftLimit(stock: number) {
  return Math.max(0, Math.min(5, Math.floor(stock)));
}

export function renderGifts(
  root: HTMLElement,
  catalog: Catalog,
  seedCounts: Record<string, number>,
  friends: Friend[],
  gifts: Gifts,
  now: number,
  onSend: (fields: CommandFields) => void,
) {
  const state = { friend: "", crop: "", quantity: 1, confirming: false };
  const crops = catalog.crops.filter((crop) => (seedCounts[crop.id] ?? 0) > 0);
  const recent = (id: string) => gifts.sent.some((gift) => gift.recipientId === id && now - Date.parse(gift.createdAt) < 86_400_000);
  const pick = (label: string, selected: boolean, action: () => void) => {
    const node = control(label, action);
    node.setAttribute("aria-pressed", String(selected));
    return node;
  };
  const draw = () => {
    root.replaceChildren();
    root.className = "gift-panel";
    const icon = text("span", "", "gift-icon");
    icon.append(image("classic/icons/asset_presente.jpg"));
    root.append(icon, text("h3", "Presentes"));
    const friend = friends.find((item) => item.id === state.friend);
    const crop = crops.find((item) => item.id === state.crop);
    const limit = giftLimit(crop ? seedCounts[crop.id] : 0);
    state.quantity = Math.min(Math.max(1, state.quantity), Math.max(1, limit));
    if (!friends.length) root.append(text("p", "Adicione um amigo para enviar presentes.", "empty-state"));
    else if (!crops.length) root.append(text("p", "Você não tem sementes para presentear.", "empty-state"));
    else {
      const people = text("div", "", "gift-options");
      for (const item of friends) {
        const button = pick(item.name, item.id === state.friend, () => { state.friend = item.id; state.confirming = false; draw(); });
        button.disabled = recent(item.id);
        people.append(button);
      }
      const seeds = text("div", "", "gift-options");
      for (const item of crops)
        seeds.append(pick(`${item.name} (${seedCounts[item.id]})`, item.id === state.crop, () => { state.crop = item.id; state.confirming = false; draw(); }));
      const less = control("−", () => { state.quantity--; draw(); }), more = control("+", () => { state.quantity++; draw(); });
      less.disabled = state.quantity <= 1;
      more.disabled = state.quantity >= limit;
      const quantity = text("div", "", "gift-quantity");
      quantity.append(less, text("strong", `${state.quantity} semente(s)`), more);
      root.append(text("p", "Amigo"), people, text("p", "Semente"), seeds, quantity);
      const reason = friend && recent(friend.id) ? "Você já presenteou este amigo nas últimas 24 horas."
        : friend && crop?.vipOnly && !friend.vipActive ? `${crop.name} exige VIP ativo do seu amigo.` : "";
      if (reason) root.append(text("p", reason, "empty-state"));
      if (state.confirming && friend && crop) {
        const confirm = text("div", "", "gift-confirm");
        confirm.append(
          text("p", `Enviar ${state.quantity} semente(s) de ${crop.name} para ${friend.name}?`),
          control("Confirmar", () => { state.confirming = false; draw(); onSend({ recipientId: friend.id, cropId: crop.id, quantity: state.quantity }); }),
          control("Cancelar", () => { state.confirming = false; draw(); }),
        );
        root.append(confirm);
      } else {
        const send = control("Enviar presente", () => { state.confirming = true; draw(); });
        send.disabled = !friend || !crop || !!reason;
        root.append(send);
      }
    }
    root.append(text("h4", "Recebidos"));
    for (const gift of gifts.received) {
      const card = text("article", "", "gift-card");
      const art = text("span", "", "gift-icon");
      art.append(image("classic/icons/asset_presente.jpg"));
      const name = catalog.crops.find((item) => item.id === gift.cropId)?.name ?? gift.cropId;
      card.append(art, text("strong", `${gift.senderName ?? "Amigo"} enviou ${gift.quantity} semente(s) de ${name}`),
        text("span", new Date(gift.createdAt).toLocaleString("pt-BR")));
      root.append(card);
    }
    if (!gifts.received.length) root.append(text("p", "Nenhum presente recebido ainda.", "empty-state"));
  };
  draw();
}

export function renderAudioSettings(
  root: HTMLElement,
  settings: {
    music: boolean;
    effects: boolean;
    musicVolume: number;
    effectsVolume: number;
  },
  onChange: (
    channel: "music" | "effects",
    enabled: boolean,
    volume: number,
  ) => void,
) {
  root.replaceChildren();
  for (const [channel, name] of [
    ["music", "Música"],
    ["effects", "Efeitos"],
  ] as const) {
    const row = document.createElement("section");
    row.className = "audio-setting";
    const label = document.createElement("label"),
      check = document.createElement("input");
    check.type = "checkbox";
    check.checked = settings[channel];
    check.setAttribute("aria-label", name);
    label.append(check, text("span", name));
    const volume = document.createElement("input");
    volume.type = "range";
    volume.min = "0";
    volume.max = "100";
    volume.value = String(
      settings[channel === "music" ? "musicVolume" : "effectsVolume"] * 100,
    );
    volume.setAttribute("aria-label", `Volume de ${name}`);
    const value = text("output", `${volume.value}%`);
    const update = () => {
      value.textContent = `${volume.value}%`;
      onChange(channel, check.checked, Number(volume.value) / 100);
    };
    check.onchange = update;
    volume.oninput = update;
    row.append(label, volume, value);
    root.append(row);
  }
  root.append(
    text(
      "p",
      "Preferências salvas neste navegador. Áudio inicia após sua interação.",
      "muted",
    ),
  );
}
