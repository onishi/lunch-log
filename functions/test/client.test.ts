import { describe, expect, it, vi } from "vitest";
import { createPlacesClient, PlacesApiError } from "../src/places/client";

const shibuya = { lat: 35.6581, lng: 139.7016, radiusMeters: 150 };

function jsonResponse(body: unknown, status = 200): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  } as unknown as Response;
}

describe("createPlacesClient", () => {
  it("APIキーとFieldMaskを送る", async () => {
    // キーは端末に置かずサーバだけが持つ (SPEC §6.1)。
    // FieldMask を絞るのは課金対象のフィールドを増やさないため。
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ places: [] }));
    await createPlacesClient("KEY", fetchImpl as unknown as typeof fetch)(shibuya);

    const [, init] = fetchImpl.mock.calls[0]!;
    const headers = init.headers as Record<string, string>;
    expect(headers["X-Goog-Api-Key"]).toBe("KEY");
    expect(headers["X-Goog-FieldMask"]).toContain("places.id");
    expect(headers["X-Goog-FieldMask"]).not.toContain("places.rating");
  });

  it("半径と種別を指定して検索する", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ places: [] }));
    await createPlacesClient("KEY", fetchImpl as unknown as typeof fetch)(shibuya);

    const body = JSON.parse((fetchImpl.mock.calls[0]![1] as { body: string }).body);
    expect(body.locationRestriction.circle.radius).toBe(150);
    expect(body.locationRestriction.circle.center).toEqual({ latitude: 35.6581, longitude: 139.7016 });
    expect(body.includedPrimaryTypes).toContain("restaurant");
    expect(body.rankPreference).toBe("DISTANCE");
    expect(body.languageCode).toBe("ja");
  });

  it("レスポンスを候補へ変換する", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      jsonResponse({
        places: [
          {
            id: "ChIJ1",
            displayName: { text: "◯◯食堂" },
            location: { latitude: 35.6585, longitude: 139.702 },
            primaryType: "restaurant",
          },
        ],
      }),
    );
    const candidates = await createPlacesClient("KEY", fetchImpl as unknown as typeof fetch)(shibuya);

    expect(candidates).toHaveLength(1);
    expect(candidates[0]).toMatchObject({ placeId: "ChIJ1", name: "◯◯食堂" });
    expect(candidates[0]!.distanceMeters).toBeGreaterThan(0);
  });

  it("エラー応答はPlacesApiErrorにする", async () => {
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({ error: "quota" }, 429));
    const client = createPlacesClient("KEY", fetchImpl as unknown as typeof fetch);

    await expect(client(shibuya)).rejects.toBeInstanceOf(PlacesApiError);
    await expect(client(shibuya)).rejects.toMatchObject({ status: 429 });
  });
});
