import { describe, expect, it } from "vitest";
import { gridKey } from "../src/places/grid";

describe("gridKey", () => {
  it("同じ場所は同じキーになる", () => {
    expect(gridKey(35.6581, 139.7016)).toBe(gridKey(35.6581, 139.7016));
  });

  it("数メートルのずれでは同じキーになる", () => {
    // 端末の位置は呼び出しごとに数 m 揺れる。ここでキャッシュが外れると費用が跳ねる。
    const a = gridKey(35.6581, 139.7016);
    const b = gridKey(35.65812, 139.70162);
    expect(b).toBe(a);
  });

  it("数百メートル離れると別のキーになる", () => {
    const shibuya = gridKey(35.6581, 139.7016);
    const farther = gridKey(35.6631, 139.7016); // 約 550m 北
    expect(farther).not.toBe(shibuya);
  });

  it("経度側も緯度に応じて約100mで区切られる", () => {
    // 東京 (北緯 35 度) では経度 0.0011 度 ≒ 100m。
    const base = gridKey(35.6581, 139.7016);
    const eastSmall = gridKey(35.6581, 139.70165); // 約 5m 東
    const eastLarge = gridKey(35.6581, 139.7056); // 約 360m 東
    expect(eastSmall).toBe(base);
    expect(eastLarge).not.toBe(base);
  });

  it("高緯度でもキーを作れる", () => {
    // cos(緯度) が 0 に近づいても発散しないこと。
    expect(() => gridKey(89.9, 10)).not.toThrow();
    expect(gridKey(89.9, 10)).toMatch(/^-?\d+:-?\d+$/);
  });

  it("不正な座標は例外", () => {
    expect(() => gridKey(Number.NaN, 0)).toThrow();
    expect(() => gridKey(0, Number.POSITIVE_INFINITY)).toThrow();
  });
});
