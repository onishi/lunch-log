import { getAuth } from "firebase-admin/auth";
import { initializeApp } from "firebase-admin/app";
import { onRequest } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import { logger } from "firebase-functions";

import { MemoryCacheStore } from "./places/cache";
import { createPlacesClient, PlacesApiError } from "./places/client";
import { getNearbyPlaces } from "./places/service";
import { parseNearbyQuery } from "./http/validation";
import { RateLimiter } from "./http/rateLimit";

initializeApp();

const placesApiKey = defineSecret("PLACES_API_KEY");

// 関数インスタンスが生きている間だけ効く一次キャッシュ (SPEC §6.1)。
// インスタンスをまたぐ永続キャッシュは、費用が見えてきた段階で Firestore に足す。
const cache = new MemoryCacheStore();
const rateLimiter = new RateLimiter();

/**
 * POST /v1/places/nearby (SPEC §8)
 *
 * 位置情報から近隣の飲食店候補を返す。API キーを端末に置かないための中継。
 */
export const placesNearby = onRequest(
  { region: "asia-northeast1", secrets: [placesApiKey], cors: false },
  async (req, res) => {
    if (req.method !== "POST") {
      res.status(405).json(errorBody("METHOD_NOT_ALLOWED", "POST のみ受け付けます", false));
      return;
    }

    const uid = await verifyIdToken(req.get("Authorization"));
    if (!uid) {
      res.status(401).json(errorBody("UNAUTHENTICATED", "ログインが必要です", false));
      return;
    }

    if (!rateLimiter.tryAcquire(uid)) {
      res.status(429).json(errorBody("RATE_LIMITED", "呼び出しが多すぎます", true));
      return;
    }

    const parsed = parseNearbyQuery(req.body);
    if (!parsed.ok) {
      res.status(400).json(errorBody(parsed.code, parsed.message, false));
      return;
    }

    try {
      const result = await getNearbyPlaces(parsed.value, {
        searchNearby: createPlacesClient(placesApiKey.value()),
        cache,
      });
      res.status(200).json({ candidates: result.candidates, cached: result.cached });
    } catch (error) {
      // 候補が取れなくても手入力で記録は続けられる (SPEC §6.1 のフォールバック)。
      // ここで落ちてもクライアントを止めないよう、retryable を伝えるだけにする。
      const status = error instanceof PlacesApiError ? error.status : 500;
      logger.error("店舗候補の取得に失敗", { status, error });
      res
        .status(502)
        .json(errorBody("PLACES_UNAVAILABLE", "店舗候補を取得できませんでした", status >= 500));
    }
  },
);

/** SPEC §8 の共通エラー形式。 */
function errorBody(code: string, message: string, retryable: boolean) {
  return { error: { code, message, retryable } };
}

async function verifyIdToken(header: string | undefined): Promise<string | null> {
  const token = header?.startsWith("Bearer ") ? header.slice("Bearer ".length) : null;
  if (!token) return null;
  try {
    return (await getAuth().verifyIdToken(token)).uid;
  } catch {
    return null;
  }
}
