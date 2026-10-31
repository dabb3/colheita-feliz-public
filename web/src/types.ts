export interface Tile {
 column:number; row:number; unlocked:boolean; state:string; cropId?:string;
 stage:number; season:number; seasons:number; dry:boolean; paused:boolean;
 weeds:boolean; insects:number; fertilized:boolean; protected:boolean;
 harvestRevision:string; remainingSeconds:number;nominalRemainingSeconds:number;
}
export interface Animal { id:string; stage:string; status:string; remainingSeconds:number; }
export interface Farm {
 owner:string; name:string; revision:string; serverTime:string; own:boolean;
 tiles:Tile[]; animals:Animal[];
 coins?:string; greenCoins?:string; level?:number; currentXp?:string; xpToNext?:string; depositTotals?:Record<string,string|number>;
 vipActive?:boolean; vipExpiresAt?:string; seedCounts?:Record<string,number>;
 produceLots?:{itemId:string; quantity:number; currency:string;vipOnly?:boolean}[];
 feed?:Record<string,number>; fertilizers?:Record<string,number>;
 tutorial?:{title?:string; hint?:string};tutorialStep?:number;tutorialTotal?:number; missions?:{id:string; title:string;green:number;progress:number;target:number;done:boolean}[];
 nextPlot?:{column:number;row:number};
}
export interface Crop {id:string;name:string;growthSeconds:number;seasons:number;stages:number;spriteStem:string;requiredLevel:number;vipOnly:boolean;seedPrices:Record<string,number>;}
export interface AnimalType {id:string;name:string;kind:string;requiredLevel:number;prices:Record<string,number>;productId:string;productName:string;}
export interface Fertilizer {id:string;name:string;prices:Record<string,number>;requiredLevel:number;reductionSeconds:number;friendsOnly:boolean;}
export interface Catalog {crops:Crop[];animals:AnimalType[];fertilizers:Fertilizer[];feedPrices:Record<string,number>;vip:{greenPrice:number};}
export interface Reply {farm?:Farm; visitedFarm?:Farm; result?:{success:boolean;message:string};code?:string;expiresAt?:string;}
export type CommandFields=Record<string,string|number>;
export interface Config {api:string;development:boolean;supabaseUrl:string;supabaseKey:string;}
declare global {interface Window {COLHEITA_CONFIG:Config;}}
