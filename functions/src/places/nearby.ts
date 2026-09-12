import { haversineMeters } from "./distance";
import type { NearbyQuery, PlaceCandidate } from "./types";

/**
 * 近隣の飲食店を探す処理 (SPEC §6.1)。
 *
 * 外部 API の呼び出し自体は [searchNearby] に注入する。ここでは
 * 「半径を広げる」「候補を並べ替える」「件数を絞る」という判断だけを持たせ、
 * ネットワークなしでテストできるようにしている。
 */

/** SPEC §6.1: 半径 150m、取れなければ 300m まで自動拡大。 */
export const SEARCH_RADII_METERS = [150, 300] as const;

/** SPEC §6.1: 候補チップは上位 5 件。多すぎると選ぶのが面倒になる。 */
export const MAX_CANDIDATES = 5;

/** SPEC §6.1: 昼に開いている店を拾うための種別。 */
export const INCLUDED_PRIMARY_TYPES = [
  "restaurant",
  "cafe",
  "bakery",
  "meal_takeaway",
] as const;

/** Places API を呼ぶ関数の形。テストでは差し替える。 */
export type SearchNearbyFn = (
  query: Required<NearbyQuery>,
) => Promise<PlaceCandidate[]>;

/**
 * 候補を並べ替える。
 *
 * MVP は距離順のみ (PLAN.md Phase 1-5)。訪問履歴や営業時間による加点は
 * Phase 2 で足す。ここを差し替えられるよう関数として切り出しておく。
 */
export function rankCandidates(
  candidates: PlaceCandidate[],
  limit: number = MAX_CANDIDATES,
): PlaceCandidate[] {
  const seen = new Set<string>();
  return [...candidates]
    .filter((c) => {
      if (seen.has(c.placeId)) return false;
      seen.add(c.placeId);
      return true;
    })
    .sort((a, b) => a.distanceMeters - b.distanceMeters || a.name.localeCompare(b.name))
    .slice(0, limit);
}

/**
 * 半径を広げながら候補を探す。
 * 1 件でも見つかった時点で打ち切る (無駄な呼び出しはそのまま費用になる)。
 */
export async function findNearbyPlaces(
  query: NearbyQuery,
  searchNearby: SearchNearbyFn,
): Promise<PlaceCandidate[]> {
  const radii = query.radiusMeters ? [query.radiusMeters] : [...SEARCH_RADII_METERS];

  for (const radiusMeters of radii) {
    const found = await searchNearby({ lat: query.lat, lng: query.lng, radiusMeters });
    if (found.length > 0) {
      return rankCandidates(found);
    }
  }
  return [];
}

/** Places API (New) searchNearby のレスポンスのうち、使う部分だけ。 */
export interface PlacesApiPlace {
  id?: string;
  displayName?: { text?: string };
  formattedAddress?: string;
  shortFormattedAddress?: string;
  location?: { latitude?: number; longitude?: number };
  primaryType?: string;
}

/**
 * Places API のレスポンスを候補へ変換する。
 *
 * 外部のデータなので、欠けた項目があっても落とさずに「使えるものだけ通す」。
 * id か座標か名前が欠けている要素は候補として成立しないため除く。
 */
export function toCandidates(
  places: PlacesApiPlace[] | undefined,
  origin: { lat: number; lng: number },
): PlaceCandidate[] {
  if (!places) return [];

  return places.flatMap((place) => {
    const placeId = place.id;
    const name = place.displayName?.text;
    const lat = place.location?.latitude;
    const lng = place.location?.longitude;

    if (!placeId || !name || typeof lat !== "number" || typeof lng !== "number") {
      return [];
    }

    return [
      {
        placeId,
        name,
        address: place.shortFormattedAddress ?? place.formattedAddress,
        lat,
        lng,
        distanceMeters: Math.round(haversineMeters(origin, { lat, lng })),
        primaryType: place.primaryType,
      },
    ];
  });
}
