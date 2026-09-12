package app.lunchlog.core.photo

/**
 * アップロード前のリサイズ寸法の計算 (SPEC §4, §6.7)。
 *
 * 3 段構成: サムネイル 320px / 表示用 2048px / 原本。
 * 原本の保持は Phase 3 (F-117) だが、寸法の決め方はここに集約しておく。
 */
object ImageResize {

    /** SPEC §4: 表示用は長辺 2048px / JPEG 品質 85。 */
    const val DISPLAY_MAX_LONG_EDGE = 2048
    const val DISPLAY_JPEG_QUALITY = 85

    /** SPEC §6.7: サムネイルは長辺 320px / JPEG 品質 70。 */
    const val THUMBNAIL_MAX_LONG_EDGE = 320
    const val THUMBNAIL_JPEG_QUALITY = 70

    data class Size(val width: Int, val height: Int) {
        init {
            require(width > 0 && height > 0) { "寸法は正の数であること: ${width}x$height" }
        }

        val longEdge: Int get() = maxOf(width, height)
    }

    /**
     * 長辺を [maxLongEdge] に収める寸法を返す。縦横比は保つ。
     *
     * 元が既に小さければ**拡大せずそのまま返す**。引き伸ばしても情報は増えず、
     * ファイルサイズだけが増えるため。
     */
    fun fit(source: Size, maxLongEdge: Int): Size {
        require(maxLongEdge > 0) { "maxLongEdge は正の数であること: $maxLongEdge" }
        if (source.longEdge <= maxLongEdge) return source

        val scale = maxLongEdge.toDouble() / source.longEdge
        // 丸めで 0 にならないよう最低 1px を保証する (極端に細長い画像への備え)。
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return Size(width, height)
    }

    fun display(source: Size): Size = fit(source, DISPLAY_MAX_LONG_EDGE)

    fun thumbnail(source: Size): Size = fit(source, THUMBNAIL_MAX_LONG_EDGE)
}
