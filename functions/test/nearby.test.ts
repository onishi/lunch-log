import { describe, expect, it, vi } from "vitest";
import {
  findNearbyPlaces,
  MAX_CANDIDATES,
  rankCandidates,
  toCandidates,
  type PlacesApiPlace,
} from "../src/places/nearby";
import type { PlaceCandidate } from "../src/places/types";

function candidate(name: string, distanceMeters: number, placeId = name): PlaceCandidate {
  return { placeId, name, lat: 35.6581, lng: 139.7016, distanceMeters };
}

describe("rankCandidates", () => {
  it("距離の近い順に並べる", () => {
    const ranked = rankCandidates([candidate("遠", 300), candidate("近", 20), candidate("中", 150)]);
    expect(ranked.map((c) => c.name)).toEqual(["近", "中", "遠"]);
  });

  it("上位5件に絞る", () => {
    const many = Array.from({ length: 12 }, (_, i) => candidate(`店${i}`, i * 10));
    expect(rankCandidates(many)).toHaveLength(MAX_CANDIDATES);
  });

  it("同じ店が重複しても1件にする", () => {
    const ranked = rankCandidates([candidate("A", 10, "p1"), candidate("A", 12, "p1")]);
    expect(ranked).toHaveLength(1);
    expect(ranked[0]?.distanceMeters).toBe(10);
  });

  it("距離が同じなら名前順で安定させる", () => {
    const ranked = rankCandidates([candidate("い", 50), candidate("あ", 50)]);
    expect(ranked.map((c) => c.name)).toEqual(["あ", "い"]);
  });
});

describe("findNearbyPlaces", () => {
  it("150mで見つかればそれ以上広げない", async () => {
    const search = vi.fn().mockResolvedValue([candidate("◯◯食堂", 30)]);
    const found = await findNearbyPlaces({ lat: 35.6581, lng: 139.7016 }, search);

    expect(found).toHaveLength(1);
    expect(search).toHaveBeenCalledTimes(1);
    expect(search.mock.calls[0]?.[0]).toMatchObject({ radiusMeters: 150 });
  });

  it("150mで見つからなければ300mまで広げる", async () => {
    const search = vi
      .fn()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([candidate("△△亭", 250)]);

    const found = await findNearbyPlaces({ lat: 35.6581, lng: 139.7016 }, search);

    expect(found.map((c) => c.name)).toEqual(["△△亭"]);
    expect(search).toHaveBeenCalledTimes(2);
    expect(search.mock.calls[1]?.[0]).toMatchObject({ radiusMeters: 300 });
  });

  it("どこにも無ければ空を返す", async () => {
    // SPEC §6.1: 候補が無いこと自体は失敗ではない。手入力に落ちる。
    const search = vi.fn().mockResolvedValue([]);
    await expect(findNearbyPlaces({ lat: 0, lng: 0 }, search)).resolves.toEqual([]);
    expect(search).toHaveBeenCalledTimes(2);
  });

  it("半径が指定されたら広げない", async () => {
    const search = vi.fn().mockResolvedValue([]);
    await findNearbyPlaces({ lat: 35.6581, lng: 139.7016, radiusMeters: 500 }, search);
    expect(search).toHaveBeenCalledTimes(1);
  });
});

describe("toCandidates", () => {
  const origin = { lat: 35.6581, lng: 139.7016 };

  it("必要な項目を取り出して距離を計算する", () => {
    const places: PlacesApiPlace[] = [
      {
        id: "ChIJ1",
        displayName: { text: "◯◯食堂" },
        shortFormattedAddress: "渋谷区道玄坂1-2-3",
        location: { latitude: 35.6585, longitude: 139.702 },
        primaryType: "restaurant",
      },
    ];
    const [c] = toCandidates(places, origin);
    expect(c).toMatchObject({ placeId: "ChIJ1", name: "◯◯食堂", primaryType: "restaurant" });
    expect(c?.distanceMeters).toBeGreaterThan(0);
    expect(c?.distanceMeters).toBeLessThan(100);
  });

  it("項目が欠けた要素は捨てる", () => {
    // 外部のデータなので欠けていても落とさない。使えるものだけ通す。
    const places: PlacesApiPlace[] = [
      { displayName: { text: "IDなし" }, location: { latitude: 35.6, longitude: 139.7 } },
      { id: "no-name", location: { latitude: 35.6, longitude: 139.7 } },
      { id: "no-location", displayName: { text: "座標なし" } },
      { id: "ok", displayName: { text: "使える店" }, location: { latitude: 35.6, longitude: 139.7 } },
    ];
    expect(toCandidates(places, origin).map((c) => c.name)).toEqual(["使える店"]);
  });

  it("placesが未定義でも空配列", () => {
    expect(toCandidates(undefined, origin)).toEqual([]);
  });

  it("住所はshortFormattedAddressを優先する", () => {
    const places: PlacesApiPlace[] = [
      {
        id: "p",
        displayName: { text: "店" },
        shortFormattedAddress: "短い住所",
        formattedAddress: "長い住所",
        location: { latitude: 35.6, longitude: 139.7 },
      },
    ];
    expect(toCandidates(places, origin)[0]?.address).toBe("短い住所");
  });
});
