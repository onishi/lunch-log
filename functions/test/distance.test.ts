import { describe, expect, it } from "vitest";
import { haversineMeters } from "../src/places/distance";

describe("haversineMeters", () => {
  it("同じ点なら0", () => {
    expect(haversineMeters({ lat: 35.6581, lng: 139.7016 }, { lat: 35.6581, lng: 139.7016 })).toBe(0);
  });

  it("緯度0.001度は約111m", () => {
    const d = haversineMeters({ lat: 35.0, lng: 139.0 }, { lat: 35.001, lng: 139.0 });
    expect(d).toBeGreaterThan(105);
    expect(d).toBeLessThan(115);
  });

  it("東京駅と大阪駅は約400km", () => {
    const d = haversineMeters({ lat: 35.6812, lng: 139.7671 }, { lat: 34.7025, lng: 135.4959 });
    expect(d).toBeGreaterThan(390_000);
    expect(d).toBeLessThan(410_000);
  });
});
