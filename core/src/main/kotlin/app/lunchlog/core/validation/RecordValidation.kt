package app.lunchlog.core.validation

import app.lunchlog.core.model.LunchRecord

/** 記録を保存できない理由 (SPEC §9.1「入力最小化の設計原則」)。 */
enum class RecordError {
    /** 写真もメニュー名もない。保存に必須なのはこのどちらか一方だけ。 */
    EMPTY_RECORD,
    RATING_OUT_OF_RANGE,
    NEGATIVE_PRICE,
    DISH_NAME_TOO_LONG,
    RESTAURANT_NAME_TOO_LONG,
    MEMO_TOO_LONG,
    TOO_MANY_PHOTOS,
}

sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: List<RecordError>) : ValidationResult
}

/**
 * 保存前の検証。
 *
 * 設計方針 (SPEC §9.1): **保存に必須なのは「写真 1 枚」か「メニュー名」の
 * どちらか一方だけ。** 店名も日時も自動で埋まるので、利用者に required を
 * 増やさない。ここを緩くしておかないと記録が続かない。
 */
object RecordValidator {

    const val MAX_PHOTOS = 10 // SPEC F-101
    const val MAX_NAME_LENGTH = 200
    const val MAX_MEMO_LENGTH = 2000

    fun validate(record: LunchRecord): ValidationResult {
        val errors = buildList {
            if (record.photos.isEmpty() && record.dishName.isNullOrBlank()) {
                add(RecordError.EMPTY_RECORD)
            }
            if (record.photos.size > MAX_PHOTOS) add(RecordError.TOO_MANY_PHOTOS)
            record.rating?.let { if (it !in 1..5) add(RecordError.RATING_OUT_OF_RANGE) }
            record.price?.let { if (it < 0) add(RecordError.NEGATIVE_PRICE) }
            if ((record.dishName?.length ?: 0) > MAX_NAME_LENGTH) add(RecordError.DISH_NAME_TOO_LONG)
            if ((record.restaurantName?.length ?: 0) > MAX_NAME_LENGTH) add(RecordError.RESTAURANT_NAME_TOO_LONG)
            if ((record.memo?.length ?: 0) > MAX_MEMO_LENGTH) add(RecordError.MEMO_TOO_LONG)
        }
        return if (errors.isEmpty()) ValidationResult.Valid else ValidationResult.Invalid(errors)
    }

    /**
     * 保存前に文字列を整える。利用者に注意を強いる代わりにこちらで吸収する。
     * タグは前後の空白を落とし、空と重複を除く (順序は保つ)。
     */
    fun normalize(record: LunchRecord): LunchRecord = record.copy(
        dishName = record.dishName?.trim()?.takeIf { it.isNotEmpty() },
        restaurantName = record.restaurantName?.trim()?.takeIf { it.isNotEmpty() },
        memo = record.memo?.trim()?.takeIf { it.isNotEmpty() },
        tags = record.tags.map { it.trim().removePrefix("#") }
            .filter { it.isNotEmpty() }
            .distinct(),
    )
}
