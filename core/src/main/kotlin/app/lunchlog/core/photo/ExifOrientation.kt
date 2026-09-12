package app.lunchlog.core.photo

/**
 * EXIF の Orientation タグの解釈 (SPEC §10)。
 *
 * リサイズのときに向きを反映しないと、横向きの料理写真がそのまま保存される。
 * EXIF から GPS は落とすが**向きと撮影日時は残す**ため、向きの解釈はここで行う。
 *
 * 値は EXIF 規格の 1..8。未知の値は回転なしとして扱い、読み込みを失敗させない。
 */
enum class ExifOrientation(
    val exifValue: Int,
    val rotationDegrees: Int,
    val mirrored: Boolean,
) {
    NORMAL(1, 0, false),
    FLIP_HORIZONTAL(2, 0, true),
    ROTATE_180(3, 180, false),
    FLIP_VERTICAL(4, 180, true),
    TRANSPOSE(5, 90, true),
    ROTATE_90(6, 90, false),
    TRANSVERSE(7, 270, true),
    ROTATE_270(8, 270, false),
    ;

    /** 回転によって幅と高さが入れ替わるか。リサイズ後の寸法計算に必要。 */
    val swapsDimensions: Boolean get() = rotationDegrees == 90 || rotationDegrees == 270

    /** 向きを反映した後の寸法。 */
    fun applyTo(size: ImageResize.Size): ImageResize.Size =
        if (swapsDimensions) ImageResize.Size(size.height, size.width) else size

    companion object {
        fun fromExifValue(value: Int?): ExifOrientation =
            entries.firstOrNull { it.exifValue == value } ?: NORMAL
    }
}
