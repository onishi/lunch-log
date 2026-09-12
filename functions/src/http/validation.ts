import type { NearbyQuery } from "../places/types";

export type ParseResult<T> =
  | { ok: true; value: T }
  | { ok: false; code: string; message: string };

/**
 * リクエストの検証。
 *
 * クライアントの入力は信用しない。特に半径は、大きな値を渡されると
 * そのまま費用になるため上限を設ける。
 */
export const MAX_REQUEST_RADIUS_METERS = 1000;

export function parseNearbyQuery(body: unknown): ParseResult<NearbyQuery> {
  if (typeof body !== "object" || body === null) {
    return { ok: false, code: "INVALID_BODY", message: "リクエストの形式が不正です" };
  }
  const { lat, lng, radiusMeters } = body as Record<string, unknown>;

  if (typeof lat !== "number" || !Number.isFinite(lat) || lat < -90 || lat > 90) {
    return { ok: false, code: "INVALID_LAT", message: "lat は -90..90 の数値であること" };
  }
  if (typeof lng !== "number" || !Number.isFinite(lng) || lng < -180 || lng > 180) {
    return { ok: false, code: "INVALID_LNG", message: "lng は -180..180 の数値であること" };
  }

  if (radiusMeters === undefined || radiusMeters === null) {
    return { ok: true, value: { lat, lng } };
  }
  if (
    typeof radiusMeters !== "number" ||
    !Number.isFinite(radiusMeters) ||
    radiusMeters <= 0 ||
    radiusMeters > MAX_REQUEST_RADIUS_METERS
  ) {
    return {
      ok: false,
      code: "INVALID_RADIUS",
      message: `radiusMeters は 1..${MAX_REQUEST_RADIUS_METERS} の数値であること`,
    };
  }
  return { ok: true, value: { lat, lng, radiusMeters } };
}
