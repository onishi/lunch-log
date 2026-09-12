/** 店舗候補 1 件。クライアントへ返す形 (SPEC §8 `/v1/places/nearby`)。 */
export interface PlaceCandidate {
  placeId: string;
  name: string;
  address?: string;
  lat: number;
  lng: number;
  /** 要求地点からの距離 (m)。候補の並べ替えと「徒歩◯分」表示に使う。 */
  distanceMeters: number;
  primaryType?: string;
}

/** 近隣検索の要求。 */
export interface NearbyQuery {
  lat: number;
  lng: number;
  /** 省略時は既定の半径から順に広げる。 */
  radiusMeters?: number;
}
