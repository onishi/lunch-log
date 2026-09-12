package app.lunchlog.core.tabelog

import java.net.URI
import java.net.URISyntaxException

/**
 * 食べログ URL の検証と正規化 (SPEC §6.3)。
 *
 * **食べログにはアクセスしない。** 保存するのは URL の文字列だけで、
 * ページの取得も解析も行わない。ここでやるのは、貼り付けられた文字列を
 * 見た目の上で整えることだけ。
 *
 * 条件を満たさない URL も**保存自体は許可する** (SPEC §6.3)。利用者が
 * 意図して別の URL を入れている可能性があるため、警告に留めて判断を委ねる。
 */
object TabelogUrl {

    private const val HOST = "tabelog.com"

    enum class Warning {
        /** https ではない。 */
        NOT_HTTPS,

        /** 食べログのホストではない。 */
        NOT_TABELOG,

        /** URL として解釈できない。 */
        MALFORMED,
    }

    data class Inspection(
        /** 正規化した URL。解釈できなければ入力のまま。 */
        val url: String,
        val isTabelog: Boolean,
        val warning: Warning?,
    )

    fun inspect(raw: String): Inspection {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Inspection(trimmed, isTabelog = false, warning = Warning.MALFORMED)
        }

        val uri = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return Inspection(trimmed, isTabelog = false, warning = Warning.MALFORMED)
        }

        val host = uri.host?.lowercase()
        if (host == null || uri.scheme == null) {
            return Inspection(trimmed, isTabelog = false, warning = Warning.MALFORMED)
        }

        // "eviltabelog.com" を食べログと誤認しないよう、ドット区切りで判定する。
        val isTabelog = host == HOST || host.endsWith(".$HOST")
        val warning = when {
            !isTabelog -> Warning.NOT_TABELOG
            !uri.scheme.equals("https", ignoreCase = true) -> Warning.NOT_HTTPS
            else -> null
        }

        return Inspection(url = normalize(uri) ?: trimmed, isTabelog = isTabelog, warning = warning)
    }

    /**
     * クエリと fragment を落とす (SPEC §6.3, §10)。
     *
     * 共有経由の URL にはトラッキング ID が付く。そのまま保存すると、
     * 記録を書き出したときに一緒に外へ出てしまう。アフィリエイトタグを
     * 付けない方針 (SPEC §10) の裏返しでもある。
     */
    private fun normalize(uri: URI): String? {
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host?.lowercase() ?: return null
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val path = uri.path.orEmpty().ifEmpty { "/" }
        return "$scheme://$host$port$path"
    }
}
