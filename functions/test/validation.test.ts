import { describe, expect, it } from "vitest";
import { MAX_REQUEST_RADIUS_METERS, parseNearbyQuery } from "../src/http/validation";

describe("parseNearbyQuery", () => {
  it("緯度経度だけで通る", () => {
    const r = parseNearbyQuery({ lat: 35.6581, lng: 139.7016 });
    expect(r).toEqual({ ok: true, value: { lat: 35.6581, lng: 139.7016 } });
  });

  it("半径を指定できる", () => {
    const r = parseNearbyQuery({ lat: 35.6581, lng: 139.7016, radiusMeters: 300 });
    expect(r.ok && r.value.radiusMeters).toBe(300);
  });

  it.each([
    ["本体がnull", null],
    ["本体が文字列", "lat=1"],
    ["latがない", { lng: 139 }],
    ["latが文字列", { lat: "35.6", lng: 139 }],
    ["latが範囲外", { lat: 91, lng: 139 }],
    ["lngが範囲外", { lat: 35, lng: 181 }],
    ["latがNaN", { lat: Number.NaN, lng: 139 }],
  ])("%s は弾く", (_label, body) => {
    expect(parseNearbyQuery(body).ok).toBe(false);
  });

  it("大きすぎる半径は弾く", () => {
    // 大きな半径はそのまま費用になるため上限を設けている。
    const r = parseNearbyQuery({
      lat: 35.6581,
      lng: 139.7016,
      radiusMeters: MAX_REQUEST_RADIUS_METERS + 1,
    });
    expect(r.ok).toBe(false);
    expect(!r.ok && r.code).toBe("INVALID_RADIUS");
  });

  it("0以下の半径は弾く", () => {
    expect(parseNearbyQuery({ lat: 35, lng: 139, radiusMeters: 0 }).ok).toBe(false);
    expect(parseNearbyQuery({ lat: 35, lng: 139, radiusMeters: -100 }).ok).toBe(false);
  });
});
