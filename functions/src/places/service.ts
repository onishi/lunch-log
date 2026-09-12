import { CACHE_TTL_MS, cacheKey, isFresh, type CacheStore } from "./cache";
import { findNearbyPlaces, SEARCH_RADII_METERS, type SearchNearbyFn } from "./nearby";
import type { NearbyQuery, PlaceCandidate } from "./types";

export interface NearbyResult {
  candidates: PlaceCandidate[];
  /** キャッシュから返したか。クライアントは使わないが、動作確認と費用の把握に使う。 */
  cached: boolean;
}

/**
 * 近隣検索の本体。キャッシュを挟んでから Places API を呼ぶ (SPEC §6.1, §13)。
 *
 * キャッシュのキーは「約 100m のマス + 半径」。半径を広げる判断は
 * findNearbyPlaces が持つため、ここでは既定の半径 (150m) をキーに使う。
 */
export async function getNearbyPlaces(
  query: NearbyQuery,
  deps: { searchNearby: SearchNearbyFn; cache: CacheStore; now?: () => number },
): Promise<NearbyResult> {
  const now = deps.now ?? Date.now;
  const key = cacheKey(query.lat, query.lng, query.radiusMeters ?? SEARCH_RADII_METERS[0]);

  const cached = await deps.cache.get(key);
  if (cached && isFresh(cached, now(), CACHE_TTL_MS)) {
    return { candidates: cached.candidates, cached: true };
  }

  const candidates = await findNearbyPlaces(query, deps.searchNearby);

  // 空の結果もキャッシュする。「この辺には店がない」も 24 時間有効な答えで、
  // ここを素通しにすると人けのない場所で毎回課金される。
  await deps.cache.set(key, { candidates, cachedAt: now() });

  return { candidates, cached: false };
}
