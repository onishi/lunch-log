import { describe, expect, it, vi } from "vitest";
import { CACHE_TTL_MS, MemoryCacheStore, cacheKey, isFresh } from "../src/places/cache";
import { getNearbyPlaces } from "../src/places/service";
import type { PlaceCandidate } from "../src/places/types";

const shibuya = { lat: 35.6581, lng: 139.7016 };

function candidate(name: string, distanceMeters = 30): PlaceCandidate {
  return { placeId: name, name, lat: 35.6581, lng: 139.7016, distanceMeters };
}

describe("cacheKey", () => {
  it("半径が違えばキーも違う", () => {
    // 150m の結果を 300m の要求に使い回さないため。
    expect(cacheKey(35.6581, 139.7016, 150)).not.toBe(cacheKey(35.6581, 139.7016, 300));
  });

  it("同じマス・同じ半径なら同じキー", () => {
    expect(cacheKey(35.6581, 139.7016, 150)).toBe(cacheKey(35.65812, 139.70161, 150));
  });
});

describe("isFresh", () => {
  const entry = { candidates: [], cachedAt: 1_000_000 };

  it("24時間以内は有効", () => {
    expect(isFresh(entry, entry.cachedAt + CACHE_TTL_MS - 1)).toBe(true);
  });

  it("24時間ちょうどで期限切れ", () => {
    expect(isFresh(entry, entry.cachedAt + CACHE_TTL_MS)).toBe(false);
  });

  it("時計が巻き戻っていても期限内として扱う", () => {
    // 再取得しても直らないため、無駄な呼び出しを増やさない。
    expect(isFresh(entry, entry.cachedAt - 60_000)).toBe(true);
  });
});

describe("getNearbyPlaces", () => {
  it("初回はAPIを呼び、2回目はキャッシュから返す", async () => {
    const searchNearby = vi.fn().mockResolvedValue([candidate("◯◯食堂")]);
    const cache = new MemoryCacheStore();

    const first = await getNearbyPlaces(shibuya, { searchNearby, cache });
    const second = await getNearbyPlaces(shibuya, { searchNearby, cache });

    expect(first.cached).toBe(false);
    expect(second.cached).toBe(true);
    expect(second.candidates.map((c) => c.name)).toEqual(["◯◯食堂"]);
    expect(searchNearby).toHaveBeenCalledTimes(1);
  });

  it("数メートルずれた再呼び出しもキャッシュに当たる", async () => {
    const searchNearby = vi.fn().mockResolvedValue([candidate("◯◯食堂")]);
    const cache = new MemoryCacheStore();

    await getNearbyPlaces(shibuya, { searchNearby, cache });
    const again = await getNearbyPlaces({ lat: 35.65811, lng: 139.70161 }, { searchNearby, cache });

    expect(again.cached).toBe(true);
    expect(searchNearby).toHaveBeenCalledTimes(1);
  });

  it("24時間を過ぎたら取り直す", async () => {
    const searchNearby = vi.fn().mockResolvedValue([candidate("◯◯食堂")]);
    const cache = new MemoryCacheStore();
    let now = 1_000_000;

    await getNearbyPlaces(shibuya, { searchNearby, cache, now: () => now });
    now += CACHE_TTL_MS + 1;
    const after = await getNearbyPlaces(shibuya, { searchNearby, cache, now: () => now });

    expect(after.cached).toBe(false);
    expect(searchNearby).toHaveBeenCalledTimes(2);
  });

  it("空の結果もキャッシュする", async () => {
    // 人けのない場所で毎回 2 回ずつ課金されるのを防ぐ。
    const searchNearby = vi.fn().mockResolvedValue([]);
    const cache = new MemoryCacheStore();

    await getNearbyPlaces(shibuya, { searchNearby, cache });
    const second = await getNearbyPlaces(shibuya, { searchNearby, cache });

    expect(second.cached).toBe(true);
    expect(second.candidates).toEqual([]);
    expect(searchNearby).toHaveBeenCalledTimes(2); // 初回の 150m と 300m のみ
  });

  it("遠い場所は別のキャッシュになる", async () => {
    const searchNearby = vi.fn().mockResolvedValue([candidate("店")]);
    const cache = new MemoryCacheStore();

    await getNearbyPlaces(shibuya, { searchNearby, cache });
    const osaka = await getNearbyPlaces({ lat: 34.7025, lng: 135.4959 }, { searchNearby, cache });

    expect(osaka.cached).toBe(false);
    expect(searchNearby).toHaveBeenCalledTimes(2);
  });
});
