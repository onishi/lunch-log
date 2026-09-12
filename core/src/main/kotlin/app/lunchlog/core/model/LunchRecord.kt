package app.lunchlog.core.model

import java.time.Instant

/** 記録の作成経路 (SPEC §7.2 `source`)。 */
enum class RecordSource(val id: String) {
    CAMERA("camera"),
    GALLERY("gallery"),
    SHARE("share"),
    WEB("web"),
    ;

    companion object {
        fun fromId(id: String?): RecordSource = entries.firstOrNull { it.id == id } ?: CAMERA
    }
}

/**
 * 食事 1 回の記録 (SPEC §7.2)。
 *
 * MVP で使うのは id / eatenAt / mealType / dishName / restaurantName /
 * photos / location / タイムスタンプ類まで。price 以降は Phase 2 で使い始めるが、
 * 後からのマイグレーションを減らすためモデルには最初から持たせておく
 * (PLAN.md Phase 1-2)。
 *
 * [eatenAt] は UTC。表示のときだけ利用者のタイムゾーンへ変換する。
 */
data class LunchRecord(
    val id: String,
    val eatenAt: Instant,
    val mealType: MealType = MealType.LUNCH,
    val dishName: String? = null,
    val restaurantName: String? = null,
    val restaurantId: String? = null,
    val photos: List<Photo> = emptyList(),
    val location: GeoLocation? = null,
    val price: Int? = null,
    val currency: String = "JPY",
    val rating: Int? = null,
    val memo: String? = null,
    val tags: List<String> = emptyList(),
    val tabelogUrl: String? = null,
    val source: RecordSource = RecordSource.CAMERA,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null,
) {
    /** 論理削除済みか (SPEC F-112)。一覧では除外する。 */
    val isDeleted: Boolean get() = deletedAt != null

    /** 一覧のカードに出す写真。料理写真を優先し、なければ先頭。 */
    val coverPhoto: Photo?
        get() = photos.filter { it.kind == PhotoKind.DISH }.minByOrNull { it.order }
            ?: photos.minByOrNull { it.order }

    /** 表示上の題名。メニュー名がなければ店名、それもなければ null。 */
    val displayTitle: String? get() = dishName?.takeIf { it.isNotBlank() } ?: restaurantName?.takeIf { it.isNotBlank() }
}
