package app.lunchlog.core.timeline

import app.lunchlog.core.model.LunchRecord
import app.lunchlog.core.model.MealType
import java.time.LocalDate
import java.time.ZoneId

/** タイムラインの 1 日分 (SPEC F-201)。 */
data class DaySection(
    val date: LocalDate,
    val records: List<LunchRecord>,
)

/**
 * 記録を日付ごとのセクションにまとめる (SPEC F-201)。
 *
 * 日付の区切りは**利用者のタイムゾーン**で決める。UTC で区切ると、
 * 深夜や早朝の記録が前日・翌日に紛れ込む。
 */
object TimelineGrouping {

    /**
     * @param mealTypes 表示する食事種別。既定はランチのみ (SPEC F-206)。
     *                  空集合を渡すと絞り込みをしない (全種別を表示)。
     */
    fun group(
        records: List<LunchRecord>,
        zone: ZoneId,
        mealTypes: Set<MealType> = setOf(MealType.LUNCH),
    ): List<DaySection> =
        records
            .asSequence()
            .filterNot { it.isDeleted } // 論理削除は一覧に出さない (SPEC F-112)
            .filter { mealTypes.isEmpty() || it.mealType in mealTypes }
            .groupBy { it.eatenAt.atZone(zone).toLocalDate() }
            .map { (date, dayRecords) ->
                DaySection(date, dayRecords.sortedByDescending { it.eatenAt })
            }
            .sortedByDescending { it.date }
            .toList()
}
