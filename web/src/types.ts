export interface Tile {
  column: number;
  row: number;
  unlocked: boolean;
  requiredLevel?: number;
  price?: string | number;
  currency?: string;
  purchasable?: boolean;
  state: string;
  cropId?: string;
  stage: number;
  season: number;
  seasons: number;
  watered: boolean;
  dry: boolean;
  paused: boolean;
  weeds: boolean;
  insects: number;
  fertilized: boolean;
  /** Sent only to the owner; visits never reveal protected plots. */
  protected?: boolean;
  harvestRevision: string;
  remainingSeconds: number;
  nominalRemainingSeconds: number;
}
export interface Animal {
  id: string;
  stage: string;
  status: string;
  remainingSeconds: number;
  saleQuote?: string;
  saleCurrency?: string;
  economyVersion?: number;
}
export interface AutomationService {id:string; name:string; description:string; greenPrice:number; durationDays:number;}
export interface UpdateNotes {version:string; prNumber:number; date:string; items:string[];}
export interface AwayReport {
  /** One id per absence: the same report repeats until acknowledged; equal content in a later absence is a new report. */
  id:string;
  awaySeconds:number;
  harvests:{cropId:string;name:string;stem:string;quantity:number}[];
  /** Collected by automation into the deposit. */
  products:{productId:string;name:string;animalId:string;quantity:number}[];
  /** Ready in the barn, still waiting to be collected. */
  readyProducts:{productId:string;name:string;animalId:string;quantity:number}[];
  thefts:{at:string;actor:string;itemId:string;name:string;stem:string;quantity:number}[];
  defenses:{at:string;actor:string}[];
  care:{waterings:number;pestsRemoved:number;animalCares:number};
  pending:({kind:'AUTOMATION';serviceId:string;name:string;expiredAt:string}|{kind:'VIP';expiredAt:string}|{kind:'FEED';animalId:string;name:string;stock:number})[];
}
export interface SocialCooldowns {
  stealNextAt: string|null;
  insectsNextAt: string|null;
  weedsNextAt: string|null;
}
export interface Farm {
  socialCooldowns?: SocialCooldowns;
  automations?: (AutomationService & {expiresAt:string|null;status:"ACTIVE"|"SUSPENDED"|"EXPIRED"|"NOT_PURCHASED"})[];
  updateNotes?: UpdateNotes;
  awayReport?: AwayReport;
  avatar?: {id:string;photoUrl?:string|null};
  avatarSelected?: boolean;
  notifications?: GameNotification[];
  unreadNotifications?: number;
  owner: string;
  name: string;
  revision: string;
  serverTime: string;
  sundayHarvestEvent?: SundayHarvestEvent;
  own: boolean;
  tiles: Tile[];
  animals: Animal[];
  coins?: string;
  greenCoins?: string;
  level?: number;
  currentXp?: string;
  xpToNext?: string;
  vipActive?: boolean;
  vipExpiresAt?: string;
  seedCounts?: Record<string, number>;
  depositTotals?: Record<string, string | number>;
  itemSaleTotals?: Record<string, Record<string, string | number>>;
  itemSaleSingles?: Record<string, Record<string, string | number>>;
  produceLots?: {
    itemId: string;
    quantity: number;
    currency: string;
    unitCost?: string | number;
    unitSale?: string | number;
    vipOnly?: boolean;
  }[];
  feed?: Record<string, number>;
  fertilizers?: Record<string, number>;
  dailyAllowance?: { amount: string; available: boolean; nextResetAt: string };
  loginRewards?: {
    completedDay: number;
    nextDay: number;
    available: boolean;
    streakReset: boolean;
    nextResetAt: string;
    rewards: { day: number; kind: "GOLD" | "SEED"; currency: "GOLD" | "GREEN"; amount: string; itemId?: string }[];
  };
  tutorial?: { title?: string; hint?: string };
  tutorialStep?: number;
  tutorialTotal?: number;
  missions?: {
    id: string;
    title: string;
    green: number;
    progress: number;
    target: number;
    done: boolean;
  }[];
  cooperativeBonus?: { green: number; done: boolean };
  achievements?: { id: string; category: string; categoryLabel: string; title: string; target: number; progress: number; unlocked: boolean }[];
  equippedTitleId?: string | null;
  activeBackgroundId?: string;
  ownedBackgroundIds?: string[];
  backgroundPrice?: string;
  equippedTitle?: string | null;
  weeklyTasks?: {
    weekStartsAt: string;
    nextResetAt: string;
    tasks: { id: string; title: string; progress: number; target: number; rewardXp: number; done: boolean }[];
  };
  nextPlot?: { column: number; row: number; requiredLevel?:number; price?:string|number; currency?:string; purchasable?:boolean };
}
export interface Friend { vipActive?:boolean; id:string; name:string; avatar?:Farm['avatar']; equippedTitle?:string|null; }
export interface Gift { id:string; senderId?:string; senderName?:string; recipientId?:string; recipientName?:string; cropId:string; quantity:number; createdAt:string; state:'DELIVERED'; }
export interface Gifts { received:Gift[]; sent:Gift[]; }
export interface GameNotification {
  id: string;
  type: 'THEFT'|'CROP_READY'|'ANIMAL_READY'|'VIP_EXPIRED'|'CHRISTMAS_GIFT';
  message: string;
  createdAt: string;
  read: boolean;
  metadata: Record<string,string>;
}
export interface Crop {
  id: string;
  name: string;
  growthSeconds: number;
  regrowthSeconds: number;
  seasons: number;
  stages: number;
  spriteStem: string;
  requiredLevel: number;
  vipOnly: boolean;
  seedPrices: Record<string, number>;
  acquisitionSource?: "SHOP" | "LOGIN_REWARD";
  cycleRevenueTenths?: number;
}
export interface AnimalType {
  id: string;
  name: string;
  kind: string;
  requiredLevel: number;
  prices: Record<string, number>;
  salePrices?: Record<string, {commonTenths: number; vipTenths: number}>;
  productId: string;
  productName: string;
  productionSeconds?: number;
  babySeconds?: number;
  youngSeconds?: number;
}
export interface Fertilizer {
  id: string;
  name: string;
  prices: Record<string, number>;
  requiredLevel: number;
  reductionSeconds: number;
  friendsOnly: boolean;
}
export interface Catalog {
  crops: Crop[];
  animals: AnimalType[];
  fertilizers: Fertilizer[];
  feedPrices: Record<string, number>;
  vip: { greenPrice: number };
  automationServices?: AutomationService[];
}
export interface Reply {
  farm?: Farm;
  visitedFarm?: Farm;
  theftOutcome?: { kind: "STOLEN" | "PROTECTED" | "BITTEN" | "BLOCKED"; goldDebited: string };
  result?: { success: boolean; message: string };
  code?: string;
  expiresAt?: string;
}
export type CommandFields = Record<string, string | number>;
export interface Config {
  buildVersion?: string;
  api: string;
  development: boolean;
  supabaseUrl: string;
  supabaseKey: string;
}
declare global {
  interface Window {
    COLHEITA_CONFIG: Config;
  }
}

export interface SundayHarvestEvent {id:string;state:'ACTIVE'|'UPCOMING';multiplier:number;startsAt:string;endsAt:string;nextStartsAt:string;nextEndsAt:string;cropIds:string[];timezone:string}
