package app.lunchlog.core

import app.lunchlog.core.ocr.MenuTextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MenuTextParserTest {

    @Test
    fun `価格を伴う行からメニュー名と価格を取り出す`() {
        val candidates = MenuTextParser.parse(
            """
            日替わり定食 980円
            からあげ定食 ¥1,200
            チキン南蛮定食 1100
            """.trimIndent(),
        )

        assertEquals(
            listOf("日替わり定食" to 980, "からあげ定食" to 1200, "チキン南蛮定食" to 1100),
            candidates.map { it.name to it.price },
        )
        assertTrue(candidates.all { it.hasPrice })
    }

    @Test
    fun `区切り文字を名前から取り除く`() {
        val candidates = MenuTextParser.parse("さばの塩焼き定食 ......... 850円")
        assertEquals("さばの塩焼き定食", candidates.single().name)
        assertEquals(850, candidates.single().price)
    }

    @Test
    fun `価格のない短い行も候補にする`() {
        val candidates = MenuTextParser.parse(
            """
            本日のおすすめ
            ハンバーグ定食 1000円
            """.trimIndent(),
        )
        // 価格を伴うほうが確からしいので先に出す。
        assertEquals(listOf("ハンバーグ定食", "本日のおすすめ"), candidates.map { it.name })
        assertEquals(listOf(true, false), candidates.map { it.hasPrice })
    }

    @Test
    fun `長い行は説明文とみなして拾わない`() {
        val long = "当店のお料理はすべて国産の食材を使用しており、ご注文をいただいてから丁寧にお作りしております"
        assertTrue(MenuTextParser.parse(long).isEmpty())
    }

    @Test
    fun `記号だけの行は拾わない`() {
        assertTrue(MenuTextParser.parse("―――――\n＊＊＊").isEmpty())
    }

    @Test
    fun `同じ名前は1件にまとめる`() {
        val candidates = MenuTextParser.parse(
            """
            カレーライス 800円
            カレーライス
            """.trimIndent(),
        )
        assertEquals(1, candidates.size)
        assertEquals(800, candidates.single().price)
    }

    @Test
    fun `カンマ区切りの価格を数値にする`() {
        assertEquals(1200, MenuTextParser.parse("特上寿司 1,200円").single().price)
    }

    @Test
    fun `候補は上限まで`() {
        val text = (1..30).joinToString("\n") { "メニュー$it ${it * 100 + 500}円" }
        assertEquals(MenuTextParser.MAX_CANDIDATES, MenuTextParser.parse(text).size)
    }

    @Test
    fun `空の入力は空を返す`() {
        assertTrue(MenuTextParser.parse("").isEmpty())
        assertTrue(MenuTextParser.parse("   \n  \n ").isEmpty())
    }
}
