package app.lunchlog.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.lunchlog.core.model.GeoLocation
import app.lunchlog.core.model.LocationSource
import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import app.lunchlog.core.model.RecordSource
import java.time.Instant

/**
 * Room に保存する記録 (SPEC §7.2)。
 *
 * Room は Single Source of Truth (SPEC §5.2)。画面はここだけを見て描き、
 * Firestore との同期は裏で走る。オフラインでも記録・閲覧・編集ができる (F-111)。
 *
 * 写真は件数が少なく常に記録と一緒に読むため、別テーブルにせず JSON 1 列に入れる。
 * 写真だけを検索する要件は今のところない。
 */
@Entity(tableName = "records")
data class RecordEntity(
    @PrimaryKey val id: String,
    val eatenAtMillis: Long,
    val mealType: String,
    val dishName: String?,
    val restaurantName: String?,
    val restaurantId: String?,
    val photosJson: String,
    val lat: Double?,
    val lng: Double?,
    val locationAccuracy: Float?,
    val locationSource: String?,
    val price: Int?,
    val currency: String,
    val rating: Int?,
    val memo: String?,
    val tagsJson: String,
    val tabelogUrl: String?,
    val source: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val deletedAtMillis: Long?,

    /**
     * Firestore へ未反映か。保存時に true、同期成功で false にする。
     * 端末ごとの状態なので Firestore には送らない。
     */
    val needsSync: Boolean = true,
)

fun RecordEntity.toDomain(): LunchRecord = LunchRecord(
    id = id,
    eatenAt = Instant.ofEpochMilli(eatenAtMillis),
    mealType = MealType.fromId(mealType),
    dishName = dishName,
    restaurantName = restaurantName,
    restaurantId = restaurantId,
    photos = JsonColumns.decode(photosJson),
    location = if (lat != null && lng != null) {
        GeoLocation(lat, lng, locationAccuracy, LocationSource.fromId(locationSource))
    } else {
        null
    },
    price = price,
    currency = currency,
    rating = rating,
    memo = memo,
    tags = JsonColumns.decodeStrings(tagsJson),
    tabelogUrl = tabelogUrl,
    source = RecordSource.fromId(source),
    createdAt = Instant.ofEpochMilli(createdAtMillis),
    updatedAt = Instant.ofEpochMilli(updatedAtMillis),
    deletedAt = deletedAtMillis?.let(Instant::ofEpochMilli),
)

fun LunchRecord.toEntity(needsSync: Boolean = true): RecordEntity = RecordEntity(
    id = id,
    eatenAtMillis = eatenAt.toEpochMilli(),
    mealType = mealType.id,
    dishName = dishName,
    restaurantName = restaurantName,
    restaurantId = restaurantId,
    photosJson = JsonColumns.encode(photos),
    lat = location?.lat,
    lng = location?.lng,
    locationAccuracy = location?.accuracy,
    locationSource = location?.source?.id,
    price = price,
    currency = currency,
    rating = rating,
    memo = memo,
    tagsJson = JsonColumns.encodeStrings(tags),
    tabelogUrl = tabelogUrl,
    source = source.id,
    createdAtMillis = createdAt.toEpochMilli(),
    updatedAtMillis = updatedAt.toEpochMilli(),
    deletedAtMillis = deletedAt?.toEpochMilli(),
    needsSync = needsSync,
)
