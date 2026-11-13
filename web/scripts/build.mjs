import { build } from "esbuild";
import { checkAssetSizes } from "./check-assets.mjs";
import { mkdir, cp, rm, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";
import { execFileSync } from "node:child_process";
const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const local = process.argv.includes("--local");
function required(key) {
  const value = process.env[key];
  if (!value) throw new Error(`Missing ${key}`);
  return value;
}
const config = local
  ? {
      api: "http://127.0.0.1:8080",
      development: true,
      supabaseUrl: "",
      supabaseKey: "",
    }
  : {
      api: required("PUBLIC_API_URL"),
      development: false,
      supabaseUrl: required("PUBLIC_SUPABASE_URL"),
      supabaseKey: required("PUBLIC_SUPABASE_PUBLISHABLE_KEY"),
    };
let buildVersion=process.env.CF_PAGES_COMMIT_SHA;
if (!buildVersion) try { buildVersion=execFileSync('git',['rev-parse','HEAD'],{cwd:root,encoding:'utf8',stdio:['ignore','pipe','ignore']}).trim(); } catch { /* Source archives may not include Git metadata. */ }
if (/^[a-f0-9]{7,40}$/.test(buildVersion??'')) config.buildVersion=buildVersion;
if (!local)
  for (const value of [config.api, config.supabaseUrl]) {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.origin !== value)
      throw new Error("Public endpoints must be exact HTTPS origins");
  }
const dist = resolve(root, "dist");
await mkdir(dist, { recursive: true });
await build({
  entryPoints: [resolve(root, "src/main.ts")],
  outfile: resolve(dist, "app.js"),
  bundle: true,
  minify: true,
  target: "es2022",
  sourcemap: false,
});
for (const name of ["index.html", "style.css"])
  await cp(resolve(root, name), resolve(dist, name));
await cp(resolve(root, "../assets/classic"), resolve(dist, "assets/classic"), {
  recursive: true,
});
await cp(resolve(root, "../assets/sounds"), resolve(dist, "assets/sounds"), {
  recursive: true,
});
await cp(resolve(root, "public"), dist, { recursive: true });
if (local) await cp(resolve(root, "dev"), resolve(dist, "dev"), { recursive: true });
else await rm(resolve(dist, "dev"), { recursive: true, force: true });
await writeFile(
  resolve(dist, "config.js"),
  `window.COLHEITA_CONFIG=${JSON.stringify(config)};\n`,
);
await writeFile(
  resolve(dist, "_headers"),
  `/*\n  X-Content-Type-Options: nosniff\n  Referrer-Policy: no-referrer\n  Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data: https://lh3.googleusercontent.com; connect-src 'self' ${config.api} ${config.supabaseUrl}; base-uri 'none'; frame-ancestors 'none'; form-action 'self'\n/config.js\n  Cache-Control: no-store\n/index.html\n  Cache-Control: no-cache\n`,
);
await checkAssetSizes(dist);
console.log(
  `Built ${local ? "localhost test" : "production"} client in web/dist`,
);
