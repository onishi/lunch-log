import { INCLUDED_PRIMARY_TYPES, toCandidates, type PlacesApiPlace } from "./nearby";
import type { NearbyQuery, PlaceCandidate } from "./types";

/**
 * Places API (New) の searchNearby を呼ぶ。
 *
 * API キーをクライアントに置かないためサーバ経由にしている (SPEC §6.1)。
 * 応答の一部だけを使うので FieldMask を明示し、課金対象のフィールドを絞る。
 */
const ENDPOINT = "https://places.googleapis.com/v1/places:searchNearby";

const FIELD_MASK = [
  "places.id",
  "places.displayName",
  "places.shortFormattedAddress",
  "places.location",
  "places.primaryType",
].join(",");

export class PlacesApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = "PlacesApiError";
  }
}

export function createPlacesClient(apiKey: string, fetchImpl: typeof fetch = fetch) {
  return async function searchNearby(query: Required<NearbyQuery>): Promise<PlaceCandidate[]> {
    const response = await fetchImpl(ENDPOINT, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "X-Goog-Api-Key": apiKey,
        "X-Goog-FieldMask": FIELD_MASK,
      },
      body: JSON.stringify({
        includedPrimaryTypes: INCLUDED_PRIMARY_TYPES,
        maxResultCount: 10,
        rankPreference: "DISTANCE",
        languageCode: "ja",
        locationRestriction: {
          circle: {
            center: { latitude: query.lat, longitude: query.lng },
            radius: query.radiusMeters,
          },
        },
      }),
    });

    if (!response.ok) {
      const body = await response.text().catch(() => "");
      throw new PlacesApiError(response.status, `Places API が ${response.status}: ${body.slice(0, 200)}`);
    }

    const json = (await response.json()) as { places?: PlacesApiPlace[] };
    return toCandidates(json.places, { lat: query.lat, lng: query.lng });
  };
}
