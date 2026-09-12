package app.lunchlog.core.ocr

/**
 * メニュー写真の OCR 結果から、メニュー名と価格の候補を取り出す (SPEC §6.2)。
 *
 * 精度は期待しない。**あくまで候補の提示**であり、手入力を一級市民として
 * 扱う (SPEC §13 のリスク対策)。拾いすぎるより取りこぼすほうがましなので、
 * 価格を伴う行を優先し、それ以外は短い行だけを拾う。
 */
object MenuTextParser {

    /** メニュー名として妥当な長さ。長い行は説明文や注意書きとみなす。 */
    private val NAME_LENGTH = 1..30

    /** 候補の上限。チップで並べて選べる数に収める。 */
    const val MAX_CANDIDATES = 20

    /** SPEC §6.2 の「価格らしき数値」。¥980 / 980円 / 1,200円 のいずれも拾う。 */
    private val PRICE = Regex("""[¥￥]?\s?(\d{1,3}(?:,\d{3})+|\d{3,5})\s?円?""")

    /** メニュー名と価格の間によくある区切り。名前から取り除く。 */
    private val SEPARATORS = Regex("""[.・…‥:：\-―ー—\s]+$""")

    data class MenuCandidate(
        val name: String,
        val price: Int?,
        /** 価格を伴って見つかったか。伴うほうが確からしい。 */
        val hasPrice: Boolean,
    )

    fun parse(ocrText: String): List<MenuCandidate> {
        val lines = ocrText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val withPrice = mutableListOf<MenuCandidate>()
        val withoutPrice = mutableListOf<MenuCandidate>()

        for (line in lines) {
            val match = PRICE.find(line)
            if (match != null) {
                val name = line.removeRange(match.range).replace(SEPARATORS, "").trim()
                val price = match.groupValues[1].replace(",", "").toIntOrNull()
                if (name.length in NAME_LENGTH) {
                    withPrice += MenuCandidate(name, price, hasPrice = true)
                }
            } else if (line.length in NAME_LENGTH && line.any { it.isLetterOrDigit() }) {
                withoutPrice += MenuCandidate(line, price = null, hasPrice = false)
            }
        }

        // 価格を伴う候補を先に出す。同じ名前が両方に出たら価格付きを残す。
        val seen = mutableSetOf<String>()
        return (withPrice + withoutPrice)
            .filter { seen.add(it.name) }
            .take(MAX_CANDIDATES)
    }
}
