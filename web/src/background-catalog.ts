// Closed whitelist of farm backgrounds. File names are explicit because the real assets are not
// named consistently (backgound-inverno.png, _v2, _noite_real); never derive a path from an id.
export type Period = "day" | "sunset" | "night";
export interface BackgroundPack { id: string; name: string; files: Record<Period, string> }

export const DEFAULT_BACKGROUND = "default";

export const BACKGROUND_PACKS: readonly BackgroundPack[] = [
  { id: "default", name: "Clássico", files: { day: "background.png", sunset: "background-sunset.png", night: "background-night.png" } },
  { id: "inverno", name: "Inverno", files: { day: "backgound-inverno.png", sunset: "background-inverno-tarde.png", night: "background-inverno-noite.png" } },
  { id: "outono", name: "Outono", files: { day: "background-outono.png", sunset: "background-outono-tarde.png", night: "background-outono-noite.png" } },
  { id: "savana", name: "Savana", files: { day: "background-savana.png", sunset: "background-savana-tarde.png", night: "background-savana-noite.png" } },
  { id: "cerejeiras", name: "Cerejeiras", files: { day: "background_cerejeiras_v2.png", sunset: "background_cerejeiras_tarde_v2.png", night: "background_cerejeiras_noite_v2.png" } },
  { id: "floresta_encantada", name: "Floresta encantada", files: { day: "background_floresta_encantada_v2.png", sunset: "background_floresta_encantada_tarde_v2.png", night: "background_floresta_encantada_noite_v2.png" } },
  { id: "tropical", name: "Tropical", files: { day: "background_tropical_v2.png", sunset: "background_tropical_tarde_v2.png", night: "background_tropical_noite_v2.png" } },
  { id: "mundo_dos_doces", name: "Mundo dos doces", files: { day: "background_mundo_dos_doces_dia.png", sunset: "background_mundo_dos_doces_tarde.png", night: "background_mundo_dos_doces_noite_real.png" } },
  { id: "planeta_alienigena", name: "Planeta alienígena", files: { day: "background_planeta_alienigena_dia.png", sunset: "background_planeta_alienigena_tarde.png", night: "background_planeta_alienigena_noite_real.png" } },
  { id: "terra_vulcanica", name: "Terra vulcânica", files: { day: "background_terra_vulcanica_dia.png", sunset: "background_terra_vulcanica_tarde.png", night: "background_terra_vulcanica_noite_real.png" } },
  { id: "jardim_oriental", name: "Jardim oriental", files: { day: "background_jardim_oriental_dia.png", sunset: "background_jardim_oriental_tarde.png", night: "background_jardim_oriental_noite_real.png" } },
];

/** Unknown or missing ids (old saves, a pack removed later) fall back to the free classic pack. */
export function backgroundPack(id?: string | null): BackgroundPack {
  return BACKGROUND_PACKS.find((pack) => pack.id === id) ?? BACKGROUND_PACKS[0];
}

export function backgroundPath(id: string | null | undefined, period: Period): string {
  return `classic/background/${backgroundPack(id).files[period]}`;
}
