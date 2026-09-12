import { gridKey } from "./grid";
import type { PlaceCandidate } from "./types";

/**
 * 店舗候補のキャッシュ (SPEC §6.1「24 時間キャッシュ」, §13 コスト対策)。
 *
 * 保存先は呼び出し側が決める (Firestore / メモリ)。ここでは
 * 「キーの決め方」と「期限切れの判定」だけを持つ。
 */
export const CACHE_TTL_MS = 24 * 60 * 60 * 1000;

export interface CacheEntry {
  candidates: PlaceCandidate[];
  /** 保存時刻 (epoch ms)。 */
  cachedAt: number;
}

export interface CacheStore {
  get(key: string): Promise<CacheEntry | undefined>;
  set(key: string, entry: CacheEntry): Promise<void>;
}

/** 同じマス・同じ半径なら同じキー。半径を含めないと 150m の結果を 300m に使い回してしまう。 */
export function cacheKey(lat: number, lng: number, radiusMeters: number): string {
  return `${gridKey(lat, lng)}:${radiusMeters}`;
}

export function isFresh(entry: CacheEntry, now: number, ttlMs: number = CACHE_TTL_MS): boolean {
  const age = now - entry.cachedAt;
  // 時計のずれで未来の時刻が入っていた場合も期限内として扱う (再取得しても直らないため)。
  return age < ttlMs;
}

/** メモリ上のキャッシュ。関数インスタンスが生きている間だけ効く一次キャッシュ。 */
export class MemoryCacheStore implements CacheStore {
  private readonly entries = new Map<string, CacheEntry>();

  async get(key: string): Promise<CacheEntry | undefined> {
    return this.entries.get(key);
  }

  async set(key: string, entry: CacheEntry): Promise<void> {
    this.entries.set(key, entry);
  }
}
