package app.lunchlog.data.local

import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * リスト型の値と JSON の相互変換 (Room の 1 列に収めるため)。
 *
 * Android に同梱の org.json を使い、依存を増やさない。
 * 読み込みは壊れた値で落とさない — 1 件の不正なレコードで一覧全体が
 * 表示できなくなるのを避ける (SPEC §7.2 の方針と揃える)。
 */
object JsonColumns {

    fun encode(photos: List<Photo>): String {
        val array = JSONArray()
        photos.forEach { photo ->
            array.put(
                JSONObject().apply {
                    put("id", photo.id)
                    put("kind", photo.kind.id)
                    putOpt("localPath", photo.localPath)
                    putOpt("thumbPath", photo.thumbPath)
                    putOpt("displayPath", photo.displayPath)
                    putOpt("originalPath", photo.originalPath)
                    putOpt("width", photo.width)
                    putOpt("height", photo.height)
                    putOpt("takenAt", photo.takenAt?.toString())
                    put("order", photo.order)
                },
            )
        }
        return array.toString()
    }

    fun decode(json: String): List<Photo> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Photo(
                id = id,
                kind = PhotoKind.fromId(obj.optString("kind")),
                localPath = obj.optStringOrNull("localPath"),
                thumbPath = obj.optStringOrNull("thumbPath"),
                displayPath = obj.optStringOrNull("displayPath"),
                originalPath = obj.optStringOrNull("originalPath"),
                width = obj.optIntOrNull("width"),
                height = obj.optIntOrNull("height"),
                takenAt = obj.optStringOrNull("takenAt")?.let { runCatching { Instant.parse(it) }.getOrNull() },
                order = obj.optInt("order", index),
            )
        }
    }.getOrDefault(emptyList())

    fun encodeStrings(values: List<String>): String = JSONArray(values).toString()

    fun decodeStrings(json: String): List<String> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotEmpty) }
    }.getOrDefault(emptyList())

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.optIntOrNull(key: String): Int? = if (isNull(key)) null else optInt(key)
}
