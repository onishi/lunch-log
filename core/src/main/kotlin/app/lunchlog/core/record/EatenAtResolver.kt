package app.lunchlog.core.record

import java.time.Duration
import java.time.Instant

/**
 * 記録の日時 [eatenAt] の既定値を決める (SPEC F-106)。
 *
 * 「既定は写真の撮影日時 (EXIF)、無ければ保存時刻」。ただし EXIF の日時は
 * 信用しきれない — 日付が未設定のカメラや、取り込み時に壊れた値が入ることがある。
 * 明らかにおかしい値は黙って採用せず、保存時刻に落とす。
 */
object EatenAtResolver {

    /** これより古い撮影日時は壊れているとみなす (カメラの日付未設定でよくある値)。 */
    private val EARLIEST_PLAUSIBLE = Instant.parse("2000-01-01T00:00:00Z")

    /** 未来方向のずれの許容。端末の時計が少し進んでいる程度は許す。 */
    private val FUTURE_TOLERANCE: Duration = Duration.ofDays(1)

    fun resolve(exifTakenAt: Instant?, now: Instant): Instant {
        if (exifTakenAt == null) return now
        if (exifTakenAt.isBefore(EARLIEST_PLAUSIBLE)) return now
        if (exifTakenAt.isAfter(now.plus(FUTURE_TOLERANCE))) return now
        return exifTakenAt
    }

    /** 複数枚から決めるとき: 最も古い撮影日時を採る (料理が出てくる前のメニュー写真を含むため)。 */
    fun resolveFromPhotos(takenAtList: List<Instant?>, now: Instant): Instant {
        val plausible = takenAtList.mapNotNull { it }
            .filter { !it.isBefore(EARLIEST_PLAUSIBLE) && !it.isAfter(now.plus(FUTURE_TOLERANCE)) }
        return plausible.minOrNull() ?: now
    }
}
