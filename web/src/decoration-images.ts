/** Image decoding cache for the Decorations carousel. Keeps only a small moving window in memory. */
export interface DecodableImage {
  src: string;
  readonly naturalWidth: number;
  decode(): Promise<void>;
}

export class DecorationImageCache<T extends DecodableImage> {
  private readonly entries = new Map<string, Promise<T>>();
  private readonly create: () => T;
  private readonly maxEntries: number;

  constructor(create: () => T, maxEntries = 8) {
    this.create = create;
    this.maxEntries = maxEntries;
    if (!Number.isInteger(maxEntries) || maxEntries < 1) throw new RangeError('Invalid image cache size');
  }

  load(src: string): Promise<T> {
    const cached = this.entries.get(src);
    if (cached) {
      this.entries.delete(src);
      this.entries.set(src, cached);
      return cached;
    }

    const image = this.create();
    let pending: Promise<T>;
    pending = Promise.resolve().then(async () => {
      image.src = src;
      await image.decode();
      if (image.naturalWidth <= 0) throw new Error('Image decoded without dimensions');
      return image;
    }).catch(error => {
      // Failed loads may be retried by the user. Do not evict a newer retry.
      if (this.entries.get(src) === pending) this.entries.delete(src);
      throw error;
    });
    this.entries.set(src, pending);
    while (this.entries.size > this.maxEntries) this.entries.delete(this.entries.keys().next().value!);
    return pending;
  }

  preload(paths: Iterable<string>): void {
    for (const src of new Set(paths)) void this.load(src).catch(() => {});
  }
}

/** Only the latest requested preview may replace the picture currently on screen. */
export class LatestDecorationPreview<T extends DecodableImage> {
  private requestId = 0;
  private readonly cache: DecorationImageCache<T>;

  constructor(cache: DecorationImageCache<T>) { this.cache = cache; }

  cancel(): void { this.requestId++; }

  async show(src: string, ready: (image: T) => void, failed: () => void): Promise<void> {
    const request = ++this.requestId;
    try {
      const image = await this.cache.load(src);
      if (request === this.requestId) ready(image);
    } catch {
      if (request === this.requestId) failed();
    }
  }
}
