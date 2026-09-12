package app.lunchlog.core.model

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 食事種別 (SPEC F-116)。
 *
 * [id] は Firestore / Room / スプレッドシートに保存する値。表示名は端末側の
 * リソースで解決するため、ここには持たせない。
 */
enum class MealType(val id: String) {
    LUNCH("lunch"),
    BREAKFAST("breakfast"),
    DINNER("dinner"),
    CAFE("cafe"),
    SNACK("snack"),
    OTHER("other"),
    ;

    companion object {
        /** 保存値から復元する。未知の値は [OTHER] として扱い、読み込みを失敗させない。 */
        fun fromId(id: String?): MealType = entries.firstOrNull { it.id == id } ?: OTHER
    }
}

/**
 * 記録時刻から食事種別の既定値を推定する (SPEC F-116)。
 *
 * SPEC が定めているのは「11〜15 時ならランチ」だけで、他の時間帯の割り当ては
 * 実装上の判断。いずれも 1 タップで変更できる既定値にすぎないため、
 * 迷ったらランチ以外は緩めに倒す。
 */
object MealTypeInference {

    private val LUNCH_RANGE = LocalTime.of(11, 0)..LocalTime.of(14, 59, 59)
    private val BREAKFAST_RANGE = LocalTime.of(5, 0)..LocalTime.of(10, 59, 59)
    private val CAFE_RANGE = LocalTime.of(15, 0)..LocalTime.of(17, 59, 59)
    private val DINNER_RANGE = LocalTime.of(18, 0)..LocalTime.of(22, 59, 59)

    fun infer(at: Instant, zone: ZoneId): MealType = infer(at.atZone(zone).toLocalTime())

    fun infer(localTime: LocalTime): MealType = when (localTime) {
        in LUNCH_RANGE -> MealType.LUNCH
        in BREAKFAST_RANGE -> MealType.BREAKFAST
        in CAFE_RANGE -> MealType.CAFE
        in DINNER_RANGE -> MealType.DINNER
        else -> MealType.SNACK // 深夜 (23:00-4:59)
    }
}
