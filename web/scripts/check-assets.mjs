import { readdir, stat } from "node:fs/promises";
import { join } from "node:path";

export async function checkAssetSizes(directory, limit = 25 * 1024 * 1024) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name);
    if (entry.isDirectory()) await checkAssetSizes(path, limit);
    else if (entry.isFile()) {
      const { size } = await stat(path);
      if (size > limit)
        throw new Error(
          `Cloudflare Pages asset exceeds ${limit} bytes: ${path} (${size} bytes)`,
        );
    }
  }
}
