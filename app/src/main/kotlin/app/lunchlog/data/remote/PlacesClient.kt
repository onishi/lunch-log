package app.lunchlog.data.remote

import app.lunchlog.BuildConfig
import app.lunchlog.core.restaurant.RestaurantSuggestion
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 店舗候補 API の呼び出し (SPEC §8 `/v1/places/nearby`)。
 *
 * Places の API キーはサーバだけが持つため、端末はこの中継を叩く。
 *
 * **失敗しても例外を投げず空リストを返す。** 候補が出ないだけで記録は
 * 手入力で続けられる (SPEC §6.1 のフォールバック)。ここで例外を投げると
 * 電波の悪い店内で保存できなくなる。
 */
class PlacesClient(private val auth: FirebaseAuth) {

    suspend fun nearby(lat: Double, lng: Double): List<RestaurantSuggestion> {
        val baseUrl = BuildConfig.FUNCTIONS_BASE_URL
        if (baseUrl.isEmpty()) return emptyList() // 未設定なら機能ごと無効

        val token = runCatching { auth.currentUser?.getIdToken(false)?.await()?.token }.getOrNull()
            ?: return emptyList()

        return withContext(Dispatchers.IO) {
            runCatching { request(baseUrl, token, lat, lng) }.getOrDefault(emptyList())
        }
    }

    private fun request(baseUrl: String, token: String, lat: Double, lng: Double): List<RestaurantSuggestion> {
        val connection = (URL("$baseUrl/v1/places/nearby").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }

        return try {
            connection.outputStream.use {
                it.write(JSONObject(mapOf("lat" to lat, "lng" to lng)).toString().toByteArray())
            }
            if (connection.responseCode !in 200..299) return emptyList()

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parse(body: String): List<RestaurantSuggestion> {
        val candidates = JSONObject(body).optJSONArray("candidates") ?: return emptyList()
        return (0 until candidates.length()).mapNotNull { index ->
            val item = candidates.optJSONObject(index) ?: return@mapNotNull null
            val name = item.optString("name").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            RestaurantSuggestion(
                name = name,
                placeId = item.optString("placeId").takeIf { it.isNotEmpty() },
                distanceMeters = if (item.isNull("distanceMeters")) null else item.optInt("distanceMeters"),
                source = RestaurantSuggestion.Source.NEARBY,
            )
        }
    }

    private companion object {
        // 店内で待たされないよう短めに。取れなければ履歴の候補に落とす。
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 5_000
    }
}
