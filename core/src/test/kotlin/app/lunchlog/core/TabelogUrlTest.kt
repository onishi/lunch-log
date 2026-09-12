package app.lunchlog.core

import app.lunchlog.core.tabelog.TabelogUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabelogUrlTest {

    @Test
    fun `食べログのURLを受け入れる`() {
        val result = TabelogUrl.inspect("https://tabelog.com/tokyo/A1303/A130301/13001234/")
        assertTrue(result.isTabelog)
        assertNull(result.warning)
        assertEquals("https://tabelog.com/tokyo/A1303/A130301/13001234/", result.url)
    }

    @Test
    fun `サブドメインも食べログとして扱う`() {
        assertTrue(TabelogUrl.inspect("https://s.tabelog.com/tokyo/A1303/").isTabelog)
    }

    @Test
    fun `似た名前のホストは食べログではない`() {
        // "eviltabelog.com" を通すと、無関係なサイトへのリンクを食べログとして出してしまう。
        val result = TabelogUrl.inspect("https://eviltabelog.com/tokyo/")
        assertFalse(result.isTabelog)
        assertEquals(TabelogUrl.Warning.NOT_TABELOG, result.warning)
    }

    @Test
    fun `クエリとfragmentを落とす`() {
        // 共有経由の URL に付くトラッキング ID を保存しない (SPEC §6.3, §10)。
        val result = TabelogUrl.inspect("https://tabelog.com/tokyo/A1303/13001234/?utm_source=line&ref=abc#top")
        assertEquals("https://tabelog.com/tokyo/A1303/13001234/", result.url)
    }

    @Test
    fun `httpは警告するが値は残す`() {
        // SPEC §6.3: 条件を満たさなくても保存自体は許可する。
        val result = TabelogUrl.inspect("http://tabelog.com/tokyo/")
        assertTrue(result.isTabelog)
        assertEquals(TabelogUrl.Warning.NOT_HTTPS, result.warning)
        assertEquals("http://tabelog.com/tokyo/", result.url)
    }

    @Test
    fun `前後の空白を落とす`() {
        assertEquals(
            "https://tabelog.com/tokyo/",
            TabelogUrl.inspect("  https://tabelog.com/tokyo/  ").url,
        )
    }

    @Test
    fun `URLとして解釈できないものは警告する`() {
        listOf("", "   ", "これはURLではありません", "http://").forEach { input ->
            assertEquals(input, TabelogUrl.Warning.MALFORMED, TabelogUrl.inspect(input).warning)
        }
    }

    @Test
    fun `パスがなければルートにする`() {
        assertEquals("https://tabelog.com/", TabelogUrl.inspect("https://tabelog.com").url)
    }

    @Test
    fun `大文字のホストも食べログとして扱う`() {
        val result = TabelogUrl.inspect("https://TABELOG.COM/tokyo/")
        assertTrue(result.isTabelog)
        assertEquals("https://tabelog.com/tokyo/", result.url)
    }
}
