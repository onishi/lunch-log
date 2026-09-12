/**
 * Places API の結果をキャッシュするためのグリッドキー (SPEC §6.1, §13)。
 *
 * 呼び出しごとに座標が数 m ずれるだけでキャッシュが外れると、コストが
 * 利用回数に比例して増える。そこで座標を約 100m のマスに丸め、
 * 同じマスからの要求は同じキャッシュを引く。
 *
 * 緯度 0.001 度 ≒ 111m。経度 1 度の距離は緯度によって縮むため、
 * 経度側は cos(緯度) で補正して、どの緯度でも概ね 100m 四方に保つ。
 */
export const GRID_SIZE_METERS = 100;

const METERS_PER_LAT_DEGREE = 111_320;

export function gridKey(lat: number, lng: number): string {
  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    throw new Error(`座標が不正: lat=${lat}, lng=${lng}`);
  }
  const latStep = GRID_SIZE_METERS / METERS_PER_LAT_DEGREE;

  // 高緯度では cos が 0 に近づき経度の刻みが発散するため、下限を設ける。
  const cosLat = Math.max(Math.cos((lat * Math.PI) / 180), 0.01);
  const lngStep = latStep / cosLat;

  const latCell = Math.floor(lat / latStep);
  const lngCell = Math.floor(lng / lngStep);
  return `${latCell}:${lngCell}`;
}
