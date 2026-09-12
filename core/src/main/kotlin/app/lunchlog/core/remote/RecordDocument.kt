package app.lunchlog.core.remote

import app.lunchlog.core.geo.Geohash
import app.lunchlog.core.model.GeoLocation
import app.lunchlog.core.model.LocationSource
import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.Photo
import app.lunchlog.core.model.PhotoKind
import app.lunchlog.core.model.RecordSource
import java.time.Instant
import java.time.format.DateTimeParseException

/**
 * 記録と Firestore ドキュメントの相互変換 (SPEC §7.2)。
 *
 * `Map<String, Any?>` を介すことで、このモジュールを Firebase SDK から切り離し、
 * 端末なしで変換規則をテストできるようにしている。
 *
 * 日時は **ISO-8601 (UTC) の文字列**で保存する。文字列のまま辞書順で並べても
 * 時刻順になるため、一覧の並べ替え (SPEC §7.5) はこのままで成り立つ。
 *
 * 読み込みは**壊れた値で落とさない**。別の端末や将来のバージョンが書いた
 * ドキュメントを読むことがあるため、知らない項目は無視し、欠けた項目は既定値にする。
 */
object RecordDocument {

    fun toMap(record: LunchRecord): Map<String, Any?> = buildMap {
        put("id", record.id)
        put("eatenAt", record.eatenAt.toString())
        put("mealType", record.mealType.id)
        put("dishName", record.dishName)
        put("restaurantName", record.restaurantName)
        put("restaurantId", record.restaurantId)
        put("price", record.price)
        put("currency", record.currency)
        put("rating", record.rating)
        put("memo", record.memo)
        put("tags", record.tags)
        put("tabelogUrl", record.tabelogUrl)
        put("source", record.source.id)
        put("photos", record.photos.map(::photoToMap))
        put("location", record.location?.let(::locationToMap))
        put("createdAt", record.createdAt.toString())
        put("updatedAt", record.updatedAt.toString())
        put("deletedAt", record.deletedAt?.toString())
    }

    /**
     * ドキュメントから記録を復元する。復元できない場合は null。
     *
     * id と eatenAt が読めないものは記録として成立しないため捨てる。
     * それ以外の欠損は既定値で埋める。
     */
    fun fromMap(map: Map<String, Any?>): LunchRecord? {
        val id = map.string("id") ?: return null
        val eatenAt = map.instant("eatenAt") ?: return null
        val createdAt = map.instant("createdAt") ?: eatenAt
        val updatedAt = map.instant("updatedAt") ?: createdAt

        return LunchRecord(
            id = id,
            eatenAt = eatenAt,
            mealType = MealType.fromId(map.string("mealType")),
            dishName = map.string("dishName"),
            restaurantName = map.string("restaurantName"),
            restaurantId = map.string("restaurantId"),
            photos = map.maps("photos").mapNotNull(::photoFromMap).sortedBy { it.order },
            location = (map["location"] as? Map<*, *>)?.let { locationFromMap(it) },
            price = map.int("price"),
            currency = map.string("currency") ?: "JPY",
            rating = map.int("rating"),
            memo = map.string("memo"),
            tags = map.strings("tags"),
            tabelogUrl = map.string("tabelogUrl"),
            source = RecordSource.fromId(map.string("source")),
            createdAt = createdAt,
            updatedAt = updatedAt,
            deletedAt = map.instant("deletedAt"),
        )
    }

    private fun photoToMap(photo: Photo): Map<String, Any?> = mapOf(
        "id" to photo.id,
        "kind" to photo.kind.id,
        "thumbPath" to photo.thumbPath,
        "displayPath" to photo.displayPath,
        "originalPath" to photo.originalPath,
        "width" to photo.width,
        "height" to photo.height,
        "takenAt" to photo.takenAt?.toString(),
        "order" to photo.order,
    )

    private fun photoFromMap(map: Map<*, *>): Photo? {
        val id = map.string("id") ?: return null
        return Photo(
            id = id,
            kind = PhotoKind.fromId(map.string("kind")),
            // localPath は端末ごとの値なので同期しない。
            thumbPath = map.string("thumbPath"),
            displayPath = map.string("displayPath"),
            originalPath = map.string("originalPath"),
            width = map.int("width"),
            height = map.int("height"),
            takenAt = map.instant("takenAt"),
            order = map.int("order") ?: 0,
        )
    }

    private fun locationToMap(location: GeoLocation): Map<String, Any?> = mapOf(
        "lat" to location.lat,
        "lng" to location.lng,
        "geohash" to Geohash.encode(location),
        "accuracy" to location.accuracy?.toDouble(),
        "source" to location.source.id,
    )

    private fun locationFromMap(map: Map<*, *>): GeoLocation? {
        val lat = map.double("lat") ?: return null
        val lng = map.double("lng") ?: return null
        // 範囲外の座標で例外を投げると、1 件の壊れたドキュメントで一覧全体が落ちる。
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return GeoLocation(
            lat = lat,
            lng = lng,
            accuracy = map.double("accuracy")?.toFloat(),
            source = LocationSource.fromId(map.string("source")),
        )
    }
}

private fun Map<*, *>.string(key: String): String? = (this[key] as? String)?.takeIf { it.isNotBlank() }

private fun Map<*, *>.int(key: String): Int? = when (val value = this[key]) {
    is Int -> value
    is Long -> value.toInt()
    is Number -> value.toInt()
    else -> null
}

private fun Map<*, *>.double(key: String): Double? = (this[key] as? Number)?.toDouble()

private fun Map<*, *>.instant(key: String): Instant? = (this[key] as? String)?.let {
    try {
        Instant.parse(it)
    } catch (_: DateTimeParseException) {
        null
    }
}

private fun Map<*, *>.strings(key: String): List<String> =
    (this[key] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

private fun Map<*, *>.maps(key: String): List<Map<*, *>> =
    (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
